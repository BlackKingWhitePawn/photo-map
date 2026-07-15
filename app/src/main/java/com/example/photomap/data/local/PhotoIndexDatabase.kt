package com.example.photomap.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.photomap.data.media.PhotoIndexStats

class PhotoIndexDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DatabaseName,
    null,
    DatabaseVersion
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $PhotosTable (
                $MediaId INTEGER PRIMARY KEY,
                $Uri TEXT NOT NULL,
                $DisplayName TEXT,
                $MimeType TEXT,
                $DateAdded INTEGER,
                $DateModified INTEGER,
                $DateTaken INTEGER,
                $Width INTEGER,
                $Height INTEGER,
                $Size INTEGER,
                $Orientation INTEGER,
                $Latitude REAL,
                $Longitude REAL,
                $LocationScanned INTEGER NOT NULL,
                $IndexedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX index_photos_location_scanned ON $PhotosTable($LocationScanned)")
        db.execSQL("CREATE INDEX index_photos_date_taken ON $PhotosTable($DateTaken)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $PhotosTable")
        onCreate(db)
    }

    fun getAllPhotosById(): Map<Long, IndexedPhoto> {
        val photos = LinkedHashMap<Long, IndexedPhoto>()
        readableDatabase.query(
            PhotosTable,
            Columns,
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val photo = cursor.toIndexedPhoto()
                photos[photo.mediaId] = photo
            }
        }
        return photos
    }

    fun upsertPhotos(photos: List<IndexedPhoto>) {
        if (photos.isEmpty()) {
            return
        }

        writableDatabase.withTransaction {
            photos.forEach { photo ->
                insertWithOnConflict(
                    PhotosTable,
                    null,
                    photo.toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
        }
    }

    fun deleteMissingPhotos(existingMediaIds: Set<Long>) {
        val idsToDelete = mutableListOf<Long>()
        readableDatabase.query(
            PhotosTable,
            arrayOf(MediaId),
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->
            val mediaIdIndex = cursor.getColumnIndexOrThrow(MediaId)
            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(mediaIdIndex)
                if (mediaId !in existingMediaIds) {
                    idsToDelete += mediaId
                }
            }
        }

        if (idsToDelete.isEmpty()) {
            return
        }

        writableDatabase.withTransaction {
            idsToDelete.forEach { mediaId ->
                delete(PhotosTable, "$MediaId = ?", arrayOf(mediaId.toString()))
            }
        }
    }

    fun getStats(): PhotoIndexStats {
        readableDatabase.rawQuery(
            """
            SELECT 
                COUNT(*) AS total_count,
                SUM(CASE WHEN $LocationScanned = 1 THEN 1 ELSE 0 END) AS location_scanned_count,
                SUM(CASE WHEN $Latitude IS NOT NULL AND $Longitude IS NOT NULL THEN 1 ELSE 0 END) AS location_count
            FROM $PhotosTable
            """.trimIndent(),
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) {
                PhotoIndexStats(
                    totalCount = cursor.getIntValue("total_count"),
                    locationScannedCount = cursor.getIntValue("location_scanned_count"),
                    locationCount = cursor.getIntValue("location_count")
                )
            } else {
                PhotoIndexStats()
            }
        }
    }

    private fun IndexedPhoto.toContentValues(): ContentValues {
        return ContentValues().apply {
            put(MediaId, mediaId)
            put(Uri, uri)
            put(DisplayName, displayName)
            put(MimeType, mimeType)
            put(DateAdded, dateAdded)
            put(DateModified, dateModified)
            put(DateTaken, dateTaken)
            put(Width, width)
            put(Height, height)
            put(Size, size)
            put(Orientation, orientation)
            put(Latitude, latitude)
            put(Longitude, longitude)
            put(LocationScanned, if (locationScanned) 1 else 0)
            put(IndexedAt, indexedAt)
        }
    }

    private fun Cursor.toIndexedPhoto(): IndexedPhoto {
        return IndexedPhoto(
            mediaId = getLongValue(MediaId) ?: error("Indexed photo without media id"),
            uri = getStringValue(Uri) ?: "",
            displayName = getStringValue(DisplayName),
            mimeType = getStringValue(MimeType),
            dateAdded = getLongValue(DateAdded),
            dateModified = getLongValue(DateModified),
            dateTaken = getLongValue(DateTaken),
            width = getIntNullableValue(Width),
            height = getIntNullableValue(Height),
            size = getLongValue(Size),
            orientation = getIntNullableValue(Orientation),
            latitude = getDoubleValue(Latitude),
            longitude = getDoubleValue(Longitude),
            locationScanned = (getIntNullableValue(LocationScanned) ?: 0) == 1,
            indexedAt = getLongValue(IndexedAt) ?: 0L
        )
    }

    private fun Cursor.getStringValue(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private fun Cursor.getLongValue(columnName: String): Long? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getLong(index) else null
    }

    private fun Cursor.getIntNullableValue(columnName: String): Int? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getInt(index) else null
    }

    private fun Cursor.getIntValue(columnName: String): Int {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getInt(index) else 0
    }

    private fun Cursor.getDoubleValue(columnName: String): Double? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getDouble(index) else null
    }

    private inline fun SQLiteDatabase.withTransaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    companion object {
        private const val DatabaseName = "photo_index.db"
        private const val DatabaseVersion = 1

        private const val PhotosTable = "photos"
        private const val MediaId = "media_id"
        private const val Uri = "uri"
        private const val DisplayName = "display_name"
        private const val MimeType = "mime_type"
        private const val DateAdded = "date_added"
        private const val DateModified = "date_modified"
        private const val DateTaken = "date_taken"
        private const val Width = "width"
        private const val Height = "height"
        private const val Size = "size"
        private const val Orientation = "orientation"
        private const val Latitude = "latitude"
        private const val Longitude = "longitude"
        private const val LocationScanned = "location_scanned"
        private const val IndexedAt = "indexed_at"

        private val Columns = arrayOf(
            MediaId,
            Uri,
            DisplayName,
            MimeType,
            DateAdded,
            DateModified,
            DateTaken,
            Width,
            Height,
            Size,
            Orientation,
            Latitude,
            Longitude,
            LocationScanned,
            IndexedAt
        )
    }
}
