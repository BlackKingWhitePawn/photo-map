# PR: Permissions and MediaStore photo reading

## Summary

* Added Android photo permissions for Android 10-14+.
* Added `ACCESS_MEDIA_LOCATION` request for future original GPS metadata access.
* Added a MediaStore reader that loads image metadata through `ContentResolver`.
* Added `DevicePhoto` domain model.
* Added a Russian Compose screen for requesting access, showing permission state, scanning photos, and opening app settings.
* Kept MediaStore access outside `MainActivity` and outside direct ViewModel calls to `ContentResolver`.

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

Not run by Codex yet in this branch.

## Manual Review Notes

On a device or emulator with photos:

1. Launch the app.
2. Tap `Предоставить доступ`.
3. Grant full or selected photo access.
4. Confirm the screen shows the number of found photos.
5. Check Logcat tag `PhotoMapMediaStore` for scanned photo entries.
