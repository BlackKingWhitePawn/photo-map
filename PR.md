# PR: Fix singleton map clusters

## Summary

* Prepared patch release metadata for `v0.8.2`.
* Bumped Android version to `versionName=0.8.2`, `versionCode=14`.
* Normalized stored clusters with `photoCount=1` to visible single-photo map items.
* Changed visible map item aggregate detection so only `photoCount > 1` is treated as an aggregate.
* Fixed duplicated single-photo rendering where the same photo could appear as both a cluster and a photo marker.
* Cleared duplicate MapLibre marker sources so visible markers are rendered only by the Compose overlay.

## Scope

This PR is a patch on top of the `v0.8.1` date filter patch release.

Not included:

* Building or signing a `v0.8.2` release APK by Codex.
* Running Gradle build, test, lint, `npx`, `tsc`, or `eslint` commands.
* Room migration.
* Rebuilding the stored cluster index.
* Changing original user photos.

## Checks

* `git diff --check` completed without whitespace errors.
* Conflict markers were not found in the changed files.
* Test, lint, Gradle build, `npx`, `tsc`, and `eslint` commands were not run by Codex for this patch, per the local project rule.
* A `v0.8.2` APK was not built or inspected after the version bump.

## Safety

The app remains read-only for user photos:

* no MediaStore delete/trash/write requests;
* no EXIF mutation;
* no upload of photos, coordinates, EXIF, or file identifiers;
* cluster and map data stay local to the device.

## Release

Target release:

```text
v0.8.2
```

Release notes:

```text
release-notes/v0.8.2.md
```

APK asset:

```text
not built after the v0.8.2 version bump
```
