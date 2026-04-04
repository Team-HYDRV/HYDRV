<p align="center">
  <img src="HYDRV/app/src/main/res/drawable-nodpi/ic_launcher_foreground_image.png" alt="HYDRV logo" width="180">
</p>

<h1 align="center">HYDRV</h1>

<p align="center">
  Android release browser for discovering app updates and downloading files through signed download links.
</p>

<p align="center">
  <a href="https://github.com/Team-HYDRV/HYDRV/actions/workflows/android-ci.yml">
    <img src="https://github.com/Team-HYDRV/HYDRV/actions/workflows/android-ci.yml/badge.svg" alt="Android CI">
  </a>
  <a href="https://github.com/Team-HYDRV/HYDRV/releases/latest">
    <img src="https://img.shields.io/github/v/release/Team-HYDRV/HYDRV?include_prereleases" alt="Latest release">
  </a>
  <a href="https://hydrv.app">
    <img src="https://img.shields.io/badge/Website-hydrv.app-111111?style=flat-square" alt="HYDRV website">
  </a>
</p>

<p align="center">
  <a href="https://github.com/Team-HYDRV/HYDRV/releases/latest">Latest release</a>
  &middot;
  <a href="https://hydrv.app">Website</a>
  &middot;
  <a href="HYDRV/docs/backend-example.md">Backend example</a>
  &middot;
  <a href="RELEASES.md">Release checklist</a>
</p>

## What Lives Here

- Android app source for the HYDRV release browser
- Cloudflare Worker and R2-backed download flow documentation
- GitHub Actions for CI and release builds
- Issue, pull request, security, and contribution templates

## At a Glance

| Area | What it covers |
| --- | --- |
| App | Browse releases, queue downloads, and manage installs from Android. |
| Backend | Serve the public catalogue and resolve signed download links. |
| Website | Present the public project, latest releases, and supporting pages. |
| Releases | Publish APK builds through GitHub tags and release automation. |

## Quick Start

1. Open `HYDRV/` in Android Studio.
2. Sync Gradle.
3. Run the app on a device or emulator.

## Build

From `HYDRV/`:

```powershell
.\gradlew.bat assembleDebug
```

## Visual Identity

The app artwork and launcher styling live under `HYDRV/app/src/main/res/`. The main brand mark used for the app is:

<p align="center">
  <img src="HYDRV/app/src/main/res/drawable-nodpi/ic_launcher_foreground_image.png" alt="HYDRV brand mark" width="260">
</p>

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

