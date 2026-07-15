# PR: MapLibre photo map

## Summary

* Added a MapLibre-based map screen without Google Maps API key.
* Switched the default map style to OpenFreeMap Liberty through `MAP_STYLE_URL`.
* Added read-only MediaStore/EXIF GPS coordinate loading for map markers.
* Displayed photos with valid coordinates on the map.
* Added basic nearby-point grouping for low zoom levels.
* Fit the initial camera to the bounding box of all mapped photos.
* Removed country boundary layers from the loaded map style.
* Enabled map scroll and zoom gestures.
* Removed transitive location and Wi-Fi permissions pulled by the map SDK.

## Scope

This PR covers the first map-screen slice on top of the permissions and MediaStore work.

Not included:

* Room index.
* Gallery grid with Coil.
* Fullscreen viewer.
* WorkManager background scan.

## Checks

* `.\gradlew.bat assembleDebug` - passed after OpenFreeMap switch.
* APK permissions inspected with `aapt2 dump permissions`.
* Confirmed the APK does not request `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, or `ACCESS_WIFI_STATE`.
* Debug APK metadata inspected: `versionCode=3`, `versionName=0.3.0`.
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
5. Tap `Открыть карту`.
6. Confirm the map opens, can be scrolled/zoomed, and starts around the bounding box of photos with coordinates.

## Safety

The app only requests read-oriented media permissions:

* `READ_EXTERNAL_STORAGE` with `maxSdkVersion=32`;
* `READ_MEDIA_IMAGES`;
* `READ_MEDIA_VISUAL_USER_SELECTED`;
* `ACCESS_MEDIA_LOCATION`.

No source code path deletes, trashes, writes, updates, moves, or overwrites user photos.

## Map

The map screen uses MapLibre Android SDK with OpenFreeMap Liberty style and does not require Google Maps API key, Google Maps billing, registration, or Google Play Services.

Default style URL:

```text
https://tiles.openfreemap.org/styles/liberty
```

## Release

Target release:

```text
v0.3.0
```

Expected APK asset:

```text
photomap-v0.3.0.apk
```
