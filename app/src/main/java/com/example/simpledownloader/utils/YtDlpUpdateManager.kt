package com.example.simpledownloader.utils

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Manager for yt-dlp binary updates
 * Provides centralized update management with status tracking
 */
class YtDlpUpdateManager(private val context: Context) {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _currentVersion = MutableStateFlow<String?>(null)
    val currentVersion: StateFlow<String?> = _currentVersion.asStateFlow()

    sealed class UpdateState {
        data object Idle : UpdateState()
        data object Checking : UpdateState()
        data object Updating : UpdateState()
        data class Success(val newVersion: String, val wasUpdated: Boolean) : UpdateState()
        data class Error(val message: String) : UpdateState()
    }

    /**
     * Initialize and get current version
     */
    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(context)
                val version = YoutubeDL.getInstance().version(context)
                _currentVersion.value = version
                Timber.d("YtDlpUpdateManager initialized. Version: $version")
            } catch (e: Exception) {
                if (e.message?.contains("already initialized", ignoreCase = true) == true) {
                    try {
                        val version = YoutubeDL.getInstance().version(context)
                        _currentVersion.value = version
                        Timber.d("YoutubeDL already initialized. Version: $version")
                    } catch (e2: Exception) {
                        Timber.e(e2, "Failed to get version after initialization")
                    }
                } else {
                    Timber.e(e, "Failed to initialize YoutubeDL")
                    throw e
                }
            }
        }
    }

    /**
     * Check for updates and apply if available
     * @return true if update was successful, false otherwise
     */
    suspend fun checkAndUpdate(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                _updateState.value = UpdateState.Checking
                
                // Ensure initialized
                try {
                    YoutubeDL.getInstance().init(context)
                } catch (e: Exception) {
                    // Already initialized, continue
                }

                val oldVersion = YoutubeDL.getInstance().version(context) ?: "unknown"
                _currentVersion.value = oldVersion
                Timber.d("Current yt-dlp version: $oldVersion")

                _updateState.value = UpdateState.Updating

                val updateStatus = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
                val newVersion = YoutubeDL.getInstance().version(context) ?: "unknown"
                _currentVersion.value = newVersion

                val wasUpdated = oldVersion != newVersion
                
                if (wasUpdated) {
                    Timber.d("yt-dlp updated from $oldVersion to $newVersion")
                    _updateState.value = UpdateState.Success(newVersion, true)
                } else {
                    Timber.d("yt-dlp is already up to date: $newVersion (status: $updateStatus)")
                    _updateState.value = UpdateState.Success(newVersion, false)
                }

                wasUpdated
            } catch (e: YoutubeDLException) {
                Timber.e(e, "Failed to update yt-dlp")
                _updateState.value = UpdateState.Error("Güncelleme başarısız: ${e.message}")
                false
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error during yt-dlp update")
                _updateState.value = UpdateState.Error("Beklenmeyen hata: ${e.message}")
                false
            }
        }
    }

    /**
     * Force update yt-dlp binary
     */
    suspend fun forceUpdate(): Boolean {
        return checkAndUpdate()
    }

    /**
     * Reset state to idle
     */
    fun resetState() {
        _updateState.value = UpdateState.Idle
    }

    /**
     * Get current yt-dlp version
     */
    suspend fun getVersion(): String? {
        return withContext(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().version(context)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get yt-dlp version")
                null
            }
        }
    }

    companion object {
        @Volatile
        private var instance: YtDlpUpdateManager? = null

        fun getInstance(context: Context): YtDlpUpdateManager {
            return instance ?: synchronized(this) {
                instance ?: YtDlpUpdateManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
