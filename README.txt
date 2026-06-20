Arya Background Client

Free build method:
1. Install Android Studio from developer.android.com/studio.
2. Open Android Studio > Open > select this AryaBackgroundClient folder.
3. Wait for Gradle Sync to finish.
4. Connect Android phone with USB debugging OR use Build > Build APK(s).
5. Install APK on phone.
6. Open Arya Client.
7. Enter your hosted website URL, usernames, password, and room IDs.
8. Tap Start Background WebSocket.
9. Tap Allow Battery Background and allow it.
10. Tap Open Website UI for your existing HTML UI.

Hosted HTML bridge optional code:
When user logs in, you can call:
if (window.AryaAndroid) window.AryaAndroid.startBackground(usernamesCsv, password, roomIdsCsv);
When user disconnects:
if (window.AryaAndroid) window.AryaAndroid.stopBackground();

Important:
- The background connection is in Android native Foreground Service, not WebView JavaScript.
- Android shows a permanent notification while it runs.
- Some phone brands may still need Settings > Battery > Unrestricted.
