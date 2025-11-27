package com.example.simpledownloader.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * Utility to check and manage storage permissions
 */
object PermissionsHelper {
    
    /**
     * Get required storage permissions based on Android version
     */
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: Need READ_MEDIA_VIDEO permission
            arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-12: Scoped storage, no permission needed for MediaStore writes
            arrayOf()
        } else {
            // Android 9 and below: Need WRITE_EXTERNAL_STORAGE
            arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
    
    /**
     * Check if all required permissions are granted
     */
    fun hasStoragePermission(context: Context): Boolean {
        val permissions = getRequiredPermissions()
        
        // If no permissions required (Android 10-12), return true
        if (permissions.isEmpty()) {
            return true
        }
        
        // Check each required permission
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Get user-friendly permission status message
     */
    fun getPermissionStatusMessage(context: Context): String {
        return if (hasStoragePermission(context)) {
            "İzinler verildi ✓"
        } else {
            val permissions = getRequiredPermissions()
            if (permissions.isEmpty()) {
                "İzin gerekmiyor" // Android 10-12
            } else {
                "İzin gerekli"
            }
        }
    }
    
    /**
     * Check if permission should be shown (for Android 10-12, we don't need permission)
     */
    fun shouldShowStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            true // Android 13+: Always show
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            false // Android 10-12: No permission needed
        } else {
            true // Android 9 and below: Show
        }
    }
    
    /**
     * Log current permission status for debugging
     */
    fun logPermissionStatus(context: Context) {
        val permissions = getRequiredPermissions()
        Timber.d("Android SDK: ${Build.VERSION.SDK_INT}")
        Timber.d("Required permissions: ${permissions.joinToString()}")
        Timber.d("Permission granted: ${hasStoragePermission(context)}")
    }
}
