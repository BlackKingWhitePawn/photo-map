# PR: Map date filtering and cancellable clustering

## Summary

* Prepared minor release metadata for `v0.8.0`.
* Bumped Android version to `versionName=0.8.0`, `versionCode=12`.
* Added a map date range filter opened from a second FAB.
* Added a two-sided range slider that applies the date filter only after release.
* Shows slider drag dates as `dd MMMM yyyy`.
* Shows the collapsed date FAB range as `dd.MM.yy - dd.MM.yy`.
* Made map cluster rebuilding and viewport loading cancellable so stale work does not overwrite newer map state.
* Moved screen-level marker compaction work off the main thread and added cooperative cancellation.

## Scope

This PR focuses on map filtering and map responsiveness during heavy cluster recalculation.

Not included:

* Building or signing a release APK by Codex.
* Running Gradle build, test, lint, `npx`, `tsc`, or `eslint` commands.
* Room migration.
* Coil gallery grid.
* Fullscreen photo viewer.
* WorkManager background scan.

## Checks

* `assembleRelease` could not be completed because Gradle cache access outside the sandbox was not approved.
* Test, lint, `npx`, `tsc`, and `eslint` commands were not run by Codex for this patch, per the local project rule.

## Safety

The app remains read-only for user photos:

* no MediaStore delete/trash/write requests;
* no EXIF mutation;
* no upload of photos, coordinates, EXIF, or file identifiers;
* cluster and map data stay local to the device.

## Release

Target release:

```text
v0.8.0
```

Release notes:

```text
release-notes/v0.8.0.md
```

APK asset:

```text
not built by Codex for this patch
```
