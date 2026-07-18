# Branch `release-0-11-0`

## Release Target

```text
v0.11.0
```

## Task

Implement the new home screen described in `mainPage.md`.

The home screen becomes the app start screen and shows the user's local photo geography through:

* a compact trip heatmap preview;
* a featured trips carousel;
* popular places cards;
* an "On this day in the past" section;
* reusable mini-gallery components for shared thumbnail rows.

## Scope

Included:

* `home` navigation route as the start destination;
* all-places and place-details routes;
* home heatmap preview using existing ready trip heat cells;
* trip cards built from existing `TripMapMarker` data;
* popular place cards built from already indexed photos with coordinates;
* mini-galleries grouped by day on the place details screen;
* reusable `MiniPhotoGallery` and `MiniPhotoThumbnail`;
* Android metadata and release docs for `v0.11.0`.

Not included:

* a persisted places table;
* manual place editing;
* network geocoding for place cards;
* new analytics, server sync, or photo write operations.

## Acceptance Criteria

* App opens to the home screen after permissions are available.
* Home screen scrolls vertically and uses lazy sections.
* Top map preview renders the existing trip heatmap data and does not run trip detection or H3 aggregation.
* Embedded map previews do not interfere with vertical scrolling.
* Trip cards open trip details and "All trips" opens the trip map.
* Place cards open a place detail screen with grouped mini-galleries.
* The mini-gallery is reusable outside the home feature.
* Full photo map, trip map, settings, and permission flows remain reachable.
* Original user photos remain read-only.
* Codex does not run Gradle build, tests, lint, `npx`, `tsc`, or `eslint`.
