<p align="center">
  <img src="assets/ic_rounded.png" alt="HYDRV logo" width="180">
</p>

<h1 align="center">HYDRV</h1>

<p align="center">
  <strong>Browse releases, ship signed downloads, and keep the public face of the project polished.</strong>
</p>

<p align="center">
  <em>An Android release browser with a Cloudflare-backed catalogue, signed download links, and GitHub release integration.</em>
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
  <a href="https://ko-fi.com/xc3fff0e">
    <img src="https://img.shields.io/badge/Support-Ko--fi-ff5f5f?style=flat-square" alt="Support on Ko-fi">
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
  &middot;
  <a href="CHANGELOG.md">Changelog</a>
</p>

---

## Overview

HYDRV is the public release front door for the project: a polished Android app, a signed-download backend, and a website that keeps releases easy to find.

The app reads a public catalogue, the backend resolves the real file path privately, and the website mirrors the release story with a clean public-facing landing page.

## What You Get

<table>
  <tr>
    <td width="33%" valign="top">
      <h3>Browse</h3>
      <p>Discover app releases from Android with a layout tuned for updates, versions, and install flow.</p>
    </td>
    <td width="33%" valign="top">
      <h3>Deliver</h3>
      <p>Serve files through a Cloudflare Worker and R2-backed catalogue with signed download URLs.</p>
    </td>
    <td width="33%" valign="top">
      <h3>Publish</h3>
      <p>Ship APK releases through GitHub tags, CI, and release automation with a matching public website.</p>
    </td>
  </tr>
</table>

## Highlights

<table>
  <tr>
    <td width="50%" valign="top">
      <h3>Android App</h3>
      <ul>
        <li>Browse releases, queue downloads, and manage installs.</li>
        <li>Load the public catalogue from the backend.</li>
        <li>Open the latest GitHub release directly from update prompts.</li>
      </ul>
    </td>
    <td width="50%" valign="top">
      <h3>Cloudflare Backend</h3>
      <ul>
        <li>Keep the real file path private in R2.</li>
        <li>Issue signed, short-lived download URLs.</li>
        <li>Support resume-friendly downloads when the client asks for them.</li>
      </ul>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <h3>Website</h3>
      <ul>
        <li>Show the public release story in a lightweight landing page.</li>
        <li>Surface the latest GitHub releases and changelogs.</li>
        <li>Keep privacy, terms, and support pages close at hand.</li>
      </ul>
    </td>
    <td width="50%" valign="top">
      <h3>Repo Quality</h3>
      <ul>
        <li>CI and release workflows are already wired in.</li>
        <li>Issue, PR, security, and contribution templates are included.</li>
        <li>Release notes and backend docs live alongside the app source.</li>
      </ul>
    </td>
  </tr>
</table>

## At a Glance

| Area | What it covers |
| --- | --- |
| App | Browse releases, queue downloads, and manage installs from Android. |
| Backend | Serve the public catalogue and resolve signed download links. |
| Website | Present the public project, latest releases, and supporting pages. |
| Releases | Publish APK builds through GitHub tags and release automation. |

## Quick Links

- [Latest GitHub release](https://github.com/Team-HYDRV/HYDRV/releases/latest)
- [Public website](https://hydrv.app)
- [Backend example](HYDRV/docs/backend-example.md)
- [Release checklist](RELEASES.md)
- [Changelog](CHANGELOG.md)
- [Support on Ko-fi](https://ko-fi.com/xc3fff0e)

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

The main brand mark used for HYDRV lives in the app resources:

<p align="center">
  <img src="assets/ic_rounded.png" alt="HYDRV brand mark" width="260">
</p>

## Repository Map

- `HYDRV/` - Android app source
- `HYDRV/docs/` - backend and workflow examples
- `.github/` - CI, release, and contribution automation
- `CHANGELOG.md` - release note template
- `RELEASES.md` - tag and publish checklist

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
