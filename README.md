<p align="center">
  <img src="assets/readme-banner-v2.svg" alt="HYDRV banner">
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

<h2 align="center">Screenshots</h2>

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

---

<h2 align="center">Snapshot</h2>

<table>
  <tr>
    <td width="33%" valign="top" align="center">
      <h3>App</h3>
      <p>Browse releases, follow downloads, and manage installs in a personalized Android store.</p>
    </td>
    <td width="33%" valign="top" align="center">
      <h3>Backend</h3>
      <p>Resolve the private file path and issue signed download URLs through Cloudflare.</p>
    </td>
    <td width="33%" valign="top" align="center">
      <h3>Website</h3>
      <p>Show the latest release info, changelogs, and supporting pages in one calm place.</p>
    </td>
  </tr>
</table>

<h2 align="center">Visual Identity</h2>

<p align="center">
  <img src="assets/ic_rounded_repo_v2.png" alt="HYDRV brand mark" width="260">
</p>

<h2 align="center">Quick Start</h2>

1. Open `HYDRV/` in Android Studio.
2. Sync Gradle.
3. Run the app on a device or emulator.

<h2 align="center">Build</h2>

1. Open `HYDRV/` in Android Studio or a terminal.
2. Sync the project if needed.
3. Run:

```powershell
.\gradlew.bat assembleDebug
```

To build a release APK instead, use:

```powershell
.\gradlew.bat assembleRelease
```

<h2 align="center">Project Structure</h2>

<ul>
  <li><code>HYDRV/</code> - Android app source</li>
  <li><code>HYDRV/docs/</code> - backend, release, and docs hub</li>
  <li><code>assets/</code> - README branding, banner, and screenshot assets</li>
  <li><code>.github/</code> - CI, release, and contribution automation</li>
  <li><code>CHANGELOG.md</code> - release note template</li>
  <li><code>RELEASES.md</code> - tag and publish checklist</li>
</ul>

<h2 align="center">For Contributors</h2>

<table>
  <tr>
    <td width="50%" valign="top">
      <h3 align="center">Before you send a PR</h3>
      <ul>
        <li>Open the app in Android Studio and run <code>assembleDebug</code>.</li>
        <li>Update the release checklist if you tag a new build.</li>
        <li>Keep screenshots and docs in sync with visible UI changes.</li>
      </ul>
    </td>
    <td width="50%" valign="top">
      <h3 align="center">Keep an eye on</h3>
      <ul>
        <li>The public catalogue should stay aligned with the backend example.</li>
        <li>Release note wording should also reflect GitHub releases.</li>
        <li>Brand assets belong in <code>assets/</code> so the README can render them cleanly.</li>
      </ul>
    </td>
  </tr>
</table>
