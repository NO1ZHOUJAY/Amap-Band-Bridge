package com.jacob.amapband;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class AmapHook implements IXposedHookLoadPackage {
    private static final String AMAP = "com.autonavi.minimap";
    private static final String ACTION_UPDATE = "com.jacob.amapband.NAV_UPDATE";
    private static final String RECEIVER = "com.jacob.amapband.BridgeReceiver";
    private static final Map<String, Long> RECENT = new LinkedHashMap<String, Long>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > 80;
        }
    };
    private static volatile Context applicationContext;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!AMAP.equals(lpparam.packageName)) return;
        XposedBridge.log("AmapBand: loaded in " + lpparam.processName);

        hookTextView();
        hookAjxLabels(lpparam.classLoader);
        hookContentDescription();
        hookSpeech();
        hookNotificationBuilder();
    }

    private static void hookTextView() {
        XposedBridge.hookAllMethods(TextView.class, "setText", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof TextView) {
                    TextView view = (TextView) param.thisObject;
                    capture("text", view.getText(), view.getContext());
                }
            }
        });
    }

    private static void hookAjxLabels(ClassLoader classLoader) {
        // 高德 AJX 的 Label 直接继承 View，并不是 TextView。公交导航的可见站点文本
        // 大量通过这些 setText(String) 写入，之前只 Hook TextView 会完整漏掉。
        String[] classNames = {
                "com.autonavi.minimap.ajx3.widget.view.Label",
                "com.autonavi.map.widget.AjxLabel",
                "com.autonavi.minimap.ajx3.widget.gradient.LinearGradientLabel",
                "com.autonavi.minimap.ajx3.widget.gradient.AjxLinearGradientLabel"
        };
        for (String className : classNames) {
            try {
                Class<?> labelClass = Class.forName(className, false, classLoader);
                XposedBridge.hookAllMethods(labelClass, "setText", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Context context = param.thisObject instanceof View
                                ? ((View) param.thisObject).getContext() : null;
                        if (param.args != null && param.args.length > 0) {
                            capture("ajx-label", param.args[0], context);
                        }
                    }
                });
                XposedBridge.log("AmapBand: hooked AJX label " + className);
            } catch (Throwable throwable) {
                XposedBridge.log("AmapBand: AJX label unavailable " + className);
            }
        }
    }

    private static void hookContentDescription() {
        XposedBridge.hookAllMethods(View.class, "setContentDescription", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Context context = param.thisObject instanceof View ? ((View) param.thisObject).getContext() : null;
                if (param.args != null && param.args.length > 0) capture("description", param.args[0], context);
            }
        });
    }

    private static void hookSpeech() {
        XposedBridge.hookAllMethods(TextToSpeech.class, "speak", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args != null && param.args.length > 0) capture("tts", param.args[0], null);
            }
        });
    }

    private static void hookNotificationBuilder() {
        XC_MethodHook callback = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args != null && param.args.length > 0) capture("notification", param.args[0], null);
            }
        };
        XposedBridge.hookAllMethods(Notification.Builder.class, "setContentTitle", callback);
        XposedBridge.hookAllMethods(Notification.Builder.class, "setContentText", callback);
    }

    private static void capture(String source, Object value, Context sourceContext) {
        if (!(value instanceof CharSequence)) return;
        String text = NavParser.clean(value.toString());
        if (!NavParser.isCandidate(text)) return;

        long now = SystemClock.elapsedRealtime();
        synchronized (RECENT) {
            Long last = RECENT.get(source + '\n' + text);
            if (last != null && now - last < 3000L) return;
            RECENT.put(source + '\n' + text, now);
        }

        try {
            if (sourceContext != null) {
                Context candidate = sourceContext.getApplicationContext();
                applicationContext = candidate != null ? candidate : sourceContext;
            }
            Context context = applicationContext;
            if (context == null) return;
            Intent intent = new Intent(ACTION_UPDATE);
            intent.setComponent(new ComponentName("com.jacob.amapband", RECEIVER));
            // HyperOS 会延迟普通后台广播；导航事件需要尽快送达接收应用。
            // 自启动/后台运行权限仍需由用户在系统设置中允许。
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            intent.putExtra("source", source);
            intent.putExtra("text", text);
            intent.putExtra("captured_at", System.currentTimeMillis());
            context.sendBroadcast(intent);
        } catch (Throwable throwable) {
            XposedBridge.log(throwable);
        }
    }
}
