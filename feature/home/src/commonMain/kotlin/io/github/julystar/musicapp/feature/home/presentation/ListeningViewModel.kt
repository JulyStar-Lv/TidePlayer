package io.github.julystar.musicapp.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.julystar.musicapp.core.domain.repository.LibraryRepository
import io.github.julystar.musicapp.feature.home.domain.HomeStatisticsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ListeningViewModel(
    private val statisticsRepository: HomeStatisticsRepository,
    libraryRepository: LibraryRepository,
) : ViewModel() {
    private val selectedTab = MutableStateFlow(ListeningTab.Overview)

    val state = combine(
        statisticsRepository.listeningStatistics,
        libraryRepository.tracks,
        libraryRepository.initialLoadComplete,
        selectedTab,
    ) { statistics, tracks, initialLoadComplete, tab ->
        buildListeningState(
            snapshot = statistics,
            libraryTracks = tracks,
            selectedTab = tab,
            isLoading = !initialLoadComplete,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ListeningState(),
    )

    fun onAction(action: ListeningAction) {
        when (action) {
            is ListeningAction.SelectTab -> selectedTab.value = action.tab
            ListeningAction.NavigateBack,
            is ListeningAction.PlayTrack -> Unit
        }
    }
}
