package io.github.julystar.musicapp.feature.importing.presentation

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.source.api.SourceNode
import io.github.julystar.musicapp.source.api.SourceNodeType
import kotlin.test.Test
import kotlin.test.assertEquals

class ImportPathFidelityTest {
    @Test
    fun clickedFolderLabelIsRawNameWhileNavigationPathStaysOpaque() {
        val rawPath = "/音乐 space/%25 #? 😀"
        val node = SourceNode(
            accountId = SourceAccountId("openlist:1"),
            nodeId = rawPath,
            remoteId = rawPath,
            name = "%25 #? 😀",
            path = rawPath,
            type = SourceNodeType.Folder,
        )

        assertEquals(node.name, sourceBreadcrumbName(node.path, "%25", mapOf(node.path to node.name)))
        assertEquals(rawPath, node.path)
    }
}
