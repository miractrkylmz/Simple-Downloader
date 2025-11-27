package com.example.simpledownloader.domain.model

/**
 * Download settings model
 * Manages folder selection preferences for video downloads
 */
data class DownloadSettings(
    val askEveryTime: Boolean = true,
    val fixedFolderPath: String? = null
)
