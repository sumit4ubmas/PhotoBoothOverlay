# Photo Booth Overlay APK

A companion overlay app that adds a persistent floating 📷 button on top of all screens on your tablet. Tap it to launch/close the Photo Booth app at any time.

## Features
- Floating camera button always visible on top of every app
- Tap to **launch** Photo Booth, tap again to **dismiss** it to background
- **Draggable** — reposition the button anywhere on screen
- **Auto-starts on boot** — always available after tablet restarts
- Works on Android 8.0+ (API 26+)

---

## How to Build

### Option 1: GitHub Actions (Easiest — no setup needed)
1. Create a new GitHub repository
2. Push this entire folder to it:
   ```
   git init
   git add .
   git commit -m "Initial commit"
   git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
   git push -u origin main
   ```
3. Go to your repo on GitHub → **Actions** tab
4. The build runs automatically — wait ~3 minutes
5. Click the completed workflow → scroll down to **Artifacts** → download `PhotoBoothOverlay-debug.apk`

### Option 2: Android Studio
1. Open Android Studio → **Open** → select this folder
2. Wait for Gradle sync to finish
3. Click **Build → Build Bundle(s) / APK(s) → Build APK(s)**
4. APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

### Option 3: Command Line
```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## Installation on Tablet
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```
Or copy the APK to the tablet and open it with a file manager.

## First-Time Setup
1. Open **Photo Booth Overlay** app
2. Tap **GRANT DRAW OVER APPS PERMISSION** → enable it in Settings
3. Tap **START OVERLAY**
4. The red 📷 button will appear — drag it anywhere, tap to toggle Photo Booth

## Requirements
- Android 8.0+ (API 26)
- Photo Booth app (`com.example.photobooth`) must be installed
