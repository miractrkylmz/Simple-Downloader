package com.example.simpledownloader.domain.model

/**
 * Sealed class representing different states of the download process
 */
sealed class DownloadState {
    data object Idle : DownloadState()
    
    data class Analyzing(
        val step: AnalysisStep,
        val progress: Float = 0f
    ) : DownloadState()
    
    data class Ready(
        val videoInfo: VideoInfo
    ) : DownloadState()
    
    data object Preparing : DownloadState()
    
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSecond: Long,
        val etaSeconds: Int
    ) : DownloadState()
    
    data object Paused : DownloadState()
    
    data class Completed(
        val filePath: String,
        val fileSize: Long
    ) : DownloadState()
    
    data class Failed(
        val error: DownloadError
    ) : DownloadState()
}

/**
 * Analysis steps shown to the user
 */
enum class AnalysisStep(val emoji: String, val message: String) {
    VALIDATING("🔍", "Validating URL..."),
    FETCHING("📡", "Fetching video metadata..."),
    ANALYZING("🎬", "Analyzing formats..."),
    READY("✅", "Ready to download!")
}

/**
 * Types of errors that can occur
 */
sealed class DownloadError {
    data class InvalidUrl(val reason: String) : DownloadError()
    data class NetworkError(val message: String) : DownloadError()
    data class StorageError(val message: String) : DownloadError()
    data class UnsupportedPlatform(val url: String) :DownloadError()
    data class VideoUnavailable(val reason: String) : DownloadError()
    data class UnknownError(val throwable: Throwable) : DownloadError()
    
    fun getUserMessage(): String = when (this) {
        is InvalidUrl -> "Invalid URL: $reason"
        is NetworkError -> "Network error: $message"
        is StorageError -> "Storage error: $message"
        is UnsupportedPlatform -> "This platform is not supported yet"
        is VideoUnavailable -> "Video unavailable: $reason"
        is UnknownError -> "An unknown error occurred: ${throwable.message}"
    }
}
