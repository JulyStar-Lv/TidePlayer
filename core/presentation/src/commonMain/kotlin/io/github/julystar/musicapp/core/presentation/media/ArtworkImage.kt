package io.github.julystar.musicapp.core.presentation.media

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import io.github.julystar.musicapp.core.domain.model.Artwork
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import musicapp.core.presentation.generated.resources.Res
import musicapp.core.presentation.generated.resources.cover_default_image
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ArtworkImage(
    modifier: Modifier,
    artwork: Artwork?,
    contentScale: ContentScale = ContentScale.FillWidth,
    smoothTransition: Boolean = false,
    loader: ArtworkImageLoader = koinInject(),
    fallback: @Composable () -> Unit = { DefaultArtworkImage() },
) {
    ArtworkImageInternal(
        modifier = modifier,
        artwork = artwork,
        contentScale = contentScale,
        smoothTransition = smoothTransition,
        preparePlayerBackground = false,
        loader = loader,
        fallback = fallback,
    )
}

@Composable
fun PlayerBackgroundArtworkImage(
    modifier: Modifier,
    artwork: Artwork?,
    blurRadius: Dp,
    contentScale: ContentScale = ContentScale.FillWidth,
    smoothTransition: Boolean = false,
    loader: ArtworkImageLoader = koinInject(),
    fallback: @Composable () -> Unit = { DefaultArtworkImage() },
) {
    ArtworkImageInternal(
        modifier = modifier.platformPlayerBackgroundBlur(blurRadius),
        artwork = artwork,
        contentScale = contentScale,
        smoothTransition = smoothTransition,
        preparePlayerBackground = true,
        loader = loader,
        fallback = fallback,
    )
}

@Composable
private fun ArtworkImageInternal(
    modifier: Modifier,
    artwork: Artwork?,
    contentScale: ContentScale,
    smoothTransition: Boolean,
    preparePlayerBackground: Boolean,
    loader: ArtworkImageLoader,
    fallback: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        var loadedArtwork: LoadedArtwork? by remember {
            mutableStateOf(null)
        }
        val cachedBitmap = remember(artwork, loader) {
            artwork?.let(loader::cachedBitmap)
        }

        LaunchedEffect(artwork, loader) {
            when {
                artwork == null -> loadedArtwork = null
                cachedBitmap != null -> loadedArtwork = LoadedArtwork(artwork, cachedBitmap)
                else -> {
                    if (!smoothTransition) loadedArtwork = null
                    loadedArtwork = LoadedArtwork(artwork, loader.loadBitmap(artwork))
                }
            }
        }

        val displayedBitmap = cachedBitmap ?: loadedArtwork
            ?.takeIf { loaded ->
                loaded.artwork == artwork || (smoothTransition && artwork != null)
            }
            ?.bitmap
        val renderedBitmap = if (preparePlayerBackground && displayedBitmap != null) {
            rememberPlayerBackgroundBitmap(displayedBitmap)
        } else {
            displayedBitmap
        }
        if (smoothTransition) {
            Crossfade(
                targetState = renderedBitmap,
                animationSpec = tween(durationMillis = 180),
                label = "artworkImage",
            ) { bitmap ->
                ArtworkImageContent(
                    bitmap = bitmap,
                    contentScale = contentScale,
                    fallback = fallback,
                )
            }
        } else {
            ArtworkImageContent(
                bitmap = renderedBitmap,
                contentScale = contentScale,
                fallback = fallback,
            )
        }
    }
}

private data class LoadedArtwork(
    val artwork: Artwork,
    val bitmap: ImageBitmap?,
)

@Composable
private fun ArtworkImageContent(
    bitmap: ImageBitmap?,
    contentScale: ContentScale,
    fallback: @Composable () -> Unit,
) {
    if (bitmap == null) {
        fallback()
    } else {
        Image(
            modifier = Modifier
                .background(MiuixTheme.colorScheme.onSurfaceVariantSummary)
                .fillMaxSize(),
            bitmap = bitmap,
            contentDescription = null,
            contentScale = contentScale,
        )
    }
}

@Composable
private fun DefaultArtworkImage() {
    Image(
        modifier = Modifier.fillMaxSize(),
        painter = painterResource(Res.drawable.cover_default_image),
        contentDescription = null,
    )
}
