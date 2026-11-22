package com.example.simpledownloader.data.repository

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.example.simpledownloader.data.local.dao.DownloadDao
import com.example.simpledownloader.data.local.entity.DownloadEntity
import com.example.simpledownloader.domain.model.*
import com.example.simpledownloader.domain.repository.VideoRepository
import com.example.simpledownloader.utils.DirectImageDownloader
import com.example.simpledownloader.utils.FileHelper
import com.example.simpledownloader.utils.PlatformDetector
import com.example.simpledownloader.utils.TwitterMediaResolver
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.File

class VideoRepositoryImpl(
    private val downloadDao: DownloadDao,
    private val context: Context
) : VideoRepository {

    init {
        // Clean up stranded temporary files from prior interrupted downloads
        cleanupOrphanTempFiles()
    }

    /**
     * Cleans up temporary download files older than 1 hour or left over from past crashes
     */
    private fun cleanupOrphanTempFiles() {
        try {
            val tempDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS ?: Environment.DIRECTORY_MOVIES),
                "temp"
            )
            if (tempDir.exists() && tempDir.isDirectory) {
                val now = System.currentTimeMillis()
                val oneHourAgo = now - (60 * 60 * 1000)
                tempDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < oneHourAgo) {
                        val deleted = file.delete()
                        if (deleted) Timber.d("Cleaned up orphaned temp file: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error cleaning up temporary files")
        }
    }

    /**
     * Ensures YoutubeDL is initialized before use.
     */
    private suspend fun ensureYoutubeDLInitialized() {
        try {
            YoutubeDL.getInstance().init(context)
            Timber.d("YoutubeDL initialized")
        } catch (e: Exception) {
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

        if (url.isBlank()) {
            emit(DownloadState.Failed(DownloadError.InvalidUrl("URL boş olamaz")))
            return@flow
        }

        val trimmedUrl = url.trim()

        // 1. Direct Image URL Detection (instant, bypasses yt-dlp)
        if (PlatformDetector.isDirectImageUrl(trimmedUrl)) {
            emit(DownloadState.Analyzing(AnalysisStep.FETCHING))
            try {
                val imageInfo = DirectImageDownloader.analyzeImage(trimmedUrl)
                emit(DownloadState.Analyzing(AnalysisStep.ANALYZING))
                emit(DownloadState.Ready(imageInfo))
                return@flow
            } catch (e: Exception) {
                Timber.w(e, "Direct image analysis failed, falling back to general extractor: $trimmedUrl")
            }
        }

        // 2. Twitter / X Media Resolution (handles photos, GIFs, and videos cleanly)
        if (TwitterMediaResolver.isTwitterUrl(trimmedUrl)) {
            emit(DownloadState.Analyzing(AnalysisStep.FETCHING))
            try {
                val tweetMedia = TwitterMediaResolver.resolveTweetMedia(trimmedUrl)
                if (tweetMedia != null) {
                    emit(DownloadState.Analyzing(AnalysisStep.ANALYZING))
                    emit(DownloadState.Ready(tweetMedia))
                    return@flow
                }
            } catch (e: Exception) {
                Timber.w(e, "Twitter media resolver failed, falling back to yt-dlp: $trimmedUrl")
            }
        }

        // Ensure YoutubeDL is initialized for video / platform extraction
        try {
            ensureYoutubeDLInitialized()
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize YoutubeDL")
            emit(DownloadState.Failed(DownloadError.UnknownError(e)))
            return@flow
        }

        val platform = PlatformDetector.detectPlatform(trimmedUrl)

        // Retry configuration for analysis
        val maxRetries = 3
        var retryCount = 0
        var lastException: Exception? = null

        while (retryCount < maxRetries) {
            try {
                if (retryCount > 0) {
                    val delay = (1000L * (1 shl (retryCount - 1)))
                    Timber.d("Analysis retry attempt $retryCount after ${delay}ms delay")
                    kotlinx.coroutines.delay(delay)
                }

                emit(DownloadState.Analyzing(AnalysisStep.FETCHING))

                val request = YoutubeDLRequest(trimmedUrl)
                request.addOption("--no-playlist")
                request.addOption("--skip-download")

                // Platform-specific headers
                when {
                    trimmedUrl.contains("youtube.com", ignoreCase = true) || trimmedUrl.contains("youtu.be", ignoreCase = true) -> {
                        request.addOption("--no-check-certificate")
                        request.addOption("--force-ipv4")
                        request.addOption("--socket-timeout", "60")
                        request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                        request.addOption("--extractor-args", "youtube:player_client=android,web")
                    }
                    trimmedUrl.contains("instagram.com", ignoreCase = true) -> {
                        request.addOption("--no-check-certificate")
                        request.addOption("--force-ipv4")
                        request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                        request.addOption("--referer", "https://www.instagram.com/")
                    }
                    trimmedUrl.contains("tiktok.com", ignoreCase = true) -> {
                        request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                        request.addOption("--referer", "https://www.tiktok.com/")
                        request.addOption("--no-check-certificate")
                        request.addOption("--force-ipv4")
                    }
                    trimmedUrl.contains("pinterest.com", ignoreCase = true) || trimmedUrl.contains("pin.it", ignoreCase = true) -> {
                        request.addOption("--no-check-certificate")
                        request.addOption("--force-ipv4")
                        request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                    }
                    else -> {
                        request.addOption("--no-check-certificate")
                        request.addOption("--force-ipv4")
                    }
                }

                Timber.d("Fetching info from YoutubeDL for: $trimmedUrl (attempt ${retryCount + 1})")
                val response = YoutubeDL.getInstance().getInfo(request)

                emit(DownloadState.Analyzing(AnalysisStep.ANALYZING))

                // Detect if the content is an image/photo
                val duration = response.duration?.toInt() ?: 0
                val isImage = duration == 0 && (
                    response.ext.equals("jpg", ignoreCase = true) ||
                    response.ext.equals("jpeg", ignoreCase = true) ||
                    response.ext.equals("png", ignoreCase = true) ||
                    response.ext.equals("webp", ignoreCase = true) ||
                    platform == Platform.PINTEREST ||
                    (platform == Platform.INSTAGRAM && trimmedUrl.contains("/p/"))
                )

                val mediaType = if (isImage) MediaType.IMAGE else MediaType.VIDEO

                val videoInfo = VideoInfo(
                    url = trimmedUrl,
                    title = response.title ?: if (isImage) "Fotoğraf" else "İçerik",
                    thumbnailUrl = response.thumbnail ?: "",
                    duration = if (isImage) null else duration,
                    platform = platform,
                    uploader = response.uploader ?: "Bilinmeyen Kullanıcı",
                    description = response.description ?: "",
                    viewCount = response.viewCount?.toLong() ?: 0,
                    mediaType = mediaType,
                    availableFormats = emptyList()
                )

                Timber.d("Analysis complete: ${videoInfo.title} [Type: ${videoInfo.mediaType}]")
                emit(DownloadState.Ready(videoInfo))
                return@flow

            } catch (e: YoutubeDLException) {
                lastException = e
                val isNetworkError = e.message?.contains("IncompleteRead", ignoreCase = true) == true ||
                                     e.message?.contains("Unable to download webpage", ignoreCase = true) == true ||
                                     e.message?.contains("network", ignoreCase = true) == true ||
                                     e.message?.contains("Remote end closed connection", ignoreCase = true) == true

                if (isNetworkError && retryCount < maxRetries - 1) {
                    retryCount++
                    continue
                } else {
                    val error = when {
                        e.message?.contains("Unsupported URL", ignoreCase = true) == true ->
                            DownloadError.UnsupportedPlatform(url = trimmedUrl.substringAfter("://").substringBefore("/"))
                        e.message?.contains("not available", ignoreCase = true) == true ->
                            DownloadError.VideoUnavailable("İçerik mevcut değil veya silinmiş.")
                        e.message?.contains("private", ignoreCase = true) == true ->
                            DownloadError.VideoUnavailable("Bu içerik gizli ve erişilemiyor.")
                        isNetworkError ->
                            DownloadError.NetworkError("Ağ bağlantısı hatası. Lütfen internetinizi kontrol edin.")
                        else -> DownloadError.UnknownError(e)
                    }
                    emit(DownloadState.Failed(error))
                    return@flow
                }
            } catch (e: Exception) {
                lastException = e
                if (retryCount < maxRetries - 1) {
                    retryCount++
                    continue
                } else {
                    emit(DownloadState.Failed(DownloadError.UnknownError(e)))
                    return@flow
                }
            }
        }

        if (lastException != null) {
            emit(DownloadState.Failed(DownloadError.NetworkError("Analiz $maxRetries denemeden sonra başarısız oldu.")))
        }
    }.flowOn(Dispatchers.IO)

    override fun downloadVideo(videoInfo: VideoInfo, format: VideoFormat?, destinationUri: String?): Flow<DownloadState> = channelFlow {
        val tempDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS ?: Environment.DIRECTORY_MOVIES),
            "temp"
        ).apply { if (!exists()) mkdirs() }

        val timestamp = System.currentTimeMillis()
        val sanitizedTitle = FileHelper.sanitizeFilename(videoInfo.title).ifBlank { "Media_$timestamp" }

        // A. Direct Media Download Path (Photos or resolved direct media URLs like Twitter/X)
        val hasDirectUrl = !videoInfo.directMediaUrl.isNullOrBlank()
        if (videoInfo.mediaType == MediaType.IMAGE || hasDirectUrl) {
            val downloadUrl = videoInfo.directMediaUrl
                ?: if (PlatformDetector.isDirectImageUrl(videoInfo.url)) videoInfo.url
                else videoInfo.thumbnailUrl ?: videoInfo.url

            val isImage = videoInfo.mediaType == MediaType.IMAGE ||
                    downloadUrl.contains(".jpg", ignoreCase = true) ||
                    downloadUrl.contains(".jpeg", ignoreCase = true) ||
                    downloadUrl.contains(".png", ignoreCase = true) ||
                    downloadUrl.contains(".webp", ignoreCase = true)

            val ext = if (isImage) {
                when {
                    downloadUrl.contains(".png", ignoreCase = true) -> "png"
                    downloadUrl.contains(".webp", ignoreCase = true) -> "webp"
                    else -> "jpg"
                }
            } else {
                "mp4"
            }

            val mimeType = if (isImage) "image/$ext" else "video/mp4"
            val actualMediaType = if (isImage) MediaType.IMAGE else MediaType.VIDEO
            val tempFile = File(tempDir, "${timestamp}.${ext}")

            try {
                send(DownloadState.Preparing)
                DirectImageDownloader.downloadImage(downloadUrl, tempFile) { progress, downloadedBytes, totalBytes ->
                    trySend(DownloadState.Downloading(
                        progress = progress,
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                        speedBytesPerSecond = 0,
                        etaSeconds = 0
                    ))
                }

                val finalPath = saveFileToFinalDestination(tempFile, sanitizedTitle, ext, mimeType, destinationUri, actualMediaType)
                val fileSize = if (tempFile.exists()) tempFile.length() else File(finalPath).length()

                saveToDatabase(videoInfo, finalPath, fileSize, actualMediaType)
                send(DownloadState.Completed(filePath = finalPath, fileSize = fileSize))
                return@channelFlow

            } catch (e: Exception) {
                Timber.e(e, "Direct media download failed")
                if (tempFile.exists()) tempFile.delete()
                if (actualMediaType == MediaType.VIDEO) {
                    Timber.d("Direct video download failed, falling back to yt-dlp")
                } else {
                    send(DownloadState.Failed(DownloadError.UnknownError(e)))
                    return@channelFlow
                }
            }
        }

        // B. Video / Media Download Path using yt-dlp
        try {
            ensureYoutubeDLInitialized()
        } catch (e: Exception) {
            send(DownloadState.Failed(DownloadError.UnknownError(e)))
            return@channelFlow
        }

        val maxRetries = 3
        var retryCount = 0
        var lastException: Exception? = null

        while (retryCount < maxRetries) {
            try {
                if (retryCount > 0) {
                    val delay = (1000L * (1 shl (retryCount - 1)))
                    kotlinx.coroutines.delay(delay)
                    send(DownloadState.Preparing)
                }

                val request = YoutubeDLRequest(videoInfo.url)
                val outputTemplate = "$timestamp.%(ext)s"
                request.addOption("-o", "${tempDir.absolutePath}/$outputTemplate")

                request.addOption("--no-check-certificate")
                request.addOption("--force-ipv4")
                request.addOption("--no-cache-dir")
                request.addOption("--geo-bypass")

                // Platform specific options
                when {
                    videoInfo.platform == Platform.YOUTUBE -> {
                        request.addOption("--extractor-retries", "10")
                        request.addOption("--fragment-retries", "10")
                        request.addOption("--socket-timeout", "60")
                        request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                        request.addOption("--extractor-args", "youtube:player_client=android,web")
                    }
                    videoInfo.platform == Platform.INSTAGRAM -> {
                        request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                        request.addOption("--referer", "https://www.instagram.com/")
                    }
                    videoInfo.platform == Platform.TIKTOK -> {
                        request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                        request.addOption("--referer", "https://www.tiktok.com/")
                    }
                    else -> {
                        request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                    }
                }

                var lastProgress = 0f
                var downloadedBytes = 0L
                var totalBytes = 0L
                var speedBps = 0L
                var etaSecs = 0

                YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                    try {
                        val progressPercent = progress / 100f
                        if (line.contains("[download]")) {
                            val sizeMatch = Regex("of\\s+([\\d.]+)\\s*(\\w+)").find(line)
                            if (sizeMatch != null) {
                                val size = sizeMatch.groupValues[1].toFloatOrNull() ?: 0f
                                val unit = sizeMatch.groupValues[2].uppercase()
                                totalBytes = when (unit) {
                                    "KIB" -> (size * 1024).toLong()
                                    "MIB" -> (size * 1024 * 1024).toLong()
                                    "GIB" -> (size * 1024 * 1024 * 1024).toLong()
                                    else -> 0L
                                }
                            }
                            val speedMatch = Regex("at\\s+([\\d.]+)\\s*(\\w+)/s").find(line)
                            if (speedMatch != null) {
                                val speed = speedMatch.groupValues[1].toFloatOrNull() ?: 0f
                                val unit = speedMatch.groupValues[2].uppercase()
                                speedBps = when (unit) {
                                    "KIB" -> (speed * 1024).toLong()
                                    "MIB" -> (speed * 1024 * 1024).toLong()
                                    else -> 0L
                                }
                            }
                        }
                        downloadedBytes = if (totalBytes > 0) (totalBytes * progressPercent).toLong() else 0L
                        etaSecs = etaInSeconds.toInt()

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

                // Locate downloaded temp file
                val downloadedFile = tempDir.listFiles()?.find { it.name.startsWith(timestamp.toString()) }
                if (downloadedFile == null || !downloadedFile.exists()) {
                    throw Exception("İndirilen geçici dosya bulunamadı")
                }

                val actualExt = downloadedFile.extension.ifBlank { "mp4" }
                val isImageFile = actualExt in setOf("jpg", "jpeg", "png", "webp", "gif")
                val actualMediaType = if (isImageFile) MediaType.IMAGE else MediaType.VIDEO

                val mimeType = when (actualExt.lowercase()) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "gif" -> "image/gif"
                    "mp4" -> "video/mp4"
                    "webm" -> "video/webm"
                    "mkv" -> "video/x-matroska"
                    else -> if (actualMediaType == MediaType.IMAGE) "image/*" else "video/*"
                }

                val fileSize = downloadedFile.length()
                val finalPath = saveFileToFinalDestination(downloadedFile, sanitizedTitle, actualExt, mimeType, destinationUri, actualMediaType)

                saveToDatabase(videoInfo, finalPath, fileSize, actualMediaType)
                send(DownloadState.Completed(filePath = finalPath, fileSize = fileSize))
                return@channelFlow

            } catch (e: YoutubeDLException) {
                lastException = e
                val isNetworkError = e.message?.contains("Remote end closed connection", ignoreCase = true) == true ||
                                     e.message?.contains("Unable to download webpage", ignoreCase = true) == true ||
                                     e.message?.contains("network", ignoreCase = true) == true

                if (isNetworkError && retryCount < maxRetries - 1) {
                    retryCount++
                    continue
                } else {
                    send(DownloadState.Failed(DownloadError.NetworkError("İndirme sırasında ağ bağlantısı kesildi.")))
                    return@channelFlow
                }
            } catch (e: Exception) {
                lastException = e
                if (retryCount < maxRetries - 1) {
                    retryCount++
                    continue
                } else {
                    send(DownloadState.Failed(DownloadError.UnknownError(e)))
                    return@channelFlow
                }
            }
        }

        if (lastException != null) {
            send(DownloadState.Failed(DownloadError.NetworkError("İndirme $maxRetries denemeden sonra başarısız oldu.")))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Copies the downloaded temp file to MediaStore (Android 10+) or public storage / custom folder
     * Produces clean, readable filenames and routes images to Pictures and videos to Movies.
     */
    private fun saveFileToFinalDestination(
        tempFile: File,
        sanitizedTitle: String,
        extension: String,
        mimeType: String,
        destinationUri: String?,
        mediaType: MediaType
    ): String {
        val readableFilename = "${sanitizedTitle}.${extension}"

        // Custom folder selected via SAF (Storage Access Framework)
        if (destinationUri != null) {
            val treeUri = Uri.parse(destinationUri)
            val docFile = DocumentFile.fromTreeUri(context, treeUri)
                ?: throw Exception("Seçilen klasöre erişilemedi")

            if (!docFile.canWrite()) {
                throw Exception("Seçilen klasöre yazma izni yok")
            }

            val newFile = docFile.createFile(mimeType, readableFilename)
                ?: throw Exception("Klasörde dosya oluşturulamadı")

            context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                tempFile.inputStream().use { input -> input.copyTo(out) }
            }
            tempFile.delete()
            return newFile.uri.toString()
        }

        // Modern Android 10+ (API 29+) Scoped Storage via MediaStore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val isImage = mediaType == MediaType.IMAGE || mimeType.startsWith("image/")

            val collectionUri = if (isImage) {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val relativeFolder = if (isImage) {
                "${Environment.DIRECTORY_PICTURES}/SimpleDownloader"
            } else {
                "${Environment.DIRECTORY_MOVIES}/SimpleDownloader"
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, readableFilename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeFolder)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val itemUri = resolver.insert(collectionUri, contentValues)
                ?: throw Exception("MediaStore kaydı oluşturulamadı")

            resolver.openOutputStream(itemUri)?.use { out ->
                tempFile.inputStream().use { input -> input.copyTo(out) }
            }

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(itemUri, contentValues, null, null)

            tempFile.delete()
            return itemUri.toString()
        } else {
            // Android 9 and below: Legacy file system storage
            val isImage = mediaType == MediaType.IMAGE || mimeType.startsWith("image/")
            val baseDir = if (isImage) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            }

            val targetDir = File(baseDir, "SimpleDownloader").apply { if (!exists()) mkdirs() }
            val targetFile = File(targetDir, readableFilename)

            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()

            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                arrayOf(mimeType),
                null
            )
            return targetFile.absolutePath
        }
    }

    private suspend fun saveToDatabase(videoInfo: VideoInfo, finalPath: String, fileSize: Long, mediaType: MediaType) {
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
            status = "completed",
            mediaType = mediaType.name
        )
        downloadDao.insertDownload(downloadEntity)
        Timber.d("Saved download to database: $finalPath [Type: ${mediaType.name}]")
    }

    override fun getDownloadHistory(): Flow<List<DownloadEntity>> {
        return downloadDao.getAllDownloads()
    }

    override suspend fun deleteDownload(downloadId: String) {
        try {
            val id = downloadId.toLongOrNull() ?: return
            val download = downloadDao.getDownloadById(id) ?: return

            // Delete actual file from device storage
            val path = download.filePath
            if (path.startsWith("content://")) {
                try {
                    context.contentResolver.delete(Uri.parse(path), null, null)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to delete via ContentResolver: $path")
                }
            } else {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }

            downloadDao.deleteById(id)
            Timber.d("Successfully deleted download record and file: $id")
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete download: $downloadId")
        }
    }
}
