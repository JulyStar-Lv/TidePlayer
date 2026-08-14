package io.github.julystar.musicapp.core

import androidx.media3.common.MediaMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidNotificationLyricsTest {
    private val metadata = MediaMetadata.Builder()
        .setTitle("Track")
        .build()

    @Test
    fun enabledNotificationUsesCurrentLyricAndDeduplicatesUpdates() {
        val lyrics = AndroidNotificationLyrics()

        val changed = lyrics.update(trackTitle = "Track", lineText = "First line", enabled = true)
        val duplicateChanged =
            lyrics.update(trackTitle = "Track", lineText = "First line", enabled = true)

        assertEquals("First line", lyrics.resolveContentTitle(metadata))
        assertTrue(changed)
        assertFalse(duplicateChanged)
    }

    @Test
    fun disabledOrBlankLyricUsesTrackTitle() {
        val lyrics = AndroidNotificationLyrics()

        lyrics.update(trackTitle = "Track", lineText = "Lyric", enabled = false)
        assertEquals("Track", lyrics.resolveContentTitle(metadata))

        lyrics.update(trackTitle = "Track", lineText = "", enabled = true)
        assertEquals("Track", lyrics.resolveContentTitle(metadata))
    }

    @Test
    fun notificationLyricIsExposedThroughMediaSessionMetadata() {
        val lyrics = AndroidNotificationLyrics()

        assertEquals(
            "First line",
            lyrics.resolveSessionTitle(
                trackTitle = "Track",
                lineText = "First line",
                notificationEnabled = true,
                bluetoothEnabled = false,
            ),
        )
    }
}
