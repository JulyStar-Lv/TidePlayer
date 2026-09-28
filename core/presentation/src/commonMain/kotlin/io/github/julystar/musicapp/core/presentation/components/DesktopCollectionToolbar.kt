package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.core.presentation.theme.LocalDesignIsDarkTheme
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.icon_search
import musicapp.core.presentation.generated.resources.icon_sort
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DesktopCollectionToolbar(
    title: String,
    query: String,
    searchHint: String,
    onQueryChange: (String) -> Unit,
    sortDescription: String,
    onSortClick: () -> Unit,
    sortMenu: @Composable () -> Unit,
    extraContentHeight: Dp = 0.dp,
    extraContent: @Composable () -> Unit = {},
) {
    LiquidGlassActionBar(
        title = title,
        collapseFraction = 1f,
        extraContentHeight = extraContentHeight,
        content = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DesignTokens.adaptive.compactHeaderHeight)
                        .padding(start = TopAppBarDefaults.TitlePadding, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = MiuixTheme.textStyles.title3.fontSize,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        DesktopRoundButton(description = sortDescription, onClick = onSortClick) {
                            Icon(
                                painter = painterResource(CoreRes.drawable.icon_sort),
                                contentDescription = null,
                                tint = if (LocalDesignIsDarkTheme.current) MiuixTheme.colorScheme.onBackground else Color.Black,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                        sortMenu()
                    }
                    Spacer(Modifier.width(8.dp))
                    DesktopSearchField(value = query, hint = searchHint, onValueChange = onQueryChange)
                }
                extraContent()
            }
        },
    )
}

@Composable
private fun DesktopSearchField(
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
) {
    val isDark = LocalDesignIsDarkTheme.current
    val foreground = if (isDark) Color.White else Color.Black
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .width(196.dp)
            .height(36.dp)
            .appleToolbarShadow(shape, isDark)
            .clip(shape)
            .background(if (isDark) Color.White.copy(alpha = 0.075f) else Color.White.copy(alpha = 0.55f))
            .border(
                0.5.dp,
                if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.86f),
                shape,
            )
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(CoreRes.drawable.icon_search),
            contentDescription = null,
            tint = foreground.copy(alpha = 0.48f),
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MiuixTheme.textStyles.body2.copy(color = foreground.copy(alpha = 0.86f), fontSize = 13.sp),
            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
            modifier = Modifier.weight(1f).semantics { contentDescription = hint },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = hint,
                            color = foreground.copy(alpha = 0.45f),
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun DesktopRoundButton(
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val isDark = LocalDesignIsDarkTheme.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val background = if (isDark) {
        Color.White.copy(alpha = when { pressed -> 0.14f; hovered -> 0.10f; else -> 0.075f })
    } else {
        Color.White.copy(alpha = when { pressed -> 0.70f; hovered -> 0.60f; else -> 0.55f })
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .appleToolbarShadow(CircleShape, isDark)
            .clip(CircleShape)
            .background(background)
            .border(0.5.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.86f), CircleShape)
            .hoverable(interaction)
            .semantics { contentDescription = description }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

private fun Modifier.appleToolbarShadow(shape: Shape, isDark: Boolean): Modifier =
    if (isDark) {
        this
    } else {
        dropShadow(
            shape = shape,
            shadow = Shadow(
                radius = 36.dp,
                offset = DpOffset(0.dp, 6.dp),
                color = Color.Black.copy(alpha = 0.04f),
            ),
        ).dropShadow(
            shape = shape,
            shadow = Shadow(
                radius = 16.dp,
                offset = DpOffset(0.dp, 5.dp),
                color = Color.Black.copy(alpha = 0.04f),
            ),
        )
    }
