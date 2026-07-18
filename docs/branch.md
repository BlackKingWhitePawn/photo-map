# Branch `release-1-0-0`

## Release Target

```text
v1.0.0
```

## Task

Prepare the first stable Traverse release after the home widget, trip export, settings, and map control polish.

The branch packages the current app state with:

* real trip heatmap rendering in the home gallery geography widget;
* hidden political borders and country labels in the compact home map preview;
* trip export images backed by the first trip photo;
* simplified settings without algorithm/debug controls;
* updated map bottom controls with a segmented heatmap/photo switch;
* visible product naming changed to Traverse;
* Android metadata for `v1.0.0`.

## Scope

Included:

* release branch `release-1-0-0`;
* Android metadata bump to `versionName=1.0.0`, `versionCode=19`;
* visible app name changed to `Traverse`;
* changelog entry for `v1.0.0`;
* release notes for `v1.0.0`;
* prepared APK asset `app/release/traverse-v1.0.0.apk`;
* updated PR and branch documentation.

Not included:

* signed release APK build;
* Play Console upload;
* tag creation;
* remote push;
* package/class namespace rename from `photomap`/`PhotoMap`.

## Acceptance Criteria

* App metadata reports `versionName=1.0.0` and `versionCode=19`.
* Visible app branding says `Traverse`.
* `CHANGELOG.md` contains a dated `1.0.0` section.
* `release-notes/v1.0.0.md` exists and records APK metadata status inside `Version`.
* Release APK asset is available as `app/release/traverse-v1.0.0.apk`.
* Home widget uses real trip heatmap data without country borders or country labels.
* Trip share export uses the first trip photo as its background and omits confidence.
* Settings do not expose algorithm tuning, debug controls, or diagnostic log export.
* Bottom map controls use spaced layout with same-height date and display-mode controls.
* Codex does not run tests, `npx`, `tsc`, or `eslint`.
