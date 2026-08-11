package io.github.julystar.musicapp.core

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Gives every Media3 controller the same command semantics as TidePlayer's in-app controls.
 *
 * Automatic transitions still use the native Media3 timeline. Explicit next/previous commands are
 * delegated to the application controller so true-random shuffle, requested-next ordering, repeat
 * policy, and queue-boundary behavior remain identical across the UI, headset, Bluetooth, Wear OS,
 * and Android Auto. STOP remains a resumable pause for external controllers.
 */
@OptIn(UnstableApi::class)
internal class TidePlayerSessionPlayer(
    player: Player,
    private val onNextBoundary: () -> Unit,
    private val onPreviousBoundary: () -> Unit,
) : ForwardingPlayer(player) {
    override fun stop() {
        pause()
    }

    override fun seekToNextMediaItem() {
        onNextBoundary()
    }

    override fun seekToNext() {
        onNextBoundary()
    }

    override fun seekToPreviousMediaItem() {
        onPreviousBoundary()
    }

    override fun seekToPrevious() {
        onPreviousBoundary()
    }
}
