<p align="center">
  <img src="assets/readme-banner.svg" alt="HYDRV banner">
</p>

<p align="center">
  <a href="https://github.com/Team-HYDRV/HYDRV/actions/workflows/android-ci.yml">
    <img src="https://github.com/Team-HYDRV/HYDRV/actions/workflows/android-ci.yml/badge.svg" alt="Android CI">
  </a>
  <a href="https://github.com/Team-HYDRV/HYDRV/releases/latest">
    <img src="https://img.shields.io/badge/Latest%20release-GitHub-181717?style=flat-square&logo=github" alt="Latest release">
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
  <a href="HYDRV/docs/backend-example.md">Backend guide</a>
  &middot;
  <a href="HYDRV/docs/samples/catalogue.json">Sample catalogue</a>
  &middot;
  <a href="RELEASES.md">Release checklist</a>
  &middot;
  <a href="CHANGELOG.md">Changelog</a>
  &middot;
  <a href="HYDRV/docs/index.md">Docs</a>
</p>

<p align="center">
  <strong>HYDRV is a personalized store made just for you.</strong>
</p>

---

## Snapshot

<table>
  <tr>
    <td width="33%" valign="top">
      <h3>App</h3>
      <p>Browse releases, follow downloads, and manage installs in a personalized Android store.</p>
    </td>
    <td width="33%" valign="top">
      <h3>Backend</h3>
      <p>Resolve the private file path and issue signed download URLs through Cloudflare.</p>
    </td>
    <td width="33%" valign="top">
      <h3>Website</h3>
      <p>Show the latest release info, changelogs, and supporting pages in one calm place.</p>
    </td>
  </tr>
</table>

## Live At a Glance

<table>
  <tr>
    <td width="25%" valign="top">
      <h3>Release</h3>
      <p><a href="https://github.com/Team-HYDRV/HYDRV/releases/latest">View the latest release</a></p>
    </td>
    <td width="25%" valign="top">
      <h3>CI</h3>
      <p><a href="https://github.com/Team-HYDRV/HYDRV/actions/workflows/android-ci.yml">Android CI status</a></p>
    </td>
    <td width="25%" valign="top">
      <h3>Website</h3>
      <p><a href="https://hydrv.app">Public project site</a></p>
    </td>
    <td width="25%" valign="top">
      <h3>Docs</h3>
      <p><a href="HYDRV/docs/index.md">Docs landing page</a></p>
    </td>
  </tr>
</table>

## Release Preview

<table>
  <tr>
    <td width="66%" valign="top">
      <h3>Latest GitHub release</h3>
      <p>The app and website both point to the latest release, so everything stays lined up.</p>
      <p>
        <a href="https://github.com/Team-HYDRV/HYDRV/releases/latest"><strong>View latest release</strong></a>
        &middot;
        <a href="CHANGELOG.md"><strong>Release changelog template</strong></a>
      </p>
    </td>
    <td width="34%" valign="top">
      <h3>Public release flow</h3>
      <p>Tag a build, publish the GitHub release, and let the app show the same notes to users.</p>
    </td>
  </tr>
</table>

## Highlights

<table>
  <tr>
    <td width="50%" valign="top">
      <h3>Polished release browsing</h3>
      <p>Open an app, scan its versions, and move through release details without extra clutter.</p>
    </td>
    <td width="50%" valign="top">
      <h3>Signed download flow</h3>
      <p>The public catalogue points at token endpoints, while the Worker keeps the real file path private.</p>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <h3>GitHub release awareness</h3>
      <p>The app and website both surface the latest GitHub release so changelogs stay aligned.</p>
    </td>
    <td width="50%" valign="top">
      <h3>Clean project tooling</h3>
      <p>CI, release automation, docs, contribution templates, and support links are already in place.</p>
    </td>
  </tr>
</table>

## How It Fits Together

```mermaid
flowchart LR
    A["Android app"] --> B["Public catalogue.json"]
    B --> C["Token endpoint"]
    C --> D["Cloudflare Worker"]
    D --> E["Private catalogue.private.json"]
    D --> F["Signed download URL"]
    F --> G["R2 file download"]
    A --> H["GitHub latest release"]
    H --> I["Release notes and changelog"]
```

In practice, HYDRV keeps the public release flow simple: the app reads the visible catalogue, the Worker signs the real download path, and GitHub stays the source of truth for release notes.

## Quick Links

- [Latest GitHub release](https://github.com/Team-HYDRV/HYDRV/releases/latest)
- [Public website](https://hydrv.app)
- [Backend guide](HYDRV/docs/backend-example.md)
- [Sample public catalogue](HYDRV/docs/samples/catalogue.json)
- [Sample private catalogue](HYDRV/docs/samples/catalogue.private.json)
- [Docs landing page](HYDRV/docs/index.md)
- [Release checklist](RELEASES.md)
- [Changelog](CHANGELOG.md)
- [Support on Ko-fi](https://ko-fi.com/xc3fff0e)

## Screenshots

<table>
  <tr>
    <td width="25%" align="center" valign="top">
      <img src="assets/screenshots/permissions.png" alt="HYDRV permissions screen" width="100%">
      <p><strong>Permissions</strong><br>Clear setup steps before you start downloading.</p>
    </td>
    <td width="25%" align="center" valign="top">
      <img src="assets/screenshots/home.png" alt="HYDRV home screen" width="100%">
      <p><strong>Home</strong><br>Browse releases, track versions, and keep installs organized.</p>
    </td>
    <td width="25%" align="center" valign="top">
      <img src="assets/screenshots/settings.png" alt="HYDRV settings screen" width="100%">
      <p><strong>Settings</strong><br>Backend controls, diagnostics, and support options.</p>
    </td>
    <td width="25%" align="center" valign="top">
      <img src="assets/screenshots/about.png" alt="HYDRV About screen" width="100%">
      <p><strong>About</strong><br>Project info, quick links, and the current brand.</p>
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

## Visual Identity

<p align="center">
  <img src="assets/ic_rounded_repo.png" alt="HYDRV brand mark" width="260">
</p>

## Quick Start

1. Open `HYDRV/` in Android Studio.
2. Sync Gradle.
3. Run the app on a device or emulator.

## Build

From `HYDRV/`:

```powershell
.\gradlew.bat assembleDebug
```

## Project Structure

- `HYDRV/` - Android app source
- `HYDRV/docs/` - backend, release, and docs hub
- `assets/` - README branding, banner, and screenshot assets
- `.github/` - CI, release, and contribution automation
- `CHANGELOG.md` - release note template
- `RELEASES.md` - tag and publish checklist

## For Contributors

<table>
  <tr>
    <td width="50%" valign="top">
      <h3>Before you send a PR</h3>
      <ul>
        <li>Open the app in Android Studio and run <code>assembleDebug</code>.</li>
        <li>Update the release checklist if you tag a new build.</li>
        <li>Keep screenshots and docs in sync with visible UI changes.</li>
      </ul>
    </td>
    <td width="50%" valign="top">
      <h3>Keep an eye on</h3>
      <ul>
        <li>The public catalogue should stay aligned with the backend example.</li>
        <li>Release note wording should also reflect GitHub releases.</li>
        <li>Brand assets belong in <code>assets/</code> so the README can render them cleanly.</li>
      </ul>
    </td>
  </tr>
</table>

## Release Flow

See [`RELEASES.md`](RELEASES.md) for the tag and publish checklist. If you want release announcements to post to Discord automatically, add the `DISCORD_RELEASE_WEBHOOK_URL` secret in GitHub Actions.

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
