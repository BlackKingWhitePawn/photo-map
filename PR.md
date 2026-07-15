# PR: Large gallery performance

## Summary

* Optimized startup behavior for large local galleries, target size: 40k photos.
* Stopped automatic startup scan from reading EXIF for every photo.
* Added a separate manual EXIF GPS scan action.
* Added scan progress updates every 500 photos.
* Removed per-photo Logcat logging during MediaStore scans.
* Stored GPS photo count in UI state instead of recalculating it in Compose.
* Added grouped map marker icons that display the photo count.
* Limited map marker rendering to the visible viewport after the initial fit-to-bounds.

## Scope

This PR covers performance and UX fixes for large local galleries after the first MapLibre map release.

Not included:

* Room index.
* Gallery grid with Coil.
* Fullscreen viewer.
* WorkManager background scan.
* Persistent incremental index for EXIF results.

## Checks

* `.\gradlew.bat assembleDebug` - passed.
* Source checked for MediaStore delete/write operations.

The command was run with `JAVA_HOME` set to Android Studio JBR for the current process:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
```

## Manual Review Notes

On a device or emulator with photos:

1. Launch the app.
2. Tap `Предоставить доступ`.
3. Grant full or selected photo access.
4. Confirm the screen shows the number of found photos.
5. Confirm startup scan finishes without reading EXIF for all photos.
6. Tap `Искать GPS в EXIF` only when a deep GPS scan is needed.
7. Confirm progress text shows `Обработано X из Y`.
8. Tap `Открыть карту` and confirm grouped markers show photo counts.

## Safety

The app only requests read-oriented media permissions:

* `READ_EXTERNAL_STORAGE` with `maxSdkVersion=32`;
* `READ_MEDIA_IMAGES`;
* `READ_MEDIA_VISUAL_USER_SELECTED`;
* `ACCESS_MEDIA_LOCATION`.

No source code path deletes, trashes, writes, updates, moves, or overwrites user photos.

## Map

The map keeps the existing MapLibre/OpenFreeMap setup. Marker rendering is now viewport-bound after the first camera fit, so zooming into a dense area does not render all mapped photos worldwide.

## Release

No release APK is prepared in this branch. If this branch is released separately after merge, use:

```text
v0.3.1
```

Expected APK asset:

```text
photomap-v0.3.1.apk
```
