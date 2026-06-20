package com.arya.client;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
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
import android.view.View;
import android.webkit.JavascriptInterface;
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

    private String lastUsers = "";
    private String lastPassword = "";
    private String lastRooms = "";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
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
                injectAryaBridgeHooks();
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AryaBridge(this), "AryaAndroid");

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
            setupOverlay.setVisibility(View.GONE);
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
        TextView hint = label("Ek baar background permission allow karo. Uske baad ye screen hide ho jayegi aur sirf website UI dikhegi. Username/password/rooms website ke andar hi daalna.", 14, 0xFFB8C0CC);
        hint.setGravity(Gravity.CENTER);

        Button allowBtn = btn("Allow Background & Continue");
        Button continueBtn = btn("Continue Only");

        allowBtn.setOnClickListener(v -> {
            prefs.edit().putBoolean(KEY_SETUP_DONE, true).apply();
            setupOverlay.setVisibility(View.GONE);
            requestIgnoreBatteryOptimization();
            Toast.makeText(this, "Website UI opened. Login karte hi background service start hogi.", Toast.LENGTH_LONG).show();
        });

        continueBtn.setOnClickListener(v -> {
            prefs.edit().putBoolean(KEY_SETUP_DONE, true).apply();
            setupOverlay.setVisibility(View.GONE);
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

    private void startAryaService(String users, String pass, String rooms) {
        users = users == null ? "" : users.trim();
        pass = pass == null ? "" : pass;
        rooms = rooms == null ? "" : rooms.trim();
        if (users.isEmpty() || pass.isEmpty()) return;

        lastUsers = users;
        lastPassword = pass;
        if (!rooms.isEmpty()) lastRooms = rooms;

        Intent i = new Intent(this, AryaForegroundService.class);
        i.setAction(AryaForegroundService.ACTION_START);
        i.putExtra(AryaForegroundService.EXTRA_USERS, lastUsers);
        i.putExtra(AryaForegroundService.EXTRA_PASSWORD, lastPassword);
        i.putExtra(AryaForegroundService.EXTRA_ROOMS, lastRooms);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    private void updateServiceRooms(String rooms) {
        rooms = rooms == null ? "" : rooms.trim();
        if (rooms.isEmpty()) return;
        lastRooms = rooms;

        Intent i = new Intent(this, AryaForegroundService.class);
        i.setAction(AryaForegroundService.ACTION_UPDATE_ROOMS);
        i.putExtra(AryaForegroundService.EXTRA_ROOMS, lastRooms);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    private void stopAryaService() {
        Intent i = new Intent(this, AryaForegroundService.class);
        i.setAction(AryaForegroundService.ACTION_STOP);
        startService(i);
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

    private void injectAryaBridgeHooks() {
        String js = "javascript:(function(){" +
                "if(window.__aryaNativeBridgeInstalled)return;window.__aryaNativeBridgeInstalled=true;" +
                "function v(id){var e=document.getElementById(id);return e?String(e.value||'').trim():'';}" +
                "function cleanRooms(s){var out=[];String(s||'').split(/[,;\\s]+/).forEach(function(x){x=x.trim();if(/^\\d+$/.test(x)&&out.indexOf(x)<0)out.push(x);});return out.join(',');}" +
                "function getRooms(){return cleanRooms((localStorage.getItem('arya_bg_rooms')||'')+','+v('roomId'));}" +
                "function addRoom(id){id=String(id||'').trim();if(!/^\\d+$/.test(id))return;var old=getRooms();var next=cleanRooms(old+','+id);localStorage.setItem('arya_bg_rooms',next);try{AryaAndroid.updateRooms(next);}catch(e){}}" +
                "function startBg(){try{var u=v('username'),p=v('password'),r=getRooms();if(u&&p&&window.AryaAndroid){AryaAndroid.startBackground(u,p,r);}}catch(e){}}" +
                "document.addEventListener('click',function(e){var t=e.target;" +
                "if(t&&t.closest&&t.closest('#loginBtn')){startBg();setTimeout(startBg,1200);}" +
                "if(t&&t.closest&&(t.closest('#joinByIdBtn')||t.closest('#joinByNameBtn')||t.closest('.room-item'))){setTimeout(function(){addRoom(v('roomId'));startBg();},900);}" +
                "if(t&&t.closest&&t.closest('#disconnectBtn')){try{AryaAndroid.stopBackground();}catch(x){}}" +
                "},true);" +
                "setInterval(function(){var u=v('username'),p=v('password');if(u&&p){startBg();}},60000);" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    public class AryaBridge {
        private final Context context;
        AryaBridge(Context context) { this.context = context; }

        @JavascriptInterface
        public void startBackground(String usernames, String password, String roomIds) {
            runOnUiThread(() -> startAryaService(usernames, password, roomIds));
        }

        @JavascriptInterface
        public void updateRooms(String roomIds) {
            runOnUiThread(() -> updateServiceRooms(roomIds));
        }

        @JavascriptInterface
        public void stopBackground() {
            runOnUiThread(() -> stopAryaService());
        }
    }
}
