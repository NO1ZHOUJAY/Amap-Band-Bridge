package com.jacob.amapband;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;

import java.text.DateFormat;
import java.util.Date;

public final class BridgeReceiver extends BroadcastReceiver {
    static final String CHANNEL_ID = "bus_navigation";
    static final String PREFS = "bridge_state";
    private static final int NOTIFICATION_ID = 14001;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!"com.jacob.amapband.NAV_UPDATE".equals(intent.getAction())) return;
        String text = intent.getStringExtra("text");
        String source = intent.getStringExtra("source");
        if (text == null || !NavParser.isCandidate(text)) return;

        long now = System.currentTimeMillis();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String oldLog = prefs.getString("log", "");
        String line = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date(now))
                + " [" + source + "] " + text;
        String log = line + (oldLog.isEmpty() ? "" : "\n" + oldLog);
        String[] lines = log.split("\n");
        if (lines.length > 60) {
            StringBuilder trimmed = new StringBuilder();
            for (int i = 0; i < 60; i++) {
                if (i > 0) trimmed.append('\n');
                trimmed.append(lines[i]);
            }
            log = trimmed.toString();
        }
        prefs.edit().putString("last", text).putString("source", source)
                .putLong("updated", now).putString("log", log).apply();

        NavParser.Result result = NavParser.parse(text);
        if (result.useful && shouldPost(prefs, result, now)) postNavigationNotification(context, result);
    }

    static void postTest(Context context) {
        postNavigationNotification(context,
                NavParser.parse("1站后后 沈杜公路换乘 8号线(市光路方向)"));
    }

    private static void postNavigationNotification(Context context, NavParser.Result result) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        ensureChannel(manager);

        String title;
        if (result.urgent) {
            title = "公交提醒：准备下车";
        } else if (result.current != null) {
            title = "当前：" + result.current;
        } else if (result.next != null) {
            title = "下一站：" + result.next;
        } else if (result.target != null && result.action != null) {
            title = "下一步：" + result.target + result.action;
        } else {
            title = "公交导航";
        }

        StringBuilder detail = new StringBuilder();
        if (result.remaining != null) detail.append("剩余 ").append(result.remaining).append(" 站");
        if (result.next != null) {
            if (detail.length() > 0) detail.append(" · ");
            detail.append("下一站 ").append(result.next);
        } else if (result.target != null && result.action != null) {
            if (detail.length() > 0) detail.append(" · ");
            detail.append(result.target).append(result.action);
        }
        if (detail.length() == 0) detail.append(result.raw);

        Intent launch = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bus)
                .setContentTitle(title)
                .setContentText(detail.toString())
                .setStyle(new Notification.BigTextStyle().bigText(detail + "\n" + result.raw))
                .setContentIntent(pendingIntent)
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setColor(Color.rgb(22, 119, 255))
                .setOnlyAlertOnce(false)
                .setAutoCancel(false)
                .build();
        manager.notify(NOTIFICATION_ID, notification);
    }

    private static boolean shouldPost(SharedPreferences prefs, NavParser.Result result, long now) {
        int previousRemaining = prefs.getInt("notified_remaining", -1);
        long previousAt = prefs.getLong("notified_at", 0L);

        // 行程总览会在同一瞬间依次渲染当前换乘和后续路段。优先保留较近的动作，
        // 但数分钟后允许换乘完成后的新路段从小数字重新跳到较大的剩余站数。
        if (result.remaining != null && previousRemaining >= 0
                && result.remaining > previousRemaining && now - previousAt < 15_000L) {
            return false;
        }

        String key = String.valueOf(result.current) + '|' + result.next + '|'
                + result.remaining + '|' + result.target + '|' + result.action + '|' + result.urgent;
        if (key.equals(prefs.getString("notified_key", "")) && now - previousAt < 30 * 60_000L) {
            return false;
        }

        SharedPreferences.Editor editor = prefs.edit()
                .putString("notified_key", key)
                .putLong("notified_at", now);
        if (result.remaining != null) {
            editor.putInt("notified_remaining", result.remaining);
        } else {
            editor.remove("notified_remaining");
        }
        editor.apply();
        return true;
    }

    static void ensureChannel(NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "公交实时站点", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("将高德公交导航状态同步到手环");
            channel.enableVibration(true);
            manager.createNotificationChannel(channel);
        }
    }
}
