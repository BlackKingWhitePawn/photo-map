# PR: Map date filtering and cancellable clustering

## Summary

* Prepared minor release metadata for `v0.8.0`.
* Bumped Android version to `versionName=0.8.0`, `versionCode=12`.
* Added a map date range filter opened from a second FAB.
* Added a two-sided range slider that applies the date filter only after release.
* Shows slider drag dates as `dd MMMM yyyy`.
* Shows the collapsed date FAB range as `dd.MM.yy - dd.MM.yy`.
* Made map cluster rebuilding and viewport loading cancellable so stale work does not overwrite newer map state.
* Moved screen-level marker compaction work off the main thread and added cooperative cancellation.

## Scope

This PR focuses on map filtering and map responsiveness during heavy cluster recalculation.

Not included:

* Building or signing a release APK by Codex.
* Running Gradle build, test, lint, `npx`, `tsc`, or `eslint` commands.
* Room migration.
* Coil gallery grid.
* Fullscreen photo viewer.
* WorkManager background scan.

## Checks

* Release APK inspected: `app/release/photomap-v0.8.0.apk`.
* APK metadata inspected with `aapt2 dump badging`: `versionCode=12`, `versionName=0.8.0`.
* APK permissions inspected with `aapt2 dump permissions`.
* APK signature inspected with `apksigner verify --verbose --print-certs`: `Verifies`, v2 signature enabled.
* APK alignment inspected with `zipalign -c -p 4`.
* Test, lint, `npx`, `tsc`, and `eslint` commands were not run by Codex for this patch, per the local project rule.

## Safety

The app remains read-only for user photos:

* no MediaStore delete/trash/write requests;
* no EXIF mutation;
* no upload of photos, coordinates, EXIF, or file identifiers;
* cluster and map data stay local to the device.

## Release

Target release:

```text
v0.8.0
```

Release notes:

```text
release-notes/v0.8.0.md
```

APK asset:

```text
app/release/photomap-v0.8.0.apk
```

SHA-256:

```text
39BD6FF2ACB5619FA3FFF46FEEC4AA6980B4BDFD0D8A7DF1E436E1BDEE900112
```
