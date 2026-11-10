# Crux Wall

A camera climbing trainer for Android. Prop your phone up, step back, and climb a virtual wall with your body:

- **Hands climb.** Reach the glowing hold with the matching hand (L blue, R orange), keep it there to grab, then pull it down to chest height. Each pull is one move up the wall.
- **Feet balance.** The app draws your base of support between your ankles and a plumb line from your centre of mass. Leaning out of your base, or hanging too long in a pull, fills the pump bar. At 100% you fall.
- **Coach.** Spoken and on-screen feedback on balance, stance width, quiet feet, wrong hand, slips and pull speed, plus an end-of-route summary with your biggest thing to improve.
- **Grades.** V0, V2 and V4 change the number of moves, hold size, reach, grab time and balance strictness.

## How it works

```
web/index.html      The game: canvas rendering, game logic, MediaPipe Pose Landmarker
android/            Kotlin app that hosts the game full-screen in a WebView
```

Pose tracking runs **on the phone** with MediaPipe (GPU, falling back to CPU with the lite model). There is no server. Camera frames never leave the device, and there is no network round trip between moving your hand and seeing the hold react.

The Android app:

- serves `web/` from an https origin with `WebViewAssetLoader`, which camera access and ES modules require
- grants camera access to the page after the Android runtime permission
- keeps the screen awake, runs full-screen in portrait
- exposes `window.AndroidBridge.speak()` so the voice coach uses Android text-to-speech (WebView has no `speechSynthesis`)

At build time, Gradle copies `web/` into the APK and downloads MediaPipe `tasks-vision` 0.10.14 plus the full and lite pose models into `web/vendor/` inside the APK. The app then works offline.

## Get the APK

Every push to `main` builds a debug APK in GitHub Actions. Open the latest run under **Actions → Android build** and download `crux-wall-debug-apk`. Install it on your phone (allow installs from unknown sources).

## Build locally

Requirements: JDK 17 and the Android SDK (Android Studio installs both).

```bash
cd android
./gradlew installDebug        # builds and installs on a connected phone
```

Or open the `android/` folder in Android Studio and press Run.

Debug builds enable WebView debugging: with the phone connected over USB, open `chrome://inspect` on your computer to see the page console.

## Develop the game in a browser

The game runs on its own in desktop Chrome, loading MediaPipe from the CDN:

```bash
python3 -m http.server -d web 8000
# open http://localhost:8000
```

Use **Try with touch or mouse** to test the game flow without a camera.

## Tips for a good session

- Stand 2–3 m from the phone. Frame yourself from raised hands down to your feet.
- The route starts once the camera sees your whole body for 1.5 s, so you can tap Start and walk back.
- Good light from the front helps tracking a lot.
