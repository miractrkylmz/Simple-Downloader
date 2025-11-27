package com.example.simpledownloader.utils

import android.content.ClipboardManager
import android.content.Context

/**
 * Utility object for clipboard operations
 */
object ClipboardHelper {
    /**
     * Get text from clipboard
     * @return The clipboard text or null if empty
     */
    fun getClipboardText(context: Context): String? {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        
        return if (clipboardManager?.hasPrimaryClip() == true) {
            val clipData = clipboardManager.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                clipData.getItemAt(0).text?.toString()
            } else {
                null
            }
        } else {
            null
        }
    }
    
    /**
     * Check if clipboard contains a valid URL
     */
    fun hasUrl(context: Context): Boolean {
        val text = getClipboardText(context) ?: return false
        return text.trim().startsWith("http://") || text.trim().startsWith("https://")
    }
}
