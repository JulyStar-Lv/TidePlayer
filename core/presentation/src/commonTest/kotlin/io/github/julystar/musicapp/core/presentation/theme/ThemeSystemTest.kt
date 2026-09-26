package io.github.julystar.musicapp.core.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.julystar.musicapp.core.domain.model.AppThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

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

        assertEquals(ColorSchemeMode.Light, observedMode)
        assertFalse(observedDynamicColor)
        assertEquals(DesignPalette.BrandButtonLight, observedPrimary)
    }

    @Test
    fun `explicit theme mode updates descendants immediately`() = runComposeUiTest {
        var themeMode by mutableStateOf(AppThemeMode.Dark)
        var observedDarkTheme: Boolean? = null

        setContent {
            AppTheme(
                themeMode = themeMode,
                manageSystemBars = false,
            ) {
                observedDarkTheme = LocalDesignIsDarkTheme.current
            }
        }
        waitForIdle()
        assertEquals(true, observedDarkTheme)

        runOnIdle { themeMode = AppThemeMode.Light }
        waitForIdle()
        assertEquals(false, observedDarkTheme)
    }

    @Test
    fun `manual theme colors leave backgrounds and text at Miuix defaults`() = runComposeUiTest {
        listOf(
            Triple("brand", DesignPalette.DefaultManualThemeSeed, DesignPalette.BrandButtonDark),
            Triple("yellow", DesignPalette.SupportYellow, DesignPalette.SupportYellow),
            Triple("blue", DesignPalette.SupportBlue, DesignPalette.SupportBlue),
        ).forEach { (name, lightSeed, darkSeed) ->
            listOf(false, true).forEach { darkTheme ->
                val seed = if (darkTheme) darkSeed else lightSeed
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
                val defaults = if (darkTheme) darkColorScheme() else lightColorScheme()
                val expectedPrimary = if (name == "brand") {
                    if (darkTheme) DesignPalette.BrandButtonDark else DesignPalette.BrandButtonLight
                } else {
                    seed
                }
                assertEquals(expectedPrimary, observedColors.primary, "$name primary")
                assertEquals(defaults.background, observedColors.background, "$name background")
                assertEquals(defaults.surfaceContainer, observedColors.surfaceContainer, "$name surface")
                assertEquals(defaults.onBackground, observedColors.onBackground, "$name text")
                assertEquals(defaults.onBackgroundVariant, observedColors.onBackgroundVariant, "$name secondary text")
                assertContrastAtLeast(
                    foreground = observedColors.onBackground,
                    background = observedColors.background,
                    label = "$name ${if (darkTheme) "dark" else "light"} background",
                )
                assertContrastAtLeast(
                    foreground = observedColors.onBackgroundVariant,
                    background = observedColors.background,
                    label = "$name ${if (darkTheme) "dark" else "light"} background variant",
                    minimum = 3f,
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

    @Test
    fun `light and dark primary colors are independent`() = runComposeUiTest {
        listOf(
            false to Color(0xFF3D9AFF),
            true to Color(0xFF3DCA8A),
        ).forEach { (darkTheme, expectedPrimary) ->
            var observedPrimary = Color.Unspecified
            setContent {
                AppTheme(
                    themeMode = if (darkTheme) AppThemeMode.Dark else AppThemeMode.Light,
                    themeSeedState = ThemeSeedState(
                        manualSeedArgb = 0xFF3D9AFFL,
                        darkManualSeedArgb = 0xFF3DCA8AL,
                    ),
                    manageSystemBars = false,
                ) {
                    observedPrimary = MiuixTheme.colorScheme.primary
                }
            }
            waitForIdle()
            assertEquals(expectedPrimary, observedPrimary)
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
    minimum: Float = 4.5f,
) {
    val foregroundLuminance = foreground.luminance()
    val backgroundLuminance = background.luminance()
    val contrast = (maxOf(foregroundLuminance, backgroundLuminance) + 0.05f) /
        (minOf(foregroundLuminance, backgroundLuminance) + 0.05f)
    kotlin.test.assertTrue(contrast >= minimum, "$label contrast was $contrast")
}
