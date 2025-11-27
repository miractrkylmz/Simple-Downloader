package com.example.simpledownloader.data.local.dao

import androidx.room.*
import com.example.simpledownloader.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO (Data Access Object) for download operations
 */
@Dao
interface DownloadDao {
    /**
     * Insert a new download record
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity): Long
    
    /**
     * Get all downloads sorted by date (newest first)
     */
    @Query("SELECT * FROM downloads WHERE status = 'completed' ORDER BY downloadDate DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>
    
    /**
     * Get a specific download by ID
     */
    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: Long): DownloadEntity?
    
    /**
     * Delete a download record
     */
    @Delete
    suspend fun deleteDownload(download: DownloadEntity)
    
    /**
     * Delete download by ID
     */
    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    /**
     * Update download status
     */
    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)
    
    /**
     * Get total count of downloads
     */
    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'completed'")
    suspend fun getDownloadCount(): Int
    
    /**
     * Clear all downloads
     */
    @Query("DELETE FROM downloads")
    suspend fun clearAll()
}
