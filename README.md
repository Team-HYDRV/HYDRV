# HYDRV

[![Android CI](https://github.com/Team-HYDRV/HYDRV/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Team-HYDRV/HYDRV/actions/workflows/android-ci.yml)

HYDRV is an Android app for browsing releases and downloading files through short-lived links.

## Repo Layout

- `HYDRV/` - Android app source
- `HYDRV/docs/` - extra docs and backend examples

## Quick Start

1. Open `HYDRV/` in Android Studio.
2. Sync Gradle.
3. Run the app on a device or emulator.

## Build

From `HYDRV/`:

```powershell
.\gradlew.bat assembleDebug
```

## Backend Example

See [`HYDRV/docs/backend-example.md`](HYDRV/docs/backend-example.md) for a simple Cloudflare Worker + R2 catalogue example that matches the app's token flow.

## Releases

See [`RELEASES.md`](RELEASES.md) for the simple tag and release checklist.

## Notes

- The app uses a public `catalogue.json` and a private `catalogue.private.json`.
- Public catalogue entries point at token URLs.
- The Worker resolves the private path and returns a short-lived download URL.
- The public website is hosted separately on Cloudflare, so it is not part of this repo.
