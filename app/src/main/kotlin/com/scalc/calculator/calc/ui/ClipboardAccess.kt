package com.scalc.calculator.calc.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Plain-text access to the Android clipboard. Every call is user initiated (Copy / Paste
 * buttons, Ctrl+C / Ctrl+V); failures are reported as null / false instead of throwing.
 */
internal class ClipboardAccess(context: Context) {
    private val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    /**
     * Text of the first clip item, or null when the clipboard is empty or cannot be read.
     *
     * Deliberately not ClipData.Item.coerceToText: for an item that only holds a content URI
     * (a copied file) that would open the provider and read the whole stream on the main thread
     * without any size limit. Such an item yields its URI string instead, which is simply not a
     * valid expression.
     */
    fun readText(): String? {
        val clipboard = manager ?: return null
        return try {
            val clip = clipboard.primaryClip
            if (clip == null || clip.itemCount == 0) {
                null
            } else {
                val item = clip.getItemAt(0)
                item.text?.toString() ?: item.uri?.toString()
            }
        } catch (e: RuntimeException) {
            // SecurityException (no clipboard focus) or a misbehaving clip from another app.
            null
        }
    }

    /** Puts [text] on the clipboard; false when the clipboard is unavailable. */
    fun writeText(label: String, text: String): Boolean {
        val clipboard = manager ?: return false
        return try {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
