package io.github.julystar.musicapp.plugin.management

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import io.github.julystar.musicapp.core.presentation.components.AppSwitch
import io.github.julystar.musicapp.core.presentation.components.AppTextField
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.julystar.musicapp.core.presentation.components.DesignDialog
import io.github.julystar.musicapp.core.presentation.components.DesignBottomSheetDefaults
import io.github.julystar.musicapp.core.presentation.components.DesignBottomSheetHandle
import io.github.julystar.musicapp.core.presentation.components.DesignDialogDefaults
import io.github.julystar.musicapp.core.presentation.components.DesignDialogHost
import io.github.julystar.musicapp.core.presentation.components.DesignDialogNavigationBarStyle
import io.github.julystar.musicapp.core.presentation.components.DesignContextMenu
import io.github.julystar.musicapp.core.presentation.components.DesignContextMenuItem
import io.github.julystar.musicapp.core.presentation.components.DesignIconButton
import io.github.julystar.musicapp.core.presentation.components.DesignIconButtonSize
import io.github.julystar.musicapp.core.presentation.components.DesignIconButtonVariant
import io.github.julystar.musicapp.core.presentation.components.DesignLoadingIndicator
import io.github.julystar.musicapp.core.presentation.components.DesignListDivider
import io.github.julystar.musicapp.core.presentation.components.LocalDesignBottomContentInset
import io.github.julystar.musicapp.core.presentation.components.DesignPreferenceRow
import io.github.julystar.musicapp.core.presentation.components.DesignSettingsGroup
import io.github.julystar.musicapp.core.presentation.components.DesignStickyGlassActionBar
import io.github.julystar.musicapp.core.presentation.components.DesignTextButton
import io.github.julystar.musicapp.core.presentation.components.DesignTextButtonSize
import io.github.julystar.musicapp.core.presentation.components.DesignTextButtonVariant
import io.github.julystar.musicapp.core.presentation.components.resolveDialogMaxHeight
import io.github.julystar.musicapp.core.presentation.components.shouldDismissBottomSheet
import io.github.julystar.musicapp.core.presentation.theme.DesignPalette
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import androidx.compose.ui.graphics.Color
import io.github.julystar.musicapp.plugin.install.ManifestConfigField
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.painterResource
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.icon_chevron_right
import musicapp.core.presentation.generated.resources.icon_deleteseep
import musicapp.core.presentation.generated.resources.icon_folder
import musicapp.core.presentation.generated.resources.icon_ok
import musicapp.core.presentation.generated.resources.icon_refresh
import musicapp.core.presentation.generated.resources.icon_settings_sliders
import musicapp.core.presentation.generated.resources.icon_settings_puzzle
import musicapp.core.presentation.generated.resources.icon_vertialcal_more
import musicapp.shared.generated.resources.Res as SharedRes
import musicapp.shared.generated.resources.plugins_configure
import musicapp.shared.generated.resources.plugins_remove
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
fun PluginSettingsRoot(
    onBack: () -> Unit,
    manager: PluginManager = koinInject(),
) {
    val plugins by manager.plugins().collectAsState(initial = emptyList())
    val bottomContentInset = LocalDesignBottomContentInset.current
    val scope = rememberCoroutineScope()
    val configValues = remember { mutableStateMapOf<String, Map<String, String>>() }
    var operationError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var importState by remember { mutableStateOf(PluginImportState.Idle) }
    var selectedArchive by remember { mutableStateOf<PlatformFile?>(null) }
    var editingPluginId by remember { mutableStateOf<String?>(null) }
    var pendingUninstall by remember { mutableStateOf<PluginSummary?>(null) }
    val operationFailedText = pluginUiText("Plugin operation failed")
    val noInstallableText = pluginUiText("No installable plugin found in ZIP")
    val validationFailedText = pluginUiText("0 plugin entries failed validation")

    LaunchedEffect(plugins.map { it.id to it.updatedAt }) {
        plugins.forEach { plugin ->
            configValues[plugin.id] = manager.config(plugin.id)
        }
        configValues.keys.toList()
            .filter { pluginId -> plugins.none { it.id == pluginId } }
            .forEach(configValues::remove)
    }

    LaunchedEffect(importState) {
        if (importState == PluginImportState.Success) {
            delay(1_800)
            importState = PluginImportState.Idle
        }
    }

    fun runOperation(
        onSuccess: () -> Unit = {},
        onFailure: () -> Unit = {},
        block: suspend () -> Unit,
    ) {
        if (busy) return
        scope.launch {
            busy = true
            operationError = null
            runCatching { block() }
                .onSuccess { onSuccess() }
                .onFailure { error ->
                    operationError = error.message ?: operationFailedText
                    onFailure()
                }
            busy = false
        }
    }

    val zipPicker = rememberFilePickerLauncher(
        type = FileKitType.File(extensions = listOf("zip")),
    ) { file ->
        file ?: return@rememberFilePickerLauncher
        selectedArchive = file
        importState = PluginImportState.Selected
        operationError = null
    }

    val editingPlugin = plugins.firstOrNull { plugin -> plugin.id == editingPluginId }

    PluginRemovalDialog(
        plugin = pendingUninstall,
        onConfirm = {
            val plugin = pendingUninstall ?: return@PluginRemovalDialog
            pendingUninstall = null
            runOperation {
                manager.uninstall(plugin.id)
            }
        },
        onDismiss = { pendingUninstall = null },
    )

    PluginConfigurationDialog(
        plugin = editingPlugin,
        values = editingPlugin?.let { plugin -> configValues[plugin.id].orEmpty() }.orEmpty(),
        busy = busy,
        onValuesChange = { values ->
            editingPlugin?.let { plugin -> configValues[plugin.id] = values }
        },
        onPermissionsChange = { automatic, batch ->
            val plugin = editingPlugin ?: return@PluginConfigurationDialog
            runOperation {
                manager.setLookupPermissions(
                    pluginId = plugin.id,
                    allowManual = plugin.allowManualLookup,
                    allowAutomatic = automatic,
                    allowBatch = batch,
                )
            }
        },
        onSave = {
            val plugin = editingPlugin ?: return@PluginConfigurationDialog
            val values = configValues[plugin.id].orEmpty()
            runOperation(
                onSuccess = { editingPluginId = null },
            ) {
                plugin.configFields
                    .filterNot { field -> field.type == "markdown" }
                    .forEach { field ->
                        val value = values[field.key]
                        manager.setConfig(
                            pluginId = plugin.id,
                            key = field.key,
                            value = value?.takeUnless { it.isBlank() && !field.required },
                        )
                    }
            }
        },
        onClearCache = {
            val plugin = editingPlugin ?: return@PluginConfigurationDialog
            runOperation { manager.clearCache(plugin.id) }
        },
        onDismiss = { editingPluginId = null },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = DesignTokens.spacing.pageCompact,
                    top = DesignTokens.adaptive.compactHeaderHeight + 8.dp,
                    end = DesignTokens.spacing.pageCompact,
                    bottom = 8.dp + bottomContentInset,
                ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PluginOverviewCard(
                installedCount = plugins.size,
                enabledCount = plugins.count(PluginSummary::enabled),
            )

            DesignSettingsGroup(title = pluginUiText("Installed plugins")) {
                if (plugins.isEmpty()) {
                    EmptyPluginsRow()
                } else {
                    plugins.forEach { plugin ->
                        PluginListRow(
                            plugin = plugin,
                            busy = busy,
                            onConfigure = { editingPluginId = plugin.id },
                            onUninstall = { pendingUninstall = plugin },
                            onEnabledChange = { enabled ->
                                runOperation {
                                    manager.setEnabled(plugin.id, enabled)
                                }
                            },
                        )
                    }
                }
            }

            PluginImportCard(
                state = importState,
                archiveName = selectedArchive?.name,
                busy = busy,
                onChooseArchive = zipPicker::launch,
                onCancel = {
                    selectedArchive = null
                    importState = PluginImportState.Idle
                },
                onInstall = {
                    val archive = selectedArchive ?: return@PluginImportCard
                    importState = PluginImportState.Installing
                    runOperation(
                        onSuccess = {
                            selectedArchive = null
                            importState = PluginImportState.Success
                        },
                        onFailure = { importState = PluginImportState.Selected },
                    ) {
                        val localZip = FileKit.cacheDir / "plugin-import.zip"
                        try {
                            localZip.write(archive)
                            val result = manager.installFromZip(localZip.path)
                            if (result.installed.isEmpty()) {
                                val reason = result.failed.firstOrNull()?.reason
                                    ?: noInstallableText
                                error(reason)
                            }
                            if (result.failed.isNotEmpty()) {
                                error(validationFailedText.replaceFirst("0", result.failed.size.toString()))
                            }
                        } finally {
                            localZip.delete()
                        }
                    }
                },
            )

            operationError?.let { message ->
                DesignSettingsGroup(title = pluginUiText("Status")) {
                    DesignPreferenceRow(
                        title = pluginUiText("Plugin operation failed"),
                        summary = message,
                        titleColor = MiuixTheme.colorScheme.error,
                        showDivider = false,
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        DesignStickyGlassActionBar(
            title = pluginUiText("Metadata plugins"),
            collapseFraction = 1f,
            onNavigateBack = onBack,
            compactTitle = true,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

private enum class PluginImportState { Idle, Selected, Installing, Success }

// ── Page Components ──

@Composable
private fun PluginOverviewCard(installedCount: Int, enabledCount: Int) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MiuixTheme.colorScheme.primary.copy(alpha = 0.20f), shape)
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.06f))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(CoreRes.drawable.icon_settings_puzzle),
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = pluginUiText("Metadata providers"),
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        style = MiuixTheme.textStyles.body1,
                    )
                    Text(
                        text = "Lyrico API v1–v4",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MiuixTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = pluginUiText("Enabled plugins are available for manual lookup. Automatic and batch access can be granted separately."),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatChip(label = pluginUiText("installed"), value = installedCount.toString())
                    StatChip(
                        label = pluginUiText("enabled"),
                        value = enabledCount.toString(),
                        accentColor = DesignPalette.SupportGreen,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, accentColor: Color = MiuixTheme.colorScheme.onSurface) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = value,
            color = accentColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun PluginListRow(
    plugin: PluginSummary,
    busy: Boolean,
    onConfigure: () -> Unit,
    onUninstall: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    val enabled = plugin.enabled
    var moreMenuExpanded by remember(plugin.id) { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 82.dp)
                .alpha(if (enabled) 1f else 0.55f)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = plugin.name,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val indicatorColor = if (enabled) {
                        DesignPalette.SupportGreen
                    } else {
                        MiuixTheme.colorScheme.outline
                    }
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(indicatorColor),
                    )
                }
                Text(
                    text = "${plugin.description} · v${plugin.versionName}",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AppSwitch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                enabled = !busy,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box {
                DesignIconButton(
                    size = DesignIconButtonSize.Touch,
                    variant = DesignIconButtonVariant.Default,
                    painter = painterResource(CoreRes.drawable.icon_vertialcal_more),
                    contentDescription = pluginUiText("More options for ${plugin.name}"),
                    enabled = !busy,
                    onClick = { moreMenuExpanded = true },
                )
                Box(
                    contentAlignment = Alignment.TopEnd,
                    modifier = Modifier.offset(20.dp, 20.dp),
                ) {
                    DesignContextMenu(
                        expanded = moreMenuExpanded,
                        onDismissRequest = { moreMenuExpanded = false },
                        items = listOf(
                            DesignContextMenuItem(
                                label = SharedRes.string.plugins_configure,
                                icon = CoreRes.drawable.icon_settings_sliders,
                                onClick = onConfigure,
                            ),
                            DesignContextMenuItem(
                                label = SharedRes.string.plugins_remove,
                                icon = CoreRes.drawable.icon_deleteseep,
                                isError = true,
                                onClick = onUninstall,
                            ),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPluginsRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(CoreRes.drawable.icon_folder),
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = pluginUiText("No plugins installed"),
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = pluginUiText("Import a ZIP that follows Lyrico Plugin API v1–v4."),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun PluginImportCard(
    state: PluginImportState,
    archiveName: String?,
    busy: Boolean,
    onChooseArchive: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
) {
    DesignSettingsGroup(title = pluginUiText("Import")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val isSuccess = state == PluginImportState.Success
            val indicatorBg = if (isSuccess) {
                DesignPalette.SupportGreen.copy(alpha = 0.12f)
            } else {
                MiuixTheme.colorScheme.surfaceContainer
            }
            val indicatorTint = if (isSuccess) DesignPalette.SupportGreen
            else MiuixTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(indicatorBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        if (isSuccess) CoreRes.drawable.icon_ok
                        else CoreRes.drawable.icon_folder,
                    ),
                    contentDescription = null,
                    tint = indicatorTint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val title = when (state) {
                    PluginImportState.Idle -> pluginUiText("Import local ZIP")
                    PluginImportState.Success -> pluginUiText("Plugin installed")
                    else -> archiveName.orEmpty()
                }
                Text(
                    text = title,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = when (state) {
                    PluginImportState.Idle -> pluginUiText("Archives are validated before an existing version is replaced")
                    PluginImportState.Selected -> pluginUiText("Ready to validate and install")
                    PluginImportState.Installing -> pluginUiText("Validating archive and plugin manifest…")
                    PluginImportState.Success -> pluginUiText("Imported plugin is disabled until you review it")
                }
                Text(
                    text = subtitle,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
            when (state) {
                PluginImportState.Idle -> DesignTextButton(
                    text = pluginUiText("Choose ZIP"),
                    variant = DesignTextButtonVariant.PrimaryFilled,
                    size = DesignTextButtonSize.Medium,
                    enabled = !busy,
                    onClick = onChooseArchive,
                )
                PluginImportState.Selected -> Row {
                    DesignTextButton(
                        text = pluginUiText("Cancel"),
                        variant = DesignTextButtonVariant.Default,
                        size = DesignTextButtonSize.Small,
                        enabled = !busy,
                        onClick = onCancel,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    DesignTextButton(
                        text = pluginUiText("Install"),
                        variant = DesignTextButtonVariant.PrimaryFilled,
                        size = DesignTextButtonSize.Small,
                        enabled = !busy,
                        onClick = onInstall,
                    )
                }
                PluginImportState.Installing -> DesignLoadingIndicator(size = 16.dp)
                PluginImportState.Success -> {} // success icon only
            }
        }
    }
}

// ── Dialogs ──

@Composable
private fun PluginConfigurationDialog(
    plugin: PluginSummary?,
    values: Map<String, String>,
    busy: Boolean,
    onValuesChange: (Map<String, String>) -> Unit,
    onPermissionsChange: (automatic: Boolean, batch: Boolean) -> Unit,
    onSave: () -> Unit,
    onClearCache: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dialogVisible = plugin != null
    val visibilityState = remember { MutableTransitionState(false) }
    visibilityState.targetState = dialogVisible

    var retainedPlugin by remember { mutableStateOf(plugin) }
    var retainedValues by remember { mutableStateOf(values) }
    SideEffect {
        if (plugin != null) {
            retainedPlugin = plugin
            retainedValues = values
        }
    }

    if (!visibilityState.currentState && !visibilityState.targetState) return
    val dialogPlugin = plugin ?: retainedPlugin ?: return
    val dialogValues = if (plugin != null) values else retainedValues
    val fields = dialogPlugin.configFields
    val markdownFields = fields.filter { field ->
        field.type == "markdown" && isPluginConfigFieldVisible(field, dialogValues)
    }
    val visibleEditable = fields.filter { field ->
        field.type != "markdown" && isPluginConfigFieldVisible(field, dialogValues)
    }
    val cardShape = RoundedCornerShape(22.dp)
    val windowSize = LocalWindowInfo.current.containerDpSize
    val compact = windowSize.isSpecified &&
        isCompactPluginConfigurationDialog(windowSize.width)
    val dialogMaxHeight = resolveDialogMaxHeight(
        requestedMaxHeight = null,
        viewportHeight = windowSize.height,
    )
    val verticalAlign = if (compact) Alignment.BottomCenter else Alignment.Center
    val dialogRadius = DesignTokens.shapes.lg
    val sheetShape = if (compact) {
        RoundedCornerShape(
            topStart = dialogRadius,
            topEnd = dialogRadius,
            bottomStart = 0.dp,
            bottomEnd = 0.dp,
        )
    } else {
        RoundedCornerShape(dialogRadius)
    }
    val contentPadding = if (compact) {
        Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp)
    } else {
        Modifier.padding(24.dp)
    }
    val density = LocalDensity.current
    val dismissDistancePx = with(density) { DesignBottomSheetDefaults.dismissDistance.toPx() }
    val dismissVelocityPxPerSecond = with(density) { DesignBottomSheetDefaults.dismissVelocity.toPx() }
    val dragAnimationScope = rememberCoroutineScope()
    var sheetDragOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragAnimationJob by remember { mutableStateOf<Job?>(null) }
    val sheetDraggableState = rememberDraggableState { deltaPx ->
        sheetDragOffsetPx = (sheetDragOffsetPx + deltaPx).coerceAtLeast(0f)
    }

    LaunchedEffect(dialogPlugin.id, dialogVisible) {
        if (dialogVisible) sheetDragOffsetPx = 0f
    }

    val compactHeaderDragModifier = if (compact) {
        Modifier.draggable(
            state = sheetDraggableState,
            orientation = Orientation.Vertical,
            enabled = dialogVisible,
            onDragStarted = {
                dragAnimationJob?.cancel()
            },
            onDragStopped = { velocity ->
                if (
                    shouldDismissBottomSheet(
                        dragOffsetPx = sheetDragOffsetPx,
                        velocityPxPerSecond = velocity,
                        distanceThresholdPx = dismissDistancePx,
                        velocityThresholdPxPerSecond = dismissVelocityPxPerSecond,
                    )
                ) {
                    onDismiss()
                } else {
                    dragAnimationJob?.cancel()
                    dragAnimationJob = dragAnimationScope.launch {
                        animate(
                            initialValue = sheetDragOffsetPx,
                            targetValue = 0f,
                            animationSpec = spring(),
                        ) { value, _ ->
                            sheetDragOffsetPx = value
                        }
                    }
                }
            },
        )
    } else {
        Modifier
    }

    DesignDialogHost(
        onDismissRequest = {
            if (dialogVisible) onDismiss()
        },
        navigationBarStyle = if (compact) {
            DesignDialogNavigationBarStyle.Surface
        } else {
            DesignDialogNavigationBarStyle.Dimmed
        },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            AnimatedVisibility(
                visibleState = visibilityState,
                modifier = Modifier.fillMaxSize(),
                enter = DesignDialogDefaults.scrimEnterTransition(),
                exit = DesignDialogDefaults.scrimExitTransition(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DesignDialogDefaults.scrimColor)
                        .clickable(
                            enabled = dialogVisible,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onDismiss() },
                )
            }

            AnimatedVisibility(
                visible = dialogVisible,
                modifier = Modifier.align(verticalAlign),
                enter = if (compact) {
                    DesignBottomSheetDefaults.surfaceEnterTransition()
                } else {
                    DesignDialogDefaults.surfaceEnterTransition()
                },
                exit = if (compact) {
                    DesignBottomSheetDefaults.surfaceExitTransition()
                } else {
                    DesignDialogDefaults.surfaceExitTransition()
                },
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .then(
                            if (compact) {
                                Modifier.height(dialogMaxHeight)
                            } else {
                                Modifier.heightIn(max = dialogMaxHeight)
                            },
                        )
                        .offset {
                            IntOffset(
                                x = 0,
                                y = if (compact) sheetDragOffsetPx.roundToInt() else 0,
                            )
                        }
                        .shadow(DesignTokens.elevation.overlay, sheetShape)
                        .clip(sheetShape)
                        .background(MiuixTheme.colorScheme.background)
                        .border(
                            width = 1.dp,
                            color = MiuixTheme.colorScheme.outline.copy(alpha = 0.15f),
                            shape = sheetShape,
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { /* consume clicks */ }
                        .then(
                            if (compact) Modifier.navigationBarsPadding() else Modifier,
                        ),
                ) {
                    if (compact) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(compactHeaderDragModifier)
                                .padding(horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            DesignBottomSheetHandle()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 36.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = dialogPlugin.name,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 19.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .then(contentPadding),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        if (!compact) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(CoreRes.drawable.icon_settings_puzzle),
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = dialogPlugin.name,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 19.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        markdownFields.forEach { field ->
                            PluginMarkdownCard(
                                field = field,
                                shape = cardShape,
                                compact = compact,
                            )
                        }

                        if (visibleEditable.isNotEmpty()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PluginDialogSectionLabel(pluginUiText("Configuration"))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(cardShape)
                                        .border(
                                            1.dp,
                                            MiuixTheme.colorScheme.outline.copy(alpha = 0.15f),
                                            cardShape,
                                        )
                                        .background(MiuixTheme.colorScheme.surfaceContainer),
                                ) {
                                    visibleEditable.forEachIndexed { index, field ->
                                        PluginConfigFieldCardRow(
                                            field = field,
                                            value = dialogValues[field.key].orEmpty(),
                                            enabled = dialogVisible && !busy,
                                            onValueChange = { value ->
                                                onValuesChange(dialogValues + (field.key to value))
                                            },
                                            showDivider = index != visibleEditable.lastIndex,
                                        )
                                    }
                                }
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PluginDialogSectionLabel(pluginUiText("Additional access"))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(cardShape)
                                    .border(
                                        1.dp,
                                        MiuixTheme.colorScheme.outline.copy(alpha = 0.15f),
                                        cardShape,
                                    )
                                    .background(MiuixTheme.colorScheme.surfaceContainer),
                            ) {
                                PermissionToggleRow(
                                    title = pluginUiText("Automatic lookup"),
                                    summary = pluginUiText("Use during background metadata refresh"),
                                    checked = dialogPlugin.allowAutomaticLookup,
                                    enabled = dialogVisible && dialogPlugin.enabled && !busy,
                                    onChange = { value ->
                                        onPermissionsChange(value, dialogPlugin.allowBatchLookup)
                                    },
                                )
                                PermissionToggleRow(
                                    title = pluginUiText("Batch lookup"),
                                    summary = pluginUiText("Use when updating multiple tracks"),
                                    checked = dialogPlugin.allowBatchLookup,
                                    enabled = dialogVisible && dialogPlugin.enabled && !busy,
                                    onChange = { value ->
                                        onPermissionsChange(dialogPlugin.allowAutomaticLookup, value)
                                    },
                                    showDivider = false,
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(
                                        MiuixTheme.colorScheme.outline.copy(alpha = 0.12f),
                                    ),
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PluginClearCacheButton(
                                    enabled = dialogVisible && !busy,
                                    onClick = onClearCache,
                                )
                                DesignTextButton(
                                    text = pluginUiText(if (visibleEditable.isNotEmpty()) "Save" else "Done"),
                                    variant = DesignTextButtonVariant.PrimaryFilled,
                                    size = DesignTextButtonSize.Medium,
                                    enabled = dialogVisible && !busy,
                                    onClick = onSave,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginClearCacheButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .heightIn(min = DesignTokens.adaptive.minimumTouchTarget)
            .clip(RoundedCornerShape(50))
            .background(MiuixTheme.colorScheme.surfaceContainerHighest)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.55f)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(CoreRes.drawable.icon_refresh),
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = pluginUiText("Clear cache"),
            color = MiuixTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun PluginDialogSectionLabel(text: String) {
    Text(
        text = text,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.9.sp,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun PluginMarkdownCard(
    field: ManifestConfigField,
    shape: RoundedCornerShape,
    compact: Boolean,
) {
    val content = field.defaultValue
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: field.summary
            ?.trim()
            ?.takeIf(String::isNotBlank)
        ?: field.title
    val lines = content
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .toList()
    val bodyColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val foreground = MiuixTheme.colorScheme.onSurface
    val linkColor = MiuixTheme.colorScheme.primary
    val codeBackground = MiuixTheme.colorScheme.surfaceContainerHighest

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                1.dp,
                MiuixTheme.colorScheme.outline.copy(alpha = 0.15f),
                shape,
            )
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(if (compact) 16.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        lines.forEach { line ->
            when {
                line.startsWith("#") -> {
                    Text(
                        text = line.trimStart('#').trim(),
                        color = foreground,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
                line.startsWith("- ") -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "•",
                            color = bodyColor,
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                        )
                        Text(
                            text = buildPluginMarkdownInlineText(
                                raw = line.removePrefix("- ").trim(),
                                foreground = foreground,
                                linkColor = linkColor,
                                codeBackground = codeBackground,
                            ),
                            modifier = Modifier.weight(1f),
                            color = bodyColor,
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                        )
                    }
                }
                else -> {
                    Text(
                        text = buildPluginMarkdownInlineText(
                            raw = line,
                            foreground = foreground,
                            linkColor = linkColor,
                            codeBackground = codeBackground,
                        ),
                        color = bodyColor,
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
        }
    }
}

private fun buildPluginMarkdownInlineText(
    raw: String,
    foreground: Color,
    linkColor: Color,
    codeBackground: Color,
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    while (cursor < raw.length) {
        when {
            raw.startsWith("**", cursor) -> {
                val end = raw.indexOf("**", startIndex = cursor + 2)
                if (end < 0) {
                    append(raw.substring(cursor))
                    break
                }
                withStyle(
                    SpanStyle(
                        color = foreground,
                        fontWeight = FontWeight.SemiBold,
                    ),
                ) {
                    append(raw.substring(cursor + 2, end))
                }
                cursor = end + 2
            }
            raw[cursor] == '`' -> {
                val end = raw.indexOf('`', startIndex = cursor + 1)
                if (end < 0) {
                    append(raw.substring(cursor))
                    break
                }
                withStyle(
                    SpanStyle(
                        color = foreground,
                        background = codeBackground,
                    ),
                ) {
                    append(raw.substring(cursor + 1, end))
                }
                cursor = end + 1
            }
            raw[cursor] == '[' -> {
                val labelEnd = raw.indexOf(']', startIndex = cursor + 1)
                val urlStart = labelEnd + 1
                val urlEnd = if (
                    labelEnd > cursor &&
                    urlStart < raw.length &&
                    raw[urlStart] == '('
                ) {
                    raw.indexOf(')', startIndex = urlStart + 1)
                } else {
                    -1
                }
                if (urlEnd < 0) {
                    append(raw[cursor])
                    cursor += 1
                } else {
                    val url = raw.substring(urlStart + 1, urlEnd)
                    withLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    fontWeight = FontWeight.Medium,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                        ),
                    ) {
                        append(raw.substring(cursor + 1, labelEnd))
                    }
                    cursor = urlEnd + 1
                }
            }
            else -> {
                val nextMarker = listOf(
                    raw.indexOf("**", startIndex = cursor),
                    raw.indexOf('`', startIndex = cursor),
                    raw.indexOf('[', startIndex = cursor),
                )
                    .filter { it >= 0 }
                    .minOrNull()
                    ?: raw.length
                append(raw.substring(cursor, nextMarker))
                cursor = nextMarker
            }
        }
    }
}

internal fun isCompactPluginConfigurationDialog(windowWidth: Dp): Boolean =
    DesignDialogDefaults.isCompactWindow(windowWidth)

@Composable
private fun PluginRemovalDialog(
    plugin: PluginSummary?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dialogVisible = plugin != null
    var retainedPlugin by remember { mutableStateOf(plugin) }
    SideEffect {
        if (plugin != null) retainedPlugin = plugin
    }
    val dialogPlugin = plugin ?: retainedPlugin ?: return
    DesignDialog(
        show = dialogVisible,
        onDismiss = onDismiss,
        maxWidth = 400.dp,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(MiuixTheme.colorScheme.error.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(CoreRes.drawable.icon_deleteseep),
                contentDescription = null,
                tint = MiuixTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = pluginUiText("Uninstall ${dialogPlugin.name}?"),
            color = MiuixTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = pluginUiText("Plugin files, configuration, cache, and private runtime context will be removed from this device."),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            DesignTextButton(
                text = pluginUiText("Cancel"),
                variant = DesignTextButtonVariant.Default,
                size = DesignTextButtonSize.Medium,
                onClick = onDismiss,
            )
            Spacer(modifier = Modifier.width(8.dp))
            DesignTextButton(
                text = pluginUiText("Uninstall"),
                variant = DesignTextButtonVariant.Error,
                size = DesignTextButtonSize.Medium,
                onClick = onConfirm,
            )
        }
    }
}

// ── Config Field Helpers ──

@Composable
private fun PermissionToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    showDivider: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                )
                Text(
                    text = summary,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            AppSwitch(
                checked = checked,
                onCheckedChange = onChange,
                enabled = enabled,
            )
        }
        if (showDivider) {
            DesignListDivider()
        }
    }
}

@Composable
private fun PluginConfigFieldCardRow(
    field: ManifestConfigField,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    showDivider: Boolean = true,
) {
    when {
        field.type == "switch" || field.type == "boolean" -> {
            PermissionToggleRow(
                title = field.title,
                summary = field.summary.orEmpty(),
                checked = value.toBooleanStrictOrNull() == true,
                enabled = enabled,
                onChange = { onValueChange(it.toString()) },
                showDivider = showDivider,
            )
        }
        field.options.isNotEmpty() -> {
            PluginConfigSelectRow(
                field = field,
                value = value,
                enabled = enabled,
                onValueChange = onValueChange,
                showDivider = showDivider,
            )
        }
        else -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (enabled) 1f else 0.45f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = field.title,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                    AppTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = field.summary.orEmpty(),
                        enabled = enabled,
                        singleLine = field.type != "textarea",
                        visualTransformation = if (field.type == "password") {
                            PasswordVisualTransformation()
                        } else {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        },
                    )
                }
                if (showDivider) {
                    DesignListDivider()
                }
            }
        }
    }
}

@Composable
private fun PluginConfigSelectRow(
    field: ManifestConfigField,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    showDivider: Boolean = true,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var anchorBounds by remember { mutableStateOf<Rect?>(null) }
    val selectedLabel = field.options.firstOrNull { it.value == value }?.label ?: value
    val trailingColor = if (menuOpen) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    val windowSize = LocalWindowInfo.current.containerDpSize
    val density = LocalDensity.current
    val maxWindowMenuHeight = if (windowSize.isSpecified) {
        (windowSize.height - 32.dp).coerceAtLeast(0.dp)
    } else {
        360.dp
    }
    val estimatedMenuHeight = minOf(
        56.dp * field.options.size + 16.dp,
        360.dp,
        maxWindowMenuHeight,
    )
    val placeMenuAbove = anchorBounds?.let { bounds ->
        with(density) {
            val anchorTop = bounds.top.toDp()
            val anchorBottom = bounds.bottom.toDp()
            shouldPlacePluginConfigMenuAbove(
                anchorTop = anchorTop,
                anchorBottom = anchorBottom,
                windowHeight = windowSize.height,
                menuHeight = estimatedMenuHeight,
            )
        }
    } ?: false
    val menuOffset = anchorBounds?.let { bounds ->
        with(density) {
            if (placeMenuAbove) {
                val anchorTop = bounds.top.toDp()
                val desiredTop = maxOf(
                    16.dp,
                    anchorTop - estimatedMenuHeight - 8.dp,
                )
                IntOffset(x = 0, y = (desiredTop - anchorTop).roundToPx())
            } else {
                IntOffset(
                    x = 0,
                    y = (estimatedMenuHeight + 8.dp).roundToPx(),
                )
            }
        }
    } ?: IntOffset.Zero

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f),
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        anchorBounds = coordinates.boundsInWindow()
                    }
                    .heightIn(min = 68.dp)
                    .clickable(enabled = enabled) { menuOpen = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = field.title,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        style = MiuixTheme.textStyles.body1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    field.summary?.let { summary ->
                        Text(
                            text = summary,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                    }
                }
                Text(
                    text = selectedLabel,
                    color = if (menuOpen) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Box(
                    modifier = Modifier.size(DesignTokens.adaptive.minimumTouchTarget),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(CoreRes.drawable.icon_chevron_right),
                            contentDescription = null,
                            tint = trailingColor,
                            modifier = Modifier
                                .size(14.dp)
                                .rotate(-90f),
                        )
                        Icon(
                            painter = painterResource(CoreRes.drawable.icon_chevron_right),
                            contentDescription = null,
                            tint = trailingColor,
                            modifier = Modifier
                                .size(14.dp)
                                .rotate(90f),
                        )
                    }
                }
            }

            if (menuOpen) {
                Popup(
                    popupPositionProvider = FullScreenPopupPositionProvider,
                    properties = PopupProperties(focusable = false),
                    onDismissRequest = { menuOpen = false },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Color.Black.copy(
                                    alpha = if (
                                        MiuixTheme.colorScheme.background.luminance() < 0.5f
                                    ) {
                                        0.45f
                                    } else {
                                        0.25f
                                    },
                                ),
                            )
                            .clickable(
                                indication = null,
                                interactionSource = remember {
                                    MutableInteractionSource()
                                },
                            ) { menuOpen = false },
                    )
                }
                Popup(
                    alignment = if (placeMenuAbove) {
                        Alignment.TopEnd
                    } else {
                        Alignment.BottomEnd
                    },
                    offset = menuOffset,
                    properties = PopupProperties(focusable = true),
                    onDismissRequest = { menuOpen = false },
                ) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(28.dp))
                            .background(MiuixTheme.colorScheme.surfaceContainerHighest)
                            .widthIn(min = 200.dp, max = 360.dp)
                            .heightIn(max = estimatedMenuHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        field.options.forEach { option ->
                            val isSelected = option.value == value
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        onValueChange(option.value)
                                        menuOpen = false
                                    }
                                    .padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = option.label,
                                    color = if (isSelected) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.onSurface,
                                    style = MiuixTheme.textStyles.body1,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (isSelected) {
                                    Icon(
                                        painter = painterResource(CoreRes.drawable.icon_ok),
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showDivider) {
            DesignListDivider()
        }
    }
}

private object FullScreenPopupPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

internal fun shouldPlacePluginConfigMenuAbove(
    anchorTop: Dp,
    anchorBottom: Dp,
    windowHeight: Dp,
    menuHeight: Dp,
): Boolean =
    anchorTop > windowHeight / 2 ||
        anchorBottom + 8.dp + menuHeight > windowHeight - 16.dp

internal fun isPluginConfigFieldVisible(
    field: ManifestConfigField,
    values: Map<String, String>,
): Boolean = field.dependency?.matches(values, depth = 0) ?: true

private fun JsonObject.matches(
    values: Map<String, String>,
    depth: Int,
): Boolean {
    if (depth > MAX_PLUGIN_CONFIG_DEPENDENCY_DEPTH) return false

    val dependencyTypeCount = keys.count { key ->
        key == "match" || key == "and" || key == "or" || key == "not"
    }
    if (dependencyTypeCount != 1) return false

    (this["match"] as? JsonObject)?.let { match ->
        val key = (match["key"] as? JsonPrimitive)?.contentOrNull
        val expected = (match["value"] as? JsonPrimitive)?.contentOrNull
        return !key.isNullOrBlank() && expected != null && values[key] == expected
    }
    (this["and"] as? JsonObject)?.let { and ->
        val conditions = and["conditions"] as? JsonArray ?: return false
        return conditions.isNotEmpty() && conditions.all { condition ->
            (condition as? JsonObject)?.matches(values, depth + 1) == true
        }
    }
    (this["or"] as? JsonObject)?.let { or ->
        val conditions = or["conditions"] as? JsonArray ?: return false
        return conditions.isNotEmpty() && conditions.any { condition ->
            (condition as? JsonObject)?.matches(values, depth + 1) == true
        }
    }
    (this["not"] as? JsonObject)?.let { not ->
        val condition = not["condition"] as? JsonObject ?: return false
        return !condition.matches(values, depth + 1)
    }
    return false
}

private const val MAX_PLUGIN_CONFIG_DEPENDENCY_DEPTH = 16
