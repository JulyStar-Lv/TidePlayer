package io.github.julystar.musicapp.feature.importing.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.components.LocalDesignBottomContentInset
import io.github.julystar.musicapp.core.domain.model.ImportSelectionMode
import io.github.julystar.musicapp.core.presentation.platform.PlatformBackHandler
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.icon_chevron_right
import io.github.julystar.musicapp.source.api.SourceNode
import io.github.julystar.musicapp.source.api.SourceNodeType
import musicapp.feature.importing.generated.resources.Res
import musicapp.feature.importing.generated.resources.icon_back
import musicapp.feature.importing.generated.resources.icon_cloud
import musicapp.feature.importing.generated.resources.icon_file
import musicapp.feature.importing.generated.resources.icon_folder
import musicapp.feature.importing.generated.resources.icon_image
import musicapp.feature.importing.generated.resources.icon_music_note
import musicapp.feature.importing.generated.resources.icon_toggle_all
import musicapp.feature.importing.generated.resources.icon_warning
import musicapp.feature.importing.generated.resources.icon_yes
import musicapp.feature.importing.generated.resources.import_library_current_folder
import musicapp.feature.importing.generated.resources.import_library_empty_desc
import musicapp.feature.importing.generated.resources.import_library_empty_title
import musicapp.feature.importing.generated.resources.import_library_location_label
import musicapp.feature.importing.generated.resources.import_library_select_current
import musicapp.feature.importing.generated.resources.import_library_source_label
import musicapp.feature.importing.generated.resources.import_library_title
import musicapp.feature.importing.generated.resources.import_folder_already_imported
import musicapp.feature.importing.generated.resources.import_folder_clear_selection
import musicapp.feature.importing.generated.resources.import_folder_deselect_all
import musicapp.feature.importing.generated.resources.import_folder_empty
import musicapp.feature.importing.generated.resources.import_folder_save
import musicapp.feature.importing.generated.resources.import_folder_retry
import musicapp.feature.importing.generated.resources.import_folder_select_all
import musicapp.feature.importing.generated.resources.import_folder_selected_count
import musicapp.feature.importing.generated.resources.import_folder_selected_title
import musicapp.feature.importing.generated.resources.import_folder_a11y_enter
import musicapp.feature.importing.generated.resources.import_folder_a11y_back
import musicapp.feature.importing.generated.resources.import_folder_a11y_inherited
import musicapp.feature.importing.generated.resources.import_folder_a11y_partial
import musicapp.feature.importing.generated.resources.import_folder_a11y_selected
import musicapp.feature.importing.generated.resources.import_folder_a11y_unselected
import musicapp.feature.importing.generated.resources.import_folder_hide_selected
import musicapp.feature.importing.generated.resources.import_folder_save_hint
import musicapp.feature.importing.generated.resources.import_folder_show_selected
import musicapp.feature.importing.generated.resources.import_musics_error_authentication_desc
import musicapp.feature.importing.generated.resources.import_musics_error_authentication_title
import musicapp.feature.importing.generated.resources.import_musics_error_permission_desc
import musicapp.feature.importing.generated.resources.import_musics_error_permission_action
import musicapp.feature.importing.generated.resources.import_musics_error_permission_title
import musicapp.feature.importing.generated.resources.import_musics_error_timeout_desc
import musicapp.feature.importing.generated.resources.import_musics_error_timeout_title
import musicapp.feature.importing.generated.resources.import_musics_error_unknown_desc
import musicapp.feature.importing.generated.resources.import_musics_error_unknown_title
import musicapp.feature.importing.generated.resources.import_musics_paths_root
import musicapp.feature.importing.generated.resources.import_musics_title_default
import musicapp.feature.importing.generated.resources.import_musics_title_multi_suffix
import musicapp.feature.importing.generated.resources.import_musics_title_single_suffix

@Composable
private fun ImportEntriesSkeleton(
    horizontalPadding: Dp,
    foldersOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shapes = DesignTokens.shapes
    val spacing = DesignTokens.spacing

    @Composable
    fun Block(
        width: Dp,
        height: Dp,
    ) {
        val color = MiuixTheme.colorScheme.surfaceContainerHigh
        Box(modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(shapes.xs))
            .background(color)
        )
    }

    @Composable
    fun FolderItem() {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(38.dp)
        ) {
            Block(width = 38.dp, height = 38.dp)
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxHeight()
            ) {
                Block(width = 138.dp, height = 17.dp)
                Block(width = 45.dp, height = 9.dp)
            }
        }
    }

    @Composable
    fun FileItem() {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Block(width = 38.dp, height = 38.dp)
                Block(width = 138.dp, height = 17.dp)
            }
            Block(width = 20.dp, height = 20.dp)
        }
    }


    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = spacing.md)
    ) {
        Block(
            width = 144.dp,
            height = 17.dp
        )
        FolderItem()
        if (foldersOnly) {
            FolderItem()
            FolderItem()
        } else {
            FileItem()
            FileItem()
        }
    }
}

@Composable
private fun ImportEntry(
    entry: SourceNode,
    checked: Boolean,
    allowNodeTypes: List<SourceNodeType>,
    onClickEntry: (entry: SourceNode) -> Unit
) {
    val canCheck = allowNodeTypes.any { type -> type == entry.type }
    val canOpen = entry.type == SourceNodeType.Folder
    val painter = when (entry.type) {
        SourceNodeType.Folder -> painterResource(Res.drawable.icon_folder)
        SourceNodeType.Image -> painterResource(Res.drawable.icon_image)
        SourceNodeType.Track -> painterResource(Res.drawable.icon_music_note)
        else -> painterResource(Res.drawable.icon_file)
    }
    val onClick = {
        onClickEntry(entry)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1.0F)
            ) {
                ImportEntryIcon(painter = painter, active = canCheck || canOpen)
                Text(
                    text = entry.name,
                    color = MiuixTheme.colorScheme.onSurface,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(modifier = Modifier.width(12.dp))
            if (canCheck) {
                Checkbox(
                    state = if (checked) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off,
                    onClick = {
                        onClick()
                    },
                )
            } else if (canOpen) {
                Icon(
                    painter = painterResource(CoreRes.drawable.icon_chevron_right),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        }
    }
}

@Composable
private fun ImportEntryIcon(
    painter: Painter,
    active: Boolean,
) {
    val shape = RoundedCornerShape(DesignTokens.shapes.md)
    val tint = if (active) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(shape)
            .background(if (active) MiuixTheme.colorScheme.tertiaryContainer else MiuixTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, if (active) MiuixTheme.colorScheme.primary.copy(alpha = 0.18f) else MiuixTheme.colorScheme.outline, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun ImportSectionLabel(
    text: String,
    horizontalPadding: Dp,
) {
    Text(
        text = text,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        style = MiuixTheme.textStyles.footnote1,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = horizontalPadding),
    )
}

@Composable
private fun EmptyLibraryFolderHint() {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ImportEntryIcon(
                painter = painterResource(Res.drawable.icon_folder),
                active = false,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(Res.string.import_library_empty_title),
                    color = MiuixTheme.colorScheme.onSurface,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(Res.string.import_library_empty_desc),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
        }
    }
}

@Composable
private fun CurrentDirectoryAction(
    currentPath: String,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bottomContentInset = LocalDesignBottomContentInset.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = horizontalPadding,
                end = horizontalPadding,
                bottom = 12.dp + bottomContentInset,
            ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(Res.string.import_library_current_folder),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote2,
                )
                Text(
                    text = currentPath,
                    color = MiuixTheme.colorScheme.onSurface,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                minWidth = 132.dp,
                enabled = enabled,
                onClick = onClick,
            ) { Text(stringResource(Res.string.import_library_select_current)) }
        }
    }
}

@Composable
private fun ImportEntries(
    state: ImportState,
    horizontalPadding: Dp,
    onAction: (ImportAction) -> Unit,
) {
    val spacing = DesignTokens.spacing
    val shapes = DesignTokens.shapes
    val bottomContentInset = LocalDesignBottomContentInset.current
    val choosingDirectory = state.selectionMode == ImportSelectionMode.CurrentDirectory
    if (choosingDirectory) {
        FolderPickerEntries(state, horizontalPadding, onAction)
        return
    }
    val visibleEntries = if (choosingDirectory) {
        state.entries.filter { entry -> entry.type == SourceNodeType.Folder }
    } else {
        state.entries
    }
    val currentPath = if (state.splitPaths.isEmpty()) {
        "/"
    } else {
        state.splitPaths.joinToString(separator = "/", prefix = "/") { it.name }
    }

    @Composable
    fun PathTab(
        text: String,
        path: String,
        disabled: Boolean,
    ) {
        val shape = RoundedCornerShape(shapes.full)
        val color = if (!disabled) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.onSurfaceVariantSummary
        }
        Text(
            text = text,
            color = color,
            style = MiuixTheme.textStyles.footnote2,
            fontWeight = if (!disabled) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(shape)
                .background(if (!disabled) MiuixTheme.colorScheme.tertiaryContainer else MiuixTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, if (!disabled) MiuixTheme.colorScheme.primary.copy(alpha = 0.16f) else MiuixTheme.colorScheme.outline, shape)
                .clickable(
                    enabled = !disabled,
                    onClick = {
                        onAction(ImportAction.OpenPath(path))
                    }
                )
                .widthIn(24.dp, 148.dp)
                .padding(horizontal = spacing.sm, vertical = spacing.xxs)
        )
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (choosingDirectory) {
                ImportSectionLabel(
                    text = stringResource(Res.string.import_library_location_label),
                    horizontalPadding = horizontalPadding,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .wrapContentHeight()
                    .padding(horizontal = horizontalPadding, vertical = spacing.xs)
                    .horizontalScroll(rememberScrollState())
            ) {
                PathTab(
                    text = stringResource(Res.string.import_musics_paths_root),
                    path = "/",
                    disabled = state.splitPaths.isEmpty()
                )
                for ((index, v) in state.splitPaths.withIndex()) {
                    Icon(
                        painter = painterResource(CoreRes.drawable.icon_chevron_right),
                        contentDescription = null,
                        modifier = Modifier.size(8.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    PathTab(
                        text = v.name,
                        path = v.path,
                        disabled = index == state.splitPaths.size - 1,
                    )
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                contentPadding = PaddingValues(
                    top = spacing.xs,
                    bottom = if (choosingDirectory) {
                        112.dp + bottomContentInset
                    } else {
                        88.dp + bottomContentInset
                    },
                ),
            ) {
                if (choosingDirectory && visibleEntries.isEmpty()) {
                    item(key = "empty-library-folder") {
                        EmptyLibraryFolderHint()
                    }
                }
                itemsIndexed(visibleEntries, key = { index, item -> item.lazyListKey(index) }) { _, item ->
                    ImportEntry(
                        entry = item,
                        checked = state.selectedPaths.contains(item.path),
                        allowNodeTypes = state.allowNodeTypes,
                        onClickEntry = { entry ->
                            onAction(ImportAction.OpenEntry(entry))
                        },
                    )
                }
            }
        }
        if (choosingDirectory) {
            val selectedAccountIsSmb = state.storageAccounts
                .firstOrNull { account -> account.accountId == state.selectedStorageAccountId }
                ?.isSmb == true
            CurrentDirectoryAction(
                currentPath = currentPath,
                horizontalPadding = horizontalPadding,
                enabled = !selectedAccountIsSmb || currentPath != "/",
                modifier = Modifier
                    .align(Alignment.BottomCenter),
                onClick = { onAction(ImportAction.FinishCurrentDirectory) },
            )
        } else if (state.selectedCount > 0) {
            FloatingActionButton(
                onClick = {
                    onAction(ImportAction.FinishSelection)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = horizontalPadding, bottom = horizontalPadding)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.icon_yes),
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun FolderPickerEntries(
    state: ImportState,
    horizontalPadding: Dp,
    onAction: (ImportAction) -> Unit,
) {
    val spacing = DesignTokens.spacing
    val bottomContentInset = LocalDesignBottomContentInset.current
    val folders = state.entries.filter { it.type == SourceNodeType.Folder }
    var panelExpanded by rememberSaveable { mutableStateOf(false) }
    val selectedLabels = selectedFolderLabels(state.selectedPaths)
    val panelToggleDescription = stringResource(
        if (panelExpanded) {
            Res.string.import_folder_hide_selected
        } else {
            Res.string.import_folder_show_selected
        },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding),
        ) {
            if (state.splitPaths.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = spacing.sm),
                ) {
                    FolderBreadcrumb(
                        text = stringResource(Res.string.import_musics_paths_root),
                        enabled = true,
                        onClick = { onAction(ImportAction.OpenPath("/")) },
                    )
                    val visibleParts = if (state.splitPaths.size > 3) {
                        state.splitPaths.takeLast(2)
                    } else {
                        state.splitPaths
                    }
                    if (visibleParts.size != state.splitPaths.size) {
                        Icon(
                            painter = painterResource(CoreRes.drawable.icon_chevron_right),
                            contentDescription = null,
                            modifier = Modifier.size(8.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Text(
                            text = "…",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.footnote1,
                        )
                    }
                    visibleParts.forEach { part ->
                        Icon(
                            painter = painterResource(CoreRes.drawable.icon_chevron_right),
                            contentDescription = null,
                            modifier = Modifier.size(8.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        FolderBreadcrumb(
                            text = part.name,
                            enabled = part != state.splitPaths.last(),
                            onClick = { onAction(ImportAction.OpenPath(part.path)) },
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (folders.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(Res.string.import_folder_empty),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.body2,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(folders, key = { index, item -> item.lazyListKey(index) }) { index, folder ->
                            FolderPickerRow(
                                folder = folder,
                                selectionState = folderSelectionState(folder.path, state.selectedPaths),
                                alreadyImported = folder.path in state.persistedPaths,
                                onOpen = { onAction(ImportAction.OpenEntry(folder)) },
                                onToggle = { onAction(ImportAction.ToggleFolderSelection(folder)) },
                            )
                            if (index != folders.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
            Spacer(Modifier.height(104.dp + bottomContentInset))
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    bottom = 12.dp + bottomContentInset,
                ),
            cornerRadius = 20.dp,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            if (panelExpanded && selectedLabels.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.import_folder_selected_title),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(Res.string.import_folder_clear_selection),
                        color = MiuixTheme.colorScheme.primary,
                        style = MiuixTheme.textStyles.footnote1,
                        modifier = Modifier
                            .clickable {
                                panelExpanded = false
                                onAction(ImportAction.ClearFolderSelection)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 168.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    selectedLabels.forEach { (path, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.heightIn(min = 40.dp),
                        ) {
                            Text(
                                text = label,
                                color = MiuixTheme.colorScheme.onSurface,
                                style = MiuixTheme.textStyles.body2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "×",
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                style = MiuixTheme.textStyles.title3,
                                modifier = Modifier
                                    .clickable {
                                        if (selectedLabels.size == 1) panelExpanded = false
                                        onAction(ImportAction.RemoveFolderSelection(path))
                                    }
                                    .padding(8.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(spacing.xs))
                HorizontalDivider()
                Spacer(Modifier.height(spacing.sm))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            role = Role.Button
                            contentDescription = panelToggleDescription
                        }
                        .clickable(enabled = selectedLabels.isNotEmpty()) {
                            panelExpanded = !panelExpanded
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                Res.string.import_folder_selected_count,
                                state.selectedCount,
                            ),
                            color = MiuixTheme.colorScheme.onSurface,
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(Res.string.import_folder_save_hint),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.footnote2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (selectedLabels.isNotEmpty()) {
                        Icon(
                            painter = painterResource(CoreRes.drawable.icon_chevron_right),
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .size(12.dp)
                                .rotate(if (panelExpanded) 90f else -90f),
                        )
                    }
                }
                Spacer(Modifier.width(spacing.sm))
                Button(
                    minWidth = 104.dp,
                    minHeight = 44.dp,
                    onClick = { onAction(ImportAction.FinishCurrentDirectory) },
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.icon_yes),
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.import_folder_save))
                }
            }
        }
    }
}

@Composable
private fun FolderBreadcrumb(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (enabled) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
        style = MiuixTheme.textStyles.footnote1,
        fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun FolderPickerRow(
    folder: SourceNode,
    selectionState: FolderSelectionState,
    alreadyImported: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    val inherited = selectionState == FolderSelectionState.InheritedSelected
    val navigationDescription = stringResource(Res.string.import_folder_a11y_enter, folder.name)
    val selectionDescription = stringResource(
        when (selectionState) {
            FolderSelectionState.Unselected -> Res.string.import_folder_a11y_unselected
            FolderSelectionState.Selected -> Res.string.import_folder_a11y_selected
            FolderSelectionState.PartiallySelected -> Res.string.import_folder_a11y_partial
            FolderSelectionState.InheritedSelected -> Res.string.import_folder_a11y_inherited
        },
        folder.name,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .semantics {
                    role = Role.Button
                    contentDescription = navigationDescription
                }
                .clickable(onClick = onOpen)
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ImportEntryIcon(painterResource(Res.drawable.icon_folder), active = true)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    color = MiuixTheme.colorScheme.onSurface,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (alreadyImported) {
                    Text(
                        text = stringResource(Res.string.import_folder_already_imported),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote2,
                    )
                }
            }
        }
        Checkbox(
            state = when (selectionState) {
                FolderSelectionState.Unselected -> ToggleableState.Off
                FolderSelectionState.PartiallySelected -> ToggleableState.Indeterminate
                FolderSelectionState.Selected,
                FolderSelectionState.InheritedSelected -> ToggleableState.On
            },
            onClick = onToggle,
            enabled = !inherited,
            modifier = Modifier
                .padding(horizontal = 13.dp, vertical = 16.dp)
                .semantics {
                    contentDescription = folder.name
                    stateDescription = selectionDescription
                },
        )
    }
}

internal fun selectedFolderLabels(paths: Collection<String>): List<Pair<String, String>> {
    val names = paths.associateWith { path -> folderPathSegments(path).lastOrNull().orEmpty().ifBlank { "/" } }
    val duplicates = names.values.groupingBy { it }.eachCount()
    return names.map { (path, name) ->
        val segments = folderPathSegments(path)
        path to if (duplicates[name] == 1 || segments.size < 2) {
            name
        } else {
            "${segments[segments.lastIndex - 1]} › $name"
        }
    }
}

internal fun io.github.julystar.musicapp.source.api.SourceNode.lazyListKey(index: Int): String =
    "import-entry-$index-$path"

@Composable
private fun ImportStorageCard(
    item: ImportStorageAccountUi,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val spacing = DesignTokens.spacing
    val shapes = DesignTokens.shapes
    val shape = RoundedCornerShape(shapes.lg)
    val bgColor = if (selected) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.surfaceContainer
    }
    val textColor = if (selected) {
        MiuixTheme.colorScheme.onPrimary
    } else {
        MiuixTheme.colorScheme.onSurface
    }
    val subtitleColor = if (selected) {
        MiuixTheme.colorScheme.onPrimary.copy(alpha = 0.76f)
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    val borderColor = if (selected) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.28f)
    } else {
        MiuixTheme.colorScheme.outline
    }

    Box(
        modifier = modifier
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick)
            .background(bgColor)
            .border(1.dp, borderColor, shape)
            .heightIn(min = 76.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
        ) {
            Text(
                text = item.name,
                color = textColor,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitle,
                color = subtitleColor,
                style = MiuixTheme.textStyles.footnote2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!item.isLocal) {
            Icon(
                painter = painterResource(Res.drawable.icon_cloud),
                contentDescription = null,
                tint = if (selected) {
                    MiuixTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                } else {
                    MiuixTheme.colorScheme.primary.copy(alpha = 0.18f)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .width(27.dp)
                    .offset(7.dp, 1.dp),
            )
        }
    }
}

@Composable
private fun ImportStorages(
    state: ImportState,
    horizontalPadding: Dp,
    onAction: (ImportAction) -> Unit,
) {
    val onlyAccount = state.storageAccounts.singleOrNull()
        ?.takeIf { state.selectionMode == ImportSelectionMode.CurrentDirectory }
    if (onlyAccount != null) {
        ImportStorageCard(
            item = onlyAccount,
            selected = true,
            enabled = false,
            modifier = Modifier
                .padding(horizontal = horizontalPadding)
                .fillMaxWidth(),
            onClick = {},
        )
        return
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacing.sm),
        modifier = Modifier
            .padding(horizontal = horizontalPadding)
            .horizontalScroll(rememberScrollState()),
    ) {
        state.storageAccounts.forEach { item ->
            ImportStorageCard(
                item = item,
                selected = state.selectedStorageAccountId == item.accountId,
                enabled = true,
                modifier = Modifier.width(156.dp),
                onClick = { onAction(ImportAction.SelectStorage(item.accountId)) },
            )
        }
    }
}

@Composable
private fun ImportMusicsWarningImpl(
    title: String,
    subTitle: String,
    color: Color,
    iconPainter: Painter,
    horizontalPadding: Dp,
    actionLabel: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val spacing = DesignTokens.spacing

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding, vertical = spacing.lg),
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 336.dp),
            onClick = if (actionLabel == null) onClick else null,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(DesignTokens.shapes.full))
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = iconPainter,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Text(
                    text = title,
                    color = color,
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subTitle,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(0.dp, 256.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                actionLabel?.let { label ->
                    Button(
                        minWidth = 144.dp,
                        onClick = onClick,
                    ) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun ImportMusicsError(
    loadState: ImportLoadState,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
    onAction: (ImportAction) -> Unit,
) {
    val title = when (loadState) {
        ImportLoadState.AuthenticationFailed -> stringResource(Res.string.import_musics_error_authentication_title)
        ImportLoadState.Timeout -> stringResource(Res.string.import_musics_error_timeout_title)
        ImportLoadState.UnknownError -> stringResource(Res.string.import_musics_error_unknown_title)
        ImportLoadState.NeedsPermission -> stringResource(Res.string.import_musics_error_permission_title)
        ImportLoadState.Loading,
        ImportLoadState.Ready -> {
            throw RuntimeException("unsupported type")
        }
    }
    val desc = when (loadState) {
        ImportLoadState.AuthenticationFailed -> stringResource(Res.string.import_musics_error_authentication_desc)
        ImportLoadState.Timeout -> stringResource(Res.string.import_musics_error_timeout_desc)
        ImportLoadState.UnknownError -> stringResource(Res.string.import_musics_error_unknown_desc)
        ImportLoadState.NeedsPermission -> stringResource(Res.string.import_musics_error_permission_desc)
        ImportLoadState.Loading,
        ImportLoadState.Ready -> {
            throw RuntimeException("unsupported type")
        }
    }

    ImportMusicsWarningImpl(
        title = title,
        subTitle = desc,
        color = MiuixTheme.colorScheme.error,
        iconPainter = painterResource(Res.drawable.icon_warning),
        horizontalPadding = horizontalPadding,
        actionLabel = if (loadState == ImportLoadState.NeedsPermission) {
            stringResource(Res.string.import_musics_error_permission_action)
        } else {
            stringResource(Res.string.import_folder_retry)
        },
        modifier = modifier,
        onClick = {
            onAction(ImportAction.RecoverFromLoadError)
        }
    )
}

@Composable
fun ImportScreen(
    state: ImportState,
    onAction: (ImportAction) -> Unit,
) {
    val spacing = DesignTokens.spacing
    val selectedSourceName = state.storageAccounts
        .firstOrNull { it.accountId == state.selectedStorageAccountId }
        ?.name
    val titleText = if (state.selectionMode == ImportSelectionMode.CurrentDirectory) {
        stringResource(Res.string.import_library_title)
    } else {
        when (state.selectedCount) {
            0 -> stringResource(Res.string.import_musics_title_default)
            1 -> "${state.selectedCount} ${stringResource(Res.string.import_musics_title_single_suffix)}"
            else -> "${state.selectedCount} ${stringResource(Res.string.import_musics_title_multi_suffix)}"
        }
    }

    PlatformBackHandler(enabled = state.canUndo) {
        onAction(ImportAction.NavigateBack)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val horizontalPadding = if (maxWidth < 600.dp) spacing.pageCompact else spacing.pageMedium

        Column(
            modifier = Modifier
                .background(MiuixTheme.colorScheme.background)
                .fillMaxSize()
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 64.dp)
                    .padding(horizontal = horizontalPadding, vertical = 8.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    IconButton(
                        modifier = Modifier.size(40.dp),
                        onClick = {
                            onAction(ImportAction.NavigateBack)
                        },
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.icon_back),
                            contentDescription = stringResource(Res.string.import_folder_a11y_back),
                            tint = MiuixTheme.colorScheme.onBackground,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = titleText,
                            color = MiuixTheme.colorScheme.onBackground,
                            style = MiuixTheme.textStyles.title3,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (
                            state.selectionMode == ImportSelectionMode.CurrentDirectory &&
                            !selectedSourceName.isNullOrBlank()
                        ) {
                            Text(
                                text = selectedSourceName,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = MiuixTheme.textStyles.footnote2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (state.selectionMode == ImportSelectionMode.CurrentDirectory) {
                    val folderPaths = state.entries
                        .filter { it.type == SourceNodeType.Folder }
                        .map { it.path }
                    val allSelected = isCurrentFolderLevelSelected(state.selectedPaths, folderPaths)
                    Text(
                        text = stringResource(
                            if (allSelected) {
                                Res.string.import_folder_deselect_all
                            } else {
                                Res.string.import_folder_select_all
                            }
                        ),
                        color = if (folderPaths.isNotEmpty()) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(enabled = folderPaths.isNotEmpty()) {
                                onAction(ImportAction.ToggleAll)
                            }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                } else {
                    IconButton(
                        enabled = !state.disabledToggleAll,
                        onClick = {
                            onAction(ImportAction.ToggleAll)
                        },
                    ) { Icon(painterResource(Res.drawable.icon_toggle_all), contentDescription = null) }
                }
            }
            if (state.selectionMode != ImportSelectionMode.CurrentDirectory) {
                ImportStorages(
                    state = state,
                    horizontalPadding = horizontalPadding,
                    onAction = onAction,
                )
                Box(modifier = Modifier.height(spacing.md))
            }
            when (state.loadState) {
                ImportLoadState.Loading -> ImportEntriesSkeleton(
                    horizontalPadding = horizontalPadding,
                    foldersOnly = state.selectionMode == ImportSelectionMode.CurrentDirectory,
                )
                ImportLoadState.Timeout,
                ImportLoadState.AuthenticationFailed,
                ImportLoadState.UnknownError,
                ImportLoadState.NeedsPermission -> ImportMusicsError(
                    loadState = state.loadState,
                    horizontalPadding = horizontalPadding,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    onAction = onAction,
                )
                ImportLoadState.Ready -> {
                    ImportEntries(
                        state = state,
                        horizontalPadding = horizontalPadding,
                        onAction = onAction,
                    )
                }
            }
        }
    }
}
