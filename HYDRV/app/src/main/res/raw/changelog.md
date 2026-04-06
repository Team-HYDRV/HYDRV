# Changelog

All notable changes to HYDRV are recorded here.

## [Unreleased]

### Added

- Nothing queued for the next tag yet.

### Changed

- Ongoing polish and refinements continue as usual.

### Fixed

- Bug fixes continue to roll in with each release.

## [1.0.6] - 2026-04-06

### Added

- Cleaner settings wording so the advanced and app status screens feel less technical.
- A clearer rewarded-download message when ads are unavailable.
- More polished locale labels for the update, about, and language screens.

### Changed

- Home list rendering now reuses cached sorting to keep large catalogues a little lighter.
- Rewarded-ad availability now uses friendlier wording in the backend health panel.
- The app keeps the same download and update behavior while presenting those states more clearly.

### Fixed

- Remaining locale mojibake was cleaned up across the visible settings and language screens.
- The app version, release notes, and notification labeling stay aligned with the latest release.

## [1.0.5] - 2026-04-06

### Added

- HYDRV's own self-update now stays out of the normal Downloads list so user APKs are easier to track.
- Rewarded-download flows now allow immediate downloads when the ad status is `No fill yet`.
- The release notification label now stays clean when the release name already includes its tag.

### Changed

- Download and install tracking now key off the full release identity so same-version-name entries stay separate.
- The version sheet keeps its rows smoother under heavy download activity while still showing the correct badges.
- Edge-to-edge handling was extended to the remaining legacy screens without changing their layout behavior.

### Fixed

- Download badges no longer bleed across unrelated rows that reuse the same JSON version number.
- Downloads that finish under fast multi-download stress now settle correctly into `Done`.
- The brand-mode download button text and wave styling stay readable and consistent.

## [1.0.4] - 2026-04-06

### Added

- The version sheet now uses a RecyclerView for smoother large-catalog browsing.
- Custom backend badges now stand apart from the official HYDRV source badge.
- The backend guide now includes public and private catalogue examples for custom setups.

### Changed

- Download and version identity handling now keeps same-name releases apart by version code.
- The version sheet and download flow were tightened so progress, done states, and badges stay in sync during fast multi-downloads.
- The app and shared brand assets were updated to use the rounded HYDRV icon.

### Fixed

- Stale `Done` rows no longer linger after deleting files from the downloads list.
- Download badges no longer spread across unrelated rows that reuse the same numeric version.
- The download button text and wave styling stay readable and consistent in brand mode.

## [1.0.3] - 2026-04-05

### Added

- Additional language support for Italian, Turkish, Vietnamese, Thai, Polish, Dutch, Malay, Ukrainian, Czech, and Romanian.
- Cleaner locale selection labels and translated app surfaces for the new languages.

### Changed

- Release packaging and version metadata were updated for the new tag.
- The app now keeps the release and changelog notes aligned with the bundled repository copy.

### Fixed

- Locale plural coverage and drawable loading warnings were tightened up.
- Download speed formatting now uses an explicit locale.

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
