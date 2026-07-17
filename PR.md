# PR: Release v0.10.0 trip heatmap

## Summary

* Added a trip heatmap built from detected trips and destinations, not raw photo counts.
* Stored heatmap cells, per-trip contributions, and heatmap metadata in `photo_index.db`.
* Aggregated trip destinations into H3 levels 4, 5, 6, 7, 8, and 9 with a deterministic fallback grid.
* Weighted heat intensity by trip count, days spent, active days, and photo session count.
* Scheduled background heatmap refresh through WorkManager after media permissions are available.
* Exposed ready heat cells as GeoJSON points and rendered them through a MapLibre `HeatmapLayer`.
* Rendered the heatmap below ordinary photo clusters on the main map and below trip markers on the trip map.
* Added map/settings diagnostics for visible heatmap cells, resolution, and data version.
* Preserved the normal map center and zoom when opening the trip map.
* Bumped Android metadata to `versionName=0.10.0`, `versionCode=16`.

## Scope

Included:

* local-only trip heatmap MVP;
* full heatmap rebuild after trip segmentation;
* H3 aggregation and persisted ready cells;
* main-map and trip-map heatmap rendering;
* release documentation for `v0.10.0`.

Not included:

* incremental per-trip heatmap rebuild UI controls;
* user-selectable heatmap modes;
* recency weighting modes;
* route-only transit layer;
* changing original user photos.

## Checks

Per the local project rule, Codex did not run Gradle build, tests, lint, `npx`, `tsc`, or `eslint`.

## Safety

The app remains read-only for user photos:

* no MediaStore delete/trash/write requests;
* no EXIF mutation;
* no upload of photos, coordinates, EXIF, media IDs, or trip statistics;
* trip and heatmap data stay local to the device.

## Release

Target release:

```text
v0.10.0
```

Release notes:

```text
release-notes/v0.10.0.md
```

APK asset:

```text
not built after the v0.10.0 version bump
```
