package io.github.julystar.musicapp.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.julystar.musicapp.core.domain.model.AppLanguageMode
import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.model.AppThemeMode
import io.github.julystar.musicapp.core.domain.model.AudioEffectSettings
import io.github.julystar.musicapp.core.domain.model.AudioFocusMode
import io.github.julystar.musicapp.core.domain.model.AutoScanMode
import io.github.julystar.musicapp.core.domain.model.BackupSchedule
import io.github.julystar.musicapp.core.domain.model.DEFAULT_MANUAL_THEME_SEED_ARGB
import io.github.julystar.musicapp.core.domain.model.DownloadFinalizationSettings
import io.github.julystar.musicapp.core.domain.model.LyricFontChoice
import io.github.julystar.musicapp.core.domain.model.LyricFontSettings
import io.github.julystar.musicapp.core.domain.model.LyricOutputSettings
import io.github.julystar.musicapp.core.domain.model.LyricSourceKind
import io.github.julystar.musicapp.core.domain.model.LyricSourceMode
import io.github.julystar.musicapp.core.domain.model.LyricDisplaySettings
import io.github.julystar.musicapp.core.domain.model.LyricTextAlignment
import io.github.julystar.musicapp.core.domain.model.MetadataParsingSettings
import io.github.julystar.musicapp.core.domain.model.MissingFilePolicy
import io.github.julystar.musicapp.core.domain.model.MetadataScanMode
import io.github.julystar.musicapp.core.domain.model.PlayNextMode
import io.github.julystar.musicapp.core.domain.model.PlaybackAdvancedSettings
import io.github.julystar.musicapp.core.domain.model.PlayerInteractionSettings
import io.github.julystar.musicapp.core.domain.model.PreviousButtonBehavior
import io.github.julystar.musicapp.core.domain.model.ReplayGainMode
import io.github.julystar.musicapp.core.domain.model.ReverbPreset
import io.github.julystar.musicapp.core.domain.model.SecondaryLyricContent
import io.github.julystar.musicapp.core.domain.model.ShuffleStrategy
import io.github.julystar.musicapp.core.domain.model.StartupPlaybackMode
import io.github.julystar.musicapp.core.domain.model.SettingsBackupSelection
import io.github.julystar.musicapp.core.domain.model.SettingsBackupSettings
import io.github.julystar.musicapp.core.domain.model.normalizeAudioEffectSettings
import io.github.julystar.musicapp.core.domain.model.normalizeAudioCacheLimitBytes
import io.github.julystar.musicapp.core.domain.model.normalizeAudioPreloadBytes
import io.github.julystar.musicapp.core.domain.model.normalizeConnectionTimeoutSeconds
import io.github.julystar.musicapp.core.domain.model.normalizeImageCacheLimitBytes
import io.github.julystar.musicapp.core.domain.model.normalizeLyricFontScalePercent
import io.github.julystar.musicapp.core.domain.model.normalizeLyricFontSettings
import io.github.julystar.musicapp.core.domain.model.normalizeLyricPerspectiveAngleDegrees
import io.github.julystar.musicapp.core.domain.model.normalizeLyricPrimaryFontSizeSp
import io.github.julystar.musicapp.core.domain.model.normalizeLyricSecondaryFontSizeSp
import io.github.julystar.musicapp.core.domain.model.normalizeLyricSourcePriority
import io.github.julystar.musicapp.core.domain.model.normalizeMinimumAudioDurationMs
import io.github.julystar.musicapp.core.domain.model.normalizeNetworkRetryCount
import io.github.julystar.musicapp.core.domain.model.normalizePlaybackAdvancedSettings
import io.github.julystar.musicapp.core.domain.model.normalizeCustomThemeSeedArgbValues
import io.github.julystar.musicapp.core.domain.model.normalizeThemeSeedArgb
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.platform.applyAppLanguageMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val applyLanguageMode: (AppLanguageMode) -> Unit = ::applyAppLanguageMode,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            themeMode = preferences[THEME_MODE_KEY].enumOrDefault(AppSettings.Default.themeMode),
            artworkThemeEnabled = preferences[ARTWORK_THEME_ENABLED_KEY]
                ?: preferences[DYNAMIC_COLOR_ENABLED_KEY]
                ?: true,
            manualThemeSeedArgb = normalizeThemeSeedArgb(
                preferences[MANUAL_THEME_SEED_ARGB_KEY] ?: DEFAULT_MANUAL_THEME_SEED_ARGB,
            ),
            customThemeSeedArgbValues = normalizeCustomThemeSeedArgbValues(
                preferences[CUSTOM_THEME_SEED_ARGB_VALUES_KEY].toThemeSeedList(),
            ),
            languageMode = preferences[LANGUAGE_MODE_KEY].enumOrDefault(AppLanguageMode.System),
            audioFocusMode = preferences[AUDIO_FOCUS_MODE_KEY]
                .enumOrNull<AudioFocusMode>()
                ?: preferences[ALLOW_MIXED_PLAYBACK_KEY].toLegacyAudioFocusMode()
                ?: AppSettings.Default.audioFocusMode,
            pauseOnDisconnect = preferences[PAUSE_ON_DISCONNECT_KEY] ?: true,
            gaplessPlaybackEnabled = preferences[GAPLESS_PLAYBACK_ENABLED_KEY] ?: false,
            retryPlaybackOnFailure = preferences[RETRY_PLAYBACK_ON_FAILURE_KEY] ?: true,
            resumePlaybackAfterNetworkRecovery =
                preferences[RESUME_PLAYBACK_AFTER_NETWORK_RECOVERY_KEY] ?: true,
            keepScreenOnInPlayer = preferences[KEEP_SCREEN_ON_IN_PLAYER_KEY] ?: false,
            lyrics = LyricDisplaySettings(
                textAlignment = preferences[LYRIC_TEXT_ALIGNMENT_KEY]
                    .enumOrDefault(LyricTextAlignment.Left),
                primaryFontScalePercent = normalizeLyricFontScalePercent(
                    preferences[LYRIC_PRIMARY_FONT_SCALE_PERCENT_KEY]
                        ?: LyricDisplaySettings.Default.primaryFontScalePercent,
                ),
                primaryFontSizeSp = normalizeLyricPrimaryFontSizeSp(
                    preferences[LYRIC_PRIMARY_FONT_SIZE_SP_KEY]
                        ?: LyricDisplaySettings.Default.primaryFontSizeSp,
                ),
                secondaryFontScalePercent = normalizeLyricFontScalePercent(
                    preferences[LYRIC_SECONDARY_FONT_SCALE_PERCENT_KEY]
                        ?: LyricDisplaySettings.Default.secondaryFontScalePercent,
                ),
                secondaryFontSizeSp = normalizeLyricSecondaryFontSizeSp(
                    preferences[LYRIC_SECONDARY_FONT_SIZE_SP_KEY]
                        ?: LyricDisplaySettings.Default.secondaryFontSizeSp,
                ),
                showTranslation = preferences[LYRIC_SHOW_TRANSLATION_KEY] ?: true,
                wordLiftEnabled = preferences[LYRIC_WORD_LIFT_ENABLED_KEY] ?: true,
                blurEffectEnabled = preferences[LYRIC_BLUR_EFFECT_ENABLED_KEY] ?: true,
                perspectiveEffectEnabled =
                    preferences[LYRIC_PERSPECTIVE_EFFECT_ENABLED_KEY] ?: false,
                perspectiveAngleDegrees = normalizeLyricPerspectiveAngleDegrees(
                    preferences[LYRIC_PERSPECTIVE_ANGLE_DEGREES_KEY]
                        ?: LyricDisplaySettings.Default.perspectiveAngleDegrees,
                ),
                tapToSeekEnabled = preferences[LYRIC_TAP_TO_SEEK_ENABLED_KEY] ?: true,
                sourceMode = preferences[LYRIC_SOURCE_MODE_KEY]
                    .enumOrDefault(LyricSourceMode.Auto),
                sourcePriority = normalizeLyricSourcePriority(
                    preferences[LYRIC_SOURCE_PRIORITY_KEY].toLyricSourcePriority(),
                ),
                ignoreHeaderTags = preferences[LYRIC_IGNORE_HEADER_TAGS_KEY] ?: true,
                font = normalizeLyricFontSettings(
                    LyricFontSettings(
                        westernFont = preferences[LYRIC_WESTERN_FONT_KEY]
                            .enumOrDefault(LyricFontChoice.AppSans),
                        cjkFont = preferences[LYRIC_CJK_FONT_KEY]
                            .enumOrDefault(LyricFontChoice.AppCjk),
                        weight = preferences[LYRIC_FONT_WEIGHT_KEY]
                            ?: LyricFontSettings.Default.weight,
                        applyToLyricsPage = preferences[LYRIC_FONT_APPLY_PAGE_KEY] ?: true,
                        applyToFloatingLyrics = preferences[LYRIC_FONT_APPLY_FLOATING_KEY] ?: true,
                        applyToShareCard = preferences[LYRIC_FONT_APPLY_SHARE_KEY] ?: false,
                    )
                ),
            ),
            playbackAdvanced = normalizePlaybackAdvancedSettings(
                PlaybackAdvancedSettings(
                    crossfadeDurationMs = preferences[CROSSFADE_DURATION_MS_KEY] ?: 0,
                    replayGainMode = preferences[REPLAY_GAIN_MODE_KEY]
                        .enumOrDefault(ReplayGainMode.Off),
                    replayGainPreampTenthsDb = preferences[REPLAY_GAIN_PREAMP_TENTHS_DB_KEY] ?: 0,
                    resumePlaybackPosition = preferences[RESUME_PLAYBACK_POSITION_KEY] ?: true,
                    startupPlaybackMode = preferences[STARTUP_PLAYBACK_MODE_KEY]
                        .enumOrDefault(StartupPlaybackMode.Off),
                    previousButtonBehavior = preferences[PREVIOUS_BUTTON_BEHAVIOR_KEY]
                        .enumOrDefault(PreviousButtonBehavior.PreviousTrack),
                    playNextMode = preferences[PLAY_NEXT_MODE_KEY]
                        .enumOrDefault(PlayNextMode.FirstRequestedFirst),
                    shuffleStrategy = preferences[SHUFFLE_STRATEGY_KEY]
                        .enumOrDefault(ShuffleStrategy.QueueOrder),
                )
            ),
            playerInteraction = PlayerInteractionSettings(
                openPlayerOnPlay = preferences[OPEN_PLAYER_ON_PLAY_KEY] ?: false,
                immersiveAlbumCoverEnabled = preferences[PLAYER_IMMERSIVE_ALBUM_COVER_KEY] ?: false,
                coverSwipeEnabled = preferences[PLAYER_COVER_SWIPE_KEY] ?: true,
                showTotalDuration = preferences[PLAYER_SHOW_TOTAL_DURATION_KEY] ?: false,
                showAudioTechnicalInfo = preferences[PLAYER_SHOW_AUDIO_TECHNICAL_INFO_KEY] ?: false,
                desktopShortcutsEnabled = preferences[DESKTOP_SHORTCUTS_ENABLED_KEY] ?: true,
            ),
            metadataParsing = MetadataParsingSettings(
                artistSeparators = preferences[ARTIST_SEPARATORS_KEY]
                    ?: MetadataParsingSettings.Default.artistSeparators,
                artistProtectedNames = preferences[ARTIST_PROTECTED_NAMES_KEY].orEmpty(),
                genreSeparators = preferences[GENRE_SEPARATORS_KEY]
                    ?: MetadataParsingSettings.Default.genreSeparators,
                genreProtectedNames = preferences[GENRE_PROTECTED_NAMES_KEY].orEmpty(),
                ignoreTagCase = preferences[TAG_IGNORE_CASE_KEY] ?: false,
            ),
            downloadFinalization = DownloadFinalizationSettings(
                enrichMetadata = preferences[DOWNLOAD_FINALIZATION_ENABLED_KEY] ?: true,
                saveSidecarLyrics = preferences[SAVE_SIDECAR_LYRICS_KEY] ?: true,
            ),
            audioEffects = normalizeAudioEffectSettings(
                preferences[AUDIO_EFFECTS_CONFIG_JSON_KEY]
                    ?.let(::decodeAudioEffectSettings)
                    ?: AudioEffectSettings(
                    enabled = preferences[AUDIO_EFFECTS_ENABLED_KEY] ?: false,
                    eqBandGainsDb = preferences[EQ_BAND_GAINS_DB_KEY].toIntList(),
                    eqQHundredths = preferences[EQ_Q_HUNDREDTHS_KEY]
                        ?: AudioEffectSettings.Default.eqQHundredths,
                    bassDb = preferences[BASS_DB_KEY] ?: 0,
                    trebleDb = preferences[TREBLE_DB_KEY] ?: 0,
                    compressorEnabled = preferences[COMPRESSOR_ENABLED_KEY] ?: false,
                    compressorThresholdDb = preferences[COMPRESSOR_THRESHOLD_DB_KEY]
                        ?: AudioEffectSettings.Default.compressorThresholdDb,
                    compressorRatio = preferences[COMPRESSOR_RATIO_KEY]
                        ?: AudioEffectSettings.Default.compressorRatio,
                    compressorMakeupDb = preferences[COMPRESSOR_MAKEUP_DB_KEY] ?: 0,
                    stereoWidthPercent = preferences[STEREO_WIDTH_PERCENT_KEY]
                        ?: AudioEffectSettings.Default.stereoWidthPercent,
                    reverbPreset = preferences[REVERB_PRESET_KEY]
                        .enumOrDefault(ReverbPreset.None),
                )
            ),
            lyricOutput = LyricOutputSettings(
                floatingLyricsEnabled = preferences[FLOATING_LYRICS_ENABLED_KEY] ?: false,
                notificationLyricsEnabled = preferences[NOTIFICATION_LYRICS_ENABLED_KEY] ?: false,
                bluetoothLyricsEnabled = preferences[BLUETOOTH_LYRICS_ENABLED_KEY] ?: false,
                lyriconEnabled = preferences[LYRICON_ENABLED_KEY] ?: false,
                superLyricEnabled = preferences[SUPER_LYRIC_ENABLED_KEY] ?: false,
                lyricGetterEnabled = preferences[LYRIC_GETTER_ENABLED_KEY] ?: false,
                flymeStatusLyricsEnabled = preferences[FLYME_STATUS_LYRICS_ENABLED_KEY] ?: false,
                colorOsLockScreenLyricsEnabled =
                    preferences[COLOR_OS_LOCK_SCREEN_LYRICS_ENABLED_KEY] ?: false,
                secondaryContent = preferences[LYRIC_OUTPUT_SECONDARY_CONTENT_KEY]
                    .enumOrDefault(SecondaryLyricContent.Translation),
            ),
            backup = SettingsBackupSettings(
                selection = SettingsBackupSelection(
                    appearance = preferences[BACKUP_APPEARANCE_KEY] ?: true,
                    playback = preferences[BACKUP_PLAYBACK_KEY] ?: true,
                    lyrics = preferences[BACKUP_LYRICS_KEY] ?: true,
                    libraryAndMetadata = preferences[BACKUP_LIBRARY_KEY] ?: true,
                    networkAndCache = preferences[BACKUP_NETWORK_KEY] ?: true,
                ),
                schedule = preferences[BACKUP_SCHEDULE_KEY].enumOrDefault(BackupSchedule.Off),
                webDavAccountId = preferences[BACKUP_WEBDAV_ACCOUNT_ID_KEY],
                remoteDirectory = preferences[BACKUP_REMOTE_DIRECTORY_KEY]
                    ?: SettingsBackupSettings.Default.remoteDirectory,
            ),
            autoScanMode = preferences[AUTO_SCAN_MODE_KEY].enumOrDefault(AutoScanMode.Off),
            scanSubdirectories = preferences[SCAN_SUBDIRECTORIES_KEY]
                ?: preferences[LOCAL_SCAN_SUBDIRECTORIES_KEY]
                ?: preferences[WEB_DAV_SCAN_SUBDIRECTORIES_KEY]
                ?: true,
            webDavMetadataScanMode = preferences[WEB_DAV_METADATA_SCAN_MODE_KEY]
                .enumOrDefault(MetadataScanMode.Standard),
            minimumAudioDurationMs = normalizeMinimumAudioDurationMs(
                preferences[MINIMUM_AUDIO_DURATION_MS_KEY]
                    ?: preferences[IGNORE_SHORT_AUDIO_KEY].toLegacyMinimumDurationMs()
                    ?: AppSettings.Default.minimumAudioDurationMs,
            ),
            missingFilePolicy = preferences[MISSING_FILE_POLICY_KEY]
                .enumOrDefault(MissingFilePolicy.MarkUnavailable),
            allowMeteredNetworkUsage = preferences[ALLOW_METERED_NETWORK_USAGE_KEY] ?: false,
            networkRetryCount = normalizeNetworkRetryCount(
                preferences[NETWORK_RETRY_COUNT_KEY] ?: AppSettings.Default.networkRetryCount,
            ),
            connectionTimeoutSeconds = normalizeConnectionTimeoutSeconds(
                preferences[CONNECTION_TIMEOUT_SECONDS_KEY]
                    ?: AppSettings.Default.connectionTimeoutSeconds,
            ),
            audioPreloadBytes = normalizeAudioPreloadBytes(
                preferences[AUDIO_PRELOAD_BYTES_KEY] ?: AppSettings.Default.audioPreloadBytes,
            ),
            listenAndCacheEnabled = preferences[LISTEN_AND_CACHE_ENABLED_KEY] ?: true,
            audioCacheLimitBytes = normalizeAudioCacheLimitBytes(
                preferences[AUDIO_CACHE_LIMIT_BYTES_KEY] ?: AppSettings.Default.audioCacheLimitBytes,
            ),
            imageCacheLimitBytes = normalizeImageCacheLimitBytes(
                preferences[IMAGE_CACHE_LIMIT_BYTES_KEY] ?: AppSettings.Default.imageCacheLimitBytes,
            ),
        )
    }

    override suspend fun setThemeMode(mode: AppThemeMode) = set(THEME_MODE_KEY, mode.name)

    override suspend fun setArtworkThemeEnabled(enabled: Boolean) =
        set(ARTWORK_THEME_ENABLED_KEY, enabled)

    override suspend fun setManualThemeSeedArgb(argb: Long) =
        set(MANUAL_THEME_SEED_ARGB_KEY, normalizeThemeSeedArgb(argb))

    override suspend fun setCustomThemeSeedArgbValues(argbValues: List<Long>) =
        set(
            CUSTOM_THEME_SEED_ARGB_VALUES_KEY,
            normalizeCustomThemeSeedArgbValues(argbValues).joinToString(",") { value ->
                value.toString(16).uppercase()
            },
        )

    override suspend fun setLanguageMode(mode: AppLanguageMode) {
        set(LANGUAGE_MODE_KEY, mode.name)
        applyLanguageMode(mode)
    }

    override suspend fun setAudioFocusMode(mode: AudioFocusMode) {
        dataStore.edit { preferences ->
            preferences[AUDIO_FOCUS_MODE_KEY] = mode.name
            preferences.remove(ALLOW_MIXED_PLAYBACK_KEY)
        }
    }

    override suspend fun setPauseOnDisconnect(enabled: Boolean) =
        set(PAUSE_ON_DISCONNECT_KEY, enabled)

    override suspend fun setGaplessPlaybackEnabled(enabled: Boolean) =
        set(GAPLESS_PLAYBACK_ENABLED_KEY, enabled)

    override suspend fun setRetryPlaybackOnFailure(enabled: Boolean) =
        set(RETRY_PLAYBACK_ON_FAILURE_KEY, enabled)

    override suspend fun setResumePlaybackAfterNetworkRecovery(enabled: Boolean) =
        set(RESUME_PLAYBACK_AFTER_NETWORK_RECOVERY_KEY, enabled)

    override suspend fun setKeepScreenOnInPlayer(enabled: Boolean) =
        set(KEEP_SCREEN_ON_IN_PLAYER_KEY, enabled)

    override suspend fun setLyricTextAlignment(alignment: LyricTextAlignment) =
        set(LYRIC_TEXT_ALIGNMENT_KEY, alignment.name)

    override suspend fun setLyricPrimaryFontScalePercent(value: Int) =
        set(LYRIC_PRIMARY_FONT_SCALE_PERCENT_KEY, normalizeLyricFontScalePercent(value))

    override suspend fun setLyricPrimaryFontSizeSp(value: Int) =
        set(LYRIC_PRIMARY_FONT_SIZE_SP_KEY, normalizeLyricPrimaryFontSizeSp(value))

    override suspend fun setLyricSecondaryFontScalePercent(value: Int) =
        set(LYRIC_SECONDARY_FONT_SCALE_PERCENT_KEY, normalizeLyricFontScalePercent(value))

    override suspend fun setLyricSecondaryFontSizeSp(value: Int) =
        set(LYRIC_SECONDARY_FONT_SIZE_SP_KEY, normalizeLyricSecondaryFontSizeSp(value))

    override suspend fun setLyricTranslationVisible(visible: Boolean) =
        set(LYRIC_SHOW_TRANSLATION_KEY, visible)

    override suspend fun setLyricWordLiftEnabled(enabled: Boolean) =
        set(LYRIC_WORD_LIFT_ENABLED_KEY, enabled)

    override suspend fun setLyricBlurEffectEnabled(enabled: Boolean) =
        set(LYRIC_BLUR_EFFECT_ENABLED_KEY, enabled)

    override suspend fun setLyricPerspectiveEffectEnabled(enabled: Boolean) =
        set(LYRIC_PERSPECTIVE_EFFECT_ENABLED_KEY, enabled)

    override suspend fun setLyricPerspectiveAngleDegrees(value: Int) =
        set(LYRIC_PERSPECTIVE_ANGLE_DEGREES_KEY, normalizeLyricPerspectiveAngleDegrees(value))

    override suspend fun setLyricTapToSeekEnabled(enabled: Boolean) =
        set(LYRIC_TAP_TO_SEEK_ENABLED_KEY, enabled)

    override suspend fun setLyricSourceMode(mode: LyricSourceMode) =
        set(LYRIC_SOURCE_MODE_KEY, mode.name)

    override suspend fun setLyricSourcePriority(priority: List<LyricSourceKind>) =
        set(
            LYRIC_SOURCE_PRIORITY_KEY,
            normalizeLyricSourcePriority(priority).joinToString(",", transform = LyricSourceKind::name),
        )

    override suspend fun setIgnoreLyricHeaderTags(enabled: Boolean) =
        set(LYRIC_IGNORE_HEADER_TAGS_KEY, enabled)

    override suspend fun setLyricFontSettings(settings: LyricFontSettings) {
        val normalized = normalizeLyricFontSettings(settings)
        dataStore.edit { preferences ->
            preferences[LYRIC_WESTERN_FONT_KEY] = normalized.westernFont.name
            preferences[LYRIC_CJK_FONT_KEY] = normalized.cjkFont.name
            preferences[LYRIC_FONT_WEIGHT_KEY] = normalized.weight
            preferences[LYRIC_FONT_APPLY_PAGE_KEY] = normalized.applyToLyricsPage
            preferences[LYRIC_FONT_APPLY_FLOATING_KEY] = normalized.applyToFloatingLyrics
            preferences[LYRIC_FONT_APPLY_SHARE_KEY] = normalized.applyToShareCard
        }
    }

    override suspend fun setPlaybackAdvancedSettings(settings: PlaybackAdvancedSettings) {
        val normalized = normalizePlaybackAdvancedSettings(settings)
        dataStore.edit { preferences ->
            preferences[CROSSFADE_DURATION_MS_KEY] = normalized.crossfadeDurationMs
            preferences[REPLAY_GAIN_MODE_KEY] = normalized.replayGainMode.name
            preferences[REPLAY_GAIN_PREAMP_TENTHS_DB_KEY] = normalized.replayGainPreampTenthsDb
            preferences[RESUME_PLAYBACK_POSITION_KEY] = normalized.resumePlaybackPosition
            preferences[STARTUP_PLAYBACK_MODE_KEY] = normalized.startupPlaybackMode.name
            preferences[PREVIOUS_BUTTON_BEHAVIOR_KEY] = normalized.previousButtonBehavior.name
            preferences[PLAY_NEXT_MODE_KEY] = normalized.playNextMode.name
            preferences[SHUFFLE_STRATEGY_KEY] = normalized.shuffleStrategy.name
        }
    }

    override suspend fun setPlayerInteractionSettings(settings: PlayerInteractionSettings) {
        dataStore.edit { preferences ->
            preferences[OPEN_PLAYER_ON_PLAY_KEY] = settings.openPlayerOnPlay
            preferences[PLAYER_IMMERSIVE_ALBUM_COVER_KEY] = settings.immersiveAlbumCoverEnabled
            preferences[PLAYER_COVER_SWIPE_KEY] = settings.coverSwipeEnabled
            preferences[PLAYER_SHOW_TOTAL_DURATION_KEY] = settings.showTotalDuration
            preferences[PLAYER_SHOW_AUDIO_TECHNICAL_INFO_KEY] = settings.showAudioTechnicalInfo
            preferences[DESKTOP_SHORTCUTS_ENABLED_KEY] = settings.desktopShortcutsEnabled
        }
    }

    override suspend fun setDownloadFinalizationSettings(settings: DownloadFinalizationSettings) {
        dataStore.edit { preferences ->
            preferences[DOWNLOAD_FINALIZATION_ENABLED_KEY] = settings.enrichMetadata
            preferences[SAVE_SIDECAR_LYRICS_KEY] = settings.saveSidecarLyrics
        }
    }

    override suspend fun setMetadataParsingSettings(settings: MetadataParsingSettings) {
        dataStore.edit { preferences ->
            preferences[ARTIST_SEPARATORS_KEY] = settings.artistSeparators
            preferences[ARTIST_PROTECTED_NAMES_KEY] = settings.artistProtectedNames
            preferences[GENRE_SEPARATORS_KEY] = settings.genreSeparators
            preferences[GENRE_PROTECTED_NAMES_KEY] = settings.genreProtectedNames
            preferences[TAG_IGNORE_CASE_KEY] = settings.ignoreTagCase
        }
    }

    override suspend fun setAudioEffectSettings(settings: AudioEffectSettings) {
        val normalized = normalizeAudioEffectSettings(settings)
        dataStore.edit { preferences ->
            preferences[AUDIO_EFFECTS_ENABLED_KEY] = normalized.enabled
            preferences[EQ_BAND_GAINS_DB_KEY] = normalized.eqBandGainsDb.joinToString(",")
            preferences[EQ_Q_HUNDREDTHS_KEY] = normalized.eqQHundredths
            preferences[BASS_DB_KEY] = normalized.bassDb
            preferences[TREBLE_DB_KEY] = normalized.trebleDb
            preferences[COMPRESSOR_ENABLED_KEY] = normalized.compressorEnabled
            preferences[COMPRESSOR_THRESHOLD_DB_KEY] = normalized.compressorThresholdDb
            preferences[COMPRESSOR_RATIO_KEY] = normalized.compressorRatio
            preferences[COMPRESSOR_MAKEUP_DB_KEY] = normalized.compressorMakeupDb
            preferences[STEREO_WIDTH_PERCENT_KEY] = normalized.stereoWidthPercent
            preferences[REVERB_PRESET_KEY] = normalized.reverbPreset.name
            preferences[AUDIO_EFFECTS_CONFIG_JSON_KEY] =
                AUDIO_EFFECTS_JSON.encodeToString(normalized)
        }
    }

    override suspend fun setLyricOutputSettings(settings: LyricOutputSettings) {
        dataStore.edit { preferences ->
            preferences[FLOATING_LYRICS_ENABLED_KEY] = settings.floatingLyricsEnabled
            preferences[NOTIFICATION_LYRICS_ENABLED_KEY] = settings.notificationLyricsEnabled
            preferences[BLUETOOTH_LYRICS_ENABLED_KEY] = settings.bluetoothLyricsEnabled
            preferences[LYRICON_ENABLED_KEY] = settings.lyriconEnabled
            preferences[SUPER_LYRIC_ENABLED_KEY] = settings.superLyricEnabled
            preferences[LYRIC_GETTER_ENABLED_KEY] = settings.lyricGetterEnabled
            preferences[FLYME_STATUS_LYRICS_ENABLED_KEY] = settings.flymeStatusLyricsEnabled
            preferences[COLOR_OS_LOCK_SCREEN_LYRICS_ENABLED_KEY] =
                settings.colorOsLockScreenLyricsEnabled
            preferences[LYRIC_OUTPUT_SECONDARY_CONTENT_KEY] = settings.secondaryContent.name
        }
    }

    override suspend fun setBackupSettings(settings: SettingsBackupSettings) {
        dataStore.edit { preferences ->
            preferences[BACKUP_APPEARANCE_KEY] = settings.selection.appearance
            preferences[BACKUP_PLAYBACK_KEY] = settings.selection.playback
            preferences[BACKUP_LYRICS_KEY] = settings.selection.lyrics
            preferences[BACKUP_LIBRARY_KEY] = settings.selection.libraryAndMetadata
            preferences[BACKUP_NETWORK_KEY] = settings.selection.networkAndCache
            preferences[BACKUP_SCHEDULE_KEY] = settings.schedule.name
            settings.webDavAccountId?.let { preferences[BACKUP_WEBDAV_ACCOUNT_ID_KEY] = it }
                ?: preferences.remove(BACKUP_WEBDAV_ACCOUNT_ID_KEY)
            preferences[BACKUP_REMOTE_DIRECTORY_KEY] = settings.remoteDirectory
        }
    }

    override suspend fun replaceSettings(settings: AppSettings) {
        resetToDefaults()
        setThemeMode(settings.themeMode)
        setArtworkThemeEnabled(settings.artworkThemeEnabled)
        setManualThemeSeedArgb(settings.manualThemeSeedArgb)
        setCustomThemeSeedArgbValues(settings.customThemeSeedArgbValues)
        setLanguageMode(settings.languageMode)
        setAudioFocusMode(settings.audioFocusMode)
        setPauseOnDisconnect(settings.pauseOnDisconnect)
        setGaplessPlaybackEnabled(settings.gaplessPlaybackEnabled)
        setRetryPlaybackOnFailure(settings.retryPlaybackOnFailure)
        setResumePlaybackAfterNetworkRecovery(settings.resumePlaybackAfterNetworkRecovery)
        setKeepScreenOnInPlayer(settings.keepScreenOnInPlayer)
        setLyricTextAlignment(settings.lyrics.textAlignment)
        setLyricPrimaryFontScalePercent(settings.lyrics.primaryFontScalePercent)
        setLyricPrimaryFontSizeSp(settings.lyrics.primaryFontSizeSp)
        setLyricSecondaryFontScalePercent(settings.lyrics.secondaryFontScalePercent)
        setLyricSecondaryFontSizeSp(settings.lyrics.secondaryFontSizeSp)
        setLyricTranslationVisible(settings.lyrics.showTranslation)
        setLyricWordLiftEnabled(settings.lyrics.wordLiftEnabled)
        setLyricBlurEffectEnabled(settings.lyrics.blurEffectEnabled)
        setLyricPerspectiveEffectEnabled(settings.lyrics.perspectiveEffectEnabled)
        setLyricPerspectiveAngleDegrees(settings.lyrics.perspectiveAngleDegrees)
        setLyricTapToSeekEnabled(settings.lyrics.tapToSeekEnabled)
        setLyricSourceMode(settings.lyrics.sourceMode)
        setLyricSourcePriority(settings.lyrics.sourcePriority)
        setIgnoreLyricHeaderTags(settings.lyrics.ignoreHeaderTags)
        setLyricFontSettings(settings.lyrics.font)
        setPlaybackAdvancedSettings(settings.playbackAdvanced)
        setPlayerInteractionSettings(settings.playerInteraction)
        setMetadataParsingSettings(settings.metadataParsing)
        setDownloadFinalizationSettings(settings.downloadFinalization)
        setAudioEffectSettings(settings.audioEffects)
        setLyricOutputSettings(settings.lyricOutput)
        setBackupSettings(settings.backup)
        setAutoScanMode(settings.autoScanMode)
        setScanSubdirectories(settings.scanSubdirectories)
        setWebDavMetadataScanMode(settings.webDavMetadataScanMode)
        setMinimumAudioDurationMs(settings.minimumAudioDurationMs)
        setMissingFilePolicy(settings.missingFilePolicy)
        setAllowMeteredNetworkUsage(settings.allowMeteredNetworkUsage)
        setNetworkRetryCount(settings.networkRetryCount)
        setConnectionTimeoutSeconds(settings.connectionTimeoutSeconds)
        setAudioPreloadBytes(settings.audioPreloadBytes)
        setListenAndCacheEnabled(settings.listenAndCacheEnabled)
        setAudioCacheLimitBytes(settings.audioCacheLimitBytes)
        setImageCacheLimitBytes(settings.imageCacheLimitBytes)
    }

    override suspend fun setAutoScanMode(mode: AutoScanMode) = set(AUTO_SCAN_MODE_KEY, mode.name)

    override suspend fun setScanSubdirectories(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SCAN_SUBDIRECTORIES_KEY] = enabled
            preferences.remove(LOCAL_SCAN_SUBDIRECTORIES_KEY)
            preferences.remove(WEB_DAV_SCAN_SUBDIRECTORIES_KEY)
        }
    }

    override suspend fun setWebDavMetadataScanMode(mode: MetadataScanMode) =
        set(WEB_DAV_METADATA_SCAN_MODE_KEY, mode.name)

    override suspend fun setMinimumAudioDurationMs(value: Long) {
        dataStore.edit { preferences ->
            preferences[MINIMUM_AUDIO_DURATION_MS_KEY] = normalizeMinimumAudioDurationMs(value)
            preferences.remove(IGNORE_SHORT_AUDIO_KEY)
        }
    }

    override suspend fun setMissingFilePolicy(policy: MissingFilePolicy) =
        set(MISSING_FILE_POLICY_KEY, policy.name)

    override suspend fun setAllowMeteredNetworkUsage(enabled: Boolean) =
        set(ALLOW_METERED_NETWORK_USAGE_KEY, enabled)

    override suspend fun setNetworkRetryCount(value: Int) =
        set(NETWORK_RETRY_COUNT_KEY, normalizeNetworkRetryCount(value))

    override suspend fun setConnectionTimeoutSeconds(value: Int) =
        set(CONNECTION_TIMEOUT_SECONDS_KEY, normalizeConnectionTimeoutSeconds(value))

    override suspend fun setAudioPreloadBytes(bytes: Long) =
        set(AUDIO_PRELOAD_BYTES_KEY, normalizeAudioPreloadBytes(bytes))

    override suspend fun setListenAndCacheEnabled(enabled: Boolean) =
        set(LISTEN_AND_CACHE_ENABLED_KEY, enabled)

    override suspend fun setAudioCacheLimitBytes(bytes: Long) =
        set(AUDIO_CACHE_LIMIT_BYTES_KEY, normalizeAudioCacheLimitBytes(bytes))

    override suspend fun setImageCacheLimitBytes(bytes: Long) =
        set(IMAGE_CACHE_LIMIT_BYTES_KEY, normalizeImageCacheLimitBytes(bytes))

    override suspend fun resetToDefaults() {
        dataStore.edit { preferences ->
            SETTINGS_KEYS.forEach(preferences::removeUntyped)
        }
        applyLanguageMode(AppSettings.Default.languageMode)
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { preferences -> preferences[key] = value }
    }
}

@Suppress("UNCHECKED_CAST")
private fun MutablePreferences.removeUntyped(key: Preferences.Key<*>) {
    remove(key as Preferences.Key<Any>)
}

private inline fun <reified T : Enum<T>> String?.enumOrNull(): T? {
    return enumValues<T>().firstOrNull { value -> value.name == this }
}

private inline fun <reified T : Enum<T>> String?.enumOrDefault(default: T): T {
    return enumOrNull() ?: default
}

private fun String?.toLyricSourcePriority(): List<LyricSourceKind> {
    return this
        ?.split(',')
        ?.mapNotNull { name -> name.enumOrNull<LyricSourceKind>() }
        .orEmpty()
}

private fun String?.toIntList(): List<Int> = this
    ?.split(',')
    ?.mapNotNull(String::toIntOrNull)
    .orEmpty()

private fun Boolean?.toLegacyAudioFocusMode(): AudioFocusMode? = when (this) {
    true -> AudioFocusMode.Mix
    false -> AudioFocusMode.Pause
    null -> null
}

private fun Boolean?.toLegacyMinimumDurationMs(): Long? = when (this) {
    true -> 30_000L
    false -> 0L
    null -> null
}

private fun String?.toThemeSeedList(): List<Long> {
    return this
        ?.split(',')
        ?.mapNotNull { value -> value.toLongOrNull(radix = 16) }
        .orEmpty()
}

internal val THEME_MODE_KEY = stringPreferencesKey("settings.themeMode")
internal val ARTWORK_THEME_ENABLED_KEY = booleanPreferencesKey("settings.artworkThemeEnabled")
internal val MANUAL_THEME_SEED_ARGB_KEY = longPreferencesKey("settings.manualThemeSeedArgb")
internal val CUSTOM_THEME_SEED_ARGB_VALUES_KEY =
    stringPreferencesKey("settings.customThemeSeedArgbValues")
// Read-only migration input from the former Android system-wallpaper setting.
internal val DYNAMIC_COLOR_ENABLED_KEY = booleanPreferencesKey("settings.dynamicColorEnabled")
internal val LANGUAGE_MODE_KEY = stringPreferencesKey("settings.languageMode")
internal val AUDIO_FOCUS_MODE_KEY = stringPreferencesKey("settings.audioFocusMode")
internal val PAUSE_ON_DISCONNECT_KEY = booleanPreferencesKey("settings.pauseOnDisconnect")
internal val GAPLESS_PLAYBACK_ENABLED_KEY = booleanPreferencesKey("settings.gaplessPlaybackEnabled")
internal val RETRY_PLAYBACK_ON_FAILURE_KEY = booleanPreferencesKey("settings.retryPlaybackOnFailure")
internal val RESUME_PLAYBACK_AFTER_NETWORK_RECOVERY_KEY =
    booleanPreferencesKey("settings.resumePlaybackAfterNetworkRecovery")
internal val KEEP_SCREEN_ON_IN_PLAYER_KEY = booleanPreferencesKey("settings.keepScreenOnInPlayer")
internal val LYRIC_TEXT_ALIGNMENT_KEY = stringPreferencesKey("settings.lyrics.textAlignment")
internal val LYRIC_PRIMARY_FONT_SCALE_PERCENT_KEY =
    intPreferencesKey("settings.lyrics.primaryFontScalePercent")
internal val LYRIC_PRIMARY_FONT_SIZE_SP_KEY = intPreferencesKey("settings.lyrics.primaryFontSizeSp")
internal val LYRIC_SECONDARY_FONT_SCALE_PERCENT_KEY =
    intPreferencesKey("settings.lyrics.secondaryFontScalePercent")
internal val LYRIC_SECONDARY_FONT_SIZE_SP_KEY =
    intPreferencesKey("settings.lyrics.secondaryFontSizeSp")
internal val LYRIC_SHOW_TRANSLATION_KEY = booleanPreferencesKey("settings.lyrics.showTranslation")
internal val LYRIC_WORD_LIFT_ENABLED_KEY = booleanPreferencesKey("settings.lyrics.wordLiftEnabled")
internal val LYRIC_BLUR_EFFECT_ENABLED_KEY =
    booleanPreferencesKey("settings.lyrics.blurEffectEnabled")
internal val LYRIC_PERSPECTIVE_EFFECT_ENABLED_KEY =
    booleanPreferencesKey("settings.lyrics.perspectiveEffectEnabled")
internal val LYRIC_PERSPECTIVE_ANGLE_DEGREES_KEY =
    intPreferencesKey("settings.lyrics.perspectiveAngleDegrees")
internal val LYRIC_TAP_TO_SEEK_ENABLED_KEY =
    booleanPreferencesKey("settings.lyrics.tapToSeekEnabled")
internal val LYRIC_SOURCE_MODE_KEY = stringPreferencesKey("settings.lyrics.sourceMode")
internal val LYRIC_SOURCE_PRIORITY_KEY = stringPreferencesKey("settings.lyrics.sourcePriority")
internal val LYRIC_IGNORE_HEADER_TAGS_KEY =
    booleanPreferencesKey("settings.lyrics.ignoreHeaderTags")
internal val LYRIC_WESTERN_FONT_KEY = stringPreferencesKey("settings.lyrics.font.western")
internal val LYRIC_CJK_FONT_KEY = stringPreferencesKey("settings.lyrics.font.cjk")
internal val LYRIC_FONT_WEIGHT_KEY = intPreferencesKey("settings.lyrics.font.weight")
internal val LYRIC_FONT_APPLY_PAGE_KEY =
    booleanPreferencesKey("settings.lyrics.font.applyToPage")
internal val LYRIC_FONT_APPLY_FLOATING_KEY =
    booleanPreferencesKey("settings.lyrics.font.applyToFloating")
internal val LYRIC_FONT_APPLY_SHARE_KEY =
    booleanPreferencesKey("settings.lyrics.font.applyToShare")
internal val CROSSFADE_DURATION_MS_KEY = intPreferencesKey("settings.playback.crossfadeDurationMs")
internal val REPLAY_GAIN_MODE_KEY = stringPreferencesKey("settings.playback.replayGainMode")
internal val REPLAY_GAIN_PREAMP_TENTHS_DB_KEY =
    intPreferencesKey("settings.playback.replayGainPreampTenthsDb")
internal val RESUME_PLAYBACK_POSITION_KEY =
    booleanPreferencesKey("settings.playback.resumePosition")
internal val STARTUP_PLAYBACK_MODE_KEY = stringPreferencesKey("settings.playback.startupMode")
internal val PREVIOUS_BUTTON_BEHAVIOR_KEY =
    stringPreferencesKey("settings.playback.previousButtonBehavior")
internal val PLAY_NEXT_MODE_KEY = stringPreferencesKey("settings.playback.playNextMode")
internal val SHUFFLE_STRATEGY_KEY = stringPreferencesKey("settings.playback.shuffleStrategy")
internal val OPEN_PLAYER_ON_PLAY_KEY = booleanPreferencesKey("settings.player.openOnPlay")
internal val PLAYER_IMMERSIVE_ALBUM_COVER_KEY =
    booleanPreferencesKey("settings.player.immersiveAlbumCover")
internal val PLAYER_COVER_SWIPE_KEY = booleanPreferencesKey("settings.player.coverSwipe")
internal val PLAYER_SHOW_TOTAL_DURATION_KEY = booleanPreferencesKey("settings.player.showTotalDuration")
internal val PLAYER_SHOW_AUDIO_TECHNICAL_INFO_KEY =
    booleanPreferencesKey("settings.player.showAudioTechnicalInfo")
internal val DESKTOP_SHORTCUTS_ENABLED_KEY = booleanPreferencesKey("settings.player.desktopShortcuts")
internal val ARTIST_SEPARATORS_KEY = stringPreferencesKey("settings.metadata.artistSeparators")
internal val ARTIST_PROTECTED_NAMES_KEY = stringPreferencesKey("settings.metadata.artistProtectedNames")
internal val GENRE_SEPARATORS_KEY = stringPreferencesKey("settings.metadata.genreSeparators")
internal val GENRE_PROTECTED_NAMES_KEY = stringPreferencesKey("settings.metadata.genreProtectedNames")
internal val TAG_IGNORE_CASE_KEY = booleanPreferencesKey("settings.metadata.ignoreTagCase")
internal val DOWNLOAD_FINALIZATION_ENABLED_KEY =
    booleanPreferencesKey("settings.downloadFinalization.enrichMetadata")
internal val SAVE_SIDECAR_LYRICS_KEY =
    booleanPreferencesKey("settings.downloadFinalization.saveSidecarLyrics")
internal val AUDIO_EFFECTS_ENABLED_KEY = booleanPreferencesKey("settings.audioEffects.enabled")
internal val AUDIO_EFFECTS_CONFIG_JSON_KEY =
    stringPreferencesKey("settings.audioEffects.configJson")
internal val EQ_BAND_GAINS_DB_KEY = stringPreferencesKey("settings.audioEffects.eqBandGainsDb")
internal val EQ_Q_HUNDREDTHS_KEY = intPreferencesKey("settings.audioEffects.eqQHundredths")
internal val BASS_DB_KEY = intPreferencesKey("settings.audioEffects.bassDb")
internal val TREBLE_DB_KEY = intPreferencesKey("settings.audioEffects.trebleDb")
internal val COMPRESSOR_ENABLED_KEY = booleanPreferencesKey("settings.audioEffects.compressorEnabled")
internal val COMPRESSOR_THRESHOLD_DB_KEY = intPreferencesKey("settings.audioEffects.compressorThresholdDb")
internal val COMPRESSOR_RATIO_KEY = intPreferencesKey("settings.audioEffects.compressorRatio")
internal val COMPRESSOR_MAKEUP_DB_KEY = intPreferencesKey("settings.audioEffects.compressorMakeupDb")
internal val STEREO_WIDTH_PERCENT_KEY = intPreferencesKey("settings.audioEffects.stereoWidthPercent")
internal val REVERB_PRESET_KEY = stringPreferencesKey("settings.audioEffects.reverbPreset")
internal val FLOATING_LYRICS_ENABLED_KEY = booleanPreferencesKey("settings.lyricOutput.floating")
internal val NOTIFICATION_LYRICS_ENABLED_KEY = booleanPreferencesKey("settings.lyricOutput.notification")
internal val BLUETOOTH_LYRICS_ENABLED_KEY = booleanPreferencesKey("settings.lyricOutput.bluetooth")
internal val LYRICON_ENABLED_KEY = booleanPreferencesKey("settings.lyricOutput.lyricon")
internal val SUPER_LYRIC_ENABLED_KEY = booleanPreferencesKey("settings.lyricOutput.superLyric")
internal val LYRIC_GETTER_ENABLED_KEY = booleanPreferencesKey("settings.lyricOutput.lyricGetter")
internal val FLYME_STATUS_LYRICS_ENABLED_KEY = booleanPreferencesKey("settings.lyricOutput.flyme")
internal val COLOR_OS_LOCK_SCREEN_LYRICS_ENABLED_KEY =
    booleanPreferencesKey("settings.lyricOutput.colorOsLockScreen")
internal val LYRIC_OUTPUT_SECONDARY_CONTENT_KEY =
    stringPreferencesKey("settings.lyricOutput.secondaryContent")
internal val BACKUP_APPEARANCE_KEY = booleanPreferencesKey("settings.backup.appearance")
internal val BACKUP_PLAYBACK_KEY = booleanPreferencesKey("settings.backup.playback")
internal val BACKUP_LYRICS_KEY = booleanPreferencesKey("settings.backup.lyrics")
internal val BACKUP_LIBRARY_KEY = booleanPreferencesKey("settings.backup.library")
internal val BACKUP_NETWORK_KEY = booleanPreferencesKey("settings.backup.network")
internal val BACKUP_SCHEDULE_KEY = stringPreferencesKey("settings.backup.schedule")
internal val BACKUP_WEBDAV_ACCOUNT_ID_KEY = longPreferencesKey("settings.backup.webDavAccountId")
internal val BACKUP_REMOTE_DIRECTORY_KEY = stringPreferencesKey("settings.backup.remoteDirectory")
internal val AUTO_SCAN_MODE_KEY = stringPreferencesKey("settings.autoScanMode")
internal val SCAN_SUBDIRECTORIES_KEY = booleanPreferencesKey("settings.scanSubdirectories")
internal val WEB_DAV_METADATA_SCAN_MODE_KEY =
    stringPreferencesKey("settings.webDavMetadataScanMode")
internal val WEB_DAV_METADATA_SCAN_MODE_MIGRATED_KEY =
    booleanPreferencesKey("settings.webDavMetadataScanModeMigrated")
internal val MINIMUM_AUDIO_DURATION_MS_KEY = longPreferencesKey("settings.minimumAudioDurationMs")
internal val MISSING_FILE_POLICY_KEY = stringPreferencesKey("settings.missingFilePolicy")
internal val ALLOW_METERED_NETWORK_USAGE_KEY =
    booleanPreferencesKey("settings.allowMeteredNetworkUsage")
internal val NETWORK_RETRY_COUNT_KEY = intPreferencesKey("settings.networkRetryCount")
internal val CONNECTION_TIMEOUT_SECONDS_KEY = intPreferencesKey("settings.connectionTimeoutSeconds")
internal val AUDIO_PRELOAD_BYTES_KEY = longPreferencesKey("settings.audioPreloadBytes")
internal val LISTEN_AND_CACHE_ENABLED_KEY =
    booleanPreferencesKey("settings.listenAndCacheEnabled")
internal val AUDIO_CACHE_LIMIT_BYTES_KEY = longPreferencesKey("settings.audioCacheLimitBytes")
internal val IMAGE_CACHE_LIMIT_BYTES_KEY = longPreferencesKey("settings.imageCacheLimitBytes")

internal val ALLOW_MIXED_PLAYBACK_KEY = booleanPreferencesKey("settings.allowMixedPlayback")
internal val LOCAL_MUSIC_ENABLED_KEY = booleanPreferencesKey("settings.localMusicEnabled")
internal val LOCAL_SCAN_SUBDIRECTORIES_KEY = booleanPreferencesKey("settings.localScanSubdirectories")
internal val IGNORE_SHORT_AUDIO_KEY = booleanPreferencesKey("settings.ignoreShortAudio")
internal val WEB_DAV_ENABLED_KEY = booleanPreferencesKey("settings.webDavEnabled")
internal val WEB_DAV_SCAN_SUBDIRECTORIES_KEY = booleanPreferencesKey("settings.webDavScanSubdirectories")
internal val WEB_DAV_ROOT_PATHS_KEY = stringPreferencesKey("settings.webDavRootPaths")

private val SETTINGS_KEYS = setOf(
    THEME_MODE_KEY,
    ARTWORK_THEME_ENABLED_KEY,
    MANUAL_THEME_SEED_ARGB_KEY,
    CUSTOM_THEME_SEED_ARGB_VALUES_KEY,
    DYNAMIC_COLOR_ENABLED_KEY,
    LANGUAGE_MODE_KEY,
    AUDIO_FOCUS_MODE_KEY,
    PAUSE_ON_DISCONNECT_KEY,
    GAPLESS_PLAYBACK_ENABLED_KEY,
    RETRY_PLAYBACK_ON_FAILURE_KEY,
    RESUME_PLAYBACK_AFTER_NETWORK_RECOVERY_KEY,
    KEEP_SCREEN_ON_IN_PLAYER_KEY,
    LYRIC_TEXT_ALIGNMENT_KEY,
    LYRIC_PRIMARY_FONT_SCALE_PERCENT_KEY,
    LYRIC_PRIMARY_FONT_SIZE_SP_KEY,
    LYRIC_SECONDARY_FONT_SCALE_PERCENT_KEY,
    LYRIC_SECONDARY_FONT_SIZE_SP_KEY,
    LYRIC_SHOW_TRANSLATION_KEY,
    LYRIC_WORD_LIFT_ENABLED_KEY,
    LYRIC_BLUR_EFFECT_ENABLED_KEY,
    LYRIC_PERSPECTIVE_EFFECT_ENABLED_KEY,
    LYRIC_PERSPECTIVE_ANGLE_DEGREES_KEY,
    LYRIC_TAP_TO_SEEK_ENABLED_KEY,
    LYRIC_SOURCE_MODE_KEY,
    LYRIC_SOURCE_PRIORITY_KEY,
    LYRIC_IGNORE_HEADER_TAGS_KEY,
    LYRIC_WESTERN_FONT_KEY,
    LYRIC_CJK_FONT_KEY,
    LYRIC_FONT_WEIGHT_KEY,
    LYRIC_FONT_APPLY_PAGE_KEY,
    LYRIC_FONT_APPLY_FLOATING_KEY,
    LYRIC_FONT_APPLY_SHARE_KEY,
    CROSSFADE_DURATION_MS_KEY,
    REPLAY_GAIN_MODE_KEY,
    REPLAY_GAIN_PREAMP_TENTHS_DB_KEY,
    RESUME_PLAYBACK_POSITION_KEY,
    STARTUP_PLAYBACK_MODE_KEY,
    PREVIOUS_BUTTON_BEHAVIOR_KEY,
    PLAY_NEXT_MODE_KEY,
    SHUFFLE_STRATEGY_KEY,
    OPEN_PLAYER_ON_PLAY_KEY,
    PLAYER_IMMERSIVE_ALBUM_COVER_KEY,
    PLAYER_COVER_SWIPE_KEY,
    PLAYER_SHOW_TOTAL_DURATION_KEY,
    PLAYER_SHOW_AUDIO_TECHNICAL_INFO_KEY,
    DESKTOP_SHORTCUTS_ENABLED_KEY,
    ARTIST_SEPARATORS_KEY,
    ARTIST_PROTECTED_NAMES_KEY,
    GENRE_SEPARATORS_KEY,
    GENRE_PROTECTED_NAMES_KEY,
    TAG_IGNORE_CASE_KEY,
    DOWNLOAD_FINALIZATION_ENABLED_KEY,
    SAVE_SIDECAR_LYRICS_KEY,
    AUDIO_EFFECTS_ENABLED_KEY,
    AUDIO_EFFECTS_CONFIG_JSON_KEY,
    EQ_BAND_GAINS_DB_KEY,
    EQ_Q_HUNDREDTHS_KEY,
    BASS_DB_KEY,
    TREBLE_DB_KEY,
    COMPRESSOR_ENABLED_KEY,
    COMPRESSOR_THRESHOLD_DB_KEY,
    COMPRESSOR_RATIO_KEY,
    COMPRESSOR_MAKEUP_DB_KEY,
    STEREO_WIDTH_PERCENT_KEY,
    REVERB_PRESET_KEY,
    FLOATING_LYRICS_ENABLED_KEY,
    NOTIFICATION_LYRICS_ENABLED_KEY,
    BLUETOOTH_LYRICS_ENABLED_KEY,
    LYRICON_ENABLED_KEY,
    SUPER_LYRIC_ENABLED_KEY,
    LYRIC_GETTER_ENABLED_KEY,
    FLYME_STATUS_LYRICS_ENABLED_KEY,
    COLOR_OS_LOCK_SCREEN_LYRICS_ENABLED_KEY,
    LYRIC_OUTPUT_SECONDARY_CONTENT_KEY,
    BACKUP_APPEARANCE_KEY,
    BACKUP_PLAYBACK_KEY,
    BACKUP_LYRICS_KEY,
    BACKUP_LIBRARY_KEY,
    BACKUP_NETWORK_KEY,
    BACKUP_SCHEDULE_KEY,
    BACKUP_WEBDAV_ACCOUNT_ID_KEY,
    BACKUP_REMOTE_DIRECTORY_KEY,
    AUTO_SCAN_MODE_KEY,
    SCAN_SUBDIRECTORIES_KEY,
    WEB_DAV_METADATA_SCAN_MODE_KEY,
    MINIMUM_AUDIO_DURATION_MS_KEY,
    MISSING_FILE_POLICY_KEY,
    ALLOW_METERED_NETWORK_USAGE_KEY,
    NETWORK_RETRY_COUNT_KEY,
    CONNECTION_TIMEOUT_SECONDS_KEY,
    AUDIO_PRELOAD_BYTES_KEY,
    LISTEN_AND_CACHE_ENABLED_KEY,
    AUDIO_CACHE_LIMIT_BYTES_KEY,
    IMAGE_CACHE_LIMIT_BYTES_KEY,
    ALLOW_MIXED_PLAYBACK_KEY,
    LOCAL_MUSIC_ENABLED_KEY,
    LOCAL_SCAN_SUBDIRECTORIES_KEY,
    IGNORE_SHORT_AUDIO_KEY,
    WEB_DAV_ENABLED_KEY,
    WEB_DAV_SCAN_SUBDIRECTORIES_KEY,
)

private val AUDIO_EFFECTS_JSON = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private fun decodeAudioEffectSettings(value: String): AudioEffectSettings? =
    runCatching {
        AUDIO_EFFECTS_JSON.decodeFromString<AudioEffectSettings>(value)
    }.getOrNull()
