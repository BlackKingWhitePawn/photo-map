# PR: Map controls and v0.7.0 release prep

## Summary

* Prepared release metadata for `v0.7.0`.
* Bumped Android version to `versionName=0.7.0`, `versionCode=9`.
* Made the map debug panel hidden by default for new installs.
* Polished map top-bar controls, safe-area handling, settings icon usage, and mini gallery controls.
* Added an animated cluster-density FAB that shows the current density percent and expands into a full-width slider.
* Updated visible map clustering so density affects the runtime marker layer and cluster labels use real photo IDs when available.

## Scope

This PR focuses on map UX, cluster-density behavior, debug-panel defaults, and release preparation.

Not included:

* Building or signing a release APK by Codex.
* Running Gradle build, test, lint, `npx`, `tsc`, or `eslint` commands.
* Room migration.
* Coil gallery grid.
* Fullscreen photo viewer.
* WorkManager background scan.

## Checks

* Build and test commands were not run by Codex for this release prep, per the local project rule.
* Release APK was not prepared by Codex.
* APK metadata, permissions, and signature were not inspected by Codex for this release prep.

## Safety

The app remains read-only for user photos:

* no MediaStore delete/trash/write requests;
* no EXIF mutation;
* no upload of photos, coordinates, EXIF, or file identifiers;
* cluster and debug data stay local to the device.

## Release

Target release:

```text
v0.7.0
```

Release notes:

```text
release-notes/v0.7.0.md
```
