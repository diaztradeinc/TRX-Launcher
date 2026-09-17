# TRX Launcher

Native standalone Android launcher prototype for Michael Diaz's 2023 RAM TRX / Ottocast portrait display.

## Build in GitHub (no PC required)

1. Open the repository's **Actions** tab.
2. Choose **Build TRX Launcher APK** and tap **Run workflow**.
3. When the run finishes, open it and download the **TRX-Launcher-APK** artifact.
4. Unzip the artifact on the Ottocast, install `TRX-Launcher-v0.1.1.apk`, and allow installs from the browser or file manager if Android asks.
5. Press Home and select **TRX Launcher** as the default launcher.

The workflow also builds automatically whenever source is pushed to `main` or `master`. The downloadable Actions artifact is kept for 30 days.

## Local build (optional)

Open this folder in Android Studio, allow Gradle sync, then build the `app` module. From a machine with Gradle and the Android SDK configured, run:

```bash
gradle :app:assembleRelease
```

The application registers as both a normal app and an Android HOME launcher. The first Android prompt can set **TRX Launcher** as the default Home app.

## Milestone 0.1.1

- Five-page custom Canvas interface
- Full-screen portrait automotive UI
- Live clock/date
- Navigation intents
- Installed application discovery and launch
- Persistent selected page
- Read-only telemetry model prepared for a Bluetooth OBD service
- GitHub Actions cloud APK build

OBD/CAN writes are intentionally excluded.
