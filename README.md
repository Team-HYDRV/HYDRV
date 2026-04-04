# HYDRV

[![Android CI](https://github.com/Team-HYDRV/HYDRV/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Team-HYDRV/HYDRV/actions/workflows/android-ci.yml)
[![Release](https://img.shields.io/github/v/release/Team-HYDRV/HYDRV?include_prereleases)](https://github.com/Team-HYDRV/HYDRV/releases/latest)

HYDRV is an Android release browser for discovering app updates and downloading files through signed download links.

## Highlights

- Browse releases from a public catalogue
- Resolve downloads through a Cloudflare Worker and R2 backend
- Keep the app and update flow tied to the latest GitHub release
- Ship clean builds with Android CI and release automation

## Repository Layout

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

## Release Flow

See [`RELEASES.md`](RELEASES.md) for the tag and publish checklist.

## Backend Example

See [`HYDRV/docs/backend-example.md`](HYDRV/docs/backend-example.md) for a Cloudflare Worker and R2 example that matches the app's signed download flow.

## App Notes

- The app reads a public `catalogue.json` and a private `catalogue.private.json`.
- Public catalogue entries point at token URLs.
- The Worker resolves the private path and returns a signed download URL.
- The app checks GitHub releases for update awareness.

## Contribution Notes

- Open issues with the templates in `.github/ISSUE_TEMPLATE/`
- Open pull requests with the template in `.github/PULL_REQUEST_TEMPLATE.md`
- Keep release notes in [`CHANGELOG.md`](CHANGELOG.md)
- GitHub Actions handles CI and APK release builds

## Website

The public website is hosted separately on Cloudflare and is not part of this repository.
