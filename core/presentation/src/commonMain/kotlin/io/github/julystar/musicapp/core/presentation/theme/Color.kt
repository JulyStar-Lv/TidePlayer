package io.github.julystar.musicapp.core.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.julystar.musicapp.core.domain.model.DEFAULT_MANUAL_THEME_SEED_ARGB
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Shared brand tokens from Design/docs/TidePlayer-PDS-v3.md and the Figma Make design system.
 * Keep these public so feature modules can use the same MelodyTrove visual language without
 * duplicating hex values.
 */
object DesignPalette {
    val BrandPink = Color(0xFFFF5B8A)
    val DefaultManualThemeSeed = BrandPink
    val BrandButtonLight = Color(0xFFFA233B)
    val BrandButtonDark = Color(0xFFFA2E48)
    val BrandButtonForeground = Color.White
    val SecondaryButtonLight = Color(0xFFECECEC)
    val SecondaryButtonDark = Color(0xFF404141)
    val OnSecondaryButtonLight = Color(0xFF242424)
    val OnSecondaryButtonDark = Color(0xFFE2E2E2)
    val Secondary = Color(0xFF7A6CFF)
    val SupportBlue = Color(0xFF3D9AFF)
    val SupportOrange = Color(0xFFFF8A3D)
    val SupportGreen = Color(0xFF3DCA8A)
    val SupportYellow = Color(0xFFFFD93D)
    val SupportLime = Color(0xFFA4C936)
    val SupportEmerald = Color(0xFF2EAD72)
    val SupportCyan = Color(0xFF29C5C8)
    val SupportTeal = Color(0xFF117B8A)
}

@Immutable
data class DesignGradient(
    val colors: ImmutableList<Color>,
    val angleDegrees: Int = 90,
)

object DesignGradients {
    val PurplePink = DesignGradient(
        colors = persistentListOf(DesignPalette.Secondary, DesignPalette.BrandPink),
    )
    val PinkPurple = DesignGradient(
        colors = persistentListOf(DesignPalette.BrandPink, DesignPalette.Secondary),
    )
    val PinkOrange = DesignGradient(
        colors = persistentListOf(DesignPalette.BrandPink, DesignPalette.SupportOrange),
    )
    val OrangeYellow = DesignGradient(
        colors = persistentListOf(DesignPalette.SupportOrange, DesignPalette.SupportYellow),
    )
    val GreenBlue = DesignGradient(
        colors = persistentListOf(DesignPalette.SupportGreen, DesignPalette.SupportBlue),
    )
    val BluePurple = DesignGradient(
        colors = persistentListOf(DesignPalette.SupportBlue, DesignPalette.Secondary),
    )
    val LimeEmerald = DesignGradient(
        colors = persistentListOf(DesignPalette.SupportLime, DesignPalette.SupportEmerald),
    )
    val CyanTeal = DesignGradient(
        colors = persistentListOf(DesignPalette.SupportCyan, DesignPalette.SupportTeal),
    )
    val PinkYellow = DesignGradient(
        colors = persistentListOf(DesignPalette.BrandPink, DesignPalette.SupportYellow),
    )
    val BlueGreenPurple = DesignGradient(
        colors = persistentListOf(
            DesignPalette.SupportBlue,
            DesignPalette.SupportGreen,
            DesignPalette.Secondary,
        ),
    )
    val Brand = PinkPurple
}

@Composable
internal fun designPrimaryButtonColor(): Color {
    if (!usesBrandButtonPalette()) return MiuixTheme.colorScheme.primary
    return if (usesDarkButtonPalette()) {
        DesignPalette.BrandButtonDark
    } else {
        DesignPalette.BrandButtonLight
    }
}

@Composable
internal fun designOnPrimaryButtonColor(): Color {
    return if (usesBrandButtonPalette()) {
        DesignPalette.BrandButtonForeground
    } else {
        MiuixTheme.colorScheme.onPrimary
    }
}

@Composable
internal fun designSecondaryButtonColor(): Color {
    if (!usesBrandButtonPalette()) return MiuixTheme.colorScheme.secondaryVariant
    return if (usesDarkButtonPalette()) {
        DesignPalette.SecondaryButtonDark
    } else {
        DesignPalette.SecondaryButtonLight
    }
}

@Composable
internal fun designOnSecondaryButtonColor(): Color {
    if (!usesBrandButtonPalette()) return MiuixTheme.colorScheme.onSecondaryVariant
    return if (usesDarkButtonPalette()) {
        DesignPalette.OnSecondaryButtonDark
    } else {
        DesignPalette.OnSecondaryButtonLight
    }
}

@Composable
private fun usesBrandButtonPalette(): Boolean {
    return LocalThemeSeedState.current.effectiveSeedArgb == DEFAULT_MANUAL_THEME_SEED_ARGB
}

@Composable
private fun usesDarkButtonPalette(): Boolean {
    return MiuixTheme.colorScheme.background.luminance() < 0.5f
}

internal val DesignLightColors = Colors(
    primary = DesignPalette.BrandButtonLight,
    onPrimary = DesignPalette.BrandButtonForeground,
    primaryVariant = DesignPalette.Secondary,
    onPrimaryVariant = Color.White,
    error = Color(0xFFEF4444),
    onError = Color.White,
    errorContainer = Color(0xFFFFE9EF),
    onErrorContainer = Color(0xFFD91F55),
    disabledPrimary = Color(0xFFC5C2D8),
    disabledOnPrimary = Color(0xFFEAE7F5),
    disabledPrimaryButton = Color(0xFFC5C2D8),
    disabledOnPrimaryButton = Color.White,
    disabledPrimarySlider = Color(0xFFC5C2D8),
    primaryContainer = DesignPalette.DefaultManualThemeSeed,
    onPrimaryContainer = Color.White,
    secondary = DesignPalette.Secondary,
    onSecondary = Color.White,
    secondaryVariant = Color(0xFFEAE7F5),
    onSecondaryVariant = Color(0xFF0D0B18),
    disabledSecondary = Color(0xFFEAE7F5),
    disabledOnSecondary = Color(0xFF9B97B0),
    disabledSecondaryVariant = Color(0xFFEAE7F5),
    disabledOnSecondaryVariant = Color(0xFF9B97B0),
    secondaryContainer = Color(0xFFEAE7F5),
    onSecondaryContainer = Color(0xFF0D0B18),
    secondaryContainerVariant = Color(0xFFE2DEF5),
    onSecondaryContainerVariant = Color(0xFF6B6880),
    tertiaryContainer = Color(0xFFFFE9F0),
    onTertiaryContainer = DesignPalette.DefaultManualThemeSeed,
    tertiaryContainerVariant = Color(0xFFEAE7F5),
    background = Color(0xFFF4F2FA),
    onBackground = Color(0xFF0D0B18),
    onBackgroundVariant = Color(0xFF6B6880),
    surface = Color(0xFFF4F2FA),
    onSurface = Color(0xFF0D0B18),
    surfaceVariant = Color(0xFFEAE7F5),
    onSurfaceSecondary = Color(0xFF6B6880),
    onSurfaceVariantSummary = Color(0xFF6B6880),
    onSurfaceVariantActions = Color(0xFF9B97B0),
    disabledOnSurface = Color(0xFFC5C2D8),
    surfaceContainer = Color(0xFFFFFFFF),
    onSurfaceContainer = Color(0xFF0D0B18),
    onSurfaceContainerVariant = Color(0xFF6B6880),
    surfaceContainerHigh = Color(0xFFEAE7F5),
    onSurfaceContainerHigh = Color(0xFF6B6880),
    surfaceContainerHighest = Color(0xFFECEAF5),
    onSurfaceContainerHighest = Color(0xFF0D0B18),
    outline = Color(0x140D0B18),
    dividerLine = Color(0x140D0B18),
    windowDimming = Color.Black.copy(alpha = 0.32f),
    sliderKeyPoint = Color(0xFFC5C2D8).copy(alpha = 0.3f),
    sliderKeyPointForeground = DesignPalette.DefaultManualThemeSeed,
    sliderBackground = Color.Black.copy(alpha = 0.08f),
)

internal val DesignDarkColors = Colors(
    primary = DesignPalette.BrandButtonDark,
    onPrimary = DesignPalette.BrandButtonForeground,
    primaryVariant = DesignPalette.Secondary,
    onPrimaryVariant = Color.White,
    error = Color(0xFFEF4444),
    onError = Color.White,
    errorContainer = Color(0xFF3A1B27),
    onErrorContainer = Color(0xFFFF8FB0),
    disabledPrimary = Color(0xFF3A3555),
    disabledOnPrimary = Color(0xFF1E1A30),
    disabledPrimaryButton = Color(0xFF3A3555),
    disabledOnPrimaryButton = Color(0xFF9B97B0),
    disabledPrimarySlider = Color(0xFF3A3555),
    primaryContainer = DesignPalette.DefaultManualThemeSeed,
    onPrimaryContainer = Color.White,
    secondary = DesignPalette.Secondary,
    onSecondary = Color.White,
    secondaryVariant = Color(0xFF1E1A30),
    onSecondaryVariant = Color(0xFFF0EDF8),
    disabledSecondary = Color(0xFF161224),
    disabledOnSecondary = Color(0xFF6B6880),
    disabledSecondaryVariant = Color(0xFF1E1A30),
    disabledOnSecondaryVariant = Color(0xFF6B6880),
    secondaryContainer = Color(0xFF1E1A30),
    onSecondaryContainer = Color(0xFFF0EDF8),
    secondaryContainerVariant = Color(0xFF3A3555),
    onSecondaryContainerVariant = Color(0xFF9B97B0),
    tertiaryContainer = Color(0xFF3A1B27),
    onTertiaryContainer = Color(0xFFFF8FB0),
    tertiaryContainerVariant = Color(0xFF1E1A30),
    background = Color(0xFF0C0A14),
    onBackground = Color(0xFFF0EDF8),
    onBackgroundVariant = Color(0xFF9B97B0),
    surface = Color(0xFF0C0A14),
    onSurface = Color(0xFFF0EDF8),
    surfaceVariant = Color(0xFF1E1A30),
    onSurfaceSecondary = Color(0xFF9B97B0),
    onSurfaceVariantSummary = Color(0xFF9B97B0),
    onSurfaceVariantActions = Color(0xFF9B97B0),
    disabledOnSurface = Color(0xFF6B6880),
    surfaceContainer = Color(0xFF161224),
    onSurfaceContainer = Color(0xFFF0EDF8),
    onSurfaceContainerVariant = Color(0xFF9B97B0),
    surfaceContainerHigh = Color(0xFF1E1A30),
    onSurfaceContainerHigh = Color(0xFF9B97B0),
    surfaceContainerHighest = Color(0xFF1E1A30),
    onSurfaceContainerHighest = Color(0xFFF0EDF8),
    outline = Color(0x12F0EDF8),
    dividerLine = Color(0x12F0EDF8),
    windowDimming = Color.Black.copy(alpha = 0.7f),
    sliderKeyPoint = Color(0xFF9B97B0).copy(alpha = 0.35f),
    sliderKeyPointForeground = DesignPalette.DefaultManualThemeSeed,
    sliderBackground = Color.White.copy(alpha = 0.12f),
)
