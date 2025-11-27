package com.example.simpledownloader.utils

import com.example.simpledownloader.domain.model.Platform

/**
 * Utility object for detecting video platform from URL
 */
object PlatformDetector {
    /**
     * Detect the platform from a given URL
     */
    fun detectPlatform(url: String): Platform {
        val lowerUrl = url.lowercase()
        
        return when {
            lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be") -> Platform.YOUTUBE
            lowerUrl.contains("instagram.com") -> Platform.INSTAGRAM
            lowerUrl.contains("twitter.com") || lowerUrl.contains("x.com") -> Platform.TWITTER
            lowerUrl.contains("tiktok.com") -> Platform.TIKTOK
            lowerUrl.contains("reddit.com") -> Platform.REDDIT
            lowerUrl.contains("facebook.com") || lowerUrl.contains("fb.com") || lowerUrl.contains("fb.watch") -> Platform.FACEBOOK
            lowerUrl.contains("vimeo.com") -> Platform.VIMEO
            lowerUrl.contains("dailymotion.com") -> Platform.DAILYMOTION
            lowerUrl.contains("twitch.tv") -> Platform.TWITCH
            else -> Platform.UNKNOWN
        }
    }
    
    /**
     * Check if a platform is supported
     * Note: yt-dlp supports many platforms, this is for display purposes
     */
    fun isSupported(platform: Platform): Boolean {
        return platform != Platform.UNKNOWN
    }
}
