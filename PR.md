# PR: Permissions and MediaStore photo reading

## Summary

* Added Android photo permissions for Android 10-14+.
* Added `ACCESS_MEDIA_LOCATION` request for future original GPS metadata access.
* Added a MediaStore reader that loads image metadata through `ContentResolver`.
* Added `DevicePhoto` domain model.
* Added a Russian Compose screen for requesting access, showing permission state, scanning photos, and opening app settings.
* Kept MediaStore access outside `MainActivity` and outside direct ViewModel calls to `ContentResolver`.
* Prepared release version `0.2.0`.

## Scope

This PR covers stage 2: permissions and MediaStore.

Not included:

* Room index.
* EXIF parsing.
* Map and clustering.
* Gallery grid with Coil.
* Fullscreen viewer.
* WorkManager background scan.

## Checks

* `.\gradlew.bat assembleDebug` - passed.
* Debug APK metadata inspected: `versionCode=2`, `versionName=0.2.0`.
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
5. Check Logcat tag `PhotoMapMediaStore` for scanned photo entries.

## Safety

The app only requests read-oriented media permissions:

* `READ_EXTERNAL_STORAGE` with `maxSdkVersion=32`;
* `READ_MEDIA_IMAGES`;
* `READ_MEDIA_VISUAL_USER_SELECTED`;
* `ACCESS_MEDIA_LOCATION`.

No source code path deletes, trashes, writes, updates, moves, or overwrites user photos.

## Release

Target release:

```text
v0.2.0
```

Expected APK asset:

```text
photomap-v0.2.0.apk
```

The existing `app/release/app-release.apk` was generated before this release version bump and still reports `versionName=0.1.0`. Rebuild the signed release APK in Android Studio after merging these release prep changes.
