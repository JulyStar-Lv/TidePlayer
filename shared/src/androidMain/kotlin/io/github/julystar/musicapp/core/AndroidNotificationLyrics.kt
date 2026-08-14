package io.github.julystar.musicapp.core

import androidx.media3.common.MediaMetadata

/** Keeps notification-only lyric text out of the Media3 playback timeline. */
internal class AndroidNotificationLyrics {
    private var contentTitle: String? = null

    fun update(trackTitle: String, lineText: String?, enabled: Boolean): Boolean {
        val updatedTitle = (if (enabled) lineText else null)
            ?.takeIf(String::isNotBlank)
            ?: trackTitle.takeIf(String::isNotBlank)
        if (updatedTitle == contentTitle) return false

        contentTitle = updatedTitle
        return true
    }

    fun resolveContentTitle(metadata: MediaMetadata): CharSequence? =
        contentTitle ?: metadata.title

    fun resolveSessionTitle(
        trackTitle: String,
        lineText: String?,
        notificationEnabled: Boolean,
        bluetoothEnabled: Boolean,
    ): String = if (notificationEnabled || bluetoothEnabled) {
        lineText?.takeIf(String::isNotBlank) ?: trackTitle
    } else {
        trackTitle
    }
}
