Arya Client - GitHub Cloud Build Method (No Android Studio)

This project can build APK in GitHub Actions cloud, so your phone/PC does not need Android Studio.

Steps:
1. Extract this ZIP. It is small; Android Studio is NOT needed.
2. Open github.com and create a NEW PUBLIC repository, for example: arya-background-client.
3. Upload the CONTENTS of the AryaBackgroundClient folder to the repository root.
   Important: build.gradle, settings.gradle, app folder, and .github folder must be at repo root.
4. Open repo > Actions tab.
5. If GitHub asks, click "I understand my workflows, go ahead and enable them".
6. Click "Build Arya APK" workflow.
7. Click "Run workflow".
8. Wait until green check appears.
9. Open the completed run, scroll to Artifacts, download "AryaClient-debug-apk".
10. Unzip that artifact and install app-debug.apk on your Android phone.

In the app:
1. Add hosted website URL.
2. Add usernames: sher, checkbot, song
3. Add password.
4. Add room IDs: 894,689
5. Tap Start Background WebSocket.
6. Tap Allow Battery Background.
7. Tap Open Website UI.

Notes:
- This is a debug APK, good for personal/share testing. It is not Play Store release signed.
- Keep the notification visible; Android needs Foreground Service notification for background socket.
- Some phones still need Settings > Battery > App battery usage > Arya Client > Unrestricted.
