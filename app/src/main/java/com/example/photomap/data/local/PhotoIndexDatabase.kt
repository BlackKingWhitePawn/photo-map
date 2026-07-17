package com.example.photomap.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.photomap.data.cluster.CLUSTERING_VERSION
import com.example.photomap.data.cluster.PhotoClusterLink
import com.example.photomap.data.cluster.PhotoMapBounds
import com.example.photomap.data.cluster.StoredPhotoCluster
import com.example.photomap.data.media.PhotoIndexStats
import com.example.photomap.domain.model.PhotoDateFilter
import com.example.photomap.domain.model.matchesPhotoDateFilter

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
        db.createClusterTables()
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.createClusterTables()
        } else {
            db.execSQL("DROP TABLE IF EXISTS $PhotoClusterLinksTable")
            db.execSQL("DROP TABLE IF EXISTS $PhotoClustersTable")
            db.execSQL("DROP TABLE IF EXISTS $PhotoClusterMetaTable")
            db.execSQL("DROP TABLE IF EXISTS $PhotosTable")
            onCreate(db)
        }
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

    fun getPhotosInBounds(
        bounds: PhotoMapBounds,
        dateFilter: PhotoDateFilter = PhotoDateFilter()
    ): List<IndexedPhoto> {
        val photos = mutableListOf<IndexedPhoto>()
        readableDatabase.query(
            PhotosTable,
            Columns,
            "$Latitude IS NOT NULL AND $Longitude IS NOT NULL AND " +
                "$Latitude >= ? AND $Latitude <= ? AND $Longitude >= ? AND $Longitude <= ?",
            arrayOf(
                bounds.south.toString(),
                bounds.north.toString(),
                bounds.west.toString(),
                bounds.east.toString()
            ),
            null,
            null,
            "$DateTaken DESC, $DateModified DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val photo = cursor.toIndexedPhoto()
                if (photo.toDevicePhoto().matchesPhotoDateFilter(dateFilter)) {
                    photos += photo
                }
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
            idsToDelete.forEach { mediaId ->
                delete(PhotoClusterLinksTable, "$ClusterPhotoId = ?", arrayOf(mediaId.toString()))
            }
        }
    }

    fun getStoredClusterVersion(): Int? {
        readableDatabase.query(
            PhotoClusterMetaTable,
            arrayOf(ClusterMetaValue),
            "$ClusterMetaKey = ?",
            arrayOf(ClusterMetaVersionKey),
            null,
            null,
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) {
                cursor.getIntValue(ClusterMetaValue)
            } else {
                null
            }
        }
    }

    fun replaceClusters(
        clusters: List<StoredPhotoCluster>,
        links: List<PhotoClusterLink>,
        version: Int = CLUSTERING_VERSION
    ) {
        writableDatabase.withTransaction {
            delete(PhotoClusterLinksTable, null, null)
            delete(PhotoClustersTable, null, null)
            delete(PhotoClusterMetaTable, null, null)

            clusters.forEach { cluster ->
                insertWithOnConflict(
                    PhotoClustersTable,
                    null,
                    cluster.toContentValues(version),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            links.forEach { link ->
                insertWithOnConflict(
                    PhotoClusterLinksTable,
                    null,
                    link.toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            insertWithOnConflict(
                PhotoClusterMetaTable,
                null,
                ContentValues().apply {
                    put(ClusterMetaKey, ClusterMetaVersionKey)
                    put(ClusterMetaValue, version)
                },
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    fun getClustersInBounds(
        level: Int,
        bounds: PhotoMapBounds,
        version: Int = CLUSTERING_VERSION
    ): List<StoredPhotoCluster> {
        val clusters = mutableListOf<StoredPhotoCluster>()
        readableDatabase.query(
            PhotoClustersTable,
            ClusterColumns,
            "$ClusterLevel = ? AND $ClusterVersion = ? AND " +
                "$ClusterMaxLatitude >= ? AND $ClusterMinLatitude <= ? AND " +
                "$ClusterMaxLongitude >= ? AND $ClusterMinLongitude <= ?",
            arrayOf(
                level.toString(),
                version.toString(),
                bounds.south.toString(),
                bounds.north.toString(),
                bounds.west.toString(),
                bounds.east.toString()
            ),
            null,
            null,
            "$ClusterPriorityScore DESC, $ClusterPhotoCount DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                clusters += cursor.toStoredCluster()
            }
        }
        return clusters
    }

    fun getClusterPhotoIds(clusterId: String): List<Long> {
        val photoIds = mutableListOf<Long>()
        readableDatabase.query(
            PhotoClusterLinksTable,
            arrayOf(ClusterPhotoId),
            "$ClusterLinkClusterId = ?",
            arrayOf(clusterId),
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getLongValue(ClusterPhotoId)?.let { photoId -> photoIds += photoId }
            }
        }
        return photoIds
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

    private fun StoredPhotoCluster.toContentValues(version: Int): ContentValues {
        return ContentValues().apply {
            put(ClusterId, clusterId)
            put(ClusterLevel, level)
            put(ClusterH3Index, h3Index)
            put(ClusterParentId, parentClusterId)
            put(ClusterCenterLatitude, latitude)
            put(ClusterCenterLongitude, longitude)
            put(ClusterPhotoCount, photoCount)
            put(ClusterPriorityScore, priorityScore)
            put(ClusterMinLatitude, minLatitude)
            put(ClusterMaxLatitude, maxLatitude)
            put(ClusterMinLongitude, minLongitude)
            put(ClusterMaxLongitude, maxLongitude)
            put(ClusterCoverPhotoId, coverPhotoId)
            put(ClusterUpdatedAt, updatedAt)
            put(ClusterVersion, version)
        }
    }

    private fun PhotoClusterLink.toContentValues(): ContentValues {
        return ContentValues().apply {
            put(ClusterPhotoId, photoId)
            put(ClusterLinkClusterId, clusterId)
            put(ClusterLinkLevel, level)
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

    private fun Cursor.toStoredCluster(): StoredPhotoCluster {
        return StoredPhotoCluster(
            clusterId = getStringValue(ClusterId) ?: error("Stored cluster without id"),
            level = getIntNullableValue(ClusterLevel) ?: 0,
            h3Index = getStringValue(ClusterH3Index) ?: "",
            parentClusterId = getStringValue(ClusterParentId),
            latitude = getDoubleValue(ClusterCenterLatitude) ?: 0.0,
            longitude = getDoubleValue(ClusterCenterLongitude) ?: 0.0,
            photoCount = getIntNullableValue(ClusterPhotoCount) ?: 0,
            priorityScore = getDoubleValue(ClusterPriorityScore) ?: 0.0,
            minLatitude = getDoubleValue(ClusterMinLatitude) ?: 0.0,
            maxLatitude = getDoubleValue(ClusterMaxLatitude) ?: 0.0,
            minLongitude = getDoubleValue(ClusterMinLongitude) ?: 0.0,
            maxLongitude = getDoubleValue(ClusterMaxLongitude) ?: 0.0,
            coverPhotoId = getLongValue(ClusterCoverPhotoId),
            updatedAt = getLongValue(ClusterUpdatedAt) ?: 0L,
            version = getIntNullableValue(ClusterVersion) ?: 0
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

    private fun SQLiteDatabase.createClusterTables() {
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS $PhotoClustersTable (
                $ClusterId TEXT PRIMARY KEY NOT NULL,
                $ClusterLevel INTEGER NOT NULL,
                $ClusterH3Index TEXT NOT NULL,
                $ClusterParentId TEXT,
                $ClusterCenterLatitude REAL NOT NULL,
                $ClusterCenterLongitude REAL NOT NULL,
                $ClusterPhotoCount INTEGER NOT NULL,
                $ClusterPriorityScore REAL NOT NULL,
                $ClusterMinLatitude REAL NOT NULL,
                $ClusterMaxLatitude REAL NOT NULL,
                $ClusterMinLongitude REAL NOT NULL,
                $ClusterMaxLongitude REAL NOT NULL,
                $ClusterCoverPhotoId INTEGER,
                $ClusterUpdatedAt INTEGER NOT NULL,
                $ClusterVersion INTEGER NOT NULL
            )
            """.trimIndent()
        )
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS $PhotoClusterLinksTable (
                $ClusterPhotoId INTEGER NOT NULL,
                $ClusterLinkClusterId TEXT NOT NULL,
                $ClusterLinkLevel INTEGER NOT NULL,
                PRIMARY KEY ($ClusterPhotoId, $ClusterLinkClusterId)
            )
            """.trimIndent()
        )
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS $PhotoClusterMetaTable (
                $ClusterMetaKey TEXT PRIMARY KEY NOT NULL,
                $ClusterMetaValue INTEGER NOT NULL
            )
            """.trimIndent()
        )
        execSQL("CREATE INDEX IF NOT EXISTS index_clusters_level ON $PhotoClustersTable($ClusterLevel)")
        execSQL(
            "CREATE INDEX IF NOT EXISTS index_clusters_bounds ON $PhotoClustersTable(" +
                "$ClusterLevel, $ClusterMinLatitude, $ClusterMaxLatitude, " +
                "$ClusterMinLongitude, $ClusterMaxLongitude)"
        )
        execSQL("CREATE INDEX IF NOT EXISTS index_clusters_h3 ON $PhotoClustersTable($ClusterLevel, $ClusterH3Index)")
        execSQL("CREATE INDEX IF NOT EXISTS index_cluster_links_cluster ON $PhotoClusterLinksTable($ClusterLinkClusterId)")
    }

    companion object {
        private const val DatabaseName = "photo_index.db"
        private const val DatabaseVersion = 2

        private const val PhotosTable = "photos"
        private const val PhotoClustersTable = "photo_clusters"
        private const val PhotoClusterLinksTable = "photo_cluster_links"
        private const val PhotoClusterMetaTable = "photo_cluster_meta"
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

        private const val ClusterId = "id"
        private const val ClusterLevel = "level"
        private const val ClusterH3Index = "h3_index"
        private const val ClusterParentId = "parent_id"
        private const val ClusterCenterLatitude = "center_latitude"
        private const val ClusterCenterLongitude = "center_longitude"
        private const val ClusterPhotoCount = "photo_count"
        private const val ClusterPriorityScore = "priority_score"
        private const val ClusterMinLatitude = "min_latitude"
        private const val ClusterMaxLatitude = "max_latitude"
        private const val ClusterMinLongitude = "min_longitude"
        private const val ClusterMaxLongitude = "max_longitude"
        private const val ClusterCoverPhotoId = "cover_photo_id"
        private const val ClusterUpdatedAt = "updated_at"
        private const val ClusterVersion = "version"

        private const val ClusterPhotoId = "photo_id"
        private const val ClusterLinkClusterId = "cluster_id"
        private const val ClusterLinkLevel = "level"
        private const val ClusterMetaKey = "key"
        private const val ClusterMetaValue = "value"
        private const val ClusterMetaVersionKey = "cluster_version"

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

        private val ClusterColumns = arrayOf(
            ClusterId,
            ClusterLevel,
            ClusterH3Index,
            ClusterParentId,
            ClusterCenterLatitude,
            ClusterCenterLongitude,
            ClusterPhotoCount,
            ClusterPriorityScore,
            ClusterMinLatitude,
            ClusterMaxLatitude,
            ClusterMinLongitude,
            ClusterMaxLongitude,
            ClusterCoverPhotoId,
            ClusterUpdatedAt,
            ClusterVersion
        )
    }
}
