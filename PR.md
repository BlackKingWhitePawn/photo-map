# PR: Bootstrap Android project for Photo Map v0.1.0

## Summary

* Initialized the Android project baseline for `Photo Map`.
* Added release, branch, changelog, and APK storage rules.
* Set the first app version to `0.1.0`.
* Prepared release notes for `v0.1.0`.

## Release

Target tag after merge:

```text
v0.1.0
```

Expected APK asset name:

```text
photomap-v0.1.0.apk
```

The existing signed APK was generated before `versionName` was changed to `0.1.0`, so it should be rebuilt once in Android Studio before uploading it to GitHub Releases.

## Checks

Not run by Codex for this request.

## Known Limitations

* This is a bootstrap release only.
* MediaStore, permissions, Room indexing, map, gallery, viewer, and background scanning are not implemented yet.
