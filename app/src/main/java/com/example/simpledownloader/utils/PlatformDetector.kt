package com.example.simpledownloader.utils

import com.example.simpledownloader.domain.model.Platform

/**
 * Utility object for detecting media platform and type from URL
 */
object PlatformDetector {
    
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "svg")

    /**
     * Check if a URL points directly to an image file
     */
    fun isDirectImageUrl(url: String): Boolean {
        val cleanUrl = url.substringBefore("?").substringBefore("#").trim().lowercase()
        val extension = cleanUrl.substringAfterLast(".", "")
        return extension in IMAGE_EXTENSIONS || cleanUrl.contains("i.redd.it") || cleanUrl.contains("i.imgur.com")
    }

    /**
     * Detect the platform from a given URL
     */
    fun detectPlatform(url: String): Platform {
        val lowerUrl = url.lowercase()
        
        return when {
            isDirectImageUrl(lowerUrl) -> Platform.DIRECT_IMAGE
            lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be") -> Platform.YOUTUBE
            lowerUrl.contains("instagram.com") -> Platform.INSTAGRAM
            lowerUrl.contains("twitter.com") || lowerUrl.contains("x.com") -> Platform.TWITTER
            lowerUrl.contains("tiktok.com") -> Platform.TIKTOK
            lowerUrl.contains("pinterest.com") || lowerUrl.contains("pin.it") -> Platform.PINTEREST
            lowerUrl.contains("reddit.com") || lowerUrl.contains("redd.it") -> Platform.REDDIT
            lowerUrl.contains("facebook.com") || lowerUrl.contains("fb.com") || lowerUrl.contains("fb.watch") -> Platform.FACEBOOK
            lowerUrl.contains("vimeo.com") -> Platform.VIMEO
            lowerUrl.contains("dailymotion.com") -> Platform.DAILYMOTION
            lowerUrl.contains("twitch.tv") -> Platform.TWITCH
            else -> Platform.UNKNOWN
        }
    }
    
    /**
     * Check if a platform is supported
     */
    fun isSupported(platform: Platform): Boolean {
        return platform != Platform.UNKNOWN
    }
}
