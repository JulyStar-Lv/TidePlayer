package io.github.julystar.musicapp.core.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.julystar.musicapp.core.domain.model.AppThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalTestApi::class)
class ThemeSystemTest {

    @Test
    fun `app theme exposes its Miuix controller state`() = runComposeUiTest {
        var observedMode: ColorSchemeMode? = null
        var observedDynamicColor = false
        var observedPrimary = Color.Unspecified

        setContent {
            AppTheme(
                themeMode = AppThemeMode.Light,
                manageSystemBars = false,
            ) {
                observedMode = MiuixTheme.colorSchemeMode
                observedDynamicColor = MiuixTheme.isDynamicColor
                observedPrimary = MiuixTheme.colorScheme.primary
            }
        }
        waitForIdle()

        assertEquals(ColorSchemeMode.MonetLight, observedMode)
        assertTrue(observedDynamicColor)
        assertEquals(DesignPalette.BrandButtonLight, observedPrimary)
    }

    @Test
    fun `Spec2025 previews keep representative seeds readable`() = runComposeUiTest {
        listOf(
            "brand" to DesignPalette.DefaultManualThemeSeed,
            "yellow" to DesignPalette.SupportYellow,
            "blue" to DesignPalette.SupportBlue,
        ).forEach { (name, seed) ->
            listOf(false, true).forEach { darkTheme ->
                var colors: ThemeColors? = null
                setContent {
                    ThemeSeedPreviewTheme(seedColor = seed, darkTheme = darkTheme) {
                        colors = ThemeColors(
                            background = MiuixTheme.colorScheme.background,
                            onBackground = MiuixTheme.colorScheme.onBackground,
                            onBackgroundVariant = MiuixTheme.colorScheme.onBackgroundVariant,
                            surfaceContainer = MiuixTheme.colorScheme.surfaceContainer,
                            onSurfaceContainer = MiuixTheme.colorScheme.onSurfaceContainer,
                            primary = MiuixTheme.colorScheme.primary,
                            onPrimary = MiuixTheme.colorScheme.onPrimary,
                        )
                    }
                }
                waitForIdle()

                val observedColors = requireNotNull(colors)
                assertContrastAtLeast(
                    foreground = observedColors.onBackground,
                    background = observedColors.background,
                    label = "$name ${if (darkTheme) "dark" else "light"} background",
                )
                assertContrastAtLeast(
                    foreground = observedColors.onBackgroundVariant,
                    background = observedColors.background,
                    label = "$name ${if (darkTheme) "dark" else "light"} background variant",
                )
                assertContrastAtLeast(
                    foreground = observedColors.onSurfaceContainer,
                    background = observedColors.surfaceContainer,
                    label = "$name ${if (darkTheme) "dark" else "light"} surface",
                )
                assertContrastAtLeast(
                    foreground = observedColors.onPrimary,
                    background = observedColors.primary,
                    label = "$name ${if (darkTheme) "dark" else "light"} primary",
                )
            }
        }
    }
}

private data class ThemeColors(
    val background: Color,
    val onBackground: Color,
    val onBackgroundVariant: Color,
    val surfaceContainer: Color,
    val onSurfaceContainer: Color,
    val primary: Color,
    val onPrimary: Color,
)

private fun assertContrastAtLeast(
    foreground: Color,
    background: Color,
    label: String,
) {
    val foregroundLuminance = foreground.luminance()
    val backgroundLuminance = background.luminance()
    val contrast = (maxOf(foregroundLuminance, backgroundLuminance) + 0.05f) /
        (minOf(foregroundLuminance, backgroundLuminance) + 0.05f)
    kotlin.test.assertTrue(contrast >= 4.5f, "$label contrast was $contrast")
}
