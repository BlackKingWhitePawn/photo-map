# PR: Add trip segmentation map

## Summary

* Added local semantic trip segmentation for indexed photos with valid date and geotag metadata.
* Added trip persistence in `photo_index.db` for trips, trip-photo links, and destinations.
* Added `Поездки` FAB on the main map.
* Added a separate dark MapLibre trip map using `TRIP_MAP_STYLE_URL`.
* Rendered each trip as a thumbnail marker with a `+N` photo-count badge.
* Added a trip detail page opened by tapping a trip marker.
* Rendered the selected trip with platform geocoder place names in the title and trip dates in the subtitle.
* Rendered the selected trip as a smoothed chronological route line over the dark map, with photo points and a start-to-end gradient.
* Added clickable route-point thumbnails that scroll the lower gallery to the selected trip photo.
* Opened lower-grid trip photos in the default Android gallery through a read-only view intent.
* Added a grid gallery of the selected trip photos below the route map.
* Added a long transparent right-edge trip timeline scrubber that snaps to trip points and shows semi-transparent trip labels.
* Highlighted the active trip timeline point with accent color and a border; single-trip timelines render as a point without a line.
* Added a matching transparent right-edge photo scrubber for jumping between photos inside a trip.
* Added a trip detail centering action that resets the route map zoom to the whole trip.
* Switched app routing from a manual screen stack to Navigation Compose.
* Refreshed the main map viewport after scan, camera fit, and returning from trip screens so normal photo markers appear immediately.
* Preserved selected trip zoom and center when returning from trip detail.
* Bumped Android metadata to `versionName=0.9.0`, `versionCode=15`.

## Scope

Included:

* MVP heuristic trip segmentation;
* dark OpenFreeMap style for the trip map;
* local-only storage and UI for trip markers;
* trip detail route map, platform geocoder place summary, route thumbnails, read-only gallery open, and photo grid;
* Navigation Compose dependency and `NavHost` routes.

Not included:

* editing trip boundaries;
* reverse geocoding or city names from network APIs;
* external geocoding SDKs or API keys;
* HMM/HSMM, Bayesian online detection, or external ML libraries;
* changing original user photos.

## Checks

Per the local project rule, Codex did not run Gradle build, tests, lint, `npx`, `tsc`, or `eslint`.

## Safety

The app remains read-only for user photos:

* no MediaStore delete/trash/write requests;
* no EXIF mutation;
* no upload of photos, coordinates, EXIF, media IDs, or trip statistics;
* trip and map data stay local to the device.

## Release

Target release:

```text
v0.9.0
```

Release notes:

```text
release-notes/v0.9.0.md
```

APK asset:

```text
not built after the v0.9.0 version bump
```
