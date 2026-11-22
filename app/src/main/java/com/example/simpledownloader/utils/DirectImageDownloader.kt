package com.example.simpledownloader.utils

import com.example.simpledownloader.domain.model.MediaType
import com.example.simpledownloader.domain.model.Platform
import com.example.simpledownloader.domain.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Utility for analyzing and downloading direct image files (JPG, PNG, WebP, GIF, etc.)
 * Bypasses yt-dlp for direct image links to ensure speed and zero format errors.
 */
object DirectImageDownloader {

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    /**
     * Analyze a direct image URL by querying HTTP headers
     */
    suspend fun analyzeImage(url: String): VideoInfo = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val u = URL(url)
            connection = (u.openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
                instanceFollowRedirects = true
            }

            var responseCode = connection.responseCode
            // Some servers reject HEAD requests; fallback to GET with Range 0-0
            if (responseCode == HttpURLConnection.HTTP_BAD_METHOD || responseCode == HttpURLConnection.HTTP_NOT_IMPLEMENTED) {
                connection.disconnect()
                connection = (u.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
                    setRequestProperty("Range", "bytes=0-1024")
                }
                responseCode = connection.responseCode
            }

            val contentType = connection.contentType?.lowercase() ?: ""
            val contentLength = connection.contentLengthLong

            // Derive title and extension from URL
            val cleanPath = u.path.substringBefore("?").substringBefore("#")
            val rawName = cleanPath.substringAfterLast("/").ifBlank { "Gorsel_${System.currentTimeMillis()}" }
            val nameWithoutExt = rawName.substringBeforeLast(".")
            val ext = rawName.substringAfterLast(".", "").ifBlank {
                when {
                    contentType.contains("jpeg") || contentType.contains("jpg") -> "jpg"
                    contentType.contains("png") -> "png"
                    contentType.contains("webp") -> "webp"
                    contentType.contains("gif") -> "gif"
                    else -> "jpg"
                }
            }

            val displayTitle = if (nameWithoutExt.isNotBlank()) {
                FileHelper.sanitizeFilename(nameWithoutExt)
            } else {
                "Fotoğraf_${System.currentTimeMillis()}"
            }

            val platform = PlatformDetector.detectPlatform(url)

            Timber.d("Direct image analyzed: $displayTitle ($contentType, $contentLength bytes)")

            VideoInfo(
                url = url,
                title = displayTitle,
                thumbnailUrl = url,
                duration = null,
                platform = if (platform == Platform.UNKNOWN) Platform.DIRECT_IMAGE else platform,
                uploader = u.host,
                description = "Doğrudan görsel bağlantısı (${ext.uppercase()})",
                viewCount = null,
                mediaType = MediaType.IMAGE,
                availableFormats = emptyList()
            )
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Download an image directly to a destination file with progress callback
     */
    suspend fun downloadImage(
        url: String,
        outputFile: File,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val u = URL(url)
            connection = (u.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20000
                readTimeout = 30000
                setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
                instanceFollowRedirects = true
            }

            val totalBytes = connection.contentLengthLong
            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(outputFile)

            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            var downloadedBytes = 0L

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val progress = if (totalBytes > 0) {
                            (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                        } else {
                            0.5f // Indeterminate fallback
                        }
                        onProgress(progress, downloadedBytes, totalBytes)
                    }
                    output.flush()
                }
            }

            Timber.d("Direct image downloaded successfully: ${outputFile.name} (${outputFile.length()} bytes)")
        } finally {
            connection?.disconnect()
        }
    }
}
