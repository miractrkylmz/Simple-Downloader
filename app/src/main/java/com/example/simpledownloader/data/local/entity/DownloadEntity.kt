package com.example.simpledownloader.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing download history
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val videoUrl: String,
    val title: String,
    val thumbnailUrl: String?,
    val platform: String, // Stored as string name of Platform enum
    val filePath: String,
    val fileSize: Long, // in bytes
    val downloadDate: Long, // timestamp in milliseconds
    val duration: Int?, // in seconds (null or 0 for photos/images)
    val uploader: String?,
    val status: String, // "completed", "failed", "deleted"
    
    @ColumnInfo(name = "mediaType", defaultValue = "VIDEO")
    val mediaType: String = "VIDEO" // "VIDEO" or "IMAGE"
)
