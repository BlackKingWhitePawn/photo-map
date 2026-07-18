# PR: Release v0.11.0 home screen

## Summary

* Added a new `home` start screen for the app.
* Added a compact MapLibre heatmap preview using existing ready trip heat cells.
* Added a featured trips carousel with cover thumbnails, date ranges, and photo counts.
* Added popular places cards built from already indexed local photos with coordinates.
* Added all-places and place-details routes.
* Added place details with photo mini-galleries grouped by day.
* Added an "On this day in the past" section with a map preview and thumbnails.
* Extracted reusable `MiniPhotoGallery` and `MiniPhotoThumbnail` components.
* Changed permission success navigation to return to the home screen.
* Bumped Android metadata to `versionName=0.11.0`, `versionCode=17`.

## Scope

Included:

* home dashboard MVP from `mainPage.md`;
* route wiring for home, all places, and place details;
* reusable mini-gallery component;
* local-only UI derived from already indexed photos, trips, and heat cells;
* release documentation for `v0.11.0`.

Not included:

* persisted places table;
* place editing;
* network geocoding for home place cards;
* new server sync, analytics, or photo write operations.

## Checks

Per the local project rule, Codex did not run Gradle build, tests, lint, `npx`, `tsc`, or `eslint`.

## Safety

The app remains read-only for user photos:

* no MediaStore delete/trash/write requests;
* no EXIF mutation;
* no upload of photos, coordinates, EXIF, media IDs, or trip statistics;
* home, place, trip, and heatmap data stay local to the device.

## Release

Target release:

```text
v0.11.0
```

Release notes:

```text
release-notes/v0.11.0.md
```

APK asset:

```text
not built after the v0.11.0 version bump
```
