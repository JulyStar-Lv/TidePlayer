package io.github.julystar.musicapp.core.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalTestApi::class)
class ThemeVisualRegressionTest {

    @Test
    fun rendersSpec2025ThemeBoardsForRepresentativeSeeds() = runComposeUiTest {
        val outputDirectory = (
            System.getProperty("theme.visual.outputDir")
                ?: System.getenv("THEME_VISUAL_OUTPUT_DIR")
            )?.let(::File)
        outputDirectory?.mkdirs()

        listOf(
            "brand-light" to VisualThemeCase(DesignPalette.DefaultManualThemeSeed, false),
            "brand-dark" to VisualThemeCase(DesignPalette.DefaultManualThemeSeed, true),
            "yellow-light" to VisualThemeCase(DesignPalette.SupportYellow, false),
            "yellow-dark" to VisualThemeCase(DesignPalette.SupportYellow, true),
            "blue-light" to VisualThemeCase(DesignPalette.SupportBlue, false),
            "blue-dark" to VisualThemeCase(DesignPalette.SupportBlue, true),
        ).forEach { (name, themeCase) ->
            setContent {
                ThemeSeedPreviewTheme(
                    seedColor = themeCase.seed,
                    darkTheme = themeCase.darkTheme,
                ) {
                    ThemeVisualRegressionBoard(name)
                }
            }
            waitForIdle()

            val image = onNodeWithTag(ThemeBoardTag).captureToImage()
            assertTrue(image.width > 0 && image.height > 0)
            outputDirectory?.let { image.writePng(File(it, "$name.png")) }
        }
    }
}

private const val ThemeBoardTag = "theme-visual-regression-board"

private data class VisualThemeCase(
    val seed: Color,
    val darkTheme: Boolean,
)

@Composable
private fun ThemeVisualRegressionBoard(name: String) {
    Column(
        modifier = Modifier
            .width(760.dp)
            .background(MiuixTheme.colorScheme.background)
            .padding(24.dp)
            .testTag(ThemeBoardTag),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Spec 2025 · $name",
            color = MiuixTheme.colorScheme.onBackground,
            style = MiuixTheme.textStyles.title2,
        )
        Text(
            text = "Artwork and manual colors should preserve hierarchy and readable contrast.",
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            style = MiuixTheme.textStyles.body2,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Current theme",
                color = MiuixTheme.colorScheme.onSurfaceContainer,
                style = MiuixTheme.textStyles.body1,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Surface, outline, primary action, and secondary action",
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                style = MiuixTheme.textStyles.footnote1,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MiuixTheme.colorScheme.secondaryVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.68f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MiuixTheme.colorScheme.primary),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("Play")
                }
                Button(onClick = {}) {
                    Text("Later")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ColorRoleSwatch("Primary", MiuixTheme.colorScheme.primary)
            ColorRoleSwatch("Surface", MiuixTheme.colorScheme.surfaceContainer)
            ColorRoleSwatch("Outline", MiuixTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun ColorRoleSwatch(label: String, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(color),
        )
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            style = MiuixTheme.textStyles.footnote1,
        )
    }
}

private fun ImageBitmap.writePng(output: File) {
    val image = Image.makeFromBitmap(asSkiaBitmap())
    try {
        val data = requireNotNull(image.encodeToData(EncodedImageFormat.PNG))
        try {
            output.writeBytes(data.bytes)
        } finally {
            data.close()
        }
    } finally {
        image.close()
    }
}
