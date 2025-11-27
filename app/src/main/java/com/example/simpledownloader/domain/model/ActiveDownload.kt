package com.example.simpledownloader.domain.model

/**
 * Represents an active download with metadata
 */
data class ActiveDownload(
    val id: String,
    val videoInfo: VideoInfo,
    val downloadState: DownloadState.Downloading,
    val isPaused: Boolean = false
)
