# greg-kiosk

12KB fullscreen WebView kiosk APK for Wall Greg's two-panel display system.

## What It Does

Custom Android APK that replaces the home launcher on Wall Greg devices (Fire HD 8 and Skylight tablet). Boots straight to a fullscreen WebView with:

- Immersive sticky mode (no system chrome)
- `BOOT_COMPLETED` receiver + `HOME` category (survives reboot)
- JavaScript, DOM Storage, media autoplay enabled
- Cleartext traffic for localhost fallback
- Auto-reload on network error

## Devices

| Device | Serial | Content |
|--------|--------|---------|
| Skylight (dashboard) | 3481E0000131 | `greg-skylight-dashboard` on Vercel |
| Fire HD 8 (face) | GCC19D04134608TV | `greg-surface-web` on Vercel |

## Build

Requires Android SDK build-tools 35 and JDK 21. Build on G7:

```
D:\Tools\greg-kiosk\build.bat
```

Produces `build/greg-kiosk.apk` (debug-signed, ~12KB).

## Architecture

- Target SDK 28 (bypasses Android 11 scoped storage restrictions)
- `KioskActivity` loads URL from intent data, or falls back to `/sdcard/dashboard.html`
- `/sdcard/dashboard.html` contains a meta-refresh redirect to the Vercel URL
- busybox httpd started as fallback for localhost content serving
