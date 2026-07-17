# Branch `fix-13-single-photo-cluster`

## Release Target

```text
v0.8.2
```

## Goal

Fix map marker duplication where a single photo can be exposed as a cluster with `photoCount=1` and leave a visible artifact on the map.

## Scope

Included:

* normalize stored clusters with `photoCount <= 1` into visible single-photo map items;
* treat visible map items as aggregates only when `photoCount > 1`;
* keep existing singleton cluster rows usable without a Room migration or cluster index rebuild;
* clear duplicate MapLibre marker sources so markers are drawn only through the Compose overlay;
* update `v0.8.2` version metadata, changelog, PR notes, and release notes.

Not included:

* running Gradle build, compile, lint, tests, `npx`, `tsc`, or `eslint`;
* building or signing a `v0.8.2` release APK;
* rebuilding the stored cluster index;
* changing or deleting original user photos;
* sending photos, coordinates, EXIF, or identifiers outside the device.

## Checks

`git diff --check` completed without whitespace errors.
Conflict markers were not found in the changed files.
A `v0.8.2` APK was not built or inspected after the version bump.
Codex did not run build, test, lint, `npx`, `tsc`, or `eslint` commands for this patch per the local project rule.
