package io.github.julystar.musicapp.service.playback.data

import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.model.LyricFontChoice
import io.github.julystar.musicapp.core.domain.model.Lyrics
import io.github.julystar.musicapp.core.domain.model.filteredForDisplay
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.singleton.DesktopPlaybackEngine
import io.github.julystar.musicapp.singleton.RoomLibraryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JWindow
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/** Always-on-top desktop lyric window controlled by the shared lyric output setting. */
class DesktopFloatingLyricsController(
    settingsRepository: SettingsRepository,
    playerRepository: PlayerRepository,
    private val roomLibraryStore: RoomLibraryStore,
    private val playbackEngine: DesktopPlaybackEngine,
    scope: CoroutineScope,
) {
    private var settings = AppSettings.Default
    private var lyrics = Lyrics()
    private var window: JWindow? = null
    private var label: JLabel? = null
    private var currentLine = -1

    init {
        if (!GraphicsEnvironment.isHeadless()) {
            scope.launch {
                combine(settingsRepository.settings, playerRepository.music) { appSettings, music ->
                    appSettings to music?.meta?.id?.value
                }.collectLatest { (appSettings, trackId) ->
                    settings = appSettings
                    lyrics = trackId
                        ?.let { roomLibraryStore.getPlaybackLyrics(it) }
                        ?.filteredForDisplay(appSettings.lyrics)
                        ?: Lyrics()
                    currentLine = -1
                    refreshFont(label?.text.orEmpty())
                    if (appSettings.lyricOutput.floatingLyricsEnabled) showWindow() else hideWindow()
                }
            }
            scope.launch {
                while (true) {
                    delay(100)
                    if (!settings.lyricOutput.floatingLyricsEnabled) continue
                    val position = playbackEngine.readPosition().positionMs
                    val index = lyrics.lines.indexOfLast { it.duration.inWholeMilliseconds <= position }
                    if (index == currentLine) continue
                    currentLine = index
                    updateText(lyrics.lines.getOrNull(index)?.text.orEmpty())
                }
            }
        }
    }

    private fun showWindow() = SwingUtilities.invokeLater {
        if (window != null) return@invokeLater
        val lyricLabel = JLabel("Tide Player", SwingConstants.CENTER).apply {
            foreground = Color.WHITE
            background = Color(20, 20, 24, 210)
            isOpaque = true
            font = floatingFont(text)
            border = BorderFactory.createEmptyBorder(12, 28, 12, 28)
        }
        window = JWindow().apply {
            isAlwaysOnTop = true
            background = Color(0, 0, 0, 0)
            contentPane.add(lyricLabel)
            pack()
            setLocationRelativeTo(null)
            location = location.apply { y = 48 }
            isVisible = true
        }
        label = lyricLabel
    }

    private fun hideWindow() = SwingUtilities.invokeLater {
        window?.dispose()
        window = null
        label = null
    }

    private fun updateText(text: String) = SwingUtilities.invokeLater {
        val displayText = text.ifBlank { "Tide Player" }
        label?.text = displayText
        label?.font = floatingFont(displayText)
        window?.pack()
    }

    private fun refreshFont(text: String) = SwingUtilities.invokeLater {
        label?.font = floatingFont(text)
        window?.pack()
    }

    private fun floatingFont(text: String): Font {
        val fontSettings = settings.lyrics.font
        if (!fontSettings.applyToFloatingLyrics) {
            return Font(Font.SANS_SERIF, Font.BOLD, 22)
        }
        val choice = if (text.any { character -> character.isCjkCharacter() }) {
            fontSettings.cjkFont
        } else {
            fontSettings.westernFont
        }
        val family = when (choice) {
            LyricFontChoice.System -> Font.SANS_SERIF
            LyricFontChoice.AppSans -> "Plus Jakarta Sans"
            LyricFontChoice.AppCjk -> "Noto Sans SC"
            LyricFontChoice.Monospace -> Font.MONOSPACED
        }
        val style = if (fontSettings.weight >= 600) Font.BOLD else Font.PLAIN
        return Font(family, style, 22)
    }

    private fun Char.isCjkCharacter(): Boolean = code in 0x2E80..0x9FFF ||
        code in 0xAC00..0xD7AF ||
        code in 0xF900..0xFAFF
}
