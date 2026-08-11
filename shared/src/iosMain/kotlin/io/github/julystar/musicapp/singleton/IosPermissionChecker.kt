package io.github.julystar.musicapp.singleton

import io.github.julystar.musicapp.core.domain.repository.PermissionChecker

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class IosPermissionChecker : PermissionChecker {
    override val havePermission: StateFlow<Boolean> = MutableStateFlow(true)

    override fun requestStoragePermission() {
        // TidePlayer reads only its sandbox and remote providers on iOS.
    }
}
