package io.github.julystar.musicapp.car

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import io.github.julystar.musicapp.car.presentation.CarRoot
import io.github.julystar.musicapp.car.presentation.layout.CarLayoutProfileHint
import io.github.julystar.musicapp.car.presentation.layout.CarLayoutProfileResolver
import org.koin.android.ext.android.inject

/** Uses a separate unresizable task so the cockpit host grants the fullscreen drawing surface. */
class FullscreenPlaybackActivity : ComponentActivity() {
    private val layoutProfileResolver: CarLayoutProfileResolver by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.isNavigationBarContrastEnforced = false
        setContent {
            val startupState by (application as CarApplication).startupState.collectAsState()
            TideCarAppWindow(
                profileHint = CarLayoutProfileHint.FullscreenCockpit,
                onRootPositioned = { _, _ -> },
            ) { panelSize, effectiveHint ->
                when (startupState) {
                    CarStartupState.Ready -> {
                        val metrics = remember(panelSize, effectiveHint) {
                            layoutProfileResolver.resolve(panelSize, hint = effectiveHint)
                        }
                        CarRoot(
                            metrics = metrics,
                            onExit = ::restoreMainAndFinish,
                            onExitPlayback = ::exitPlayback,
                            onExitFullscreen = ::restoreMainAndFinish,
                        )
                    }
                    else -> LaunchedEffect(startupState) {
                        if (
                            startupState is CarStartupState.Failed ||
                            startupState == CarStartupState.RecoveryRequired
                        ) {
                            restoreMainAndFinish()
                        }
                    }
                }
            }
        }
    }

    private fun restoreMainAndFinish() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                ),
        )
        finish()
        overridePendingTransition(0, 0)
    }

    private fun exitPlayback() {
        (application as CarApplication).requestExitPlayback()
        restoreMainAndFinish()
    }
}
