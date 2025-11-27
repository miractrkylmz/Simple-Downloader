package com.example.simpledownloader.domain.usecase

import com.example.simpledownloader.domain.model.DownloadState
import com.example.simpledownloader.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for analyzing a video URL
 * This orchestrates the URL validation and metadata extraction
 */
class AnalyzeUrlUseCase(
    private val repository: VideoRepository
) {
    /**
     * Execute the URL analysis
     * @param url The video URL to analyze
     * @return Flow emitting download states during analysis (Analyzing → Ready or Failed)
     */
    operator fun invoke(url: String): Flow<DownloadState> {
        // Trim whitespace from URL
        val cleanUrl = url.trim()
        
        // Basic validation before passing to repository
        if (cleanUrl.isBlank()) {
            return kotlinx.coroutines.flow.flowOf(
                DownloadState.Failed(
                    com.example.simpledownloader.domain.model.DownloadError.InvalidUrl("URL cannot be empty")
                )
            )
        }
        
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            return kotlinx.coroutines.flow.flowOf(
                DownloadState.Failed(
                    com.example.simpledownloader.domain.model.DownloadError.InvalidUrl("URL must start with http:// or https://")
                )
            )
        }
        
        // Delegate to repository for actual analysis
        return repository.analyzeUrl(cleanUrl)
    }
}
