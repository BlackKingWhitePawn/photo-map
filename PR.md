# PR: Persistent photo index

## Summary

* Added a local SQLite index for processed photos.
* Persisted EXIF scan progress across app restarts.
* Reused cached coordinates for unchanged `MediaStore` items.
* Invalidated cache entries when `dateModified` or `size` changes.
* Updated indexed metadata when a photo is renamed.
* Removed missing `MediaStore` items from the local index only.
* Replaced fixed 500-step progress with time-based progress updates and percent display.
* Added GPS index status to the photo access screen.

## Scope

This PR covers a local persistent index for large galleries after the 40k performance branch.

Not included:

* Full Room migration.
* Gallery grid with Coil.
* Fullscreen viewer.
* WorkManager background scan.
* User-facing index cleanup settings.

## Checks

* `.\gradlew.bat assembleDebug` - passed.
* Debug APK metadata inspected: `versionCode=5`, `versionName=0.4.0`.
* APK permissions inspected with `aapt2 dump permissions`.
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
5. Tap `Искать GPS в EXIF`.
6. Close/reopen the app during or after scanning.
7. Confirm `GPS-индекс` keeps the saved processed count.
8. Rename a photo and confirm it is still present with updated metadata.
9. Delete a photo outside the app and confirm it disappears from the app after refresh.

## Safety

The app only requests read-oriented media permissions:

* `READ_EXTERNAL_STORAGE` with `maxSdkVersion=32`;
* `READ_MEDIA_IMAGES`;
* `READ_MEDIA_VISUAL_USER_SELECTED`;
* `ACCESS_MEDIA_LOCATION`.

No source code path deletes, trashes, writes, updates, moves, or overwrites user photos. The only delete operation in this branch removes rows from the app's private local SQLite index.

## Map

The map keeps the existing MapLibre/OpenFreeMap setup and consumes the same `DevicePhoto` list. Cached coordinates from the local index are reused for map markers.

## Release

Target release:

```text
v0.4.0
```

Expected APK asset:

```text
photomap-v0.4.0.apk
```
