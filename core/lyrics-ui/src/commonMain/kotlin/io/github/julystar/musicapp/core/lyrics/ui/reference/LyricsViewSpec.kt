package io.github.julystar.musicapp.core.lyrics.ui.reference

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Stable
data class LyricsViewSpec(
    val normalLineTextStyle: TextStyle,
    val accompanimentLineTextStyle: TextStyle,
    val phoneticTextStyle: TextStyle,
    val textColor: Color = Color.White,
    val blendMode: BlendMode = BlendMode.Plus,
    val useBlurEffect: Boolean = true,
    val animateViewportScroll: Boolean = false,
    val content: LyricsContentSpec = LyricsContentSpec(),
    val viewport: LyricsViewportSpec = LyricsViewportSpec(),
    val line: LyricsLineVisualSpec = LyricsLineVisualSpec(),
    val motion: LyricsMotionSpec = LyricsMotionSpec(),
    val progress: LyricsProgressSpec = LyricsProgressSpec(),
    val textAnimation: LyricsTextAnimationSpec = LyricsTextAnimationSpec(),
    val timing: LyricsTimingSpec = LyricsTimingSpec(),
    val breathingDots: KaraokeBreathingDotsDefaults = KaraokeBreathingDotsDefaults(),
) {
    companion object {
        fun default(
            normalLineTextStyle: TextStyle = TextStyle(
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                textMotion = TextMotion.Animated,
            ),
            accompanimentLineTextStyle: TextStyle = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textMotion = TextMotion.Animated,
            ),
            phoneticTextStyle: TextStyle = normalLineTextStyle.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
            ),
            textColor: Color = Color.White,
            breathingDots: KaraokeBreathingDotsDefaults = KaraokeBreathingDotsDefaults(),
            blendMode: BlendMode = BlendMode.Plus,
            useBlurEffect: Boolean = true,
            showTranslation: Boolean = true,
            showPhonetic: Boolean = true,
            animateViewportScroll: Boolean = false,
            focusedLineScale: Float = 1f,
            unfocusedLineScale: Float = 0.98f,
            activeLineAlpha: Float = 1f,
            inactiveLineAlpha: Float = 0.4f,
            offset: Dp = 32.dp,
            keepAliveZone: Dp = 100.dp,
            bottomContentInset: Dp = 0.dp,
            blurDelta: Float = 3f,
            topFadeLength: Dp = 20.dp,
            bottomFadeLength: Dp = 100.dp,
        ): LyricsViewSpec = LyricsViewSpec(
            normalLineTextStyle = normalLineTextStyle,
            accompanimentLineTextStyle = accompanimentLineTextStyle,
            phoneticTextStyle = phoneticTextStyle,
            textColor = textColor,
            breathingDots = breathingDots,
            blendMode = blendMode,
            useBlurEffect = useBlurEffect,
            animateViewportScroll = animateViewportScroll,
            content = LyricsContentSpec(
                showTranslation = showTranslation,
                showPhonetic = showPhonetic,
            ),
            viewport = LyricsViewportSpec(
                offset = offset,
                keepAliveZone = keepAliveZone,
                bottomContentInset = bottomContentInset,
                topFadeLength = topFadeLength,
                bottomFadeLength = bottomFadeLength,
            ),
            line = LyricsLineVisualSpec(
                focusedScale = focusedLineScale,
                unfocusedScale = unfocusedLineScale,
                activeAlpha = activeLineAlpha,
                inactiveAlpha = inactiveLineAlpha,
                blurDelta = blurDelta,
            ),
        )

        fun modern(
            normalLineTextStyle: TextStyle = TextStyle(
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textMotion = TextMotion.Animated,
                lineHeight = 38.sp,
            ),
            accompanimentLineTextStyle: TextStyle = TextStyle(
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                textMotion = TextMotion.Animated,
                lineHeight = 24.sp,
            ),
            phoneticTextStyle: TextStyle = normalLineTextStyle.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
            ),
            textColor: Color = Color.White,
            showTranslation: Boolean = true,
            showPhonetic: Boolean = true,
            useBlurEffect: Boolean = true,
            animateViewportScroll: Boolean = false,
            offset: Dp = 72.dp,
            keepAliveZone: Dp = 128.dp,
            bottomContentInset: Dp = 0.dp,
            blurDelta: Float = 2.6f,
            topFadeLength: Dp = 20.dp,
            bottomFadeLength: Dp = 100.dp,
        ): LyricsViewSpec = default(
            normalLineTextStyle = normalLineTextStyle,
            accompanimentLineTextStyle = accompanimentLineTextStyle,
            phoneticTextStyle = phoneticTextStyle,
            textColor = textColor,
            showTranslation = showTranslation,
            showPhonetic = showPhonetic,
            useBlurEffect = useBlurEffect,
            animateViewportScroll = animateViewportScroll,
            focusedLineScale = 1.015f,
            unfocusedLineScale = 0.965f,
            activeLineAlpha = 1f,
            inactiveLineAlpha = 0.28f,
            offset = offset,
            keepAliveZone = keepAliveZone,
            bottomContentInset = bottomContentInset,
            blurDelta = blurDelta,
            topFadeLength = topFadeLength,
            bottomFadeLength = bottomFadeLength,
        )

        fun compact(
            normalLineTextStyle: TextStyle = TextStyle(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textMotion = TextMotion.Animated,
                lineHeight = 34.sp,
            ),
            accompanimentLineTextStyle: TextStyle = TextStyle(
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textMotion = TextMotion.Animated,
                lineHeight = 23.sp,
            ),
            phoneticTextStyle: TextStyle = normalLineTextStyle.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
            ),
            textColor: Color = Color.White,
        ): LyricsViewSpec = modern(
            normalLineTextStyle = normalLineTextStyle,
            accompanimentLineTextStyle = accompanimentLineTextStyle,
            phoneticTextStyle = phoneticTextStyle,
            textColor = textColor,
            useBlurEffect = false,
            animateViewportScroll = true,
            bottomContentInset = 96.dp,
        )
    }
}

@Stable
data class LyricsContentSpec(
    val showTranslation: Boolean = true,
    val showPhonetic: Boolean = true,
)

@Stable
data class LyricsViewportSpec(
    val offset: Dp = 32.dp,
    val keepAliveZone: Dp = 100.dp,
    val bottomContentInset: Dp = 0.dp,
    val topFadeLength: Dp = 20.dp,
    val bottomFadeLength: Dp = 100.dp,
)

@Stable
data class LyricsLineVisualSpec(
    val balancedLineWrap: Boolean = true,
    val focusedScale: Float = 1f,
    val unfocusedScale: Float = 0.98f,
    val activeAlpha: Float = 1f,
    val inactiveAlpha: Float = 0.4f,
    val accompanimentActiveAlpha: Float = 0.6f,
    val accompanimentInactiveAlpha: Float = 0.2f,
    val clickCornerRadius: Dp = 8.dp,
    val blurDelta: Float = 3f,
    val contentVerticalPadding: Dp = 8.dp,
    val mainHorizontalPadding: Dp = 16.dp,
    val accompanimentHorizontalPadding: Dp = 0.dp,
    val contentSpacing: Dp = 2.dp,
    val translationAlpha: Float = 0.4f,
    val linePhoneticAlpha: Float = 0.6f,
    val syllablePhoneticAlpha: Float = 0.4f,
)

@Stable
data class LyricsMotionSpec(
    val initialPlacementSuppressionMs: Long = 80L,
    val focusFollowPlacementSuppressionMs: Long = 0L,
    val focusJumpPlacementSuppressionMs: Long = 220L,
    val focusedLineAlignmentCorrectionPasses: Int = 18,
    val focusedLineAlignmentTolerancePx: Float = 1f,
    val placementSpringDampingRatio: Float = 0.95f,
    val maxPlacementStiffness: Float = 120f,
    val placementStiffnessDistanceStep: Float = 20f,
    val minPlacementStiffness: Float = 20f,
    val focusedScaleAnimationDurationMs: Int = 600,
    val unfocusedScaleAnimationDurationMs: Int = 300,
    val blurAnimationDurationMs: Int = 300,
    val accompanimentVisibilityAnimationDurationMs: Int = 600,
    val manualScrollResumeDelayMs: Long = 4_000L,
) {
    fun placementStiffness(distanceWeight: Int): Float =
        (maxPlacementStiffness - distanceWeight * placementStiffnessDistanceStep)
            .coerceAtLeast(minPlacementStiffness)
}

@Stable
data class LyricsProgressSpec(
    val inactiveAlpha: Float = 0.2f,
    val featherWidthPx: Float = 100f,
)

@Stable
data class LyricsTextAnimationSpec(
    val simpleAnimationDurationMs: Float = 700f,
    val simpleLiftPx: Float = 4f,
    val fastCharacterThresholdMs: Float = 200f,
    val advancedWordMinDurationMs: Long = 1000L,
    val advancedDurationFraction: Float = 0.8f,
    val advancedLiftPx: Float = 4f,
    val advancedShadowBlurPx: Float = 10f,
    val advancedShadowAlpha: Float = 0.4f,
    val maxDip: Double = 0.5,
    val maxSwell: Double = 0.1,
    val phoneticVerticalGap: Dp = 4.dp,
)

@Stable
data class LyricsTimingSpec(
    val instrumentalBreakMinDurationMs: Int = 5000,
    val accompanimentVisibilityPaddingMs: Int = 600,
)
