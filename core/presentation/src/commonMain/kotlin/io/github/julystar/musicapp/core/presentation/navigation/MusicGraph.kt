package io.github.julystar.musicapp.core.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface MusicGraph {
    @Serializable
    data object Home : MusicGraph

    @Serializable
    data object Search : MusicGraph

    @Serializable
    data object Downloads : MusicGraph

    @Serializable
    data object Browse : MusicGraph

    @Serializable
    data class BrowseGenre(val genre: String) : MusicGraph

    @Serializable
    data object Radio : MusicGraph

    @Serializable
    data class Album(val id: Long) : MusicGraph

    @Serializable
    data class Artist(val id: Long) : MusicGraph

    @Serializable
    data object Playlists : MusicGraph

    @Serializable
    data class Playlist(val id: Long) : MusicGraph

    @Serializable
    data object Favorites : MusicGraph

    @Serializable
    data class Lyrics(val id: Long) : MusicGraph

    @Serializable
    data object RecentlyAdded : MusicGraph

    @Serializable
    data object RecentlyPlayed : MusicGraph

    @Serializable
    data object Listening : MusicGraph

    @Serializable
    data object Onboarding : MusicGraph

    @Serializable
    data class EditStorage(
        val id: Long = NEW_STORAGE_ID,
        val sourceType: String? = null,
    ) : MusicGraph

    @Serializable
    data class Import(val type: String) : MusicGraph

    @Serializable
    data object PluginSettings : MusicGraph

    @Serializable
    data object NowPlaying : MusicGraph
}

const val NEW_STORAGE_ID = -1L

fun routeIsHome(route: String?): Boolean {
    return route != null && (route == "Home" || route.endsWith(".Home"))
}
