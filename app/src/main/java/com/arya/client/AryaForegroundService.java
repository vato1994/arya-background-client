package com.arya.client;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class AryaForegroundService extends Service {
    public static final String ACTION_START = "com.arya.client.START";
    public static final String ACTION_STOP = "com.arya.client.STOP";
    public static final String EXTRA_USERS = "users";
    public static final String EXTRA_PASSWORD = "password";
    public static final String EXTRA_ROOMS = "rooms";

    private static final String WS_URL = "wss://app.howdies.app/howdies/";
    private static final String CHANNEL_ID = "arya_socket_channel";
    private static final int NOTIFICATION_ID = 27204;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, AccountSession> sessions = new HashMap<>();
    private OkHttpClient client;
    private PowerManager.WakeLock wakeLock;
    private String latestStatus = "starting";

    private final Runnable heartbeatLoop = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            for (AccountSession s : new ArrayList<>(sessions.values())) {
                if (!s.desiredOnline) continue;
                if (s.webSocket == null) {
                    scheduleReconnect(s, "no socket");
                    continue;
                }
                try {
                    s.send(new JSONObject().put("handler", "ping").put("source", "arya-android-service").put("t", now).toString());
                    s.send(new JSONObject().put("handler", "getchatrooms").put("admin", s.username).toString());
                } catch (Exception ignored) {}

                if (now - s.lastSeen > 90000) {
                    try { s.webSocket.close(1000, "heartbeat timeout"); } catch (Exception ignored) {}
                    scheduleReconnect(s, "heartbeat timeout");
                }
            }
            handler.postDelayed(this, 25000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        client = new OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS) // real WebSocket protocol ping from OkHttp
                .retryOnConnectionFailure(true)
                .build();
        acquireWakeLock();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Arya starting..."));
        handler.postDelayed(heartbeatLoop, 25000);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopEverything();
            stopSelf();
            return START_NOT_STICKY;
        }

        String users = intent != null ? intent.getStringExtra(EXTRA_USERS) : "";
        String pass = intent != null ? intent.getStringExtra(EXTRA_PASSWORD) : "";
        String rooms = intent != null ? intent.getStringExtra(EXTRA_ROOMS) : "";

        startForeground(NOTIFICATION_ID, buildNotification("Arya connecting..."));
        startAccounts(users, pass, parseRooms(rooms));
        return START_STICKY;
    }

    private void startAccounts(String usersRaw, String password, List<Integer> roomIds) {
        if (usersRaw == null || usersRaw.trim().isEmpty() || password == null || password.isEmpty()) {
            updateNotification("Missing username/password");
            return;
        }
        String[] users = usersRaw.split("[,;\\s]+");
        for (String u : users) {
            String username = u.trim();
            if (username.isEmpty()) continue;
            String key = username.toLowerCase(Locale.ROOT);
            AccountSession s = sessions.get(key);
            if (s == null) {
                s = new AccountSession(username, password, roomIds);
                sessions.put(key, s);
            } else {
                s.password = password;
                s.roomIds = new ArrayList<>(roomIds);
                s.desiredOnline = true;
            }
            connect(s, 0);
        }
    }

    private List<Integer> parseRooms(String raw) {
        List<Integer> out = new ArrayList<>();
        if (raw == null) return out;
        for (String p : raw.split("[,;\\s]+")) {
            try {
                int id = Integer.parseInt(p.trim());
                if (id > 0 && !out.contains(id)) out.add(id);
            } catch (Exception ignored) {}
        }
        return out;
    }

    private void connect(AccountSession s, long delayMs) {
        handler.postDelayed(() -> {
            if (!s.desiredOnline) return;
            try { if (s.webSocket != null) s.webSocket.cancel(); } catch (Exception ignored) {}
            s.connected = false;
            s.loggedIn = false;
            s.lastSeen = System.currentTimeMillis();
            updateNotification("Connecting " + s.username + "...");
            Request req = new Request.Builder().url(WS_URL).build();
            s.webSocket = client.newWebSocket(req, new AryaSocketListener(s));
        }, Math.max(0, delayMs));
    }

    private void scheduleReconnect(AccountSession s, String why) {
        if (!s.desiredOnline || s.reconnectScheduled) return;
        s.reconnectScheduled = true;
        long delay = Math.min(s.reconnectDelayMs, 30000);
        updateNotification("Reconnecting " + s.username + "...");
        handler.postDelayed(() -> {
            s.reconnectScheduled = false;
            if (!s.desiredOnline) return;
            s.reconnectDelayMs = Math.min((long)(delay * 1.7), 30000);
            connect(s, 0);
        }, delay);
    }

    private JSONObject loginPayload(AccountSession s) throws Exception {
        JSONObject devData = new JSONObject()
                .put("uniqueID", "ANDROID-ARYA-" + s.username + "-" + (new Random().nextInt(9000) + 1000))
                .put("manufacturer", Build.MANUFACTURER == null ? "android" : Build.MANUFACTURER)
                .put("buildNumber", "237")
                .put("apiLevel", Build.VERSION.SDK_INT)
                .put("language", "en")
                .put("model", Build.MODEL == null ? "Android" : Build.MODEL)
                .put("brand", Build.BRAND == null ? "android" : Build.BRAND)
                .put("instanceID", "arya-android-" + s.username)
                .put("isEmulator", false)
                .put("isLowRamDevice", false)
                .put("OS", "Android");

        return new JSONObject()
                .put("handler", "loginnew")
                .put("username", s.username)
                .put("password", s.password)
                .put("fcmt", "arya-android-bg-" + s.username)
                .put("lp", true)
                .put("loginType", "H")
                .put("devData", devData);
    }

    private void onLoginSuccess(AccountSession s) {
        s.connected = true;
        s.loggedIn = true;
        s.reconnectDelayMs = 1000;
        updateNotification("Online: " + onlineCount() + "/" + sessions.size());
        try { s.send(new JSONObject().put("handler", "getchatrooms").put("admin", s.username).toString()); } catch (Exception ignored) {}
        rejoinRooms(s);
    }

    private void rejoinRooms(AccountSession s) {
        int i = 0;
        for (Integer rid : s.roomIds) {
            long delay = i * 700L;
            handler.postDelayed(() -> {
                try { s.send(new JSONObject().put("handler", "joinchatroom").put("roomid", rid).toString()); } catch (Exception ignored) {}
            }, delay);
            i++;
        }
    }

    private int onlineCount() {
        int n = 0;
        for (AccountSession s : sessions.values()) if (s.loggedIn) n++;
        return n;
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AryaClient:SocketWakeLock");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            }
        } catch (Exception ignored) {}
    }

    private void releaseWakeLock() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) {}
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Arya Background Socket", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Keeps Arya WebSocket connected in background");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        latestStatus = text;
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        b.setContentTitle("Arya Client running")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .setContentIntent(pi);
        return b.build();
    }

    private void updateNotification(String text) {
        latestStatus = text;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(text));
    }

    private void stopEverything() {
        handler.removeCallbacksAndMessages(null);
        for (AccountSession s : new ArrayList<>(sessions.values())) {
            s.desiredOnline = false;
            try { if (s.webSocket != null) s.webSocket.close(1000, "stopped"); } catch (Exception ignored) {}
        }
        sessions.clear();
        releaseWakeLock();
    }

    @Override public void onDestroy() {
        stopEverything();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private class AryaSocketListener extends WebSocketListener {
        private final AccountSession s;
        AryaSocketListener(AccountSession s) { this.s = s; }

        @Override public void onOpen(WebSocket webSocket, Response response) {
            s.webSocket = webSocket;
            s.connected = true;
            s.lastSeen = System.currentTimeMillis();
            try { s.send(loginPayload(s).toString()); } catch (Exception e) { scheduleReconnect(s, "login json failed"); }
        }

        @Override public void onMessage(WebSocket webSocket, String text) {
            s.lastSeen = System.currentTimeMillis();
            try {
                JSONObject msg = new JSONObject(text);
                if (msg.has("data") && msg.opt("data") instanceof JSONObject) {
                    JSONObject inner = msg.getJSONObject("data");
                    if (!inner.has("roomid") && msg.has("roomid")) inner.put("roomid", msg.opt("roomid"));
                    msg = inner;
                }
                String handlerName = msg.optString("handler", msg.optString("type", ""));
                String blob = msg.toString().toLowerCase(Locale.ROOT);

                if ("ping".equals(handlerName)) {
                    s.send(new JSONObject().put("handler", "pong").put("t", msg.optLong("t", System.currentTimeMillis())).toString());
                    return;
                }
                if ("pong".equals(handlerName)) return;

                boolean loginSuccess = (!s.loggedIn && (msg.optInt("error", -999) == 0 || "success".equalsIgnoreCase(msg.optString("status"))) && blob.contains("login"))
                        || (("loginnew".equals(handlerName) || "login".equals(handlerName) || "loginnewsuccess".equals(handlerName)) && (msg.optInt("error", -999) == 0 || blob.contains("success")));

                if (loginSuccess) onLoginSuccess(s);

                if ("joinchatroom".equals(handlerName) && msg.optInt("error", 0) == 0) {
                    updateNotification("Joined room: " + msg.optInt("roomid", msg.optInt("roomId", 0)) + " • Online " + onlineCount() + "/" + sessions.size());
                }
            } catch (Exception ignored) {
                // Ignore non-JSON or unknown packets.
            }
        }

        @Override public void onClosing(WebSocket webSocket, int code, String reason) {
            try { webSocket.close(code, reason); } catch (Exception ignored) {}
        }

        @Override public void onClosed(WebSocket webSocket, int code, String reason) {
            s.connected = false;
            s.loggedIn = false;
            if (s.desiredOnline) scheduleReconnect(s, "closed");
        }

        @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            s.connected = false;
            s.loggedIn = false;
            if (s.desiredOnline) scheduleReconnect(s, "failure");
        }
    }

    private static class AccountSession {
        final String username;
        String password;
        List<Integer> roomIds;
        WebSocket webSocket;
        boolean desiredOnline = true;
        boolean connected = false;
        boolean loggedIn = false;
        boolean reconnectScheduled = false;
        long reconnectDelayMs = 1000;
        long lastSeen = System.currentTimeMillis();

        AccountSession(String username, String password, List<Integer> roomIds) {
            this.username = username;
            this.password = password;
            this.roomIds = new ArrayList<>(roomIds);
        }

        void send(String text) {
            if (webSocket != null) webSocket.send(text);
        }
    }
}
