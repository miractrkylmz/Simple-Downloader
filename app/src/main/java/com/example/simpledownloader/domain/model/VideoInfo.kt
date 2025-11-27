package com.example.simpledownloader.domain.model

/**
 * Domain model representing video information extracted from a URL
 */
data class VideoInfo(
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val duration: Int?, // in seconds
    val platform: Platform,
    val uploader: String?,
    val description: String?,
    val viewCount: Long?,
    val availableFormats: List<VideoFormat> = emptyList()
)

/**
 * Represents different video format options
 */
data class VideoFormat(
    val formatId: String,
    val ext: String, // mp4, webm, etc.
    val quality: String, // 720p, 1080p, etc.
    val fileSize: Long?, // in bytes
    val hasVideo: Boolean,
    val hasAudio: Boolean
)

/**
 * Supported platforms
 */
enum class Platform(val displayName: String, val emoji: String) {
    YOUTUBE("YouTube", "📺"),
    INSTAGRAM("Instagram", "📷"),
    TWITTER("Twitter/X", "🐦"),
    TIKTOK("TikTok", "🎵"),
    REDDIT("Reddit", "🤖"),
    FACEBOOK("Facebook", "📘"),
    VIMEO("Vimeo", "🎬"),
    DAILYMOTION("Dailymotion", "🎥"),
    TWITCH("Twitch", "🎮"),
    UNKNOWN("Unknown", "🌐")
}
