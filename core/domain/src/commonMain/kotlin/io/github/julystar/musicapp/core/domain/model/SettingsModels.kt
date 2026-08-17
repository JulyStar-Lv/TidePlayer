package io.github.julystar.musicapp.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class AppThemeMode {
    System,
    Light,
    Dark,
}

@Serializable
enum class AppLanguageMode {
    System,
    Chinese,
    English,
}

@Serializable
enum class AudioFocusMode {
    Pause,
    Duck,
    Mix,
}

@Serializable
enum class AutoScanMode {
    Off,
    OnStartup,
    Periodic,
}

@Serializable
enum class MissingFilePolicy {
    MarkUnavailable,
    RemoveOnScan,
}

@Serializable
enum class LyricTextAlignment {
    Left,
    Center,
    Right,
}

@Serializable
enum class LyricSourceMode {
    Auto,
    Embedded,
    External,
}

@Serializable
enum class LyricSourceKind {
    EmbeddedTtml,
    EmbeddedWordTimed,
    EmbeddedPlain,
    ExternalTtml,
    ExternalWordTimed,
    ExternalPlain,
}

@Serializable
enum class LyricFontChoice {
    System,
    AppSans,
    AppCjk,
    Monospace,
}

@Serializable
data class LyricFontSettings(
    val westernFont: LyricFontChoice = LyricFontChoice.AppSans,
    val cjkFont: LyricFontChoice = LyricFontChoice.AppCjk,
    val weight: Int = DEFAULT_LYRIC_FONT_WEIGHT,
    val applyToLyricsPage: Boolean = true,
    val applyToFloatingLyrics: Boolean = true,
    val applyToShareCard: Boolean = false,
) {
    companion object {
        val Default = LyricFontSettings()
    }
}

@Serializable
data class LyricDisplaySettings(
    val textAlignment: LyricTextAlignment = LyricTextAlignment.Left,
    val primaryFontScalePercent: Int = DEFAULT_LYRIC_FONT_SCALE_PERCENT,
    val primaryFontSizeSp: Int = DEFAULT_LYRIC_PRIMARY_FONT_SIZE_SP,
    val secondaryFontScalePercent: Int = DEFAULT_LYRIC_FONT_SCALE_PERCENT,
    val secondaryFontSizeSp: Int = DEFAULT_LYRIC_SECONDARY_FONT_SIZE_SP,
    val showTranslation: Boolean = true,
    val wordLiftEnabled: Boolean = true,
    val blurEffectEnabled: Boolean = true,
    val perspectiveEffectEnabled: Boolean = false,
    val perspectiveAngleDegrees: Int = DEFAULT_LYRIC_PERSPECTIVE_ANGLE_DEGREES,
    val tapToSeekEnabled: Boolean = true,
    val sourceMode: LyricSourceMode = LyricSourceMode.Auto,
    val sourcePriority: List<LyricSourceKind> = DEFAULT_LYRIC_SOURCE_PRIORITY,
    val ignoreHeaderTags: Boolean = true,
    val font: LyricFontSettings = LyricFontSettings.Default,
) {
    companion object {
        val Default = LyricDisplaySettings()
    }
}

@Serializable
enum class StartupPlaybackMode {
    Off,
    ResumeLastQueue,
    ShuffleLibrary,
}

@Serializable
enum class PreviousButtonBehavior {
    PreviousTrack,
    RestartCurrentTrack,
}

@Serializable
enum class PlayNextMode {
    FirstRequestedFirst,
    LastRequestedFirst,
}

@Serializable
enum class ShuffleStrategy {
    QueueOrder,
    TrueRandom,
}

@Serializable
enum class ReplayGainMode {
    Off,
    Track,
    Album,
    Auto,
}

@Serializable
data class PlaybackAdvancedSettings(
    val crossfadeDurationMs: Int = 0,
    val replayGainMode: ReplayGainMode = ReplayGainMode.Off,
    val replayGainPreampTenthsDb: Int = 0,
    val resumePlaybackPosition: Boolean = true,
    val startupPlaybackMode: StartupPlaybackMode = StartupPlaybackMode.Off,
    val previousButtonBehavior: PreviousButtonBehavior = PreviousButtonBehavior.PreviousTrack,
    val playNextMode: PlayNextMode = PlayNextMode.FirstRequestedFirst,
    val shuffleStrategy: ShuffleStrategy = ShuffleStrategy.QueueOrder,
) {
    companion object {
        val Default = PlaybackAdvancedSettings()
    }
}

@Serializable
data class PlayerInteractionSettings(
    val immersiveAlbumCoverEnabled: Boolean = false,
    val audioReactiveBackgroundEnabled: Boolean = false,
    val coverSwipeEnabled: Boolean = true,
    val showTotalDuration: Boolean = false,
    val showAudioTechnicalInfo: Boolean = false,
    val desktopShortcutsEnabled: Boolean = true,
) {
    companion object {
        val Default = PlayerInteractionSettings()
    }
}

@Serializable
data class MetadataParsingSettings(
    val artistSeparators: String = DEFAULT_ARTIST_SEPARATORS,
    val artistProtectedNames: String = "",
    val genreSeparators: String = DEFAULT_GENRE_SEPARATORS,
    val genreProtectedNames: String = "",
    val ignoreTagCase: Boolean = false,
) {
    companion object {
        val Default = MetadataParsingSettings()
    }
}

@Serializable
data class DownloadFinalizationSettings(
    val enrichMetadata: Boolean = true,
    val saveSidecarLyrics: Boolean = true,
) {
    companion object {
        val Default = DownloadFinalizationSettings()
    }
}

@Serializable
enum class ReverbPreset {
    None,
    SmallRoom,
    MediumRoom,
    LargeRoom,
    Hall,
    Plate,
}

@Serializable
enum class EqualizerMode {
    Graphic,
    Parametric,
}

@Serializable
enum class ParametricEqFilterType {
    Peak,
    LowShelf,
    HighShelf,
    LowPass,
    HighPass,
    BandPass,
    Notch,
}

@Serializable
data class ParametricEqBand(
    val enabled: Boolean = true,
    val type: ParametricEqFilterType = ParametricEqFilterType.Peak,
    val frequencyHz: Int = 1_000,
    val gainTenthsDb: Int = 0,
    val qHundredths: Int = 100,
)

@Serializable
data class GraphicEqualizerSettings(
    val enabled: Boolean = true,
    val bandGainsDb: List<Int> = DEFAULT_EQ_BAND_GAINS_DB,
    val qHundredths: Int = DEFAULT_EQ_Q_HUNDREDTHS,
    val preampTenthsDb: Int = 0,
)

@Serializable
data class ParametricEqualizerSettings(
    val enabled: Boolean = false,
    val preampTenthsDb: Int = 0,
    val bands: List<ParametricEqBand> = emptyList(),
)

@Serializable
data class ToneControlSettings(
    val enabled: Boolean = true,
    val bassGainDb: Int = 0,
    val bassFrequencyHz: Int = 120,
    val trebleGainDb: Int = 0,
    val trebleFrequencyHz: Int = 8_000,
)

@Serializable
data class CompressorSettings(
    val enabled: Boolean = false,
    val thresholdDb: Int = DEFAULT_COMPRESSOR_THRESHOLD_DB,
    val ratio: Int = DEFAULT_COMPRESSOR_RATIO,
    val attackMs: Int = 10,
    val releaseMs: Int = 120,
    val makeupGainDb: Int = 0,
    val kneeDb: Int = 6,
)

@Serializable
data class LoudnessSettings(
    val enabled: Boolean = false,
    val amountPercent: Int = 0,
    val balancePercent: Int = 0,
)

@Serializable
data class DynamicEqSettings(
    val enabled: Boolean = false,
    val amountPercent: Int = 0,
    val deEsserAmountPercent: Int = 0,
    val deEsserFrequencyHz: Int = 6_500,
)

@Serializable
data class MonoBassSettings(
    val enabled: Boolean = false,
    val crossoverHz: Int = 120,
    val amountPercent: Int = 100,
)

@Serializable
data class StereoWidthSettings(
    val enabled: Boolean = false,
    val widthPercent: Int = DEFAULT_STEREO_WIDTH_PERCENT,
)

@Serializable
data class CrossfeedSettings(
    val enabled: Boolean = false,
    val lowCutHz: Int = 120,
    val highCutHz: Int = 700,
    val attenuationTenthsDb: Int = 60,
)

@Serializable
enum class SpatialAudioMode {
    None,
    CrossfeedAndWidth,
    Surround360,
    Panoramic360,
}

@Serializable
data class SpatialAudioSettings(
    val mode: SpatialAudioMode = SpatialAudioMode.None,
    val intensityPercent: Int = 0,
    val azimuthDegrees: Int = 0,
    val elevationDegrees: Int = 0,
    val autoRotateDegreesPerSecond: Int = 0,
    val roomAmountPercent: Int = 15,
)

@Serializable
enum class MoogFilterMode {
    LowPass24,
    LowPass12,
    HighPass24,
    BandPass12,
    Notch,
}

@Serializable
data class MoogFilterSettings(
    val enabled: Boolean = false,
    val mode: MoogFilterMode = MoogFilterMode.LowPass24,
    val cutoffHz: Int = 8_000,
    val resonancePercent: Int = 0,
    val driveTenthsDb: Int = 0,
    val mixPercent: Int = 100,
)

@Serializable
enum class SpeakerOutputMode {
    Elasticity,
    Powerful,
    Wide,
}

@Serializable
data class SpeakerOutputSettings(
    val enabled: Boolean = false,
    val mode: SpeakerOutputMode = SpeakerOutputMode.Elasticity,
    val strengthPercent: Int = 50,
)

@Serializable
data class LimiterSettings(
    val enabled: Boolean = true,
    val ceilingTenthsDb: Int = -5,
    val attackHundredthsMs: Int = 25,
    val releaseMs: Int = 80,
    val truePeakEnabled: Boolean = false,
    val oversampling: Int = 1,
    val lookaheadMs: Int = 3,
)

@Serializable
enum class HeadroomMode {
    Off,
    Automatic,
    Manual,
}

@Serializable
data class HeadroomSettings(
    val mode: HeadroomMode = HeadroomMode.Off,
    val manualTenthsDb: Int = 0,
)

@Serializable
data class ReverbSettings(
    val preset: ReverbPreset = ReverbPreset.None,
    val wetPercent: Int = 15,
)

@Serializable
data class AudioEffectProfile(
    val equalizerMode: EqualizerMode = EqualizerMode.Graphic,
    val graphicEqualizer: GraphicEqualizerSettings = GraphicEqualizerSettings(),
    val parametricEqualizer: ParametricEqualizerSettings = ParametricEqualizerSettings(),
    val tone: ToneControlSettings = ToneControlSettings(),
    val compressor: CompressorSettings = CompressorSettings(),
    val loudness: LoudnessSettings = LoudnessSettings(),
    val dynamicEq: DynamicEqSettings = DynamicEqSettings(),
    val monoBass: MonoBassSettings = MonoBassSettings(),
    val stereoWidth: StereoWidthSettings = StereoWidthSettings(),
    val crossfeed: CrossfeedSettings = CrossfeedSettings(),
    val spatialAudio: SpatialAudioSettings = SpatialAudioSettings(),
    val moogFilter: MoogFilterSettings = MoogFilterSettings(),
    val speakerOutput: SpeakerOutputSettings = SpeakerOutputSettings(),
    val limiter: LimiterSettings = LimiterSettings(),
    val reverb: ReverbSettings = ReverbSettings(),
) {
    companion object {
        val Default = AudioEffectProfile()
    }
}

@Serializable
data class AudioEffectPreset(
    val id: String,
    val name: String,
    val profile: AudioEffectProfile,
)

@Serializable
data class AudioEffectSettings(
    val enabled: Boolean = false,
    // Legacy mirrors are retained so old backups and DataStore keys migrate
    // without losing the existing ten-band settings.
    val eqBandGainsDb: List<Int> = DEFAULT_EQ_BAND_GAINS_DB,
    val eqQHundredths: Int = DEFAULT_EQ_Q_HUNDREDTHS,
    val bassDb: Int = 0,
    val trebleDb: Int = 0,
    val compressorEnabled: Boolean = false,
    val compressorThresholdDb: Int = DEFAULT_COMPRESSOR_THRESHOLD_DB,
    val compressorRatio: Int = DEFAULT_COMPRESSOR_RATIO,
    val compressorMakeupDb: Int = 0,
    val stereoWidthPercent: Int = DEFAULT_STEREO_WIDTH_PERCENT,
    val reverbPreset: ReverbPreset = ReverbPreset.None,
    val schemaVersion: Int = 0,
    val profile: AudioEffectProfile = AudioEffectProfile.Default,
    val headroom: HeadroomSettings = HeadroomSettings(),
    val userPresets: List<AudioEffectPreset> = emptyList(),
) {
    companion object {
        val Default = AudioEffectSettings(schemaVersion = AUDIO_DSP_SCHEMA_VERSION)
    }
}

@Serializable
enum class SecondaryLyricContent {
    Off,
    Translation,
    Pronunciation,
}

@Serializable
data class LyricOutputSettings(
    val floatingLyricsEnabled: Boolean = false,
    val notificationLyricsEnabled: Boolean = false,
    val bluetoothLyricsEnabled: Boolean = false,
    val lyriconEnabled: Boolean = false,
    val superLyricEnabled: Boolean = false,
    val lyricGetterEnabled: Boolean = false,
    val flymeStatusLyricsEnabled: Boolean = false,
    val colorOsLockScreenLyricsEnabled: Boolean = false,
    val secondaryContent: SecondaryLyricContent = SecondaryLyricContent.Translation,
) {
    companion object {
        val Default = LyricOutputSettings()
    }
}

@Serializable
enum class BackupSchedule {
    Off,
    Daily,
    Weekly,
}

@Serializable
data class SettingsBackupSelection(
    val appearance: Boolean = true,
    val playback: Boolean = true,
    val lyrics: Boolean = true,
    val libraryAndMetadata: Boolean = true,
    val networkAndCache: Boolean = true,
) {
    companion object {
        val All = SettingsBackupSelection()
    }
}

@Serializable
data class SettingsBackupSettings(
    val selection: SettingsBackupSelection = SettingsBackupSelection.All,
    val schedule: BackupSchedule = BackupSchedule.Off,
    val webDavAccountId: Long? = null,
    val remoteDirectory: String = "/TidePlayer/Backups",
) {
    companion object {
        val Default = SettingsBackupSettings()
    }
}

@Serializable
data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.System,
    val artworkThemeEnabled: Boolean = true,
    val manualThemeSeedArgb: Long = DEFAULT_MANUAL_THEME_SEED_ARGB,
    val customThemeSeedArgbValues: List<Long> = emptyList(),
    val languageMode: AppLanguageMode = AppLanguageMode.System,
    val audioFocusMode: AudioFocusMode = AudioFocusMode.Pause,
    val pauseOnDisconnect: Boolean = true,
    val gaplessPlaybackEnabled: Boolean = false,
    val retryPlaybackOnFailure: Boolean = true,
    val resumePlaybackAfterNetworkRecovery: Boolean = true,
    val keepScreenOnInPlayer: Boolean = false,
    val lyrics: LyricDisplaySettings = LyricDisplaySettings.Default,
    val playbackAdvanced: PlaybackAdvancedSettings = PlaybackAdvancedSettings.Default,
    val playerInteraction: PlayerInteractionSettings = PlayerInteractionSettings.Default,
    val metadataParsing: MetadataParsingSettings = MetadataParsingSettings.Default,
    val downloadFinalization: DownloadFinalizationSettings = DownloadFinalizationSettings.Default,
    val audioEffects: AudioEffectSettings = AudioEffectSettings.Default,
    val lyricOutput: LyricOutputSettings = LyricOutputSettings.Default,
    val backup: SettingsBackupSettings = SettingsBackupSettings.Default,
    val autoScanMode: AutoScanMode = AutoScanMode.Off,
    val scanSubdirectories: Boolean = true,
    val webDavMetadataScanMode: MetadataScanMode = MetadataScanMode.Standard,
    val minimumAudioDurationMs: Long = DEFAULT_MINIMUM_AUDIO_DURATION_MS,
    val missingFilePolicy: MissingFilePolicy = MissingFilePolicy.MarkUnavailable,
    val allowMeteredNetworkUsage: Boolean = false,
    val networkRetryCount: Int = DEFAULT_NETWORK_RETRY_COUNT,
    val connectionTimeoutSeconds: Int = DEFAULT_CONNECTION_TIMEOUT_SECONDS,
    val audioPreloadBytes: Long = DEFAULT_AUDIO_PRELOAD_BYTES,
    val listenAndCacheEnabled: Boolean = true,
    val audioCacheLimitBytes: Long = DEFAULT_AUDIO_CACHE_LIMIT_BYTES,
    val imageCacheLimitBytes: Long = DEFAULT_IMAGE_CACHE_LIMIT_BYTES,
) {
    companion object {
        val Default = AppSettings()
    }
}

fun AppSettings.metadataScanModeFor(isWebDav: Boolean): MetadataScanMode {
    return if (isWebDav) webDavMetadataScanMode else MetadataScanMode.Full
}

data class AudioDspCapabilities(
    val graphicEqualizer: Boolean = false,
    val parametricEqualizer: Boolean = false,
    val toneControl: Boolean = false,
    val compressor: Boolean = false,
    val dynamicEq: Boolean = false,
    val loudness: Boolean = false,
    val monoBass: Boolean = false,
    val stereoWidth: Boolean = false,
    val crossfeed: Boolean = false,
    val surround360: Boolean = false,
    val panoramic360: Boolean = false,
    val convolution: Boolean = false,
    val moogFilter: Boolean = false,
    val speakerOutput: Boolean = false,
    val reverb: Boolean = false,
    val peakLimiter: Boolean = false,
    val truePeakLimiter: Boolean = false,
    val maxParametricBands: Int = 0,
    val supportedChannelCounts: Set<Int> = emptySet(),
    val resourceDependent: Boolean = false,
) {
    val anySoftwareDsp: Boolean
        get() = graphicEqualizer || parametricEqualizer

    companion object {
        val SharedCore = AudioDspCapabilities(
            graphicEqualizer = true,
            parametricEqualizer = true,
            toneControl = true,
            compressor = true,
            dynamicEq = true,
            loudness = true,
            monoBass = true,
            stereoWidth = true,
            crossfeed = true,
            surround360 = true,
            panoramic360 = true,
            convolution = false,
            moogFilter = true,
            speakerOutput = true,
            reverb = true,
            peakLimiter = true,
            truePeakLimiter = true,
            maxParametricBands = MAX_PARAMETRIC_EQ_BANDS,
            supportedChannelCounts = setOf(1, 2),
        )
    }
}

enum class AudioSampleFormat {
    Pcm16,
    Float32,
}

data class AudioPipelineCapabilities(
    val dspInputSampleFormats: Set<AudioSampleFormat> = emptySet(),
    val dspOutputSampleFormats: Set<AudioSampleFormat> = emptySet(),
    val highResolutionDspOutput: Boolean = false,
)

data class SettingsCapabilities(
    val backgroundScanSupported: Boolean = false,
    val customMusicDirectorySupported: Boolean = false,
    val customCacheDirectorySupported: Boolean = false,
    val secureCredentialStoreSupported: Boolean = false,
    val systemEqualizerSupported: Boolean = false,
    val floatingLyricsSupported: Boolean = false,
    val inAppUpdateSupported: Boolean = false,
    val desktopMediaKeysSupported: Boolean = false,
    val audioFocusSupported: Boolean = false,
    val deviceDisconnectSupported: Boolean = false,
    val gaplessPlaybackSupported: Boolean = false,
    val crossfadeSupported: Boolean = false,
    val replayGainSupported: Boolean = false,
    val audioEffectsSupported: Boolean = false,
    val audioDsp: AudioDspCapabilities = AudioDspCapabilities(),
    val audioPipeline: AudioPipelineCapabilities = AudioPipelineCapabilities(),
    val lyricFontSelectionSupported: Boolean = true,
    val networkStatusSupported: Boolean = false,
    val audioPreloadSupported: Boolean = false,
    val diagnosticsExportSupported: Boolean = false,
    val diagnosticsCenterSupported: Boolean = false,
    val safeModeSupported: Boolean = false,
    val platformExitInfoSupported: Boolean = false,
    val historicalAnrTraceSupported: Boolean = false,
    val incidentRecoverySupported: Boolean = false,
    val fileShareSupported: Boolean = false,
    val notificationLyricsSupported: Boolean = false,
    val bluetoothLyricsSupported: Boolean = false,
    val lyriconSupported: Boolean = false,
    val superLyricSupported: Boolean = false,
    val lyricGetterSupported: Boolean = false,
    val flymeStatusLyricsSupported: Boolean = false,
    val colorOsLockScreenLyricsSupported: Boolean = false,
    val desktopShortcutsSupported: Boolean = false,
    val audioOutputSelectionSupported: Boolean = false,
    val audioRoutePickerSupported: Boolean = false,
    val settingsBackupSupported: Boolean = false,
    val scheduledBackupSupported: Boolean = false,
)

sealed interface SettingsBackupResult {
    data class Success(val path: String) : SettingsBackupResult
    data class Failure(val message: String) : SettingsBackupResult
}

data class StorageUsage(
    val audioBytes: Long? = null,
    val imageBytes: Long? = null,
    val downloadBytes: Long? = null,
    val databaseBytes: Long? = null,
    val logBytes: Long? = null,
    val totalBytes: Long? = null,
) {
    companion object {
        val Unknown = StorageUsage()
    }
}

data class LocalMusicDirectory(
    val id: String,
    val accountId: SourceAccountId,
    val displayName: String,
    val path: String,
    val lastScannedAtEpochMs: Long?,
)

data class NetworkStatus(
    val isOnline: Boolean,
    val isMetered: Boolean,
) {
    companion object {
        val Unknown = NetworkStatus(isOnline = true, isMetered = false)
    }
}

data class DiagnosticsReport(
    val generatedAtEpochMs: Long,
    val appVersion: String,
    val buildInfo: String,
    val gitCommitSha: String,
    val platformInfo: String,
    val databaseVersion: Int,
    val sourceCount: Int,
    val trackCount: Long,
    val recentScanSummary: String?,
    val playerStateSummary: String,
    val storageUsage: StorageUsage,
    val recentErrors: List<String>,
)

sealed interface DiagnosticsExportResult {
    data class Success(val path: String) : DiagnosticsExportResult
    data class Failure(val message: String) : DiagnosticsExportResult
}

data class LibraryRebuildState(
    val status: LibraryRebuildStatus = LibraryRebuildStatus.Idle,
    val completedSources: Int = 0,
    val totalSources: Int = 0,
    val failureMessage: String? = null,
)

enum class LibraryRebuildStatus {
    Idle,
    Clearing,
    Scanning,
    Completed,
    Failed,
}

const val AUDIO_CACHE_LIMIT_DISABLED_BYTES = 0L
const val DEFAULT_MANUAL_THEME_SEED_ARGB = 0xFFFF5B8AL
const val MAX_CUSTOM_THEME_SEEDS = 12
const val DEFAULT_AUDIO_CACHE_LIMIT_BYTES = 1_073_741_824L
const val MAX_AUDIO_CACHE_LIMIT_BYTES = 10_737_418_240L
const val DEFAULT_IMAGE_CACHE_LIMIT_BYTES = 268_435_456L
const val MAX_IMAGE_CACHE_LIMIT_BYTES = 2_147_483_648L
const val DEFAULT_AUDIO_PRELOAD_BYTES = 4_194_304L
const val MAX_AUDIO_PRELOAD_BYTES = 67_108_864L
const val DEFAULT_MINIMUM_AUDIO_DURATION_MS = 30_000L
const val MAX_MINIMUM_AUDIO_DURATION_MS = 86_400_000L
const val DEFAULT_NETWORK_RETRY_COUNT = 2
const val MAX_NETWORK_RETRY_COUNT = 5
const val DEFAULT_CONNECTION_TIMEOUT_SECONDS = 20
const val MIN_CONNECTION_TIMEOUT_SECONDS = 5
const val MAX_CONNECTION_TIMEOUT_SECONDS = 120
const val MIN_LYRIC_FONT_SCALE_PERCENT = 75
const val DEFAULT_LYRIC_FONT_SCALE_PERCENT = 100
const val MAX_LYRIC_FONT_SCALE_PERCENT = 175
const val MIN_LYRIC_PRIMARY_FONT_SIZE_SP = 20
const val DEFAULT_LYRIC_PRIMARY_FONT_SIZE_SP = 32
const val MAX_LYRIC_PRIMARY_FONT_SIZE_SP = 54
const val MIN_LYRIC_SECONDARY_FONT_SIZE_SP = 12
const val DEFAULT_LYRIC_SECONDARY_FONT_SIZE_SP = 19
const val MAX_LYRIC_SECONDARY_FONT_SIZE_SP = 30
const val MIN_LYRIC_PERSPECTIVE_ANGLE_DEGREES = 0
const val DEFAULT_LYRIC_PERSPECTIVE_ANGLE_DEGREES = 25
const val MAX_LYRIC_PERSPECTIVE_ANGLE_DEGREES = 45
const val DEFAULT_LYRIC_FONT_WEIGHT = 700
const val MIN_LYRIC_FONT_WEIGHT = 100
const val MAX_LYRIC_FONT_WEIGHT = 900
const val MAX_CROSSFADE_DURATION_MS = 30_000
const val MIN_REPLAY_GAIN_PREAMP_TENTHS_DB = -200
const val MAX_REPLAY_GAIN_PREAMP_TENTHS_DB = 200
const val DEFAULT_ARTIST_SEPARATORS = ";,/&、，"
const val DEFAULT_GENRE_SEPARATORS = ";,/、，"
const val AUDIO_DSP_SCHEMA_VERSION = 2
const val EQ_BAND_COUNT = 10
const val MAX_PARAMETRIC_EQ_BANDS = 40
const val MIN_EQ_BAND_GAIN_DB = -12
const val MAX_EQ_BAND_GAIN_DB = 12
const val DEFAULT_EQ_Q_HUNDREDTHS = 100
const val MIN_EQ_Q_HUNDREDTHS = 25
const val MAX_EQ_Q_HUNDREDTHS = 400
const val DEFAULT_COMPRESSOR_THRESHOLD_DB = -18
const val DEFAULT_COMPRESSOR_RATIO = 4
const val DEFAULT_STEREO_WIDTH_PERCENT = 100
const val MIN_SCANNED_AUDIO_DURATION_MS = DEFAULT_MINIMUM_AUDIO_DURATION_MS

val DEFAULT_LYRIC_SOURCE_PRIORITY = LyricSourceKind.entries.toList()
val DEFAULT_EQ_BAND_GAINS_DB = List(EQ_BAND_COUNT) { 0 }

fun normalizeThemeSeedArgb(value: Long): Long = 0xFF000000L or (value and 0x00FFFFFFL)

fun normalizeCustomThemeSeedArgbValues(values: List<Long>): List<Long> {
    return values
        .map(::normalizeThemeSeedArgb)
        .distinct()
        .take(MAX_CUSTOM_THEME_SEEDS)
}

val AUDIO_CACHE_LIMIT_PRESETS_BYTES = listOf(
    AUDIO_CACHE_LIMIT_DISABLED_BYTES,
    536_870_912L,
    DEFAULT_AUDIO_CACHE_LIMIT_BYTES,
    2_147_483_648L,
    4_294_967_296L,
)

val IMAGE_CACHE_LIMIT_PRESETS_BYTES = listOf(
    AUDIO_CACHE_LIMIT_DISABLED_BYTES,
    134_217_728L,
    DEFAULT_IMAGE_CACHE_LIMIT_BYTES,
    536_870_912L,
    1_073_741_824L,
)

val SUPPORTED_AUDIO_EXTENSIONS = listOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav")
val DEFAULT_IGNORED_SOURCE_DIRECTORIES = listOf(".cache", ".trash", "@eaDir", "__MACOSX")

fun normalizeAudioCacheLimitBytes(bytes: Long): Long {
    return bytes.coerceIn(AUDIO_CACHE_LIMIT_DISABLED_BYTES, MAX_AUDIO_CACHE_LIMIT_BYTES)
}

fun normalizeImageCacheLimitBytes(bytes: Long): Long {
    return bytes.coerceIn(AUDIO_CACHE_LIMIT_DISABLED_BYTES, MAX_IMAGE_CACHE_LIMIT_BYTES)
}

fun normalizeNetworkRetryCount(value: Int): Int = value.coerceIn(0, MAX_NETWORK_RETRY_COUNT)

fun normalizeConnectionTimeoutSeconds(value: Int): Int {
    return value.coerceIn(MIN_CONNECTION_TIMEOUT_SECONDS, MAX_CONNECTION_TIMEOUT_SECONDS)
}

fun normalizeAudioPreloadBytes(bytes: Long): Long {
    return bytes.coerceIn(AUDIO_CACHE_LIMIT_DISABLED_BYTES, MAX_AUDIO_PRELOAD_BYTES)
}

fun normalizeMinimumAudioDurationMs(value: Long): Long {
    return value.coerceIn(0L, MAX_MINIMUM_AUDIO_DURATION_MS)
}

fun normalizeLyricFontScalePercent(value: Int): Int {
    return value.coerceIn(MIN_LYRIC_FONT_SCALE_PERCENT, MAX_LYRIC_FONT_SCALE_PERCENT)
}

fun normalizeLyricPrimaryFontSizeSp(value: Int): Int {
    return value.coerceIn(MIN_LYRIC_PRIMARY_FONT_SIZE_SP, MAX_LYRIC_PRIMARY_FONT_SIZE_SP)
}

fun normalizeLyricSecondaryFontSizeSp(value: Int): Int {
    return value.coerceIn(MIN_LYRIC_SECONDARY_FONT_SIZE_SP, MAX_LYRIC_SECONDARY_FONT_SIZE_SP)
}

fun normalizeLyricPerspectiveAngleDegrees(value: Int): Int {
    return value.coerceIn(
        MIN_LYRIC_PERSPECTIVE_ANGLE_DEGREES,
        MAX_LYRIC_PERSPECTIVE_ANGLE_DEGREES,
    )
}

fun normalizeLyricSourcePriority(value: List<LyricSourceKind>): List<LyricSourceKind> {
    val unique = value.distinct()
    return unique + DEFAULT_LYRIC_SOURCE_PRIORITY.filterNot(unique::contains)
}

fun normalizeLyricFontSettings(value: LyricFontSettings): LyricFontSettings {
    val normalizedWeight = (value.weight.coerceIn(MIN_LYRIC_FONT_WEIGHT, MAX_LYRIC_FONT_WEIGHT) / 100) * 100
    return value.copy(weight = normalizedWeight)
}

fun normalizePlaybackAdvancedSettings(value: PlaybackAdvancedSettings): PlaybackAdvancedSettings {
    return value.copy(
        crossfadeDurationMs = value.crossfadeDurationMs.coerceIn(0, MAX_CROSSFADE_DURATION_MS),
        replayGainPreampTenthsDb = value.replayGainPreampTenthsDb.coerceIn(
            MIN_REPLAY_GAIN_PREAMP_TENTHS_DB,
            MAX_REPLAY_GAIN_PREAMP_TENTHS_DB,
        ),
    )
}

fun normalizeAudioEffectSettings(value: AudioEffectSettings): AudioEffectSettings {
    val normalizedProfile = if (value.schemaVersion < 1) {
        val gains = value.eqBandGainsDb
            .take(EQ_BAND_COUNT)
            .map { it.coerceIn(MIN_EQ_BAND_GAIN_DB, MAX_EQ_BAND_GAIN_DB) }
            .let { it + List(EQ_BAND_COUNT - it.size) { 0 } }
        normalizeAudioEffectProfile(value.profile).let { profile ->
            normalizeAudioEffectProfile(
                profile.copy(
                    graphicEqualizer = profile.graphicEqualizer.copy(
                        bandGainsDb = gains,
                        qHundredths = value.eqQHundredths,
                    ),
                    tone = profile.tone.copy(
                        bassGainDb = value.bassDb,
                        trebleGainDb = value.trebleDb,
                    ),
                    compressor = profile.compressor.copy(
                        enabled = value.compressorEnabled,
                        thresholdDb = value.compressorThresholdDb,
                        ratio = value.compressorRatio,
                        makeupGainDb = value.compressorMakeupDb,
                    ),
                    stereoWidth = profile.stereoWidth.copy(
                        enabled = value.stereoWidthPercent != DEFAULT_STEREO_WIDTH_PERCENT,
                        widthPercent = value.stereoWidthPercent,
                    ),
                    reverb = profile.reverb.copy(preset = value.reverbPreset),
                )
            )
        }
    } else {
        normalizeAudioEffectProfile(value.profile)
    }
    return value.copy(
        eqBandGainsDb = normalizedProfile.graphicEqualizer.bandGainsDb,
        eqQHundredths = normalizedProfile.graphicEqualizer.qHundredths,
        bassDb = normalizedProfile.tone.bassGainDb,
        trebleDb = normalizedProfile.tone.trebleGainDb,
        compressorEnabled = normalizedProfile.compressor.enabled,
        compressorThresholdDb = normalizedProfile.compressor.thresholdDb,
        compressorRatio = normalizedProfile.compressor.ratio,
        compressorMakeupDb = normalizedProfile.compressor.makeupGainDb,
        stereoWidthPercent = normalizedProfile.stereoWidth.widthPercent,
        reverbPreset = normalizedProfile.reverb.preset,
        schemaVersion = AUDIO_DSP_SCHEMA_VERSION,
        profile = normalizedProfile,
        headroom = value.headroom.copy(
            manualTenthsDb = value.headroom.manualTenthsDb.coerceIn(-240, 0),
        ),
        userPresets = value.userPresets
            .asSequence()
            .filter { it.id.isNotBlank() && it.name.isNotBlank() }
            .distinctBy(AudioEffectPreset::id)
            .take(64)
            .map { preset ->
                preset.copy(
                    id = preset.id.take(128),
                    name = preset.name.take(128),
                    profile = normalizeAudioEffectProfile(preset.profile),
                )
            }
            .toList(),
    )
}

fun AudioEffectSettings.withAudioEffectProfile(
    profile: AudioEffectProfile,
): AudioEffectSettings {
    return normalizeAudioEffectSettings(
        copy(
            schemaVersion = AUDIO_DSP_SCHEMA_VERSION,
            profile = profile,
        )
    )
}

fun normalizeAudioEffectProfile(value: AudioEffectProfile): AudioEffectProfile {
    val graphicGains = value.graphicEqualizer.bandGainsDb
        .take(EQ_BAND_COUNT)
        .map { it.coerceIn(MIN_EQ_BAND_GAIN_DB, MAX_EQ_BAND_GAIN_DB) }
        .let { it + List(EQ_BAND_COUNT - it.size) { 0 } }
    return value.copy(
        graphicEqualizer = value.graphicEqualizer.copy(
            bandGainsDb = graphicGains,
            qHundredths = value.graphicEqualizer.qHundredths.coerceIn(10, 1_000),
            preampTenthsDb = value.graphicEqualizer.preampTenthsDb.coerceIn(-240, 120),
        ),
        parametricEqualizer = value.parametricEqualizer.copy(
            preampTenthsDb = value.parametricEqualizer.preampTenthsDb.coerceIn(-960, 120),
            bands = value.parametricEqualizer.bands
                .take(MAX_PARAMETRIC_EQ_BANDS)
                .map { band ->
                    band.copy(
                        frequencyHz = band.frequencyHz.coerceIn(10, 20_000),
                        gainTenthsDb = band.gainTenthsDb.coerceIn(-240, 240),
                        qHundredths = band.qHundredths.coerceIn(5, 2_400),
                    )
                },
        ),
        tone = value.tone.copy(
            bassGainDb = value.tone.bassGainDb.coerceIn(-24, 24),
            bassFrequencyHz = value.tone.bassFrequencyHz.coerceIn(50, 500),
            trebleGainDb = value.tone.trebleGainDb.coerceIn(-24, 24),
            trebleFrequencyHz = value.tone.trebleFrequencyHz.coerceIn(2_000, 16_000),
        ),
        compressor = value.compressor.copy(
            thresholdDb = value.compressor.thresholdDb.coerceIn(-60, 0),
            ratio = value.compressor.ratio.coerceIn(1, 30),
            attackMs = value.compressor.attackMs.coerceIn(1, 500),
            releaseMs = value.compressor.releaseMs.coerceIn(5, 5_000),
            makeupGainDb = value.compressor.makeupGainDb.coerceIn(-12, 24),
            kneeDb = value.compressor.kneeDb.coerceIn(0, 24),
        ),
        loudness = value.loudness.copy(
            amountPercent = value.loudness.amountPercent.coerceIn(0, 100),
            balancePercent = value.loudness.balancePercent.coerceIn(-100, 100),
        ),
        dynamicEq = value.dynamicEq.copy(
            amountPercent = value.dynamicEq.amountPercent.coerceIn(0, 100),
            deEsserAmountPercent = value.dynamicEq.deEsserAmountPercent.coerceIn(0, 100),
            deEsserFrequencyHz = value.dynamicEq.deEsserFrequencyHz.coerceIn(4_000, 10_000),
        ),
        monoBass = value.monoBass.copy(
            crossoverHz = value.monoBass.crossoverHz.coerceIn(60, 300),
            amountPercent = value.monoBass.amountPercent.coerceIn(0, 100),
        ),
        stereoWidth = value.stereoWidth.copy(
            widthPercent = value.stereoWidth.widthPercent.coerceIn(0, 200),
        ),
        crossfeed = value.crossfeed.copy(
            lowCutHz = value.crossfeed.lowCutHz.coerceIn(50, 1_000),
            highCutHz = value.crossfeed.highCutHz.coerceIn(500, 8_000),
            attenuationTenthsDb = value.crossfeed.attenuationTenthsDb.coerceIn(0, 150),
        ),
        spatialAudio = value.spatialAudio.copy(
            intensityPercent = value.spatialAudio.intensityPercent.coerceIn(0, 100),
            azimuthDegrees = value.spatialAudio.azimuthDegrees.mod(360),
            elevationDegrees = value.spatialAudio.elevationDegrees.coerceIn(-90, 90),
            autoRotateDegreesPerSecond =
                value.spatialAudio.autoRotateDegreesPerSecond.coerceIn(-180, 180),
            roomAmountPercent = value.spatialAudio.roomAmountPercent.coerceIn(0, 100),
        ),
        moogFilter = value.moogFilter.copy(
            cutoffHz = value.moogFilter.cutoffHz.coerceIn(20, 20_000),
            resonancePercent = value.moogFilter.resonancePercent.coerceIn(0, 100),
            driveTenthsDb = value.moogFilter.driveTenthsDb.coerceIn(0, 180),
            mixPercent = value.moogFilter.mixPercent.coerceIn(0, 100),
        ),
        speakerOutput = value.speakerOutput.copy(
            strengthPercent = value.speakerOutput.strengthPercent.coerceIn(0, 100),
        ),
        limiter = value.limiter.copy(
            ceilingTenthsDb = value.limiter.ceilingTenthsDb.coerceIn(-120, 0),
            attackHundredthsMs = value.limiter.attackHundredthsMs.coerceIn(1, 2_000),
            releaseMs = value.limiter.releaseMs.coerceIn(5, 2_000),
            oversampling = if (value.limiter.truePeakEnabled) 4 else 1,
            lookaheadMs = value.limiter.lookaheadMs.coerceIn(1, 10),
        ),
        reverb = value.reverb.copy(
            wetPercent = value.reverb.wetPercent.coerceIn(0, 50),
        ),
    )
}
