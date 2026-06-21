package com.arya.client;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String PREFS = "arya_prefs";
    private static final String KEY_SETUP_DONE = "setup_done";

    private WebView webView;
    private FrameLayout setupOverlay;
    private SharedPreferences prefs;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        requestNotificationPermissionIfNeeded();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0F1115);

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        if (Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // IMPORTANT: No native Howdies login is injected here.
                // The website is the only place that logs in, so there is no double-login/IP-block risk.
            }
        });
        webView.setWebChromeClient(new WebChromeClient());

        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        setupOverlay = createSetupOverlay();
        root.addView(setupOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        setContentView(root);
        openIncludedWebsite();

        if (prefs.getBoolean(KEY_SETUP_DONE, false)) {
            setupOverlay.setVisibility(FrameLayout.GONE);
            startSafeForegroundService();
        }
    }

    private FrameLayout createSetupOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xEE0F1115);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackgroundColor(0xFF16181F);
        card.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = label("Arya Client", 22, 0xFFFFFFFF);
        title.setGravity(Gravity.CENTER);
        TextView hint = label(
                "Safe version: website hi login karegi. App koi second Howdies login nahi karegi, isliye logout/IP block issue nahi aayega. Background permission allow karne ke baad ye screen hide ho jayegi.",
                14,
                0xFFB8C0CC
        );
        hint.setGravity(Gravity.CENTER);

        Button allowBtn = btn("Allow Background & Continue");
        Button continueBtn = btn("Continue Only");

        allowBtn.setOnClickListener(v -> {
            prefs.edit().putBoolean(KEY_SETUP_DONE, true).apply();
            setupOverlay.setVisibility(FrameLayout.GONE);
            startSafeForegroundService();
            requestIgnoreBatteryOptimization();
            Toast.makeText(this, "Safe mode enabled. Login website ke andar hi karo.", Toast.LENGTH_LONG).show();
        });

        continueBtn.setOnClickListener(v -> {
            prefs.edit().putBoolean(KEY_SETUP_DONE, true).apply();
            setupOverlay.setVisibility(FrameLayout.GONE);
            Toast.makeText(this, "Website UI opened. Login website ke andar hi karo.", Toast.LENGTH_LONG).show();
        });

        card.addView(title);
        card.addView(hint);
        card.addView(allowBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        card.addView(continueBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.CENTER;
        lp.setMargins(dp(18), 0, dp(18), 0);
        overlay.addView(card, lp);
        return overlay;
    }

    private TextView label(String text, int sp, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setPadding(0, dp(8), 0, dp(8));
        return v;
    }

    private Button btn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        return b;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private String includedWebsiteUrl() {
        String url = getString(getResources().getIdentifier("hosted_website_url", "string", getPackageName())).trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
        return url;
    }

    private void openIncludedWebsite() {
        webView.loadUrl(includedWebsiteUrl());
    }

    private void startSafeForegroundService() {
        Intent i = new Intent(this, AryaForegroundService.class);
        i.setAction(AryaForegroundService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    private void requestIgnoreBatteryOptimization() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (Build.VERSION.SDK_INT >= 23 && pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        } catch (Exception e) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
