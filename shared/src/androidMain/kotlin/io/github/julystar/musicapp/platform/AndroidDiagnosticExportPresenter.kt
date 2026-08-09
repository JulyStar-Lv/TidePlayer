package io.github.julystar.musicapp.platform

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import androidx.core.content.FileProvider
import io.github.julystar.musicapp.core.domain.repository.DiagnosticExportPresenter
import java.io.File

actual fun diagnosticExportPresenter(): DiagnosticExportPresenter = AndroidDiagnosticExportPresenter

private object AndroidDiagnosticExportPresenter : DiagnosticExportPresenter {
    override suspend fun share(path: String): Result<Unit> = runCatching {
        val uri = exportUri(path)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(
            Intent.createChooser(intent, "Share Tide Player diagnostics")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override suspend fun saveAs(path: String): Result<Unit> = share(path)

    override suspend fun reveal(path: String): Result<Unit> = runCatching {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(exportUri(path), "application/zip")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }

    override suspend fun copyPath(path: String): Result<Unit> = runCatching {
        val clipboard = appContext.getSystemService(ClipboardManager::class.java)
            ?: error("Clipboard is unavailable")
        clipboard.setPrimaryClip(ClipData.newPlainText("Tide Player diagnostics", path))
    }

    private fun exportUri(path: String) = File(path).let { export ->
        require(isDiagnosticExportPathAllowed(export, appContext.filesDir)) {
            "Only generated diagnostic exports can be shared"
        }
        FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.diagnostics.fileprovider",
            export,
        )
    }
}

internal fun isDiagnosticExportPathAllowed(export: File, filesDir: File): Boolean {
    val exportRoot = File(filesDir, "diagnostics/exports").canonicalFile
    val candidate = export.canonicalFile
    return candidate.isFile && candidate.toPath().startsWith(exportRoot.toPath())
}
