# PR: Map marker reprojection hotfix

## Summary

* Prepared patch release metadata for `v0.7.2`.
* Bumped Android version to `versionName=0.7.2`, `versionCode=11`.
* Fixed same-location clusters so tapping them opens the mini gallery instead of endlessly zooming into one point.
* Fixed overlay markers while moving the map: markers reproject to the current camera, while full cluster recalculation waits until camera idle.
* Added a dense overlay compaction pass so nearby marker groups are merged into larger screen clusters.
* Rendered single-photo map markers as rounded square thumbnails instead of circles.
* Updated EXIF scan flow so saved geotagged batches appear on the map during scanning and cancelled scans resume from the last saved mark.
* Started EXIF GPS scanning immediately after the first gallery permission grant.

## Scope

This PR focuses on map marker stability, dense marker performance, and same-geotag cluster taps.
It also keeps long EXIF scans incremental so the map fills while the scanner is still running.

Not included:

* Building or signing a release APK by Codex.
* Running Gradle build, test, lint, `npx`, `tsc`, or `eslint` commands.
* Room migration.
* Coil gallery grid.
* Fullscreen photo viewer.
* WorkManager background scan.

## Checks

* Release APK prepared: `app/release/photomap-v0.7.2.apk`.
* APK metadata inspected with `aapt2 dump badging`: `versionCode=11`, `versionName=0.7.2`.
* APK permissions inspected with `aapt2 dump permissions`.
* APK signature inspected with `apksigner verify --verbose --print-certs`: `Verifies`, v2 signature enabled.
* Build and test commands were not run by Codex for this patch, per the local project rule.

## Safety

The app remains read-only for user photos:

* no MediaStore delete/trash/write requests;
* no EXIF mutation;
* no upload of photos, coordinates, EXIF, or file identifiers;
* cluster and map data stay local to the device.

## Release

Target release:

```text
v0.7.2
```

Release notes:

```text
release-notes/v0.7.2.md
```

APK asset:

```text
app/release/photomap-v0.7.2.apk
```

SHA-256:

```text
F2E6DBB97BB143C57F6816820045D3B531B2CD031B7AC206745F42AF61B4F033
```
