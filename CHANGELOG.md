# Changelog

All notable changes to HYDRV are recorded here.

## [Unreleased]

### Added

- Planned updates and improvements will appear here before the next tag.

### Changed

- Ongoing polish and refinements will be summarized here.

### Fixed

- Bug fixes will be listed here as they land.

## [1.0.2] - 2026-04-05

### Changed

- Release metadata and in-app release notes were cleaned up for a simpler presentation.
- Changelog display now falls back to the bundled repo notes when the live release body is unavailable.

### Fixed

- The app version now matches the next tagged release.
- Release packaging and signing paths were hardened for cleaner builds.

## [1.0.1] - 2026-04-05

### Added

- Release APK publishing through GitHub Actions.
- Cleaner release notes with changelog, recent work, and contributors.
- Android themed icon support with a dedicated monochrome launcher asset.
- A liquid-style loading animation for download progress bars.

### Changed

- Dynamic color now uses a separate Material path from the locked brand theme.
- The version sheet, app list, settings, and download UI were refined for smoother surfaces and better contrast.
- The website and README now share the same updated screenshots and branding assets.

### Fixed

- Backend URLs are validated before saving so invalid page links are rejected early.
- Snackbar contrast and backend validation feedback are now more readable.
- Startup, lifecycle, and release packaging issues were hardened and cleaned up.

## [1.0.0] - 2026-04-04

### Added

- Initial public repository import.
