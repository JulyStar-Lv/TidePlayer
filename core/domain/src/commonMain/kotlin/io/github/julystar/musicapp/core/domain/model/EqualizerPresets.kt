package io.github.julystar.musicapp.core.domain.model

data class EqualizerPreset(
    val id: String,
    val bandGainsDb: List<Int>,
) {
    init {
        require(bandGainsDb.size == EQ_BAND_COUNT) {
            "An equalizer preset must contain exactly $EQ_BAND_COUNT bands"
        }
    }
}

val BUILT_IN_EQUALIZER_PRESETS: List<EqualizerPreset> = listOf(
    EqualizerPreset("flat", listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
    EqualizerPreset("pop", listOf(-1, 1, 3, 4, 2, -1, -2, -2, 1, 2)),
    EqualizerPreset("rock", listOf(4, 3, 2, 0, -1, 0, 2, 3, 4, 4)),
    EqualizerPreset("jazz", listOf(3, 2, 1, 2, -1, -1, 0, 1, 3, 4)),
    EqualizerPreset("classical", listOf(3, 2, 1, 0, -1, -1, 0, 1, 2, 3)),
    EqualizerPreset("vocal", listOf(-2, -1, 0, 1, 2, 3, 3, 2, 0, -1)),
    EqualizerPreset("bass_boost", listOf(6, 5, 4, 2, 1, 0, 0, 0, 0, 0)),
    EqualizerPreset("treble_boost", listOf(0, 0, 0, 0, 0, 1, 2, 4, 5, 6)),
)

fun GraphicEqualizerSettings.applyPreset(
    preset: EqualizerPreset,
): GraphicEqualizerSettings = copy(
    enabled = true,
    bandGainsDb = preset.bandGainsDb.map { gain ->
        gain.coerceIn(MIN_EQ_BAND_GAIN_DB, MAX_EQ_BAND_GAIN_DB)
    },
)

fun GraphicEqualizerSettings.matchingPreset(
    presets: List<EqualizerPreset> = BUILT_IN_EQUALIZER_PRESETS,
): EqualizerPreset? = presets.firstOrNull { preset ->
    bandGainsDb == preset.bandGainsDb
}

fun GraphicEqualizerSettings.resetEqualizer(): GraphicEqualizerSettings = copy(
    bandGainsDb = DEFAULT_EQ_BAND_GAINS_DB,
    qHundredths = DEFAULT_EQ_Q_HUNDREDTHS,
    preampTenthsDb = 0,
)
