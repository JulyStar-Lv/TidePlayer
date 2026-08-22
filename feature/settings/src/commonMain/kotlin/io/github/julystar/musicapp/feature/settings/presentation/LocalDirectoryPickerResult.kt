package io.github.julystar.musicapp.feature.settings.presentation

/** Keeps a normal picker cancellation distinct from an inability to launch/read the picker. */
sealed interface LocalDirectoryPickerResult {
    data class Success(val path: String) : LocalDirectoryPickerResult
    data object Cancelled : LocalDirectoryPickerResult
    data class LaunchFailed(val cause: Throwable? = null) : LocalDirectoryPickerResult
}

internal fun localDirectoryPickerResult(
    pickedPath: String?,
    normalize: (String) -> String?,
): LocalDirectoryPickerResult {
    if (pickedPath == null) return LocalDirectoryPickerResult.Cancelled
    return normalize(pickedPath)?.let(LocalDirectoryPickerResult::Success)
        ?: LocalDirectoryPickerResult.LaunchFailed()
}
