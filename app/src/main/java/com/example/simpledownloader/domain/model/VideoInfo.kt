package com.example.simpledownloader.domain.model

/**
 * Domain model representing media (video/photo) information extracted from a URL
 */
data class VideoInfo(
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val duration: Int?, // in seconds (null or 0 for photos/images)
    val platform: Platform,
    val uploader: String?,
    val description: String?,
    val viewCount: Long?,
    val mediaType: MediaType = MediaType.VIDEO,
    val directMediaUrl: String? = null,
    val availableFormats: List<VideoFormat> = emptyList()
)

/**
 * Represents different media format options
 */
data class VideoFormat(
    val formatId: String,
    val ext: String, // mp4, webm, jpg, png etc.
    val quality: String, // 720p, 1080p, HD Image etc.
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
    PINTEREST("Pinterest", "📌"),
    REDDIT("Reddit", "🤖"),
    FACEBOOK("Facebook", "📘"),
    VIMEO("Vimeo", "🎬"),
    DAILYMOTION("Dailymotion", "🎥"),
    TWITCH("Twitch", "🎮"),
    DIRECT_IMAGE("Fotoğraf / Görsel", "🖼️"),
    UNKNOWN("Web", "🌐")
}
