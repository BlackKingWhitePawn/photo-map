# Security Policy

## User Photo Safety

Photo Map must never delete, modify, overwrite, move, upload, or rewrite original user photos.

The app may read image metadata through Android MediaStore after the user grants access. All media processing must stay local to the device.

Strictly forbidden:

* requesting `WRITE_EXTERNAL_STORAGE`;
* requesting `MANAGE_EXTERNAL_STORAGE`;
* calling `ContentResolver.delete` for photo URIs;
* calling MediaStore delete, trash, or write requests for user photos;
* opening photo `content://` URIs for write access;
* changing EXIF data in original files;
* copying original photos to a remote server.

Allowed for current and planned MVP work:

* reading image rows from MediaStore;
* reading EXIF metadata when required and permitted;
* storing local index metadata in the app's private SQLite/Room database;
* loading thumbnails through Android/Coil read APIs;
* deleting only the app's own local index records, never original media files.

If a future feature requires media mutation, it must be rejected for this project unless the product requirement explicitly changes and this policy is updated first.
