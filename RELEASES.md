# Releases

Use this file as the release checklist for HYDRV GitHub releases.

## Tag Format

- Use tags that start with `v`
- Example: `v1.0.0`, `v1.0.1`, `v1.1.0`

## Before Tagging

1. Bump `versionCode` and `versionName` in `HYDRV/app/build.gradle.kts`.
2. Run `assembleRelease` locally if you want a quick check first.
3. Make sure the release APK builds cleanly.
4. Update the matching `CHANGELOG.md` entry with `Added`, `Changed`, and `Fixed` notes for the tag.
5. Commit the version bump and changelog notes together.

## Publish Flow

1. Create the tag.
2. Push the tag to GitHub.
3. GitHub Actions runs the Android Release workflow.
4. The workflow reads the matching changelog entry, turns it into `Added`, `Changed`, and `Fixed` release notes, uploads the APK artifact, and creates the GitHub Release.

## Notes

- The release workflow is triggered by tags that match `v*`.
- Manual workflow runs are available too, but only tag pushes publish a GitHub Release entry.
- Keep the changelog and version numbers in sync with the tag you publish.
- If you want the release to post to Discord automatically, add the `DISCORD_RELEASE_WEBHOOK_URL` secret in GitHub Actions.
