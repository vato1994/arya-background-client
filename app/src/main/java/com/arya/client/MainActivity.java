package com.arya.client;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private EditText urlBox;
    private EditText usersBox;
    private EditText passBox;
    private EditText roomsBox;
    private WebView webView;
    private TextView status;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNotificationPermissionIfNeeded();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18, 18, 18, 18);
        root.setBackgroundColor(0xFF0F1115);

        TextView title = label("Arya Client Background Version");
        title.setTextSize(20);
        title.setTextColor(0xFFFFFFFF);
        root.addView(title);

        TextView hint = label("Start Background pe tap karo. Notification visible rahegi, tab service WebSocket ko background me alive rakhegi.");
        hint.setTextColor(0xFFB8C0CC);
        root.addView(hint);

        urlBox = input("Hosted website URL, example: https://your-site.com/arya.html", "");
        usersBox = input("Username(s): sher, checkbot, song", "");
        passBox = input("Password same for IDs", "");
        passBox.setInputType(0x00000081); // textPassword
        roomsBox = input("Room IDs to keep joined: 894,689", "");

        Button startBtn = btn("Start Background WebSocket");
        Button stopBtn = btn("Stop Background");
        Button openBtn = btn("Open Website UI");
        Button batteryBtn = btn("Allow Battery Background");

        status = label("Status: idle");
        status.setTextColor(0xFFFFCC80);

        root.addView(urlBox);
        root.addView(usersBox);
        root.addView(passBox);
        root.addView(roomsBox);
        root.addView(startBtn);
        root.addView(stopBtn);
        root.addView(openBtn);
        root.addView(batteryBtn);
        root.addView(status);

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AryaBridge(this), "AryaAndroid");

        LinearLayout.LayoutParams webLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        root.addView(webView, webLp);

        setContentView(root);

        startBtn.setOnClickListener(v -> startAryaService());
        stopBtn.setOnClickListener(v -> stopAryaService());
        openBtn.setOnClickListener(v -> openWebsite());
        batteryBtn.setOnClickListener(v -> requestIgnoreBatteryOptimization());
    }

    private TextView label(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setPadding(0, 8, 0, 8);
        return v;
    }

    private EditText input(String hint, String text) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(text);
        e.setSingleLine(true);
        e.setTextColor(0xFFFFFFFF);
        e.setHintTextColor(0xFF7C8AA0);
        e.setBackgroundColor(0xFF1B1F28);
        e.setPadding(18, 14, 18, 14);
        return e;
    }

    private Button btn(String text) {
        Button b = new Button(this);
        b.setText(text);
        return b;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private void startAryaService() {
        String users = usersBox.getText().toString().trim();
        String pass = passBox.getText().toString();
        String rooms = roomsBox.getText().toString().trim();
        if (users.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Username/password required", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(this, AryaForegroundService.class);
        i.setAction(AryaForegroundService.ACTION_START);
        i.putExtra(AryaForegroundService.EXTRA_USERS, users);
        i.putExtra(AryaForegroundService.EXTRA_PASSWORD, pass);
        i.putExtra(AryaForegroundService.EXTRA_ROOMS, rooms);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        status.setText("Status: background service started");
        Toast.makeText(this, "Arya background service started", Toast.LENGTH_SHORT).show();
    }

    private void stopAryaService() {
        Intent i = new Intent(this, AryaForegroundService.class);
        i.setAction(AryaForegroundService.ACTION_STOP);
        startService(i);
        status.setText("Status: background service stopped");
    }

    private void openWebsite() {
        String url = urlBox.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "Website URL daalo", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
        webView.loadUrl(url);
        status.setText("Status: website opened");
    }

    private void requestIgnoreBatteryOptimization() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (Build.VERSION.SDK_INT >= 23 && pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else {
                Toast.makeText(this, "Battery optimization already allowed", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    public class AryaBridge {
        private final Context context;
        AryaBridge(Context context) { this.context = context; }

        @JavascriptInterface
        public void startBackground(String usernames, String password, String roomIds) {
            runOnUiThread(() -> {
                usersBox.setText(usernames == null ? "" : usernames);
                passBox.setText(password == null ? "" : password);
                roomsBox.setText(roomIds == null ? "" : roomIds);
                startAryaService();
            });
        }

        @JavascriptInterface
        public void stopBackground() {
            runOnUiThread(() -> stopAryaService());
        }
    }
}
