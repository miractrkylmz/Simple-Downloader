package com.example.simpledownloader.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility object for file operations
 */
object FileHelper {
    /**
     * Sanitize a filename by removing or replacing invalid characters
     */
    fun sanitizeFilename(filename: String): String {
        // Remove or replace invalid characters for filename
        var sanitized = filename
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
        
        // Limit filename length (leave room for platform prefix and extension)
        if (sanitized.length > 100) {
            sanitized = sanitized.substring(0, 100)
        }
        
        return sanitized
    }
    
    /**
     * Generate a smart filename from video title and platform
     */
    fun generateFilename(title: String, platform: String, extension: String = "mp4"): String {
        val sanitizedTitle = sanitizeFilename(title)
        return "${platform}_${sanitizedTitle}.${extension}"
    }
    
    /**
     * Handle duplicate filenames by appending a number
     */
    fun handleDuplicate(basePath: String, filename: String): String {
        val file = java.io.File(basePath, filename)
        
        if (!file.exists()) {
            return filename
        }
        
        // Extract name and extension
        val lastDot = filename.lastIndexOf('.')
        val name = if (lastDot > 0) filename.substring(0, lastDot) else filename
        val extension = if (lastDot > 0) filename.substring(lastDot) else ""
        
        // Try appending numbers (1), (2), (3), etc.
        var counter = 1
        var newFilename: String
        do {
            newFilename = "$name($counter)$extension"
            counter++
        } while (java.io.File(basePath, newFilename).exists() && counter < 1000)
        
        return newFilename
    }
    
    /**
     * Format file size for display
     */
    fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.2f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.2f KB", kb)
            else -> "$bytes B"
        }
    }
    
    /**
     * Format download speed for display
     */
    fun formatSpeed(bytesPerSecond: Long): String {
        return "${formatFileSize(bytesPerSecond)}/s"
    }
    
    /**
     * Format date for display
     */
    fun formatDate(timestamp: Long): String {
        val formatter = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }
    
    /**
     * Format duration (seconds) to MM:SS or HH:MM:SS
     */
    fun formatDuration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, secs)
        }
    }
    
    /**
     * Format ETA (seconds) to human-readable string
     */
    fun formatEta(seconds: Int): String {
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }
    
    /**
     * Format date as relative time (e.g., "2 hours ago", "Yesterday")
     */
    fun formatRelativeDate(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            seconds < 60 -> "Az önce"
            minutes < 60 -> "$minutes dakika önce"
            hours < 24 -> "$hours saat önce"
            days == 1L -> "Dün"
            days < 7 -> "$days gün önce"
            days < 30 -> "${days / 7} hafta önce"
            else -> {
                // Fall back to formatted date for older items
                val formatter = SimpleDateFormat("dd MMM yyyy", Locale("tr", "TR"))
                formatter.format(Date(timestamp))
            }
        }
    }
    
    /**
     * Open file location in file manager
     */
    fun openFileLocation(context: android.content.Context, filePath: String) {
        try {
            var targetFile: java.io.File? = null
            
            // Check if it's a content URI (modern Android storage)
            if (filePath.startsWith("content://")) {
                try {
                    val uri = android.net.Uri.parse(filePath)
                    // Try to resolve real path from URI
                    val projection = arrayOf(android.provider.MediaStore.MediaColumns.DATA)
                    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val columnIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA)
                            val realPath = cursor.getString(columnIndex)
                            if (realPath != null) {
                                targetFile = java.io.File(realPath)
                            }
                        }
                    }
                } catch (e: Exception) {
                    timber.log.Timber.w(e, "Could not resolve real path from URI: $filePath")
                }
            } else {
                targetFile = java.io.File(filePath)
            }
            
            // If we have a target file and it exists, open its parent
            if (targetFile != null && targetFile!!.exists()) {
                val parentFile = targetFile!!.parentFile
                if (parentFile != null && parentFile.exists()) {
                    openFolderIntent(context, parentFile)
                    return
                }
            }
            
            // Fallback: If we couldn't resolve the file or it doesn't exist via File API
            // (common in Android 10+ scoped storage), try to open the default download folder
            // which is Movies/SimpleDownloader based on our repository logic
            
            val defaultFolder = java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES),
                "SimpleDownloader"
            )
            
            if (defaultFolder.exists()) {
                 openFolderIntent(context, defaultFolder)
            } else {
                // If even valid folder doesn't exist, last resort: Open generic Downloads
                openGenericDownloads(context)
            }
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error opening file location")
            android.widget.Toast.makeText(
                context,
                "Klasör açılamadı: ${e.message}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun openFolderIntent(context: android.content.Context, folder: java.io.File) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                setDataAndType(
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        folder
                    ),
                    "resource/folder"
                )
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback: Try to open with DocumentsUI
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    setDataAndType(
                        android.net.Uri.fromFile(folder),
                        "*/*"
                    )
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                 // Try opening specific folder via SAF URI construction
                 // This covers /storage/emulated/0/Movies/SimpleDownloader mapping to primary:Movies/SimpleDownloader
                 try {
                     val path = folder.absolutePath
                     val relativePath = path.substringAfter("/storage/emulated/0/")
                     val encodedPath = android.net.Uri.encode(relativePath).replace("/", "%2F")
                     val uriString = "content://com.android.externalstorage.documents/document/primary:$encodedPath"
                     
                     val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(
                            android.net.Uri.parse(uriString),
                            "vnd.android.document/directory"
                        )
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or 
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    context.startActivity(intent)
                 } catch(e3: Exception) {
                    // Final fallback: Show toast with path
                    android.widget.Toast.makeText(
                        context,
                        "Klasör: ${folder.absolutePath}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                 }
            }
        }
    }
    
    private fun openGenericDownloads(context: android.content.Context) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(
                    android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary:Download"),
                    "vnd.android.document/directory"
                )
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or 
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
             android.widget.Toast.makeText(
                context,
                "Klasör açılamadı",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}
