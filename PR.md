# PR: Map cluster tuning and overlay markers

## Summary

* Added persisted viewport cluster loading for large local photo libraries.
* Added tunable cluster settings: radius, min points, max distance, density, marker scale, thumbnail grid, visible thumbnail limit, and preload padding.
* Added a Compose overlay marker layer above MapLibre so visible clusters and thumbnails render reliably.
* Added thumbnail cluster markers with a count badge.
* Rendered single-photo stored items as photo markers instead of cluster circles.
* Recomputed marker screen positions while the map camera moves.
* Added a map debug panel with visible cluster count and coordinates.
* Added a settings toggle for the debug panel.
* Added unit tests for marker render rules and debug formatting helpers.

## Scope

This PR focuses on the map screen marker experience and cluster tuning.

Not included:

* Room migration.
* Coil gallery grid.
* Fullscreen photo viewer.
* WorkManager background scan.
* Replacing the overlay with a fully style-native MapLibre marker implementation.

## Checks

* Release APK prepared: `app/release/photomap-v0.6.0.apk`.
* APK metadata inspected with `aapt2 dump badging`: `versionCode=8`, `versionName=0.6.0`.
* APK permissions inspected with `aapt2 dump permissions`.
* APK signature inspected with `apksigner verify --verbose --print-certs`: `Verifies`, v2 signature enabled.
* Build and test commands were not run by Codex for this release prep, per the local project rule.

## Safety

The app remains read-only for user photos:

* no MediaStore delete/trash/write requests;
* no EXIF mutation;
* no upload of photos, coordinates, EXIF, or file identifiers;
* cluster and debug data stay local to the device.

## Release

Target release:

```text
v0.6.0
```

APK asset:

```text
app/release/photomap-v0.6.0.apk
```

SHA-256:

```text
FB93066803C660B04D1D12B3021876E4718042E0A5914DAF9DE3DE0531510577
```
