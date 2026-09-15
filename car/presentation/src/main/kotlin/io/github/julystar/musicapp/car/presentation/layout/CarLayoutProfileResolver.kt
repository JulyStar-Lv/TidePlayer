package io.github.julystar.musicapp.car.presentation.layout

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.car.presentation.theme.CarTouchTargets

/** Resolves layout from measured constraints and shape contracts, never exact pixels. */
class CarLayoutProfileResolver(
    private val profileHint: CarLayoutProfileHint = CarLayoutProfileHint.Automatic,
    private val strategy: CarLayoutProfileStrategy = DefaultCarLayoutProfileStrategy,
) {
    fun resolve(
        usableSize: DpSize,
        insets: CarLayoutInsets = CarLayoutInsets(),
        hint: CarLayoutProfileHint = profileHint,
    ): CarLayoutMetrics {
        require(usableSize.width.value.isFinite() && usableSize.height.value.isFinite())
        require(usableSize.width > 0.dp && usableSize.height > 0.dp)
        require(listOf(insets.left, insets.top, insets.right, insets.bottom).all { it.value.isFinite() && it >= 0.dp })
        val contentSize = DpSize(
            usableSize.width - insets.left - insets.right,
            usableSize.height - insets.top - insets.bottom,
        )
        require(contentSize.width > 0.dp && contentSize.height > 0.dp)
        val profile = when (hint) {
            CarLayoutProfileHint.Automatic -> strategy.select(contentSize, insets)
            CarLayoutProfileHint.Expanded -> CarLayoutProfile.Expanded
            CarLayoutProfileHint.VehiclePanel -> CarLayoutProfile.VehiclePanel
            CarLayoutProfileHint.FullscreenCockpit -> CarLayoutProfile.FullscreenCockpit
        }
        return createMetrics(profile, usableSize, contentSize, insets)
    }
}

object DefaultCarLayoutProfileStrategy : CarLayoutProfileStrategy {
    override fun select(usableSize: DpSize, insets: CarLayoutInsets): CarLayoutProfile {
        val aspectRatio = usableSize.width.value / usableSize.height.value
        return when {
            aspectRatio >= 3.25f -> CarLayoutProfile.FullscreenCockpit
            aspectRatio <= 2.15f -> CarLayoutProfile.VehiclePanel
            else -> CarLayoutProfile.Expanded
        }
    }
}

private fun createMetrics(
    profile: CarLayoutProfile,
    usableSize: DpSize,
    contentSize: DpSize,
    insets: CarLayoutInsets,
): CarLayoutMetrics {
    val reference = when (profile) {
        CarLayoutProfile.Expanded -> ExpandedReference
        CarLayoutProfile.VehiclePanel -> VehiclePanelReference
        CarLayoutProfile.FullscreenCockpit -> FullscreenReference
    }
    val width = contentSize.width
    val height = contentSize.height
    val minAxis = minOf(width, height)
    fun wx(value: Float) = width * (value / reference.contentWidth)
    fun hy(value: Float) = height * (value / reference.contentHeight)
    return CarLayoutMetrics(
        profile, usableSize, contentSize, insets, true,
        wx(reference.shellPadding), hy(reference.shellPadding), wx(reference.shellWidth),
        wx(reference.headerStart), hy(reference.headerTop), hy(reference.headerHeight),
        wx(reference.navigationRailStart), hy(reference.navigationRailTop), hy(reference.navigationRailBottom),
        wx(reference.navigationRailWidth), wx(reference.navigationRailInnerPadding),
        hy(reference.navigationPrimaryTop), hy(reference.navigationItemInterval),
        hy(reference.navigationLibraryLabelTop), hy(reference.navigationLibraryTop), hy(reference.miniPlayerBottom),
        wx(reference.contentPaneGap), wx(reference.contentHorizontalPadding), hy(reference.contentTop),
        hy(reference.libraryGap), hy(reference.navigationItemHeight), hy(reference.miniPlayerHeight),
        maxOf(CarTouchTargets.Minimum, hy(reference.iconSize)), hy(reference.albumCardHeight),
        hy(reference.compactCardHeight), hy(reference.quickActionHeight),
        wx(reference.homeQuickCardWidth), wx(reference.homeSearchCardWidth),
        wx(reference.homeRecentFeatureWidth), wx(reference.homeRecentCompactWidth), reference.homeRecentColumns,
        wx(reference.homeRecommendationCardWidth), minAxis * (reference.homeRecommendationArtworkSize / reference.contentHeight),
        wx(reference.homeRecommendationGap), wx(reference.recommendationCardWidth),
        hy(reference.recommendationCardHeight), hy(reference.artistCardHeight), wx(reference.cardGap),
        wx(reference.libraryPanePadding), wx(reference.mediaGridPadding), wx(reference.mediaGridHorizontalGap),
        wx(reference.nowPlayingHorizontalMargin), wx(reference.nowPlayingContentStartOffset), hy(reference.nowPlayingVerticalMargin),
        wx(reference.nowPlayingPlayerPaneWidth), wx(reference.nowPlayingPaneGap),
        minAxis * (reference.nowPlayingArtworkSize / reference.contentHeight), wx(reference.nowPlayingInnerPadding),
        maxOf(4.dp, hy(reference.progressTrackHeight)), wx(reference.detailContentMargin),
        wx(reference.detailContentEndMargin), wx(reference.detailContentPadding), wx(reference.playlistContentPadding),
        hy(reference.detailTopBarHeight), hy(reference.detailPaneTop), wx(reference.detailHeroWidth),
        wx(reference.detailPaneGap), hy(reference.detailRowHeight), wx(reference.artistAlbumCardWidth),
        minAxis * (reference.artistAlbumArtworkSize / reference.contentHeight), reference.artistVisibleTrackCount,
        hy(reference.playlistSelectedCardHeight), reference.playlistGridColumns,
        wx(reference.playlistCardWidth), minAxis * (reference.playlistArtworkSize / reference.contentHeight),
        maxOf(CarTouchTargets.Minimum, minAxis * (reference.primaryTouchTarget / reference.contentHeight)),
        reference.mediaGridColumns,
        createFullscreenMetrics(contentSize),
    )
}

private fun createFullscreenMetrics(contentSize: DpSize): CarFullscreenMetrics {
    val width = contentSize.width
    val height = contentSize.height
    val minAxis = minOf(width, height)
    fun wx(value: Float) = width * (value / FullscreenReference.contentWidth)
    fun hy(value: Float) = height * (value / FullscreenReference.contentHeight)
    fun square(value: Float) = minAxis * (value / FullscreenReference.contentHeight)
    fun offset(x: Float, y: Float) = DpOffset(wx(x), hy(y))

    return CarFullscreenMetrics(
        backgroundScaleX = 5600f / FullscreenReference.contentWidth,
        backgroundScaleY = 1784f / FullscreenReference.contentHeight,
        exitPlaybackOffset = offset(96f, 216f),
        exitFullscreenOffset = offset(184f, 216f),
        controlSize = square(72f),
        controlIconSize = square(56f),
        controlCornerRadius = square(20f),
        metadataOffset = offset(280f, 216f),
        metadataWidth = wx(1600f),
        coverFlowTop = hy(380f),
        coverFlowHeight = hy(820f),
        indicatorOffset = offset(2518f, 1260f),
        indicatorSize = DpSize(wx(84f), hy(8f)),
        minimalOffset = offset(320f, 394.25f),
        minimalSize = DpSize(wx(4544f), hy(840f)),
        minimalGap = wx(224f),
        artworkSize = square(720f),
        artworkCornerRadius = square(36f),
        lyricsSize = DpSize(wx(3528f), hy(558f)),
        coverFlowItemSize = DpSize(wx(720f), hy(832f)),
        coverFlowCenterX = wx(2200f),
        coverFlowDragInterval = wx(463f),
        coverFlowInfoWidthInset = wx(40f),
        coverFlowInfoMinimumWidth = wx(320f),
        coverFlowInfoTopCentered = hy(750f),
        coverFlowInfoTopNear = hy(684f),
        coverFlowCenterOffsets = listOf(0f, 582.5f, 1045.5f, 1461f, 1847f, 2194f).map(::wx),
        coverFlowArtworkWidths = listOf(720f, 517f, 441f, 430f, 400f, 360f).map(::wx),
        coverFlowArtworkHeights = listOf(720f, 586f, 584f, 583f, 582f, 581f).map(::hy),
        coverFlowArtworkCornerRadius = square(28f),
        coverFlowCameraDistance = 2400f,
    )
}

private data class LayoutReference(
    val contentWidth: Float,
    val contentHeight: Float = 1080f,
    val shellPadding: Float = 24f,
    val shellWidth: Float,
    val headerStart: Float = 24f,
    val headerTop: Float = 24f,
    val headerHeight: Float = 72f,
    val navigationRailStart: Float,
    val navigationRailTop: Float = 112f,
    val navigationRailBottom: Float = 48f,
    val navigationRailWidth: Float,
    val navigationRailInnerPadding: Float = 16f,
    val navigationPrimaryTop: Float = 48f,
    val navigationItemInterval: Float = 100f,
    val navigationLibraryLabelTop: Float = 356f,
    val navigationLibraryTop: Float = 390f,
    val miniPlayerBottom: Float = 24f,
    val contentPaneGap: Float = 24f,
    val contentHorizontalPadding: Float = 24f,
    val contentTop: Float = 112f,
    val libraryGap: Float = 24f,
    val navigationItemHeight: Float = 84f,
    val miniPlayerHeight: Float = 164f,
    val iconSize: Float = 56f,
    val albumCardHeight: Float = 248f,
    val compactCardHeight: Float,
    val quickActionHeight: Float = 160f,
    val homeQuickCardWidth: Float,
    val homeSearchCardWidth: Float,
    val homeRecentFeatureWidth: Float,
    val homeRecentCompactWidth: Float,
    val homeRecentColumns: Int,
    val homeRecommendationCardWidth: Float,
    val homeRecommendationArtworkSize: Float = 300f,
    val homeRecommendationGap: Float,
    val recommendationCardWidth: Float,
    val recommendationCardHeight: Float,
    val artistCardHeight: Float,
    val cardGap: Float = 24f,
    val libraryPanePadding: Float,
    val mediaGridPadding: Float,
    val mediaGridHorizontalGap: Float,
    val nowPlayingHorizontalMargin: Float,
    val nowPlayingContentStartOffset: Float = 0f,
    val nowPlayingVerticalMargin: Float = 48f,
    val nowPlayingPlayerPaneWidth: Float,
    val nowPlayingPaneGap: Float,
    val nowPlayingArtworkSize: Float,
    val nowPlayingInnerPadding: Float,
    val progressTrackHeight: Float = 14f,
    val detailContentMargin: Float,
    val detailContentEndMargin: Float,
    val detailContentPadding: Float,
    val playlistContentPadding: Float = 48f,
    val detailTopBarHeight: Float = 72f,
    val detailPaneTop: Float = 96f,
    val detailHeroWidth: Float,
    val detailPaneGap: Float,
    val detailRowHeight: Float,
    val artistAlbumCardWidth: Float,
    val artistAlbumArtworkSize: Float,
    val artistVisibleTrackCount: Int,
    val playlistSelectedCardHeight: Float,
    val playlistGridColumns: Int,
    val playlistCardWidth: Float,
    val playlistArtworkSize: Float,
    val primaryTouchTarget: Float = 128f,
    val mediaGridColumns: Int,
)

private val ExpandedReference = LayoutReference(
    contentWidth = 2496f, shellWidth = 416f, navigationRailStart = 40f, navigationRailWidth = 352f,
    compactCardHeight = 112f, homeQuickCardWidth = 576f, homeSearchCardWidth = 164f,
    homeRecentFeatureWidth = 640f, homeRecentCompactWidth = 417.33334f, homeRecentColumns = 3,
    homeRecommendationCardWidth = 332f, homeRecommendationGap = 68f,
    recommendationCardWidth = 332f, recommendationCardHeight = 400f,
    artistCardHeight = 360f, nowPlayingHorizontalMargin = 56f, nowPlayingPlayerPaneWidth = 1000f,
    nowPlayingPaneGap = 56f, nowPlayingArtworkSize = 640f, nowPlayingInnerPadding = 48f,
    libraryPanePadding = 32f, mediaGridPadding = 24f, mediaGridHorizontalGap = 16f,
    detailContentMargin = 16f, detailContentEndMargin = 40f, detailContentPadding = 48f,
    detailHeroWidth = 560f, detailPaneGap = 40f, detailRowHeight = 76f,
    artistAlbumCardWidth = 236f, artistAlbumArtworkSize = 212f, artistVisibleTrackCount = 5,
    playlistSelectedCardHeight = 136f, playlistGridColumns = 2, playlistCardWidth = 240f,
    playlistArtworkSize = 216f,
    mediaGridColumns = 4,
)

private val VehiclePanelReference = LayoutReference(
    contentWidth = 1728f, shellWidth = 280f, navigationRailStart = 24f, navigationRailWidth = 240f,
    compactCardHeight = 96f, homeQuickCardWidth = 380f, homeSearchCardWidth = 184f,
    homeRecentFeatureWidth = 480f, homeRecentCompactWidth = 416f, homeRecentColumns = 2,
    homeRecommendationCardWidth = 440f, homeRecommendationGap = 24f,
    recommendationCardWidth = 277.33334f, recommendationCardHeight = 400f,
    artistCardHeight = 360f, nowPlayingHorizontalMargin = 40f, nowPlayingPlayerPaneWidth = 660f,
    nowPlayingContentStartOffset = 80f, nowPlayingPaneGap = 44f, nowPlayingArtworkSize = 600f,
    nowPlayingInnerPadding = 40f, libraryPanePadding = 24f, mediaGridPadding = 32f,
    mediaGridHorizontalGap = 12f, detailContentMargin = 0f, detailContentEndMargin = 24f,
    detailContentPadding = 24f, detailHeroWidth = 480f, detailPaneGap = 24f, detailRowHeight = 96f,
    artistAlbumCardWidth = 320f, artistAlbumArtworkSize = 216f, artistVisibleTrackCount = 4,
    playlistSelectedCardHeight = 136f, playlistGridColumns = 1, playlistCardWidth = 240f,
    playlistArtworkSize = 216f,
    mediaGridColumns = 3,
)

private val FullscreenReference = LayoutReference(
    contentWidth = 5120f, contentHeight = 1304f, shellPadding = 0f, shellWidth = 0f,
    headerStart = 0f, headerTop = 0f, navigationRailStart = 0f, navigationRailTop = 0f,
    navigationRailBottom = 0f, navigationRailWidth = 0f, navigationRailInnerPadding = 0f,
    navigationPrimaryTop = 0f, navigationItemInterval = 0f, navigationLibraryLabelTop = 0f,
    navigationLibraryTop = 0f, miniPlayerBottom = 0f, contentPaneGap = 224f,
    contentHorizontalPadding = 320f, contentTop = 0f, libraryGap = 24f, navigationItemHeight = 84f,
    miniPlayerHeight = 164f, iconSize = 72f, albumCardHeight = 720f, compactCardHeight = 104f,
    quickActionHeight = 160f, homeQuickCardWidth = 0f, homeSearchCardWidth = 0f,
    homeRecentFeatureWidth = 0f, homeRecentCompactWidth = 0f, homeRecentColumns = 1,
    homeRecommendationCardWidth = 0f, homeRecommendationArtworkSize = 0f, homeRecommendationGap = 0f,
    recommendationCardWidth = 720f, recommendationCardHeight = 720f,
    artistCardHeight = 720f, cardGap = 72f, nowPlayingHorizontalMargin = 320f,
    nowPlayingVerticalMargin = 0f, nowPlayingPlayerPaneWidth = 720f, nowPlayingPaneGap = 224f,
    nowPlayingArtworkSize = 720f, nowPlayingInnerPadding = 64f, progressTrackHeight = 14f,
    libraryPanePadding = 0f, mediaGridPadding = 0f, mediaGridHorizontalGap = 0f,
    detailContentMargin = 0f, detailContentEndMargin = 0f, detailContentPadding = 0f,
    detailTopBarHeight = 72f, detailPaneTop = 0f, detailHeroWidth = 720f,
    detailPaneGap = 224f, detailRowHeight = 104f, artistAlbumCardWidth = 0f,
    artistAlbumArtworkSize = 0f, artistVisibleTrackCount = 0, playlistSelectedCardHeight = 0f,
    playlistGridColumns = 1, playlistCardWidth = 0f, playlistArtworkSize = 0f,
    primaryTouchTarget = 128f, mediaGridColumns = 5,
)
