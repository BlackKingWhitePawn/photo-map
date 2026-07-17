# Branch `fix-11-progressive-exif-scan`

## Release Target

```text
v0.7.2
```

## Goal

Make EXIF scanning feel incremental: photos with GPS coordinates should appear on the map as indexed batches are saved, and a cancelled EXIF scan should resume from the last saved scan mark when started again.

## Scope

Included:

* persist EXIF scan resume progress after saved batches and cancellation;
* resume an EXIF scan from the previous stable MediaStore position when the gallery order is unchanged;
* start the EXIF GPS scan immediately after the first gallery permission grant;
* keep existing indexed photos in the final scan result instead of replacing the UI with only the current scan suffix;
* refresh map clusters from saved scan batches so newly found geotagged photos appear before the full gallery scan finishes;
* update `v0.7.2` changelog, PR notes, and release notes.

Not included:

* running Gradle build, compile, lint, tests, `npx`, `tsc`, or `eslint`;
* changing APK version metadata;
* changing or deleting original user photos;
* sending photos, coordinates, EXIF, or identifiers outside the device.

## Checks

Codex does not run build/compile/test commands for this patch per the local project rule.
