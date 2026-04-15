# Changelog

All notable changes to HYDRV are recorded here.

## [Unreleased]

### Added

- Nothing queued for the next tag yet.

### Changed

- Ongoing polish and refinements continue as usual.

### Fixed

- Bug fixes continue to roll in with each release.

## [1.1.1] - 2026-04-15

### Added

- A cleaner in-app release changelog dialog with tighter spacing and less noisy header content.

### Changed

- Rewarded-download gating now waits more gracefully for ad loading instead of rejecting first taps during warm-up.
- Update flow state now keeps release notifications and hidden self-update cleanup more predictable across checks and releases.

### Fixed

- Pending uninstall state is cleared when a system uninstall prompt is canceled, so stale success state does not leak forward.
- Download persistence, version hints, and app details button state now rely on safer snapshot-style state handling during active download changes.

## [1.1.0] - 2026-04-13

### Added

- Broader localized coverage and translation sync support for the app’s release, settings, notifications, and about surfaces.
- Smoother list updating in version history, downloads, backend management, and contributors screens through more targeted adapter refreshes.

### Changed

- The changelog now follows the catalog JSON content more faithfully, preserving multi-line notes and proper bullet formatting.
- The app’s version metadata, release notes, and packaged release output are aligned for the `1.1.0` tag.

### Fixed

- Recycler rows no longer leak stale app or action state when catalog entries or downloads reuse recycled views.
- Broken localized resource placeholders and malformed escaped notification strings were repaired so debug and release builds stay clean after translation syncs.

### Recent work

- Fixed changelog rendering so JSON release notes display with the intended line breaks, bullets, and content structure.
- Tightened RecyclerView binding correctness across app, download, backend, version, and contributors surfaces while reducing unnecessary full-list redraws.
- Cleaned up repeated Crowdin sync regressions in localized resources and restored build-safe strings and format placeholders across affected locales.

## [1.0.9] - 2026-04-12

### Added

- A cleaner public website with refreshed desktop and mobile layouts, a Features section, and smoother section-aware navigation.
- DMCA verification support through the live site validation file, homepage verification tag, and linked trust badges.

### Changed

- The website and README were aligned around the same calmer HYDRV presentation, badge styling, and feature wording.
- Release metadata now matches the 1.0.9 tag and keeps the app, site, and docs in sync.

### Fixed

- RTL settings switches and Arabic, Greek, and Hebrew language picker labels now render in the correct direction without mojibake.
- Localized resource issues were cleaned up further, including malformed strings, plural coverage, and lingering question-mark corruption.

### Recent work

- Reworked the website into a cleaner desktop and mobile experience with calmer navigation and section flow.
- Added DMCA verification support, trust badges, and the matching validation file and meta tag on the site.
- Cleaned RTL settings behavior and repaired remaining language picker label corruption in Arabic, Greek, and Hebrew.

## [1.0.8] - 2026-04-11

### Added

- Cleaner download state handling so active rows stay focused on pause and stop actions.
- A small settings footer area that can be reused for lightweight status text.

### Changed

- The download row now separates active, stopped, and installed states more clearly.
- The app version metadata now matches the next tagged release.

### Fixed

- Stopped downloads no longer leak stale install actions while they are still in progress.
- Late download callbacks are ignored once a transfer has been stopped.

### Recent work

- Tightened the downloads experience so active rows, stopped rows, and installed states stay easier to read.
- Added a lightweight reusable settings footer for small status text without changing the overall screen structure.
- Cleaned up the release metadata path so the tagged build, in-app versioning, and notes stay aligned.

## [1.0.7] - 2026-04-08

### Recent work

- Bumped the release to `1.0.7` and aligned the packaged metadata with the tagged build.
- Warmed up the public copy across the README and website so the project reads more naturally.
- Reworked the repo presentation with clearer highlights, cleaner screenshot ordering, and a tighter architecture/build overview.

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
