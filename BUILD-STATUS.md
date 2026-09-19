# v4 completion status

This branch starts from the approved v4.0.0 cockpit (`0dc6587`) and keeps its artwork and layout. Internal version 4.1.0 / code 122 distinguishes it from older installed builds. It does not use the v5 interface.

## Implemented

- Shared native Google map on Home and Navigation, tilted follow mode, road-snapped red truck marker, Places search, saved destinations, traffic/satellite options, arrival metrics.
- Page-tab actions and one fixed Settings entry per tab row.
- Published media queue selection, configurable sources, playback controls, audio settings and volume profiles.
- GPS timer, recorded speed trace, history and supported OBD gauges.
- Preset/custom-accent themes, startup page, icon sizing, destinations and warning settings.

## OBDLink MX+ setup

1. Plug the MX+ into the TRX and switch the ignition on.
2. Pair it in the **Ottocast's Android Bluetooth settings**. Close other OBD apps that may hold its connection.
3. Open **Settings → Set up OBDLink MX+**, allow Bluetooth access and choose the adapter. The selection is remembered.
4. **Performance → Gauges → Connect / pair OBDLink** opens the same setup. Connection status offers Reconnect and Disconnect.

The bridge reads standard OBD-II RPM, coolant, intake, load, voltage and speed. Boost is calculated only when manifold and barometric pressure are both available. Missing readings clear. Transmission temperature remains unavailable pending a verified RAM-specific PID. Physical adapter communication has not been verified by the emulator.

## Release blockers and verification

Compilation and Android lint passed for commit `ef65f4b`. A previous crash-only screen check was insufficient: some captures showed the system launcher instead of this app. Foreground assertions and persisted-theme verification have been added; do not treat the earlier smoke result as full interaction coverage.

The emulator produced both Maps and Navigation SDK **Authorization failure**, with a message to enable **Google Maps Android API**. Review the Google Cloud project belonging to the existing GitHub `MAPS_API_KEY`: Maps SDK for Android, Navigation SDK and Places API (New), billing, and API/application restrictions. Do not paste keys into issues, source, or logs.

The inherited release configuration uses a runner-generated debug signing key. Reliable in-place updates and Android API-key restrictions need a stable signing identity; it has not been configured in this branch. Do not assume an APK will update an existing installation with a different certificate.

Live tiles, routing, the truck marker and device-dependent media/OBD behavior are not certified complete. No final APK has been published from this branch. Verification reports and redacted navigation diagnostics are attached to GitHub Actions runs.
