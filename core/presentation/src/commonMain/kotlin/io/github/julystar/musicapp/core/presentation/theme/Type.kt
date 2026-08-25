package io.github.julystar.musicapp.core.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import musicapp.core.presentation.generated.resources.Res
import musicapp.core.presentation.generated.resources.jetbrains_mono_wght
import musicapp.core.presentation.generated.resources.noto_sans_sc_wght
import musicapp.core.presentation.generated.resources.plus_jakarta_sans_wght
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.defaultTextStyles

object DesignFontFamilies {
    val JakartaSans: FontFamily
        @Composable
        get() = FontFamily(
            Font(Res.font.plus_jakarta_sans_wght, weight = FontWeight.Normal),
            Font(Res.font.plus_jakarta_sans_wght, weight = FontWeight.Medium),
            Font(Res.font.plus_jakarta_sans_wght, weight = FontWeight.SemiBold),
            Font(Res.font.plus_jakarta_sans_wght, weight = FontWeight.Bold),
        )

    val Sans: FontFamily
        @Composable
        get() = FontFamily(
            Font(Res.font.noto_sans_sc_wght, weight = FontWeight.Normal),
            Font(Res.font.noto_sans_sc_wght, weight = FontWeight.Medium),
            Font(Res.font.noto_sans_sc_wght, weight = FontWeight.SemiBold),
            Font(Res.font.noto_sans_sc_wght, weight = FontWeight.Bold),
        )

    val Mono: FontFamily
        @Composable
        get() = FontFamily(
            Font(Res.font.jetbrains_mono_wght, weight = FontWeight.Normal),
            Font(Res.font.jetbrains_mono_wght, weight = FontWeight.SemiBold),
        )
}

@Composable
internal fun designTextStyles(): TextStyles {
    val defaults = defaultTextStyles()
    val sans = DesignFontFamilies.Sans
    return defaults.copy(
        main = designTextStyle(size = 14, lineHeight = 20, fontFamily = sans),
        paragraph = designTextStyle(size = 14, lineHeight = 20, fontFamily = sans),
        body1 = designTextStyle(size = 14, lineHeight = 20, fontFamily = sans),
        body2 = designTextStyle(size = 13, lineHeight = 18, fontFamily = sans),
        button = designTextStyle(size = 14, lineHeight = 20, weight = FontWeight.SemiBold, fontFamily = sans),
        footnote1 = designTextStyle(size = 12, lineHeight = 16, fontFamily = sans),
        footnote2 = designTextStyle(size = 10, lineHeight = 14, weight = FontWeight.SemiBold, fontFamily = sans),
        headline1 = designTextStyle(size = 16, lineHeight = 22, weight = FontWeight.Medium, fontFamily = sans),
        headline2 = designTextStyle(size = 22, lineHeight = 28, weight = FontWeight.SemiBold, fontFamily = sans),
        subtitle = designTextStyle(size = 13, lineHeight = 18, fontFamily = sans),
        title1 = designTextStyle(size = 32, lineHeight = 40, weight = FontWeight.Bold, fontFamily = sans),
        title2 = designTextStyle(size = 22, lineHeight = 28, weight = FontWeight.SemiBold, fontFamily = sans),
        title3 = designTextStyle(size = 22, lineHeight = 28, weight = FontWeight.Medium, fontFamily = sans),
        title4 = designTextStyle(size = 14, lineHeight = 20, weight = FontWeight.Medium, fontFamily = sans),
    )
}

private fun designTextStyle(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    fontFamily: FontFamily,
): TextStyle = TextStyle(
    fontFamily = fontFamily,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp,
)
