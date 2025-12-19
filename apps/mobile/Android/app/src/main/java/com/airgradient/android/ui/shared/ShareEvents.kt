package com.airgradient.android.ui.shared

import android.net.Uri

/**
 * UI events for initiating platform share flows.
 */
sealed class ShareEvent {
    data class Image(
        val subject: String,
        val message: String,
        val uri: Uri,
        val mimeType: String = "image/png"
    ) : ShareEvent()

    data class Text(
        val subject: String,
        val message: String,
        val mimeType: String = "text/plain"
    ) : ShareEvent()
}
