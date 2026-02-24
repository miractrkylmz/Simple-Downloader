package com.example.simpledownloader

import android.app.Application
import com.example.simpledownloader.BuildConfig
import com.example.simpledownloader.utils.YtDlpUpdateManager
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SimpleDownloaderApplication : Application() {

    lateinit var ytDlpUpdateManager: YtDlpUpdateManager
        private set

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Initialize YtDlpUpdateManager
        ytDlpUpdateManager = YtDlpUpdateManager.getInstance(this)

        // Initialize YoutubeDL and check for updates in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ytDlpUpdateManager.initialize()
                Timber.d("YtDlpUpdateManager initialized with version: ${ytDlpUpdateManager.currentVersion.value}")
                
                // Check for updates in background
                val wasUpdated = ytDlpUpdateManager.checkAndUpdate()
                if (wasUpdated) {
                    Timber.d("yt-dlp was updated to: ${ytDlpUpdateManager.currentVersion.value}")
                } else {
                    Timber.d("yt-dlp is already up to date: ${ytDlpUpdateManager.currentVersion.value}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize or update YoutubeDL")
            }
        }
    }
}
