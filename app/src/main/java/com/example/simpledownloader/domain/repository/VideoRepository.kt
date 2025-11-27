package com.example.simpledownloader.domain.repository

import com.example.simpledownloader.data.local.entity.DownloadEntity
import com.example.simpledownloader.domain.model.DownloadState
import com.example.simpledownloader.domain.model.VideoFormat
import com.example.simpledownloader.domain.model.VideoInfo
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for video operations
 * Implementation will be in the data layer
 */
interface VideoRepository {
    /**
     * Analyze a URL and extract video information
     * @param url The video URL to analyze
     * @return Flow emitting download states during analysis
     */
    /**
     * Analyze a URL and extract video information
     * @param url The video URL to analyze
     * @return Flow emitting download states during analysis
     */
    fun analyzeUrl(url: String): Flow<DownloadState>
    
    /**
     * Download a video
     * @param videoInfo The video information to download
     * @param format The specific format to download (optional)
     * @return Flow emitting download progress states
     */
    fun downloadVideo(videoInfo: VideoInfo, format: VideoFormat?, destinationUri: String? = null): Flow<DownloadState>
    
    /**
     * Get the history of downloads
     * @return Flow emitting list of downloads
     */
    fun getDownloadHistory(): Flow<List<DownloadEntity>>
    
    /**
     * Delete a download
     * @param downloadId The ID of the download to delete
     */
    suspend fun deleteDownload(downloadId: String)
}
