# TRX Launcher

Current build: **v4.0.0 cockpit design**, release version **4.1.0 / 122**. See [verification results and MX+ setup](BUILD-STATUS.md). The signed APK passed build, lint and Android 16 emulator navigation/theme checks.

Google Navigation SDK 7.9 now powers the Navigation page directly inside the launcher,
including live traffic, rerouting, voice guidance, ETA, speed-limit display, themed map
controls, and compact destination suggestions. GitHub Actions injects the restricted
`MAPS_API_KEY` repository secret at build time; the key is never committed to source.

Theme Studio now coordinates the launcher accent with matching realistic TRX hero artwork: TRX Red / Sunset Ridge, Baja Amber / Desert Dusk, Stealth Black / Moon Ridge, OEM Blue / Glacier Night, and a custom-accent mode. The exact approved truck composition, lift, wheels, stance, and black RamBar are preserved across the preset artwork.

Native standalone Android launcher for Michael Diaz's 2023 RAM TRX / Ottocast portrait display.

## Build in GitHub (no PC required)

1. Open the repository's **Actions** tab.
2. Choose **Build TRX Launcher APK** and tap **Run workflow**.
3. When the run finishes, open it and download the **TRX-Launcher-APK** artifact.
4. Unzip the artifact on the Ottocast and install `TRX-Launcher-v4.1.0.apk`. An older installation signed with a different certificate must be uninstalled first, removing its saved settings.
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
