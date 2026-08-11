package io.github.julystar.musicapp.singleton

import io.github.julystar.musicapp.core.data.ToastRepositoryImpl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import uniffi.app_backend.ArgInitializeApp
import uniffi.app_backend.Backend
import uniffi.app_backend.createBackend
import io.github.julystar.musicapp.core.domain.model.DiagnosticLogCategory
import io.github.julystar.musicapp.core.domain.repository.UiMessageKey
import io.github.julystar.musicapp.core.domain.repository.emit
import io.github.julystar.musicapp.diagnostics.AppLogger


private fun normalizePath(p: String): String {
    if (p.endsWith("/")) {
        return p;
    }
    return "$p/";
}


class Bridge(
    appDocumentDir: String,
    appCacheDir: String,
    private val toastRepository: ToastRepositoryImpl
)  {
    private val _storagePath = "/"
    private var _isInit = false
    private val _arg = ArgInitializeApp(
        appDocumentDir = normalizePath(appDocumentDir),
        appCacheDir = normalizePath(appCacheDir),
        storagePath = _storagePath
    )
    private var _backend: Backend? = null

    private fun internal(): Backend {
        return _backend!!
    }

    suspend fun<R> run(block: suspend (backend: Backend) -> R): R? {
        try {
            return block(internal())
        } catch (e: Exception) {
            AppLogger.error(
                DiagnosticLogCategory.App,
                "Bridge",
                "Bridge operation failed",
                e.stackTraceToString(),
            )
            return null
        }
    }

    suspend fun<R> runRaw(block: suspend (backend: Backend) -> R): R {
        return block(internal())
    }

    fun<R> runSyncRaw(block: (backend: Backend) -> R): R {
        return block(internal())
    }

    fun<R> runSync(block: (backend: Backend) -> R): R? {
        try {
            return block(internal())
        } catch (e: Exception) {
            AppLogger.error(
                DiagnosticLogCategory.App,
                "Bridge",
                "Synchronous bridge operation failed",
                e.stackTraceToString(),
            )
            toastRepository.emit(UiMessageKey.SourceUnavailable)
            return null
        }
    }

    fun initialize() {
        if (_isInit) {
            return
        }
        _backend = createBackend(_arg)
        _backend!!.init();
        AppLogger.info(DiagnosticLogCategory.Startup, "Bridge", "Bridge initialized")
        _isInit = true
    }

    fun destroy() {
        if (!_isInit) {
            return
        }
        _backend!!.destroy()
        _backend = null
        _isInit = false
        AppLogger.info(DiagnosticLogCategory.App, "Bridge", "Bridge destroyed")
    }
}
