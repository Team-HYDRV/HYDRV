# HYDRV

[![Android CI](https://github.com/Team-HYDRV/HYDRV/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Team-HYDRV/HYDRV/actions/workflows/android-ci.yml)

HYDRV is a polished Android release browser for discovering app updates and downloading files through signed, short-lived links.

## What’s Inside

- `HYDRV/` - Android app source
- `HYDRV/docs/` - backend examples and release notes
- `.github/` - CI, release, and contribution templates

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

See [`HYDRV/docs/backend-example.md`](HYDRV/docs/backend-example.md) for a Cloudflare Worker + R2 example that matches the app’s signed download flow.

## Releases

See [`RELEASES.md`](RELEASES.md) for the tag and publish checklist.

## GitHub Workflow

- Open issues with the templates in `.github/ISSUE_TEMPLATE/`
- Open pull requests with the template in `.github/PULL_REQUEST_TEMPLATE.md`
- Keep release notes in [`CHANGELOG.md`](CHANGELOG.md)
- GitHub Actions handles CI and APK release builds

## Notes

- The app uses a public `catalogue.json` and a private `catalogue.private.json`.
- Public catalogue entries point at token URLs.
- The Worker resolves the private path and returns a short-lived signed download URL.
- The public website is hosted separately on Cloudflare, so it is not part of this repo.
