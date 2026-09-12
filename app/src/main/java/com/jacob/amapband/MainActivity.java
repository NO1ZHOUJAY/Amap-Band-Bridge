package com.jacob.amapband;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

public final class MainActivity extends Activity {
    private TextView status;
    private TextView log;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BridgeReceiver.ensureChannel(getSystemService(NotificationManager.class));
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private View buildUi() {
        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = text("公交腕上导航", 24, Color.rgb(20, 25, 35));
        root.addView(title);
        TextView intro = text("LSPosed 从高德捕获站点状态，本应用把它转换成小米运动健康可转发的标准通知。", 15, Color.DKGRAY);
        intro.setPadding(0, dp(10), 0, dp(14));
        root.addView(intro);

        status = text("", 16, Color.BLACK);
        root.addView(status);

        Button test = button("发送高德真实格式测试通知");
        test.setOnClickListener(v -> BridgeReceiver.postTest(this));
        root.addView(test);

        Button notificationSettings = button("打开本应用通知设置");
        notificationSettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        });
        root.addView(notificationSettings);

        Button moduleSettings = button("打开 LSPosed 管理器");
        moduleSettings.setOnClickListener(v -> {
            Intent intent = getPackageManager().getLaunchIntentForPackage("org.lsposed.manager");
            if (intent != null) startActivity(intent);
        });
        root.addView(moduleSettings);

        Button refresh = button("刷新捕获记录");
        refresh.setOnClickListener(v -> refresh());
        root.addView(refresh);

        TextView logTitle = text("最近捕获", 18, Color.BLACK);
        logTitle.setPadding(0, dp(18), 0, dp(8));
        root.addView(logTitle);
        log = text("尚无数据", 13, Color.DKGRAY);
        log.setTextIsSelectable(true);
        root.addView(log);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
    }

    private void refresh() {
        if (status == null) return;
        SharedPreferences prefs = getSharedPreferences(BridgeReceiver.PREFS, MODE_PRIVATE);
        long updated = prefs.getLong("updated", 0L);
        if (updated == 0L) {
            status.setText("状态：等待高德数据\n启用模块并勾选高德地图后，强制停止再重开高德。");
        } else {
            status.setText("状态：已捕获\n最后更新：" + DateFormat.getDateTimeInstance().format(new Date(updated))
                    + "\n内容：" + prefs.getString("last", ""));
        }
        log.setText(prefs.getString("log", "尚无数据"));
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        button.setLayoutParams(params);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
