package io.github.julystar.musicapp.service.librarysync.domain

import io.github.julystar.musicapp.core.domain.model.SourceAccountId

fun interface SourceAccountLibrarySyncController {
    suspend fun sync(accountId: SourceAccountId): SourceAccountLibrarySyncResult
}

data class SourceAccountLibrarySyncResult(
    val importedCount: Long,
    val skippedCount: Long,
    val failedCount: Long,
)
