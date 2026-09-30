package io.github.julystar.musicapp.feature.playlist.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.toStorageRouteIdOrNull
import io.github.julystar.musicapp.source.api.ImportRepository
import io.github.julystar.musicapp.source.api.PlaylistImportTarget
import io.github.julystar.musicapp.source.api.SourceNodeSelection
import io.github.julystar.musicapp.source.api.SourceNodeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.collections.firstOrNull

class CreatePlaylistVM constructor(
    private val importRepository: ImportRepository,
    private val playlistImportTarget: PlaylistImportTarget,
) : ViewModel() {
    private val _modalOpen = MutableStateFlow(false)
    private val _mode = MutableStateFlow(CreatePlaylistTab.Full)
    private val _fullImported = MutableStateFlow(false)
    private val _entries = MutableStateFlow(listOf<SourceNodeSelection>())
    private val _name = MutableStateFlow("")
    private val _cover = MutableStateFlow<SourceNodeSelection?>(null)

    val mode = _mode.asStateFlow()
    val musicCount = _entries.map { entries ->
        entries.count { entry -> entry.node.type == SourceNodeType.Track }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)
    val name = _name.asStateFlow()
    val recommendPlaylistNames = _entries.map { entries ->
        val seen = HashSet<String>()
        buildList {
            for (entry in entries) {
                for (p in entry.node.path.split("/").let { list ->
                    if (list.isEmpty()) emptyList() else list.take(list.size - 1)
                }) {
                    if (p.isNotBlank()) {
                        val x = decodeUrlComponent(p.trim())
                        if (seen.add(x)) add(x)
                    }
                }
            }
        }.takeLast(6)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList()
    )

    val cover = _cover.asStateFlow()
    val coverArtwork = _cover.map { cover ->
        cover?.let { sel ->
            Artwork.LegacyStorageEntry(
                storageId = sel.accountId.toStorageRouteIdOrNull() ?: 0L,
                path = "/" + sel.node.path.trimStart('/'),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val modalOpen = _modalOpen.asStateFlow()
    val fullImported = _fullImported.asStateFlow()

    val canSubmit = combine(name, mode, musicCount, cover) { n, m, mc, cv ->
        if (m == CreatePlaylistTab.Full) {
            n.isNotBlank() && (mc > 0 || cv != null)
        } else {
            n.isNotBlank()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = false
    )

    fun updateName(name: String) {
        _name.value = name
    }

    fun clearCover() {
        _cover.value = null
    }

    fun updateMode(mode: CreatePlaylistTab) {
        _mode.value = mode
    }

    fun openModal() {
        _modalOpen.value = true
    }

    fun closeModal() {
        _modalOpen.value = false
        reset()
    }

    fun reset() {
        _mode.value = CreatePlaylistTab.Full
        _fullImported.value = false
        _name.value = ""
        _cover.value = null
    }

    fun prepareImportCreate() {
        importRepository.prepare(listOf(SourceNodeType.Track, SourceNodeType.Image)) { entries ->
            _entries.value = entries.filter { v -> v.node.type == SourceNodeType.Track }
            _cover.value = entries.filter { v -> v.node.type == SourceNodeType.Image }
                .firstOrNull()
            _fullImported.value = true

            val name = recommendPlaylistNames.value.lastOrNull()
            if (name != null) {
                _name.value = name
            }
        }
    }

    fun finish(coverImage: ByteArray? = null) {
        val title = _name.value
        val cover = if (coverImage == null) _cover.value else null
        val entries = _entries.value
        viewModelScope.launch {
            playlistImportTarget.createPlaylistFromSelections(
                title = title,
                cover = cover,
                entries = entries,
                coverImage = coverImage,
            )
        }
    }
}

private fun decodeUrlComponent(value: String): String {
    val decoded = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        if (value[index] != '%' || index + 2 >= value.length) {
            decoded.append(value[index])
            index++
        } else {
            val hex = value.substring(index + 1, index + 3)
            val char = hex.toIntOrNull(16)?.toChar()
            if (char != null) {
                decoded.append(char)
                index += 3
            } else {
                decoded.append(value[index])
                index++
            }
        }
    }
    return decoded.toString()
}
