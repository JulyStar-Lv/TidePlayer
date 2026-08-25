package io.github.julystar.musicapp.core.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Shared brand tokens from Design/docs/TidePlayer-PDS-v3.md and the Figma Make design system.
 * Keep these public so feature modules can use the same TidePlayer visual language without
 * duplicating hex values.
 */
object DesignPalette {
    val BrandPink = Color(0xFFFF5B8A)
    val FavoriteRed = Color(0xFFFA233B)
    val DefaultManualThemeSeed = BrandPink
    val BrandButtonLight = Color(0xFFFA233B)
    val BrandButtonDark = Color(0xFFFA2E48)
    val SecondaryButtonLight = Color(0xFFECECEC)
    val SecondaryButtonDark = Color(0xFF404141)
    val OnSecondaryButtonLight = Color(0xFF242424)
    val OnSecondaryButtonDark = Color(0xFFE2E2E2)
    val SecondaryTextLight = Color(0xFF6E6E73)
    val SecondaryTextDark = Color(0xFF98989D)
    val TertiaryTextLight = Color(0xFF8E8E93)
    val TertiaryTextDark = Color(0xFF8E8E93)
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
    return MiuixTheme.colorScheme.primary
}

@Composable
internal fun designOnPrimaryButtonColor(): Color {
    return MiuixTheme.colorScheme.onPrimary
}

@Composable
internal fun designSecondaryButtonColor(): Color {
    return if (usesDarkButtonPalette()) {
        DesignPalette.SecondaryButtonDark
    } else {
        DesignPalette.SecondaryButtonLight
    }
}

@Composable
internal fun designOnSecondaryButtonColor(): Color {
    return if (usesDarkButtonPalette()) {
        DesignPalette.OnSecondaryButtonDark
    } else {
        DesignPalette.OnSecondaryButtonLight
    }
}

@Composable
private fun usesDarkButtonPalette(): Boolean {
    return MiuixTheme.colorScheme.background.luminance() < 0.5f
}
