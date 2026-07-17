# Branch `feature-12-map-date-filter`

## Release Target

```text
v0.8.0
```

## Goal

Add map photo filtering by shooting date and make cluster recalculation responsive during map movement and filter changes.

## Scope

Included:

* add a second map FAB for date filtering;
* add a two-sided date range slider;
* show the slider drag range as `dd MMMM yyyy`;
* show the collapsed date FAB range as `dd.MM.yy - dd.MM.yy`;
* apply date filtering only when the slider drag finishes;
* filter map clusters and max-zoom single markers by the selected date range;
* make cluster rebuilds cancellable asynchronous operations;
* cancel stale visible viewport loads during rapid map movement;
* move screen-level marker compaction off the main thread;
* update `v0.8.0` changelog, PR notes, and release notes.

Not included:

* running Gradle build, compile, lint, tests, `npx`, `tsc`, or `eslint`;
* building or signing a release APK;
* changing or deleting original user photos;
* sending photos, coordinates, EXIF, or identifiers outside the device.

## Checks

`assembleRelease` could not be completed because Gradle cache access outside the sandbox was not approved.
Codex did not run test, lint, `npx`, `tsc`, or `eslint` commands for this patch per the local project rule.
