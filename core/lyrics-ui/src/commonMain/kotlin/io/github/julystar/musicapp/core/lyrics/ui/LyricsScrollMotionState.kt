package io.github.julystar.musicapp.core.lyrics.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Stable
internal class LyricsScrollMotionState {
    var isUserBrowsing by mutableStateOf(false)
        private set
    var userScrollGeneration by mutableIntStateOf(0)
        private set
    var animationEpoch by mutableIntStateOf(0)
        private set
    var scrollOffset by mutableFloatStateOf(0f)
        private set

    fun onUserScroll() {
        if (!isUserBrowsing) resetAnimation()
        isUserBrowsing = true
        userScrollGeneration++
    }

    fun resumeFollowing() {
        isUserBrowsing = false
    }

    fun recordScroll(consumed: Float) {
        scrollOffset += consumed
    }

    fun resetAnimation() {
        scrollOffset = 0f
        animationEpoch++
    }
}
