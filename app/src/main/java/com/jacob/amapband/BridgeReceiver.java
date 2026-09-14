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
    private static final int FIRST_NOTIFICATION_ID = 14001;
    private static final int SECOND_NOTIFICATION_ID = 14002;
    private static final long NAVIGATION_SESSION_MS = 6 * 60 * 60_000L;

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
        if (lines.length > 400) {
            StringBuilder trimmed = new StringBuilder();
            for (int i = 0; i < 400; i++) {
                if (i > 0) trimmed.append('\n');
                trimmed.append(lines[i]);
            }
            log = trimmed.toString();
        }
        prefs.edit().putString("last", text).putString("source", source)
                .putLong("updated", now).putString("log", log).apply();

        boolean sessionWasActive = now < prefs.getLong("navigation_active_until", 0L);
        if (NavParser.endsNavigation(text)) {
            prefs.edit().remove("navigation_active_until").apply();
            return;
        }
        if (NavParser.startsNavigation(text)) {
            SharedPreferences.Editor session = prefs.edit()
                    .putLong("navigation_active_until", now + NAVIGATION_SESSION_MS);
            if (!sessionWasActive) {
                session.remove("notified_key").remove("notified_at");
            }
            session.apply();
            sessionWasActive = true;
        }

        NavParser.Result result = NavParser.parse(text);
        if (sessionWasActive && result.useful && shouldPost(prefs, result, now)) {
            postNavigationNotification(context, result);
        }
    }

    static void postTest(Context context) {
        postNavigationNotification(context,
                NavParser.parse("1站后后 沈杜公路换乘 8号线(市光路方向)"));
    }

    private static void postNavigationNotification(Context context, NavParser.Result result) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        ensureChannel(manager);

        String title;
        if (result.busImminent) {
            title = "公交即将进站：准备上车";
        } else if (result.busMinutes != null) {
            title = "公交快到了：准备上车";
        } else if (result.remaining != null && result.remaining == 1 && result.action != null) {
            title = "下一站" + result.action;
        } else if (result.remaining != null && result.remaining == 3 && result.action != null) {
            title = "提前提醒：3站后" + result.action;
        } else if (result.urgent) {
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
        if (result.busImminent) {
            detail.append("车辆即将到达上车站");
        } else if (result.busMinutes != null) {
            detail.append("预计 ").append(result.busMinutes).append(" 分钟");
            if (result.busStops != null) detail.append(" · ").append(result.busStops).append(" 站");
        } else if (result.remaining != null) {
            detail.append("剩余 ").append(result.remaining).append(" 站");
        }
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
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int previousId = prefs.getInt("notification_id", SECOND_NOTIFICATION_ID);
        int nextId = previousId == FIRST_NOTIFICATION_ID ? SECOND_NOTIFICATION_ID : FIRST_NOTIFICATION_ID;
        manager.cancel(previousId);
        manager.notify(nextId, notification);
        prefs.edit().putInt("notification_id", nextId).apply();
    }

    private static boolean shouldPost(SharedPreferences prefs, NavParser.Result result, long now) {
        long previousAt = prefs.getLong("notified_at", 0L);

        String key;
        long duplicateWindow;
        if (result.busImminent) {
            if (result.busNumber != null && result.busNumber > 1) return false;
            key = "bus|imminent";
            duplicateWindow = 10 * 60_000L;
        } else if (result.busMinutes != null) {
            if (result.busNumber != null && result.busNumber > 1) return false;
            if (result.busMinutes > 3 && (result.busStops == null || result.busStops > 2)) return false;
            key = "bus|near";
            duplicateWindow = 10 * 60_000L;
        } else if (result.urgent) {
            key = "urgent|" + result.raw;
            duplicateWindow = 30 * 60_000L;
        } else {
            // 普通站数递减只记录。3 站时提前预告，1 站时强提醒，避免每站震动。
            if (result.remaining == null || (result.remaining != 3 && result.remaining != 1)) return false;
            key = "step|" + result.remaining + '|' + result.target + '|' + result.action;
            duplicateWindow = 30 * 60_000L;
        }
        if (key.equals(prefs.getString("notified_key", "")) && now - previousAt < duplicateWindow) {
            return false;
        }

        prefs.edit()
                .putString("notified_key", key)
                .putLong("notified_at", now)
                .apply();
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
