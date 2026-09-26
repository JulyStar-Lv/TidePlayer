package io.github.julystar.musicapp.feature.onboarding.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.components.appIconPainter
import io.github.julystar.musicapp.core.presentation.theme.DesignPalette
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import musicapp.feature.onboarding.generated.resources.Res
import musicapp.feature.onboarding.generated.resources.onboarding_app_name
import musicapp.feature.onboarding.generated.resources.onboarding_enter
import musicapp.feature.onboarding.generated.resources.onboarding_footer
import musicapp.feature.onboarding.generated.resources.onboarding_principle_adaptive
import musicapp.feature.onboarding.generated.resources.onboarding_principle_calm
import musicapp.feature.onboarding.generated.resources.onboarding_principle_content_first
import musicapp.feature.onboarding.generated.resources.onboarding_principle_cross_platform
import musicapp.feature.onboarding.generated.resources.onboarding_principle_immersive
import musicapp.feature.onboarding.generated.resources.onboarding_principle_music_first
import musicapp.feature.onboarding.generated.resources.onboarding_principle_native
import musicapp.feature.onboarding.generated.resources.onboarding_principle_plugin_driven
import musicapp.feature.onboarding.generated.resources.onboarding_principle_simple
import musicapp.feature.onboarding.generated.resources.onboarding_tagline
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onAction: (OnboardingAction) -> Unit,
) {
    val spacing = DesignTokens.spacing
    val appName = stringResource(Res.string.onboarding_app_name)
    val principles = listOf(
        Res.string.onboarding_principle_simple,
        Res.string.onboarding_principle_calm,
        Res.string.onboarding_principle_immersive,
        Res.string.onboarding_principle_music_first,
        Res.string.onboarding_principle_content_first,
        Res.string.onboarding_principle_adaptive,
        Res.string.onboarding_principle_native,
        Res.string.onboarding_principle_cross_platform,
        Res.string.onboarding_principle_plugin_driven,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(
                        MiuixTheme.colorScheme.primary.copy(alpha = 0.22f),
                        Color.Transparent,
                    ),
                    radius = 780f,
                ),
            )
            .background(
                Brush.radialGradient(
                    listOf(
                        DesignPalette.Secondary.copy(alpha = 0.20f),
                        Color.Transparent,
                    ),
                    radius = 860f,
                ),
            )
            .background(Color(0xD60C0A14))
            .padding(horizontal = spacing.xl, vertical = spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .heightIn(min = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = appIconPainter(),
                contentDescription = appName,
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(28.dp)),
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = appName,

                style = MiuixTheme.textStyles.title1,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.onboarding_tagline),
                color = Color(0xFF9B97B0),
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(modifier = Modifier.height(48.dp))
            principles.chunked(3).forEachIndexed { index, row ->
                PrincipleRow(row)
                if (index != principles.chunked(3).lastIndex) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            Spacer(modifier = Modifier.height(44.dp))
            Row(
                modifier = Modifier
                    .height(56.dp)
                    .clip(RoundedCornerShape(DesignTokens.shapes.full))
                    .background(
                        Brush.linearGradient(
                            listOf(MiuixTheme.colorScheme.primary, MiuixTheme.colorScheme.secondary),
                        ),
                    )
                    .clickable { onAction(OnboardingAction.Finish) }
                    .padding(horizontal = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "▶",
                    color = Color.White,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(Res.string.onboarding_enter),
                    color = Color.White,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(56.dp))
            Text(
                text = stringResource(Res.string.onboarding_footer),
                color = Color(0xFF9B97B0),
                style = MiuixTheme.textStyles.footnote2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PrincipleRow(principles: List<StringResource>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        principles.forEach { principle ->
            PrincipleChip(text = stringResource(principle))
        }
    }
}

@Composable
private fun PrincipleChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(DesignTokens.shapes.full))
            .background(Color(0x99161224))
            .border(
                width = 1.dp,
                color = Color(0x12F0EDF8),
                shape = RoundedCornerShape(DesignTokens.shapes.full),
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color(0xFFF0EDF8),
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
