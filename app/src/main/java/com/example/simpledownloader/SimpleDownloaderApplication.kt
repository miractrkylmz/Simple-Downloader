package com.example.simpledownloader

import android.app.Application
import com.example.simpledownloader.BuildConfig
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SimpleDownloaderApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Initialize YoutubeDL in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                YoutubeDL.getInstance().init(applicationContext)
                val currentVersion = YoutubeDL.getInstance().version(applicationContext)
                Timber.d("YoutubeDL initialized. Version: $currentVersion")
                
                // Update yt-dlp binary to latest version
                try {
                    val updateStatus = YoutubeDL.getInstance().updateYoutubeDL(applicationContext)
                    val newVersion = YoutubeDL.getInstance().version(applicationContext)
                    Timber.d("YoutubeDL update status: $updateStatus. New Version: $newVersion")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to update YoutubeDL")
                }
            } catch (e: YoutubeDLException) {
                Timber.e(e, "Failed to initialize YoutubeDL")
            } catch (e: Exception) {
                Timber.e(e, "Unknown error initializing YoutubeDL")
            }
        }
    }
}
