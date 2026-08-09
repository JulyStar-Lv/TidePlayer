package io.github.julystar.musicapp.core.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.julystar.musicapp.core.domain.model.DEFAULT_MANUAL_THEME_SEED_ARGB
import io.github.julystar.musicapp.core.presentation.platform.SystemBarsEffect
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

enum class AppThemeMode {
    FollowSystem,
    Light,
    Dark,
}

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: AppThemeMode = AppThemeMode.FollowSystem,
    themeSeedState: ThemeSeedState = ThemeSeedState.Default,
    forceDarkSystemBars: Boolean = false,
    manageSystemBars: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorSchemeMode = when (themeMode) {
        AppThemeMode.FollowSystem -> ColorSchemeMode.MonetSystem
        AppThemeMode.Light -> ColorSchemeMode.MonetLight
        AppThemeMode.Dark -> ColorSchemeMode.MonetDark
    }
    val effectiveDarkTheme = when (themeMode) {
        AppThemeMode.FollowSystem -> darkTheme
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }
    val targetSeed = Color(themeSeedState.effectiveSeedArgb.toInt())
    val animatedSeed by animateColorAsState(
        targetValue = targetSeed,
        animationSpec = tween(DesignMotion().themeMillis),
        label = "Theme color transition",
    )
    val controller = remember(colorSchemeMode, effectiveDarkTheme, animatedSeed) {
        ThemeController(
            colorSchemeMode = colorSchemeMode,
            lightColors = DesignLightColors,
            darkColors = DesignDarkColors,
            keyColor = animatedSeed,
            colorSpec = ThemeColorSpec.Spec2021,
            paletteStyle = ThemePaletteStyle.TonalSpot,
            isDark = effectiveDarkTheme,
        )
    }
    val textStyles = designTextStyles()
    if (manageSystemBars) {
        SystemBarsEffect(isDarkTheme = effectiveDarkTheme || forceDarkSystemBars)
    }

    val colors = controller.currentColors().withResolvedPrimary(
        themeSeedState = themeSeedState,
        darkTheme = effectiveDarkTheme,
    )
    MiuixTheme(colors = colors, textStyles = textStyles) {
        CompositionLocalProvider(
            LocalDesignSpacing provides DesignSpacing(),
            LocalDesignShapes provides DesignShapes(),
            LocalDesignMotion provides DesignMotion(),
            LocalDesignBlur provides DesignBlur(),
            LocalDesignElevation provides DesignElevation(),
            LocalDesignAdaptive provides DesignAdaptive(),
            LocalDesignNavigation provides DesignNavigation(),
            LocalDesignPlayer provides DesignPlayer(),
            LocalDesignColorPicker provides DesignColorPicker(),
            LocalThemeSeedState provides themeSeedState,
            content = content,
        )
    }
}

@Composable
fun ThemeSeedPreviewTheme(
    seedColor: Color,
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val controller = remember(seedColor, darkTheme) {
        ThemeController(
            colorSchemeMode = if (darkTheme) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
            keyColor = seedColor,
            colorSpec = ThemeColorSpec.Spec2021,
            paletteStyle = ThemePaletteStyle.TonalSpot,
            isDark = darkTheme,
        )
    }
    val colors = controller.currentColors().withManualPrimary(seedColor, darkTheme)
    MiuixTheme(colors = colors, textStyles = designTextStyles()) {
        content()
    }
}

private fun Colors.withResolvedPrimary(
    themeSeedState: ThemeSeedState,
    darkTheme: Boolean,
): Colors {
    return when (themeSeedState.source) {
        ThemeSeedSource.Artwork,
        ThemeSeedSource.PreviousArtwork,
        -> this

        ThemeSeedSource.Manual -> withManualPrimary(
            seedColor = Color(themeSeedState.effectiveSeedArgb.toInt()),
            darkTheme = darkTheme,
        )
    }
}

private fun Colors.withManualPrimary(seedColor: Color, darkTheme: Boolean): Colors {
    val isDefaultBrand = seedColor.toArgb().toUInt().toLong() == DEFAULT_MANUAL_THEME_SEED_ARGB
    val primaryColor = if (isDefaultBrand) {
        if (darkTheme) DesignPalette.BrandButtonDark else DesignPalette.BrandButtonLight
    } else {
        seedColor
    }
    val secondaryText = if (darkTheme) {
        DesignPalette.SecondaryTextDark
    } else {
        DesignPalette.SecondaryTextLight
    }
    val tertiaryText = if (darkTheme) {
        DesignPalette.TertiaryTextDark
    } else {
        DesignPalette.TertiaryTextLight
    }
    return copy(
        primary = primaryColor,
        onPrimary = if (isDefaultBrand) {
            DesignPalette.BrandButtonForeground
        } else {
            primaryColor.highContrastContentColor()
        },
        onBackgroundVariant = primaryColor,
        onSurfaceSecondary = if (isDefaultBrand) secondaryText else onSurfaceSecondary,
        onSurfaceVariantSummary = if (isDefaultBrand) secondaryText else onSurfaceVariantSummary,
        onSurfaceVariantActions = if (isDefaultBrand) tertiaryText else onSurfaceVariantActions,
        onSurfaceContainerVariant = if (isDefaultBrand) secondaryText else onSurfaceContainerVariant,
        onSurfaceContainerHigh = if (isDefaultBrand) secondaryText else onSurfaceContainerHigh,
        onSecondaryContainerVariant = if (isDefaultBrand) secondaryText else onSecondaryContainerVariant,
    )
}

private fun Color.highContrastContentColor(): Color {
    val backgroundLuminance = luminance().toDouble()
    val darkInk = Color(0xFF0D0B18)
    val darkContrast = (backgroundLuminance + 0.05) / (darkInk.luminance().toDouble() + 0.05)
    val lightContrast = 1.05 / (backgroundLuminance + 0.05)
    return if (darkContrast >= lightContrast) darkInk else Color.White
}

object DesignTokens {
    val spacing: DesignSpacing
        @Composable @ReadOnlyComposable
        get() = LocalDesignSpacing.current

    val shapes: DesignShapes
        @Composable @ReadOnlyComposable
        get() = LocalDesignShapes.current

    val motion: DesignMotion
        @Composable @ReadOnlyComposable
        get() = LocalDesignMotion.current

    val blur: DesignBlur
        @Composable @ReadOnlyComposable
        get() = LocalDesignBlur.current

    val elevation: DesignElevation
        @Composable @ReadOnlyComposable
        get() = LocalDesignElevation.current

    val adaptive: DesignAdaptive
        @Composable @ReadOnlyComposable
        get() = LocalDesignAdaptive.current

    val navigation: DesignNavigation
        @Composable @ReadOnlyComposable
        get() = LocalDesignNavigation.current

    val player: DesignPlayer
        @Composable @ReadOnlyComposable
        get() = LocalDesignPlayer.current

    val colorPicker: DesignColorPicker
        @Composable @ReadOnlyComposable
        get() = LocalDesignColorPicker.current
}

@Immutable
data class DesignSpacing(
    val none: Dp = 0.dp,
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val section: Dp = 20.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val huge: Dp = 40.dp,
    val xxl: Dp = 48.dp,
    val pageCompact: Dp = 16.dp,
    val pageMedium: Dp = 20.dp,
    val pageExpanded: Dp = 24.dp,
)

@Immutable
data class DesignShapes(
    val none: Dp = 0.dp,
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val compactCard: Dp = 18.dp,
    val md: Dp = 20.dp,
    val card: Dp = 24.dp,
    val lg: Dp = 28.dp,
    val xl: Dp = 36.dp,
    val xxl: Dp = 40.dp,
    val full: Dp = 999.dp,
)

@Immutable
data class DesignMotion(
    val instantMillis: Int = 100,
    val fastMillis: Int = 180,
    val standardMillis: Int = 280,
    val emphasizedMillis: Int = 380,
    val morphMillis: Int = 500,
    val themeMillis: Int = 400,
    val playerExpandMillis: Int = 380,
)

@Immutable
data class DesignBlur(
    val none: Dp = 0.dp,
    val light: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val heavy: Dp = 32.dp,
    val ultra: Dp = 48.dp,
)

@Immutable
data class DesignElevation(
    val surface: Dp = 0.dp,
    val card: Dp = 2.dp,
    val popup: Dp = 12.dp,
    val floating: Dp = 20.dp,
    val overlay: Dp = 28.dp,
)

@Immutable
data class DesignAdaptive(
    val compactMaxWidth: Dp = 599.dp,
    val mediumMaxWidth: Dp = 839.dp,
    val expandedMaxWidth: Dp = 1279.dp,
    val largeMinWidth: Dp = 1280.dp,
    val extraLargeMinWidth: Dp = 1600.dp,
    val contentMaxWidth: Dp = 1180.dp,
    val detailMaxWidth: Dp = 720.dp,
    val sidebarWidth: Dp = 224.dp,
    val railWidth: Dp = 80.dp,
    val minimumTouchTarget: Dp = 48.dp,
    val compactHeaderCollapseDistance: Dp = 48.dp,
    val compactHeaderHeight: Dp = 58.dp,
)

@Immutable
data class DesignNavigation(
    val compactBarHeight: Dp = 62.dp,
    val compactBarDividerHeight: Dp = 1.dp,
    val compactSelectedIndicatorWidth: Dp = 48.dp,
    val compactSelectedIndicatorHeight: Dp = 28.dp,
    val compactIconSize: Dp = 20.dp,
    val compactLabelSize: TextUnit = 10.sp,
)

@Immutable
data class DesignPlayer(
    val miniBarHeight: Dp = 72.dp,
    val compactMiniBarHeight: Dp = 76.dp,
)

@Immutable
data class DesignColorPicker(
    val swatchSize: Dp = 48.dp,
    val dialogMaxWidth: Dp = 760.dp,
    val mediumDialogMaxWidth: Dp = 640.dp,
    val contentMaxHeight: Dp = 720.dp,
    val saturationValueHeight: Dp = 180.dp,
    val saturationValueCompactMinHeight: Dp = 160.dp,
    val indicatorSize: Dp = 20.dp,
    val hueVisualHeight: Dp = 32.dp,
    val gridGap: Dp = 12.dp,
    val sectionGap: Dp = 20.dp,
)

private val LocalDesignSpacing = staticCompositionLocalOf { DesignSpacing() }
private val LocalDesignShapes = staticCompositionLocalOf { DesignShapes() }
private val LocalDesignMotion = staticCompositionLocalOf { DesignMotion() }
private val LocalDesignBlur = staticCompositionLocalOf { DesignBlur() }
private val LocalDesignElevation = staticCompositionLocalOf { DesignElevation() }
private val LocalDesignAdaptive = staticCompositionLocalOf { DesignAdaptive() }
private val LocalDesignNavigation = staticCompositionLocalOf { DesignNavigation() }
private val LocalDesignPlayer = staticCompositionLocalOf { DesignPlayer() }
private val LocalDesignColorPicker = staticCompositionLocalOf { DesignColorPicker() }
val LocalThemeSeedState = staticCompositionLocalOf { ThemeSeedState.Default }
