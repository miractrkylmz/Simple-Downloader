package com.example.simpledownloader.utils

import com.example.simpledownloader.domain.model.MediaType
import com.example.simpledownloader.domain.model.Platform
import com.example.simpledownloader.domain.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * High-performance resolver for Twitter / X media (photos, GIFs, and videos)
 * Bypasses yt-dlp limitations where tweets without videos fail with 'No video found'.
 */
object TwitterMediaResolver {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    /**
     * Checks if a URL is a Twitter or X tweet status URL
     */
    fun isTwitterUrl(url: String): Boolean {
        val lower = url.lowercase()
        return (lower.contains("twitter.com") || lower.contains("x.com")) &&
                (lower.contains("/status/") || lower.contains("/statuses/"))
    }

    /**
     * Extracts status/tweet ID from a Twitter or X URL
     */
    fun extractTweetId(url: String): String? {
        val regex = Regex("""status(?:es)?/(\d+)""", RegexOption.IGNORE_CASE)
        return regex.find(url)?.groupValues?.get(1)
    }

    /**
     * Resolves media from Twitter/X using FxTwitter (primary) and VxTwitter (fallback)
     */
    suspend fun resolveTweetMedia(tweetUrl: String): VideoInfo? = withContext(Dispatchers.IO) {
        val tweetId = extractTweetId(tweetUrl) ?: return@withContext null
        Timber.d("Resolving Twitter/X media for tweet ID: $tweetId")

        // 1. Try FxTwitter API
        try {
            val fxtwitterResult = fetchFromFxTwitter(tweetUrl, tweetId)
            if (fxtwitterResult != null) {
                Timber.d("Successfully resolved Twitter media via FxTwitter")
                return@withContext fxtwitterResult
            }
        } catch (e: Exception) {
            Timber.w(e, "FxTwitter resolution failed for tweet $tweetId, attempting VxTwitter fallback")
        }

        // 2. Try VxTwitter API fallback
        try {
            val vxtwitterResult = fetchFromVxTwitter(tweetUrl, tweetId)
            if (vxtwitterResult != null) {
                Timber.d("Successfully resolved Twitter media via VxTwitter")
                return@withContext vxtwitterResult
            }
        } catch (e: Exception) {
            Timber.w(e, "VxTwitter resolution failed for tweet $tweetId")
        }

        null
    }

    private fun fetchFromFxTwitter(tweetUrl: String, tweetId: String): VideoInfo? {
        val apiUrl = "https://api.fxtwitter.com/i/status/$tweetId"
        val responseJson = httpGet(apiUrl) ?: return null

        val json = JSONObject(responseJson)
        if (json.optInt("code") != 200) return null

        val tweet = json.optJSONObject("tweet") ?: return null
        val rawText = tweet.optString("text", "").trim()
        val author = tweet.optJSONObject("author")
        val authorName = author?.optString("name") ?: ""
        val authorScreenName = author?.optString("screen_name") ?: ""
        val uploader = if (authorScreenName.isNotBlank()) "@$authorScreenName" else authorName

        val media = tweet.optJSONObject("media") ?: return null

        // A. Check for Photos
        val photos = media.optJSONArray("photos")
        if (photos != null && photos.length() > 0) {
            val firstPhoto = photos.getJSONObject(0)
            val photoUrl = firstPhoto.getString("url")
            val title = if (rawText.isNotBlank()) rawText.take(80) else "Twitter Fotoğrafı"

            return VideoInfo(
                url = tweetUrl,
                title = title,
                thumbnailUrl = photoUrl,
                duration = null,
                platform = Platform.TWITTER,
                uploader = uploader,
                description = rawText,
                viewCount = tweet.optLong("views"),
                mediaType = MediaType.IMAGE,
                directMediaUrl = photoUrl,
                availableFormats = emptyList()
            )
        }

        // B. Check for Videos
        val videos = media.optJSONArray("videos")
        if (videos != null && videos.length() > 0) {
            val firstVideo = videos.getJSONObject(0)
            val videoUrl = firstVideo.getString("url")
            val thumbUrl = firstVideo.optString("thumbnail_url", "")
            val durationSeconds = firstVideo.optDouble("duration", 0.0).toInt()
            val title = if (rawText.isNotBlank()) rawText.take(80) else "Twitter Videosu"

            return VideoInfo(
                url = tweetUrl,
                title = title,
                thumbnailUrl = thumbUrl,
                duration = if (durationSeconds > 0) durationSeconds else null,
                platform = Platform.TWITTER,
                uploader = uploader,
                description = rawText,
                viewCount = tweet.optLong("views"),
                mediaType = MediaType.VIDEO,
                directMediaUrl = videoUrl,
                availableFormats = emptyList()
            )
        }

        return null
    }

    private fun fetchFromVxTwitter(tweetUrl: String, tweetId: String): VideoInfo? {
        val apiUrl = "https://api.vxtwitter.com/Twitter/status/$tweetId"
        val responseJson = httpGet(apiUrl) ?: return null

        val json = JSONObject(responseJson)
        val hasMedia = json.optBoolean("hasMedia", false)
        if (!hasMedia) return null

        val mediaUrls = json.optJSONArray("mediaURLs")
        if (mediaUrls == null || mediaUrls.length() == 0) return null

        val firstMediaUrl = mediaUrls.getString(0)
        val rawText = json.optString("text", "").trim()
        val authorScreenName = json.optString("user_screen_name", "")
        val uploader = if (authorScreenName.isNotBlank()) "@$authorScreenName" else ""

        val isVideo = firstMediaUrl.contains(".mp4") || firstMediaUrl.contains(".m3u8")
        val mediaType = if (isVideo) MediaType.VIDEO else MediaType.IMAGE
        val title = if (rawText.isNotBlank()) rawText.take(80) else if (isVideo) "Twitter Videosu" else "Twitter Fotoğrafı"

        return VideoInfo(
            url = tweetUrl,
            title = title,
            thumbnailUrl = firstMediaUrl,
            duration = null,
            platform = Platform.TWITTER,
            uploader = uploader,
            description = rawText,
            viewCount = null,
            mediaType = mediaType,
            directMediaUrl = firstMediaUrl,
            availableFormats = emptyList()
        )
    }

    private fun httpGet(urlStr: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 15000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                instanceFollowRedirects = true
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    reader.readText()
                }
            } else {
                Timber.w("HTTP request to $urlStr returned response code: ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Timber.w(e, "Error performing GET to $urlStr")
            null
        } finally {
            connection?.disconnect()
        }
    }
}
