package io.github.julystar.musicapp.feature.importing.data

import io.github.julystar.musicapp.core.domain.model.ImportSelectionMode
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.source.api.ImportRepository
import io.github.julystar.musicapp.source.api.SourceDirectorySelection
import io.github.julystar.musicapp.source.api.SourceNodeSelection
import io.github.julystar.musicapp.source.api.SourceNodeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

typealias ImportHandler = (entries: List<SourceNodeSelection>) -> Unit
typealias DirectoryImportHandler = (selection: SourceDirectorySelection) -> Unit

class ImportRepositoryImpl : ImportRepository {
    private val _allowTypes = MutableStateFlow(listOf<SourceNodeType>())
    private val _selectionMode = MutableStateFlow(ImportSelectionMode.Entries)
    private val _currentDirectoryAccountId = MutableStateFlow<SourceAccountId?>(null)
    private var _importCallback: ((List<SourceNodeSelection>) -> Unit)? = null
    private var _directoryImportCallback: ((SourceDirectorySelection) -> Unit)? = null
    private var _directoriesImportCallback: ((List<SourceDirectorySelection>) -> Unit)? = null

    override val allowTypes = _allowTypes.asStateFlow()
    override val selectionMode = _selectionMode.asStateFlow()
    override val currentDirectoryAccountId = _currentDirectoryAccountId.asStateFlow()

    override fun prepare(types: List<SourceNodeType>, block: (List<SourceNodeSelection>) -> Unit) {
        _allowTypes.value = types
        _selectionMode.value = ImportSelectionMode.Entries
        _currentDirectoryAccountId.value = null
        _importCallback = block
        _directoryImportCallback = null
        _directoriesImportCallback = null
    }

    override fun prepareCurrentDirectory(
        accountId: SourceAccountId?,
        block: (SourceDirectorySelection) -> Unit,
    ) {
        _allowTypes.value = emptyList()
        _selectionMode.value = ImportSelectionMode.CurrentDirectory
        _currentDirectoryAccountId.value = accountId
        _importCallback = null
        _directoryImportCallback = block
        _directoriesImportCallback = null
    }

    override fun prepareDirectories(
        accountId: SourceAccountId?,
        block: (List<SourceDirectorySelection>) -> Unit,
    ) {
        _allowTypes.value = emptyList()
        _selectionMode.value = ImportSelectionMode.CurrentDirectory
        _currentDirectoryAccountId.value = accountId
        _importCallback = null
        _directoryImportCallback = null
        _directoriesImportCallback = block
    }

    override fun onFinish(entries: List<SourceNodeSelection>) {
        val callback = _importCallback
        _importCallback = null
        if (callback != null) {
            callback(entries)
        }
    }

    override fun onFinishCurrentDirectory(selection: SourceDirectorySelection) {
        val callback = _directoryImportCallback
        _directoryImportCallback = null
        if (callback != null) {
            callback(selection)
        }
    }

    override fun onFinishDirectories(selections: List<SourceDirectorySelection>) {
        val callback = _directoriesImportCallback
        _directoriesImportCallback = null
        if (callback != null) {
            callback(selections)
        } else {
            super.onFinishDirectories(selections)
        }
    }
}
