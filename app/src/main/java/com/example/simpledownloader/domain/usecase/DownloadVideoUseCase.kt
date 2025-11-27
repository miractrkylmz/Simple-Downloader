package com.example.simpledownloader.domain.usecase

import com.example.simpledownloader.domain.model.DownloadState
import com.example.simpledownloader.domain.model.VideoInfo
import com.example.simpledownloader.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for downloading a video
 * This orchestrates the download process with progress tracking
 */
class DownloadVideoUseCase(
    private val repository: VideoRepository
) {
    /**
     * Execute the video download
     * @param videoInfo The video to download
     * @param format The specific format to download (optional)
     * @return Flow emitting download states (Downloading → Completed or Failed)
     */
    operator fun invoke(
        videoInfo: VideoInfo,
        format: com.example.simpledownloader.domain.model.VideoFormat?,
        destinationUri: String? = null
    ): Flow<DownloadState> {
        return repository.downloadVideo(videoInfo, format, destinationUri)
    }
}
