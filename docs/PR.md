# PR: Release v1.0.0

## Summary

* Bumped Android metadata to `versionName=1.0.0`, `versionCode=19`.
* Renamed the visible app brand to `Traverse`.
* Added a real MapLibre trip heatmap preview to the home gallery geography widget.
* Removed country borders and country labels from the compact home heatmap preview.
* Replaced wrench-style settings icons with a dedicated gear drawable.
* Updated trip export images to use the trip's first photo as the background.
* Removed the trip export confidence metric.
* Simplified settings by removing algorithm tuning, debug controls, and diagnostic log export.
* Reworked map bottom controls into a spaced layout with same-height date and segmented display-mode controls.
* Added release notes and changelog coverage for `v1.0.0`.
* Prepared `app/release/traverse-v1.0.0.apk` from the user-built release APK.

## Scope

Included:

* local UI polish for home, map, settings, permissions, and trip sharing;
* visible app branding update to Traverse;
* Android release metadata for `v1.0.0`;
* release documentation for `v1.0.0`.

Not included:

* signed release APK build;
* Play Console upload;
* git tag creation;
* remote push;
* package/class namespace rename from `photomap`/`PhotoMap`.

## Checks

Performed without rebuilding the APK:

* `aapt dump badging` confirms `versionCode=19`, `versionName=1.0.0`, package `com.example.photomap`, label `Traverse`, `minSdk=29`, `targetSdk=36`.
* `aapt dump permissions` captured the APK permission list for release notes.
* `apksigner verify --print-certs --verbose` confirms the APK verifies with v2 signing and one signer.
* `zipalign -c -p 4` completed successfully.
* SHA-256 recorded for `app/release/traverse-v1.0.0.apk`.

Per the local project rule, Codex did not run tests, `npx`, `tsc`, or `eslint`.

## Safety

The app remains read-only for user photos:

* no MediaStore delete/trash/write requests;
* no EXIF mutation;
* no upload of photos, coordinates, EXIF, media IDs, or trip statistics;
* home, place, trip, and heatmap data stay local to the device.

## Release

Target release:

```text
v1.0.0
```

Release notes:

```text
release-notes/v1.0.0.md
```

APK asset:

```text
app/release/traverse-v1.0.0.apk
```
