package io.github.julystar.musicapp.core

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import cn.lyric.getter.api.API
import cn.lyric.getter.api.data.ExtraData
import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.model.LyricLine
import io.github.julystar.musicapp.core.domain.model.LyricFontChoice
import io.github.julystar.musicapp.core.domain.model.Lyrics
import io.github.julystar.musicapp.core.domain.model.SecondaryLyricContent
import io.github.julystar.musicapp.core.domain.model.filteredForDisplay
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.service.playback.data.PlayerRepository
import io.github.julystar.musicapp.singleton.RoomLibraryStore
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricHelper
import com.hchen.superlyricapi.SuperLyricLine
import com.hchen.superlyricapi.SuperLyricWord
import io.github.proify.lyricon.lyric.model.LyricWord as LyriconWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Publishes the active lyric line to Android/OEM and third-party lyric consumers. */
internal class AndroidLyricOutputController(
    private val context: Context,
    settingsRepository: SettingsRepository,
    private val playerRepository: PlayerRepository,
    private val roomLibraryStore: RoomLibraryStore,
    private val scope: CoroutineScope,
    private val playerProvider: () -> Player?,
    private val notificationLyrics: AndroidNotificationLyrics,
    private val refreshMediaNotification: () -> Unit,
) {
    private var settings = AppSettings.Default
    private var sourceLyrics = Lyrics()
    private var lyrics = Lyrics()
    private var currentTrackId: Long? = null
    private var currentTitle = ""
    private var currentArtist = ""
    private var currentLineIndex = -1
    private var lyricon: LyriconProvider? = null
    private val lyricGetter = API()
    private var superLyricRegistered = false
    private var overlay: TextView? = null

    init {
        scope.launch {
            combine(settingsRepository.settings, playerRepository.music) { appSettings, music ->
                appSettings to music
            }.collectLatest { (appSettings, music) ->
                settings = appSettings
                val trackId = music?.meta?.id?.value
                currentTitle = music?.meta?.title.orEmpty()
                val trackChanged = trackId != currentTrackId
                if (trackChanged) {
                    currentTrackId = trackId
                    currentArtist = ""
                    sourceLyrics = Lyrics()
                    lyrics = Lyrics()
                    currentLineIndex = -1
                    publishSessionLine(null)
                }
                currentArtist = trackId?.let { roomLibraryStore.getTrackPrimaryArtist(it) }.orEmpty()
                if (trackChanged) {
                    sourceLyrics = trackId?.let { roomLibraryStore.getPlaybackLyrics(it) } ?: Lyrics()
                }
                lyrics = sourceLyrics.filteredForDisplay(appSettings.lyrics)
                val position = playerProvider()?.currentPosition ?: 0L
                currentLineIndex = lyrics.lines.indexOfLast { line ->
                    line.duration.inWholeMilliseconds <= position
                }
                configureProviders()
                publishWholeSong()
                publishLine(lyrics.lines.getOrNull(currentLineIndex), position)
            }
        }
        scope.launch {
            while (true) {
                delay(100)
                val position = playerProvider()?.currentPosition ?: 0L
                val index = lyrics.lines.indexOfLast { line ->
                    line.duration.inWholeMilliseconds <= position
                }
                if (index == currentLineIndex) {
                    if (settings.lyricOutput.lyriconEnabled) lyricon?.player?.setPosition(position)
                    continue
                }
                currentLineIndex = index
                publishLine(lyrics.lines.getOrNull(index), position)
            }
        }
    }

    fun destroy() {
        runCatching { lyricon?.destroy() }
        lyricon = null
        if (superLyricRegistered) runCatching { SuperLyricHelper.unregisterPublisher() }
        superLyricRegistered = false
        runCatching { lyricGetter.clearLyric() }
        removeOverlay()
    }

    private fun configureProviders() {
        val output = settings.lyricOutput
        if (output.lyriconEnabled && lyricon == null) {
            runCatching {
                lyricon = LyriconFactory.createProvider(
                    context = context,
                    providerPackageName = context.packageName,
                    playerPackageName = context.packageName,
                    logo = ProviderLogo.fromDrawable(
                        context,
                        io.github.julystar.musicapp.shared.R.drawable.icon_lyrics,
                        width = 96,
                        height = 96,
                    ),
                ).also { it.register() }
            }
        } else if (!output.lyriconEnabled && lyricon != null) {
            runCatching { lyricon?.destroy() }
            lyricon = null
        }
        if (output.superLyricEnabled && !superLyricRegistered) {
            runCatching {
                if (SuperLyricHelper.isAvailable()) {
                    SuperLyricHelper.registerPublisher()
                    SuperLyricHelper.setSystemPlayStateListenerEnabled(true)
                    superLyricRegistered = true
                }
            }
        } else if (!output.superLyricEnabled && superLyricRegistered) {
            runCatching { SuperLyricHelper.unregisterPublisher() }
            superLyricRegistered = false
        }
        if (!output.lyricGetterEnabled) runCatching { lyricGetter.clearLyric() }
        if (!output.floatingLyricsEnabled) removeOverlay()
    }

    private fun publishWholeSong() {
        if (settings.lyricOutput.lyriconEnabled) {
            val durationMs = playerProvider()?.duration?.coerceAtLeast(0L) ?: 0L
            val richLines = lyrics.lines.mapIndexed { index, line ->
                val start = line.duration.inWholeMilliseconds
                val end = lyrics.lines.getOrNull(index + 1)?.duration?.inWholeMilliseconds
                    ?: durationMs.takeIf { it > start }
                    ?: (start + 3_000L)
                val textContent = line.textContent()
                val secondary = textContent.selectedSecondary()
                RichLyricLine(
                    begin = start,
                    end = end,
                    isAlignedRight = false,
                    text = textContent.primary,
                    words = line.words.map { word ->
                        val wordStart = start + word.startOffset.inWholeMilliseconds
                        LyriconWord(
                            text = word.text,
                            begin = wordStart,
                            end = wordStart + word.duration.inWholeMilliseconds,
                        )
                    }.takeIf { it.isNotEmpty() },
                    secondary = secondary,
                    secondaryWords = null,
                    translation = textContent.translation
                        .takeIf { settings.lyricOutput.secondaryContent == SecondaryLyricContent.Translation },
                    roma = textContent.pronunciation
                        .takeIf { settings.lyricOutput.secondaryContent == SecondaryLyricContent.Pronunciation },
                )
            }
            runCatching {
                lyricon?.player?.setSong(
                    io.github.proify.lyricon.lyric.model.Song(
                        id = currentTrackId?.toString().orEmpty(),
                        name = currentTitle,
                        artist = currentArtist,
                        duration = durationMs,
                        lyrics = richLines,
                    )
                )
            }
        }
    }

    private fun publishLine(line: LyricLine?, positionMs: Long) {
        val output = settings.lyricOutput
        if (output.lyriconEnabled) {
            runCatching {
                lyricon?.player?.setPlaybackState(playerProvider()?.isPlaying == true)
                lyricon?.player?.setPosition(positionMs)
            }
        }
        if (output.superLyricEnabled && superLyricRegistered && line != null) {
            val start = line.duration.inWholeMilliseconds
            val end = nextLineStart(start)
            runCatching {
                SuperLyricHelper.sendLyric(
                    SuperLyricData()
                        .setTitle(currentTitle)
                        .setArtist(currentArtist)
                        .setLyric(
                            SuperLyricLine(
                                line.outputText(),
                                line.words.map { word ->
                                    val wordStart = start + word.startOffset.inWholeMilliseconds
                                    SuperLyricWord(
                                        word.text,
                                        wordStart,
                                        wordStart + word.duration.inWholeMilliseconds,
                                    )
                                }.toTypedArray().takeIf { it.isNotEmpty() },
                                start,
                                end,
                            )
                        )
                )
            }
        }
        if (output.lyricGetterEnabled && line != null) {
            runCatching {
                lyricGetter.sendLyric(
                    line.outputText(),
                    ExtraData().apply {
                        packageName = context.packageName
                        base64Icon = ""
                        useOwnMusicController = false
                        delay = (nextLineStart(line.duration.inWholeMilliseconds) -
                            line.duration.inWholeMilliseconds).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    }
                )
            }
        }
        updateOverlay(line)
        publishSessionLine(line)
    }

    private fun publishSessionLine(line: LyricLine?) {
        val output = settings.lyricOutput
        val lineText = line?.outputText()?.takeIf(String::isNotBlank)
        val notificationChanged = notificationLyrics.update(
            trackTitle = currentTitle,
            lineText = lineText,
            enabled = output.notificationLyricsEnabled,
        )

        val player = playerProvider()
        if (player == null) {
            if (notificationChanged) refreshMediaNotification()
            return
        }
        val item = player.currentMediaItem
        if (item == null || item.mediaId != currentTrackId?.toString()) {
            if (notificationChanged) refreshMediaNotification()
            return
        }
        val extras = Bundle(item.mediaMetadata.extras ?: Bundle.EMPTY).apply {
            if (output.colorOsLockScreenLyricsEnabled) {
                putString("lyricInfo", colorOsPayload())
                putString("rawLyric", toLrc())
            } else {
                remove("lyricInfo")
                remove("rawLyric")
            }
            if (output.flymeStatusLyricsEnabled && lineText != null) {
                putString("ticker_text", lineText)
                putString("lyric", lineText)
                putBoolean("ticker_icon_switch", false)
            } else {
                remove("ticker_text")
                remove("lyric")
                remove("ticker_icon_switch")
            }
        }
        val desiredTitle = notificationLyrics.resolveSessionTitle(
            trackTitle = currentTitle,
            lineText = lineText,
            notificationEnabled = output.notificationLyricsEnabled,
            bluetoothEnabled = output.bluetoothLyricsEnabled,
        )
        val existingExtras = item.mediaMetadata.extras ?: Bundle.EMPTY
        val metadataChanged = item.mediaMetadata.title?.toString() != desiredTitle ||
            item.mediaMetadata.artist?.toString() != currentArtist ||
            !existingExtras.hasSameLyricOutput(extras)
        val index = player.currentMediaItemIndex
        val canReplaceMediaItem = metadataChanged &&
            index >= 0 &&
            player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)
        if (notificationChanged && !canReplaceMediaItem) refreshMediaNotification()
        if (!canReplaceMediaItem) return

        val metadata = item.mediaMetadata.buildUpon()
            .setTitle(desiredTitle)
            .setArtist(currentArtist)
            .setExtras(extras)
            .build()
        val updated: MediaItem = item.buildUpon().setMediaMetadata(metadata).build()
        player.replaceMediaItem(index, updated)
    }

    private fun updateOverlay(line: LyricLine?) {
        if (!settings.lyricOutput.floatingLyricsEnabled || !Settings.canDrawOverlays(context)) {
            removeOverlay()
            return
        }
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = overlay ?: TextView(context).apply {
            setTextColor(Color.WHITE)
            setShadowLayer(6f, 0f, 2f, Color.BLACK)
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(32, 16, 32, 16)
            windowManager.addView(
                this,
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = 96
                }
            )
            this@AndroidLyricOutputController.overlay = this
        }
        val text = line?.outputText() ?: currentTitle
        view.text = text
        view.applyFloatingFont(text)
    }

    private fun removeOverlay() {
        val view = overlay ?: return
        overlay = null
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        runCatching { windowManager.removeView(view) }
    }

    private fun TextView.applyFloatingFont(text: String) {
        val fontSettings = settings.lyrics.font
        if (!fontSettings.applyToFloatingLyrics) {
            typeface = Typeface.DEFAULT_BOLD
            return
        }
        val choice = if (text.any { character -> character.isCjkCharacter() }) {
            fontSettings.cjkFont
        } else {
            fontSettings.westernFont
        }
        val base = when (choice) {
            LyricFontChoice.Monospace -> Typeface.MONOSPACE
            LyricFontChoice.AppSans -> Typeface.create("sans-serif", Typeface.NORMAL)
            LyricFontChoice.AppCjk -> Typeface.create("sans-serif", Typeface.NORMAL)
            LyricFontChoice.System -> Typeface.DEFAULT
        }
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(base, fontSettings.weight.coerceIn(100, 900), false)
        } else {
            Typeface.create(base, if (fontSettings.weight >= 600) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun Char.isCjkCharacter(): Boolean = code in 0x2E80..0x9FFF ||
        code in 0xAC00..0xD7AF ||
        code in 0xF900..0xFAFF

    private data class OutputLyricText(
        val primary: String,
        val translation: String?,
        val pronunciation: String?,
    )

    private fun LyricLine.textContent(): OutputLyricText {
        val parts = text.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        return OutputLyricText(
            primary = parts.firstOrNull().orEmpty(),
            translation = parts.getOrNull(1),
            pronunciation = parts.drop(2).joinToString("\n").takeIf(String::isNotBlank),
        )
    }

    private fun OutputLyricText.selectedSecondary(): String? = when (
        settings.lyricOutput.secondaryContent
    ) {
        SecondaryLyricContent.Off -> null
        SecondaryLyricContent.Translation -> translation
        SecondaryLyricContent.Pronunciation -> pronunciation
    }

    private fun LyricLine.outputText(): String {
        val content = textContent()
        return listOfNotNull(content.primary.takeIf(String::isNotBlank), content.selectedSecondary())
            .joinToString("\n")
    }

    private fun nextLineStart(start: Long): Long = lyrics.lines
        .firstOrNull { it.duration.inWholeMilliseconds > start }
        ?.duration
        ?.inWholeMilliseconds
        ?: (start + 3_000L)

    private fun toLrc(): String = lyrics.lines.joinToString("\n") { line ->
        val timeMs = line.duration.inWholeMilliseconds.coerceAtLeast(0L)
        val minutes = timeMs / 60_000
        val seconds = (timeMs % 60_000) / 1_000
        val centiseconds = (timeMs % 1_000) / 10
        "[%02d:%02d.%02d]%s".format(minutes, seconds, centiseconds, line.outputText())
    }

    private fun colorOsPayload(): String {
        fun String.escapeJson() = replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return "{\"songName\":\"${currentTitle.escapeJson()}\",\"artist\":\"${currentArtist.escapeJson()}\"," +
            "\"songId\":\"${currentTrackId ?: 0L}\",\"lyric\":\"${toLrc().escapeJson()}\"}"
    }

    private fun Bundle.hasSameLyricOutput(other: Bundle): Boolean {
        val stringKeys = listOf("lyricInfo", "rawLyric", "ticker_text", "lyric")
        val stringsMatch = stringKeys.all { key ->
            containsKey(key) == other.containsKey(key) && getString(key) == other.getString(key)
        }
        return stringsMatch &&
            containsKey("ticker_icon_switch") == other.containsKey("ticker_icon_switch") &&
            getBoolean("ticker_icon_switch") == other.getBoolean("ticker_icon_switch")
    }
}
