package io.github.julystar.musicapp.core.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import musicapp.core.presentation.generated.resources.Res
import musicapp.core.presentation.generated.resources.noto_sans_sc_wght
import org.jetbrains.compose.resources.Font

@Composable
internal actual fun appSansFontFamily(): FontFamily = FontFamily(
    Font(Res.font.noto_sans_sc_wght, weight = FontWeight.Normal),
    Font(Res.font.noto_sans_sc_wght, weight = FontWeight.Medium),
    Font(Res.font.noto_sans_sc_wght, weight = FontWeight.SemiBold),
    Font(Res.font.noto_sans_sc_wght, weight = FontWeight.Bold),
)
