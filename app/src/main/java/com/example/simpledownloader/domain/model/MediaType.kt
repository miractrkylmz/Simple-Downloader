package com.example.simpledownloader.domain.model

/**
 * Represents the type of media being downloaded
 */
enum class MediaType(val displayName: String, val emoji: String) {
    VIDEO("Video", "🎬"),
    IMAGE("Fotoğraf", "🖼️")
}
