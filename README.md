# TRX Launcher 5 — Clean Architecture

This project intentionally contains no code from the legacy `DashboardView` build.

## Required GitHub secret

`MAPS_API_KEY` must be an Android-restricted key for package `com.mdiaz.trxlauncher` and the SHA-1 used by the build. Enable Navigation SDK, Maps SDK for Android, Places API (New), and Routes API. Billing must remain attached.

## Clean-install test

Uninstall the previous TRX Launcher before installing v5 so obsolete preferences and cached launcher state cannot survive.

## Architecture

- `MainActivity`: persistent shell, header, settings access, bottom navigation.
- `NavigationScreen`: owns the only Google `NavigationView`, Places predictions, routing, guidance and map options.
- `ObdService`: dedicated OBDLink MX+ RFCOMM connection and safe standard-PID polling.
- `MediaAccessService`: Android MediaSession bridge; permission is requested once during onboarding/settings.

Transmission temperature remains unavailable until a verified 2023 RAM TRX PID is configured; the app does not fabricate it.
