# PR: Map date filter patch release

## Summary

* Prepared patch release metadata for `v0.8.1`.
* Bumped Android version to `versionName=0.8.1`, `versionCode=13`.
* Added precise date picker selection from the date range labels.
* Added month and year tick marks behind the date range slider.
* Made the date slider scale around the active selected range and expand back when dragged toward the edges.
* Added snapping to nearby month, year, and range boundary ticks.
* Suppressed the "Нет фотографий с геопозицией" empty state when an active date filter hides all photos.

## Scope

This PR is a patch on top of the `v0.8.0` map date filter release.

Not included:

* Building or signing a `v0.8.1` release APK by Codex.
* Running Gradle build, test, lint, `npx`, `tsc`, or `eslint` commands.
* Room migration.
* Coil gallery grid.
* Fullscreen photo viewer.
* WorkManager background scan.

## Checks

* `git diff --check` completed without whitespace errors.
* Conflict markers were not found in the changed map screen file.
* Test, lint, Gradle build, `npx`, `tsc`, and `eslint` commands were not run by Codex for this patch, per the local project rule.
* A `v0.8.1` APK was not built or inspected after the version bump.

## Safety

The app remains read-only for user photos:

* no MediaStore delete/trash/write requests;
* no EXIF mutation;
* no upload of photos, coordinates, EXIF, or file identifiers;
* cluster and map data stay local to the device.

## Release

Target release:

```text
v0.8.1
```

Release notes:

```text
release-notes/v0.8.1.md
```

APK asset:

```text
not built after the v0.8.1 version bump
```
