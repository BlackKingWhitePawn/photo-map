# Branch `feature-12-map-date-filter`

## Release Target

```text
v0.8.1
```

## Goal

Add map photo filtering by shooting date and make cluster recalculation responsive during map movement and filter changes.

## Patch Goal

Polish the date filter slider after the `v0.8.0` release prep.

## Scope

Included:

* add a second map FAB for date filtering;
* add a two-sided date range slider;
* show the slider drag range as `dd MMMM yyyy`;
* show the collapsed date FAB range as `dd.MM.yy - dd.MM.yy`;
* apply date filtering only when the slider drag finishes;
* open a date picker from the range labels for precise date selection;
* draw month and year tick marks behind the date slider;
* snap slider handles to nearby month, year, and range boundary ticks;
* scale the active date slider range and expand it back when dragging toward the edges;
* filter map clusters and max-zoom single markers by the selected date range;
* make cluster rebuilds cancellable asynchronous operations;
* cancel stale visible viewport loads during rapid map movement;
* move screen-level marker compaction off the main thread;
* hide the geolocation empty state while an active date filter simply filters all photos out;
* update `v0.8.1` changelog, PR notes, and release notes.

Not included:

* running Gradle build, compile, lint, tests, `npx`, `tsc`, or `eslint`;
* building or signing a `v0.8.1` release APK;
* changing or deleting original user photos;
* sending photos, coordinates, EXIF, or identifiers outside the device.

## Checks

`git diff --check` completed without whitespace errors.
Conflict markers were not found in the changed map screen file.
A `v0.8.1` APK was not built or inspected after the version bump.
Codex did not run build, test, lint, `npx`, `tsc`, or `eslint` commands for this patch per the local project rule.
