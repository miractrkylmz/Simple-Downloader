package com.example.simpledownloader.data.repository

import android.content.Context
import com.example.simpledownloader.data.local.dao.DownloadDao
import com.example.simpledownloader.data.local.entity.DownloadEntity
import com.example.simpledownloader.domain.model.*
import com.example.simpledownloader.domain.repository.VideoRepository
import com.example.simpledownloader.utils.PlatformDetector
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.File

class VideoRepositoryImpl(
    private val downloadDao: DownloadDao,
    private val context: Context
) : VideoRepository {

    /**
     * Ensures YoutubeDL is initialized before use.
     * This handles the case where the app's onCreate() initialization
     * hasn't completed yet.
     */
    private suspend fun ensureYoutubeDLInitialized() {
        try {
            // Force initialization - this is safe to call multiple times
            YoutubeDL.getInstance().init(context)
            Timber.d("YoutubeDL initialized")
        } catch (e: Exception) {
            // If it's already initialized, we'll get an exception but that's fine
            if (e.message?.contains("already initialized", ignoreCase = true) == true) {
                Timber.d("YoutubeDL already initialized")
            } else {
                Timber.e(e, "Failed to initialize YoutubeDL")
                throw e
            }
        }
    }

    override fun analyzeUrl(url: String): Flow<DownloadState> = flow {
        emit(DownloadState.Analyzing(AnalysisStep.VALIDATING))
        
        // Ensure YoutubeDL is initialized
        try {
            ensureYoutubeDLInitialized()
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize YoutubeDL")
            emit(DownloadState.Failed(DownloadError.UnknownError(e)))
            return@flow
        }
        
        // Validate URL
        if (url.isBlank()) {
            emit(DownloadState.Failed(DownloadError.InvalidUrl("URL cannot be empty")))
            return@flow
        }
        
        // Detect platform
        val platform = PlatformDetector.detectPlatform(url)
        
        // Retry configuration for analysis
        val maxRetries = 3
        var retryCount = 0
        var lastException: Exception? = null
        
        while (retryCount < maxRetries) {
            try {
                if (retryCount > 0) {
                    val delay = (1000L * (1 shl (retryCount - 1))) // Exponential backoff: 1s, 2s, 4s
                    Timber.d("Analysis retry attempt $retryCount after ${delay}ms delay")
                    kotlinx.coroutines.delay(delay)
                }
                
                // Fetch video information using YoutubeDL
                emit(DownloadState.Analyzing(AnalysisStep.FETCHING))
                
                val request = YoutubeDLRequest(url)
                request.addOption("--no-playlist") // Single video only
                request.addOption("--skip-download") // Don't download, just get info
                
                // Platform-specific options for analysis
                // Platform-specific options for analysis
                when {
                    url.contains("youtube.com", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true) -> {
                        Timber.d("Applying YouTube-specific analysis options")
                        request.addOption("--no-check-certificate")
                        request.addOption("--force-ipv4")
                        request.addOption("--socket-timeout", "60")
                        request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        request.addOption("--extractor-args", "youtube:player_client=android,web")
                    }
                    url.contains("instagram.com", ignoreCase = true) -> {
                        Timber.d("Applying Instagram-specific analysis options")
                        request.addOption("--no-check-certificate")
                        request.addOption("--force-ipv4")
                        request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        request.addOption("--referer", "https://www.instagram.com/")
                    }
                    else -> {
                        request.addOption("--no-check-certificate")
                        request.addOption("--force-ipv4")
                    }
                }
                
                Timber.d("Fetching info from YoutubeDL for: $url (attempt ${retryCount + 1})")
                
                val response = YoutubeDL.getInstance().getInfo(request)
                
                // Parse the response into VideoInfo
                emit(DownloadState.Analyzing(AnalysisStep.ANALYZING))
                
                val videoInfo = VideoInfo(
                    url = url,
                    title = response.title ?: "Unknown Title",
                    thumbnailUrl = response.thumbnail ?: "",
                    duration = response.duration?.toInt() ?: 0,
                    platform = platform,
                    uploader = response.uploader ?: "Unknown",
                    description = response.description ?: "",
                    viewCount = response.viewCount?.toLong() ?: 0,
                    availableFormats = emptyList() // Can be enhanced later
                )
                
                Timber.d("Analysis complete: ${videoInfo.title}")
                emit(DownloadState.Ready(videoInfo))
                
                // Success - exit retry loop
                return@flow
                
            } catch (e: YoutubeDLException) {
                lastException = e
                val isNetworkError = e.message?.contains("IncompleteRead", ignoreCase = true) == true ||
                                     e.message?.contains("Unable to download webpage", ignoreCase = true) == true ||
                                     e.message?.contains("network", ignoreCase = true) == true ||
                                     e.message?.contains("Remote end closed connection", ignoreCase = true) == true
                
                if (isNetworkError && retryCount < maxRetries - 1) {
                    Timber.w(e, "Network error on analysis attempt ${retryCount + 1}, will retry")
                    retryCount++
                    continue // Retry
                } else {
                    // Not a network error or max retries reached
                    Timber.e(e, "YoutubeDL error analyzing URL: $url")
                    val error = when {
                        e.message?.contains("Unsupported URL", ignoreCase = true) == true ->
                            DownloadError.UnsupportedPlatform(url = url.substringAfter("://").substringBefore("/"))
                        e.message?.contains("not available", ignoreCase = true) == true ->
                            DownloadError.VideoUnavailable("Video is not available or private")
                        e.message?.contains("IncompleteRead", ignoreCase = true) == true ||
                        e.message?.contains("Unable to download webpage", ignoreCase = true) == true ->
                            DownloadError.NetworkError("Connection failed after $maxRetries attempts. Instagram may be blocking requests. Try again later.")
                        e.message?.contains("network", ignoreCase = true) == true ->
                            DownloadError.NetworkError("Network error occurred")
                        else -> DownloadError.UnknownError(e)
                    }
                    emit(DownloadState.Failed(error))
                    return@flow
                }
            } catch (e: Exception) {
                lastException = e
                if (retryCount < maxRetries - 1) {
                    Timber.w(e, "Analysis error on attempt ${retryCount + 1}, will retry")
                    retryCount++
                    continue // Retry
                } else {
                    Timber.e(e, "Unexpected error analyzing URL: $url")
                    emit(DownloadState.Failed(DownloadError.UnknownError(e)))
                    return@flow
                }
            }
        }
        
        // If we reach here, all retries failed
        if (lastException != null) {
            Timber.e(lastException, "Analysis failed after all retries")
            emit(DownloadState.Failed(DownloadError.NetworkError("Analysis failed after $maxRetries attempts. Please try again.")))
        }
    }.flowOn(Dispatchers.IO)

    override fun downloadVideo(videoInfo: VideoInfo, format: VideoFormat?, destinationUri: String?): Flow<DownloadState> = channelFlow {
        // Ensure YoutubeDL is initialized
        try {
            ensureYoutubeDLInitialized()
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize YoutubeDL for download")
            send(DownloadState.Failed(DownloadError.UnknownError(e)))
            return@channelFlow
        }

        // Retry configuration
        val maxRetries = 3
        var retryCount = 0
        var lastException: Exception? = null

        while (retryCount < maxRetries) {
            try {
                if (retryCount > 0) {
                    val delay = (1000L * (1 shl (retryCount - 1))) // Exponential backoff: 1s, 2s, 4s
                    Timber.d("Retry attempt $retryCount after ${delay}ms delay")
                    kotlinx.coroutines.delay(delay)
                    send(DownloadState.Preparing) // Show preparing state on retry
                }

                // Step 1: Download to app-specific storage (always works, no permission needed)
                val tempDir = File(
                    context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES),
                    "temp"
                )
                
                if (!tempDir.exists()) {
                    tempDir.mkdirs()
                }
                
                Timber.d("Downloading to temp directory: ${tempDir.absolutePath}")

                // Configure YoutubeDL request
                val request = YoutubeDLRequest(videoInfo.url)
                
                // Use simple output template with timestamp only (no special chars)
                // Format: timestamp.ext (e.g., 1701234567890.mp4)
                val timestamp = System.currentTimeMillis()
                val outputTemplate = "$timestamp.%(ext)s"
                request.addOption("-o", "${tempDir.absolutePath}/$outputTemplate")
                
                Timber.d("Output template: ${tempDir.absolutePath}/$outputTemplate")
                
                // DON'T specify format - let yt-dlp auto-select the best format
                // This addresses the warning: "To let yt-dlp download and merge the best available formats, simply do not pass any format selection"
                
                // Add robust options to fix SSL/Network issues
                request.addOption("--no-check-certificate")
                request.addOption("--force-ipv4") // Force IPv4 to avoid IPv6 issues
                request.addOption("--no-cache-dir")
                request.addOption("--geo-bypass")
                
                // Platform-specific options
                when {
                    videoInfo.platform.name.equals("YOUTUBE", ignoreCase = true) -> {
                        Timber.d("Applying YouTube-specific options")
                        // YouTube extractor arguments to handle API issues
                        request.addOption("--extractor-retries", "10")
                        request.addOption("--fragment-retries", "10")
                        request.addOption("--retry-sleep", "3")
                        request.addOption("--socket-timeout", "60")
                        // Use desktop user agent for YouTube (works better)
                        request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        // Add YouTube-specific extractor args
                        request.addOption("--extractor-args", "youtube:player_client=android,web")
                        // Skip unavailable fragments
                        request.addOption("--skip-unavailable-fragments")
                    }
                    videoInfo.platform.name.equals("INSTAGRAM", ignoreCase = true) -> {
                        Timber.d("Applying Instagram-specific options")
                        request.addOption("--extractor-retries", "5")
                        request.addOption("--fragment-retries", "5")
                        request.addOption("--retry-sleep", "2")
                        // Use desktop user agent for Instagram to avoid "No csrf token" error
                        request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        request.addOption("--referer", "https://www.instagram.com/")
                    }
                    else -> {
                        // Use a mobile User-Agent for other platforms
                        request.addOption("--user-agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36")
                    }
                }
                
                // Track progress
                var lastProgress = 0f
                var downloadedBytes = 0L
                var totalBytes = 0L
                var speedBps = 0L
                var etaSecs = 0

                // Execute download with progress callback
                // Don't set working directory, use -o with full path instead
                YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                    // Parse progress from callback
                    try {
                        val progressPercent = progress / 100f
                        
                        // Parse additional info from line if available
                        if (line.contains("[download]")) {
                            // Try to parse speed and file size
                            val sizeMatch = Regex("of\\s+([\\d.]+)\\s*(\\w+)").find(line)
                            if (sizeMatch != null) {
                                val size = sizeMatch.groupValues[1].toFloatOrNull() ?: 0f
                                val unit = sizeMatch.groupValues[2]
                                totalBytes = when (unit.uppercase()) {
                                    "KIB" -> (size * 1024).toLong()
                                    "MIB" -> (size * 1024 * 1024).toLong()
                                    "GIB" -> (size * 1024 * 1024 * 1024).toLong()
                                    else -> 0L
                                }
                            }

                            val speedMatch = Regex("at\\s+([\\d.]+)\\s*(\\w+)/s").find(line)
                            if (speedMatch != null) {
                                val speed = speedMatch.groupValues[1].toFloatOrNull() ?: 0f
                                val unit = speedMatch.groupValues[2]
                                speedBps = when (unit.uppercase()) {
                                    "KIB" -> (speed * 1024).toLong()
                                    "MIB" -> (speed * 1024 * 1024).toLong()
                                    else -> 0L
                                }
                            }
                        }

                        downloadedBytes = if (totalBytes > 0) {
                            (totalBytes * progressPercent).toLong()
                        } else {
                            0L
                        }
                        etaSecs = etaInSeconds.toInt()

                        // Only emit if progress changed significantly
                        if ((progressPercent - lastProgress) >= 0.01f || progressPercent >= 0.99f) {
                            lastProgress = progressPercent
                            trySend(DownloadState.Downloading(
                                progress = progressPercent,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                                speedBytesPerSecond = speedBps,
                                etaSeconds = etaSecs
                            ))
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Error parsing progress: $line")
                    }
                }

            // Download completed - find the actual downloaded file
            // We used timestamp.ext template, so find file starting with that timestamp
            val downloadedFile = tempDir.listFiles()
                ?.find { it.name.startsWith(timestamp.toString()) }
            
            if (downloadedFile == null || !downloadedFile.exists()) {
                Timber.e("Download completed but temp file not found in: ${tempDir.absolutePath}")
                val allFiles = tempDir.listFiles()?.joinToString { it.name } ?: "no files"
                Timber.e("Files in temp dir: $allFiles")
                throw Exception("File not found after download")
            }
            
            val tempFile = downloadedFile
            val fileSize = tempFile.length()
            val actualExtension = tempFile.extension
            val filename = tempFile.name
            Timber.d("Downloaded to temp: ${tempFile.name}, size: $fileSize bytes, extension: $actualExtension")
            
            // Determine MIME type based on file extension
            val mimeType = when (actualExtension.lowercase()) {
                "mp4" -> "video/mp4"
                "webm" -> "video/webm"
                "mkv" -> "video/x-matroska"
                else -> "video/*"
            }
            
            // Step 2: Copy to public storage
            val finalPath: String
            
            if (destinationUri != null) {
                // User selected a specific folder
                Timber.d("Copying to selected folder: $destinationUri")
                try {
                    val treeUri = android.net.Uri.parse(destinationUri)
                    val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                    
                    if (docFile == null || !docFile.canWrite()) {
                        throw Exception("Cannot write to selected folder")
                    }
                    
                    // Create file in the selected directory
                    val newFile = docFile.createFile(mimeType, filename)
                        ?: throw Exception("Failed to create file in selected folder")
                        
                    // Copy content
                    context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                        tempFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    
                    finalPath = newFile.uri.toString()
                    
                    // Clean up temp file
                    tempFile.delete()
                    Timber.d("Copied to selected folder successfully")
                    
                } catch (e: Exception) {
                    tempFile.delete()
                    Timber.e(e, "Failed to copy to selected folder")
                    send(DownloadState.Failed(DownloadError.StorageError("Failed to save to selected folder: ${e.message}")))
                    return@channelFlow
                }
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10+: Copy to MediaStore
                Timber.d("Copying to MediaStore...")
                
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.Video.Media.MIME_TYPE, mimeType)
                    put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, 
                        android.os.Environment.DIRECTORY_MOVIES + "/SimpleDownloader")
                }
                
                val resolver = context.contentResolver
                val uri = resolver.insert(
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )
                
                if (uri == null) {
                    tempFile.delete()
                    Timber.e("Failed to create MediaStore entry")
                    send(DownloadState.Failed(DownloadError.StorageError("Failed to create media file")))
                    return@channelFlow
                }
                
                // Copy file to MediaStore URI
                try {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        tempFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    Timber.d("Copied to MediaStore successfully")
                    
                    // Get the actual path for database
                    val projection = arrayOf(android.provider.MediaStore.Video.Media.DATA)
                    finalPath = resolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val columnIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA)
                            cursor.getString(columnIndex)
                        } else {
                            uri.toString() // Fallback to URI
                        }
                    } ?: uri.toString()
                    
                    // Clean up temp file
                    tempFile.delete()
                    Timber.d("Cleaned up temp file")
                    
                } catch (e: Exception) {
                    tempFile.delete()
                    Timber.e(e, "Failed to copy to MediaStore")
                    send(DownloadState.Failed(DownloadError.StorageError("Failed to save to gallery: ${e.message}")))
                    return@channelFlow
                }
            } else {
                // Android 9 and below: Copy to public storage
                Timber.d("Copying to public storage...")
                
                val publicDir = File(
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES),
                    "SimpleDownloader"
                )
                
                if (!publicDir.exists()) {
                    publicDir.mkdirs()
                }
                
                val publicFile = File(publicDir, filename)
                
                try {
                    tempFile.copyTo(publicFile, overwrite = true)
                    tempFile.delete()
                    finalPath = publicFile.absolutePath
                    
                    // Notify MediaScanner
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(finalPath),
                        arrayOf(mimeType),
                        null
                    )
                    Timber.d("Copied to public storage and triggered media scan")
                    
                } catch (e: Exception) {
                    tempFile.delete()
                    Timber.e(e, "Failed to copy to public storage")
                    send(DownloadState.Failed(DownloadError.StorageError("Failed to save: ${e.message}")))
                    return@channelFlow
                }
            }

            // Save to database
            val downloadEntity = DownloadEntity(
                videoUrl = videoInfo.url,
                title = videoInfo.title,
                thumbnailUrl = videoInfo.thumbnailUrl,
                platform = videoInfo.platform.name,
                filePath = finalPath,
                fileSize = fileSize,
                downloadDate = System.currentTimeMillis(),
                duration = videoInfo.duration,
                uploader = videoInfo.uploader,
                status = "completed"
            )
            
            downloadDao.insertDownload(downloadEntity)
            Timber.d("Saved download to database: $finalPath")

            // Emit completion state
            send(DownloadState.Completed(
                filePath = finalPath,
                fileSize = fileSize
            ))
            
            // Download successful - exit retry loop
            return@channelFlow

        } catch (e: YoutubeDLException) {
            lastException = e
            val isNetworkError = e.message?.contains("Remote end closed connection", ignoreCase = true) == true ||
                                 e.message?.contains("Unable to download webpage", ignoreCase = true) == true ||
                                 e.message?.contains("network", ignoreCase = true) == true
            
            if (isNetworkError && retryCount < maxRetries - 1) {
                Timber.w(e, "Network error on download attempt ${retryCount + 1}, will retry")
                retryCount++
                continue // Retry
            } else {
                // Not a network error or max retries reached
                Timber.e(e, "YoutubeDL download failed")
                val error = when {
                    e.message?.contains("Unsupported URL", ignoreCase = true) == true ->
                        DownloadError.UnsupportedPlatform(url = videoInfo.url.substringAfter("://").substringBefore("/"))
                    e.message?.contains("not available", ignoreCase = true) == true ->
                        DownloadError.VideoUnavailable("Video is not available or private")
                    e.message?.contains("Unable to download webpage", ignoreCase = true) == true ||
                    e.message?.contains("Remote end closed connection", ignoreCase = true) == true ->
                        DownloadError.NetworkError("Connection failed after $maxRetries attempts. Instagram may be blocking requests. Try again later.")
                    else -> DownloadError.UnknownError(e)
                }
                send(DownloadState.Failed(error))
                return@channelFlow
            }
        } catch (e: Exception) {
            lastException = e
            if (retryCount < maxRetries - 1) {
                Timber.w(e, "Download error on attempt ${retryCount + 1}, will retry")
                retryCount++
                continue // Retry
            } else {
                Timber.e(e, "Download failed after $maxRetries attempts")
                val error = when {
                    e.message?.contains("No space left", ignoreCase = true) == true ->
                        DownloadError.StorageError("Not enough storage space")
                    e.message?.contains("Network", ignoreCase = true) == true ->
                        DownloadError.NetworkError("Network error after $maxRetries attempts")
                    e.message?.contains("permission", ignoreCase = true) == true ->
                        DownloadError.StorageError("Storage permission required")
                    else -> DownloadError.UnknownError(e)
                }
                send(DownloadState.Failed(error))
                return@channelFlow
            }
        }
    }
    
    // If we reach here, all retries failed
    if (lastException != null) {
        Timber.e(lastException, "Download failed after all retries")
        send(DownloadState.Failed(DownloadError.NetworkError("Download failed after $maxRetries attempts")))
    }
    }.flowOn(Dispatchers.IO)

    override fun getDownloadHistory(): Flow<List<DownloadEntity>> {
        return downloadDao.getAllDownloads()
    }

    override suspend fun deleteDownload(downloadId: String) {
        try {
            val id = downloadId.toLongOrNull()
            if (id != null) {
                // Also delete the file
                val download = downloadDao.getDownloadById(id)
                if (download != null) {
                    val file = File(download.filePath)
                    if (file.exists()) {
                        file.delete()
                    }
                    downloadDao.deleteById(id)
                    Timber.d("Deleted download: $id")
                }
            } else {
                Timber.e("Invalid download ID: $downloadId")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete download: $downloadId")
        }
    }
}
