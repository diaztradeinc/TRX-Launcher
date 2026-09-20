# v4 completion status

This build starts from the approved v4 cockpit (`0dc6587`) and retains its red/black visual identity while replacing the space-heavy hero headers with full-screen Uconnect layouts. Version 4.2.0 / code 123 distinguishes it from older installations. It does not use the v5 interface.

## Implemented

- A single native Google map surface on Home and Navigation with no painted map underneath it, tilted follow mode, road-snapped red truck marker, Places search, themed saved destinations/options, traffic/satellite layers and arrival metrics.
- Page-tab actions and one fixed Settings entry per tab row.
- Full-height media controls, published queue selection, configurable sources, audio settings and volume profiles with enlarged text and touch targets.
- Full-screen Performance layouts for the GPS timer, recorded speed trace, history and supported OBD gauges.
- Preset/custom-accent themes, startup page, icon sizing, destinations and warning settings.

## OBDLink MX+ setup

1. Plug the MX+ into the TRX and switch the ignition on.
2. Pair it in the **Ottocast's Android Bluetooth settings**. Close other OBD apps that may hold its connection.
3. Open **Settings → Set up OBDLink MX+**, allow Bluetooth access and choose the adapter. The selection is remembered.
4. **Performance → Gauges → Connect / pair OBDLink** opens the same setup. Connection status offers Reconnect and Disconnect.

The bridge reads standard OBD-II RPM, coolant, intake, load, voltage and speed. Boost is calculated only when manifold and barometric pressure are both available. Missing readings clear. Transmission temperature remains unavailable pending a verified RAM-specific PID. Physical adapter communication has not been verified by the emulator.

## Release verification

The signed release APK was built from `edda7afe2e66311b7e9e78dbf253036cfe9d3145`. [GitHub Actions run 35488737530](https://github.com/diaztradeinc/TRX-Launcher/actions/runs/35488737530) passed compilation, Android lint and Android 16 emulator interaction checks on its successful rerun. Captures confirm all page/tab layouts, live Google map tiles, Places suggestions, a calculated route with turn instructions and the red truck marker. Themed navigation menus and saved OEM Blue theme verification also passed. No app crash was recorded.

The earlier Google authorization failure is resolved by the updated `MAPS_API_KEY` and permanent signing identity. Initial tile loading was slow in the emulator, then completed; route guidance subsequently succeeded. Google displays its normal first-use navigation notice on a new installation.

Both workflows restore the configured `ANDROID_SIGNING_JSON` repository secret. The verified SHA-1 is `CD:F9:7D:63:CC:B9:9F:C8:7E:56:46:B3:82:6E:B2:D0:14:E3:CF:C6`, for Android package `com.mdiaz.trxlauncher`. It does not match older runner-generated certificates, so the first installation may require uninstalling the old app (which removes its saved settings). Keep the private signing backup for future updates.

Release APK: `TRX-Launcher-v4.2.0.apk`, version code 123. SHA-256: `cf9fcc366c226cf7d908c8cb33f47447e75f98e7f2f6d5e35327124e28afdb83`.

Physical Ottocast Bluetooth, MX+ communication, vehicle readings and third-party player behavior cannot be verified in the emulator. Media requires notification access; queue availability depends on the player publishing it. Transmission temperature is unsupported until a correct vehicle-specific PID is available. These limitations are not represented as verified functionality.
