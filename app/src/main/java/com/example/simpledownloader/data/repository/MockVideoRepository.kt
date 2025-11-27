package com.example.simpledownloader.data.repository

import com.example.simpledownloader.domain.model.*
import com.example.simpledownloader.domain.repository.VideoRepository
import com.example.simpledownloader.data.local.entity.DownloadEntity
import com.example.simpledownloader.utils.PlatformDetector
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Mock implementation of VideoRepository for testing
 * This will be replaced with actual youtubedl-android implementation
 */
class MockVideoRepository : VideoRepository {
    
    override fun analyzeUrl(url: String): Flow<DownloadState> = flow {
        // Step 1: Validating
        emit(DownloadState.Analyzing(AnalysisStep.VALIDATING))
        delay(1000)
        
        // Step 2: Fetching metadata
        emit(DownloadState.Analyzing(AnalysisStep.FETCHING))
        delay(1500)
        
        // Step 3: Analyzing formats
        emit(DownloadState.Analyzing(AnalysisStep.ANALYZING))
        delay(1000)
        
        // Step 4: Ready
        val platform = PlatformDetector.detectPlatform(url)
        
        val videoInfo = VideoInfo(
            url = url,
            title = "Sample Video: How to Build Android Apps with Jetpack Compose",
            thumbnailUrl = "https://picsum.photos/640/360", // Random placeholder
            duration = 634, // 10:34 in seconds
            platform = platform,
            uploader = "Android Developers",
            description = "Learn how to build amazing Android apps using Jetpack Compose",
            viewCount = 125000,
            availableFormats = listOf(
                VideoFormat(
                    formatId = "best",
                    ext = "mp4",
                    quality = "1080p",
                    fileSize = 67890000, // ~67 MB
                    hasVideo = true,
                    hasAudio = true
                )
            )
        )
        
        emit(DownloadState.Ready(videoInfo))
    }
    
    override fun downloadVideo(videoInfo: VideoInfo, format: VideoFormat?, destinationUri: String?): Flow<DownloadState> = flow {
        // This will be implemented later with the download service
        emit(DownloadState.Downloading(
            progress = 0f,
            downloadedBytes = 0,
            totalBytes = 67890000,
            speedBytesPerSecond = 2048000, // 2 MB/s
            etaSeconds = 33
        ))
    }
    
    override fun getDownloadHistory(): Flow<List<DownloadEntity>> = flow {
        emit(emptyList<DownloadEntity>())
    }
    
    override suspend fun deleteDownload(downloadId: String) {
        // Not implemented in mock
    }
}
