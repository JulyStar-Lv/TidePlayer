package io.github.julystar.musicapp.plugin.management

import androidx.compose.runtime.Composable
import musicapp.shared.generated.resources.Res
import musicapp.shared.generated.resources.plugins_additional_access
import musicapp.shared.generated.resources.plugins_automatic_lookup
import musicapp.shared.generated.resources.plugins_automatic_lookup_summary
import musicapp.shared.generated.resources.plugins_batch_lookup
import musicapp.shared.generated.resources.plugins_batch_lookup_summary
import musicapp.shared.generated.resources.plugins_cancel
import musicapp.shared.generated.resources.plugins_choose_zip
import musicapp.shared.generated.resources.plugins_clear_cache
import musicapp.shared.generated.resources.plugins_configuration
import musicapp.shared.generated.resources.plugins_configuration_section
import musicapp.shared.generated.resources.plugins_confirm_uninstall_named
import musicapp.shared.generated.resources.plugins_done
import musicapp.shared.generated.resources.plugins_empty
import musicapp.shared.generated.resources.plugins_empty_summary
import musicapp.shared.generated.resources.plugins_enabled_label
import musicapp.shared.generated.resources.plugins_import
import musicapp.shared.generated.resources.plugins_more_options_named
import musicapp.shared.generated.resources.plugins_import_idle_summary
import musicapp.shared.generated.resources.plugins_import_installing_summary
import musicapp.shared.generated.resources.plugins_import_local_zip
import musicapp.shared.generated.resources.plugins_import_selected_summary
import musicapp.shared.generated.resources.plugins_import_success_summary
import musicapp.shared.generated.resources.plugins_install
import musicapp.shared.generated.resources.plugins_install_success
import musicapp.shared.generated.resources.plugins_installed
import musicapp.shared.generated.resources.plugins_installed_label
import musicapp.shared.generated.resources.plugins_no_installable
import musicapp.shared.generated.resources.plugins_operation_failed
import musicapp.shared.generated.resources.plugins_overview_summary
import musicapp.shared.generated.resources.plugins_overview_title
import musicapp.shared.generated.resources.plugins_remove_data_message
import musicapp.shared.generated.resources.plugins_save
import musicapp.shared.generated.resources.plugins_status
import musicapp.shared.generated.resources.plugins_title
import musicapp.shared.generated.resources.plugins_uninstall
import musicapp.shared.generated.resources.plugins_uninstall_named
import musicapp.shared.generated.resources.plugins_validation_failed
import org.jetbrains.compose.resources.stringResource

private val PluginValidationFailurePattern = Regex("^(\\d+) plugin entries failed validation$")
private val PluginConfigurePattern = Regex("^Configure (.+)$")
private val PluginMoreOptionsPattern = Regex("^More options for (.+)$")
private val PluginConfirmUninstallPattern = Regex("^Uninstall (.+)\\?$")
private val PluginUninstallPattern = Regex("^Uninstall (.+)$")

/**
 * Localizes only application-owned labels from the restored plugin UI while preserving
 * its original component tree and interaction model. Plugin manifest names, descriptions,
 * field labels, markdown, and option labels fall through unchanged.
 */
@Composable
internal fun pluginUiText(value: String): String {
    when (value) {
        "Metadata plugins" -> return stringResource(Res.string.plugins_title)
        "Installed plugins" -> return stringResource(Res.string.plugins_installed)
        "Status" -> return stringResource(Res.string.plugins_status)
        "Plugin operation failed" -> return stringResource(Res.string.plugins_operation_failed)
        "Metadata providers" -> return stringResource(Res.string.plugins_overview_title)
        "Enabled plugins are available for manual lookup. Automatic and batch access can be granted separately." ->
            return stringResource(Res.string.plugins_overview_summary)
        "installed" -> return stringResource(Res.string.plugins_installed_label)
        "enabled" -> return stringResource(Res.string.plugins_enabled_label)
        "No plugins installed" -> return stringResource(Res.string.plugins_empty)
        "Import a ZIP that follows Lyrico Plugin API v1–v4." -> return stringResource(Res.string.plugins_empty_summary)
        "Import" -> return stringResource(Res.string.plugins_import)
        "Import local ZIP" -> return stringResource(Res.string.plugins_import_local_zip)
        "Plugin installed" -> return stringResource(Res.string.plugins_install_success)
        "Archives are validated before an existing version is replaced" ->
            return stringResource(Res.string.plugins_import_idle_summary)
        "Ready to validate and install" -> return stringResource(Res.string.plugins_import_selected_summary)
        "Validating archive and plugin manifest…" -> return stringResource(Res.string.plugins_import_installing_summary)
        "Imported plugin is disabled until you review it" -> return stringResource(Res.string.plugins_import_success_summary)
        "Choose ZIP" -> return stringResource(Res.string.plugins_choose_zip)
        "Cancel" -> return stringResource(Res.string.plugins_cancel)
        "Install" -> return stringResource(Res.string.plugins_install)
        "Configuration" -> return stringResource(Res.string.plugins_configuration_section)
        "Additional access" -> return stringResource(Res.string.plugins_additional_access)
        "Automatic lookup" -> return stringResource(Res.string.plugins_automatic_lookup)
        "Use during background metadata refresh" -> return stringResource(Res.string.plugins_automatic_lookup_summary)
        "Batch lookup" -> return stringResource(Res.string.plugins_batch_lookup)
        "Use when updating multiple tracks" -> return stringResource(Res.string.plugins_batch_lookup_summary)
        "Save" -> return stringResource(Res.string.plugins_save)
        "Done" -> return stringResource(Res.string.plugins_done)
        "Clear cache" -> return stringResource(Res.string.plugins_clear_cache)
        "Uninstall" -> return stringResource(Res.string.plugins_uninstall)
        "No installable plugin found in ZIP" -> return stringResource(Res.string.plugins_no_installable)
        "Plugin files, configuration, cache, and private runtime context will be removed from this device." ->
            return stringResource(Res.string.plugins_remove_data_message)
    }

    PluginValidationFailurePattern.matchEntire(value)?.let { match ->
        return stringResource(Res.string.plugins_validation_failed, match.groupValues[1].toInt())
    }
    PluginConfigurePattern.matchEntire(value)?.let { match ->
        return stringResource(Res.string.plugins_configuration, match.groupValues[1])
    }
    PluginMoreOptionsPattern.matchEntire(value)?.let { match ->
        return stringResource(Res.string.plugins_more_options_named, match.groupValues[1])
    }
    PluginConfirmUninstallPattern.matchEntire(value)?.let { match ->
        return stringResource(Res.string.plugins_confirm_uninstall_named, match.groupValues[1])
    }
    PluginUninstallPattern.matchEntire(value)?.let { match ->
        return stringResource(Res.string.plugins_uninstall_named, match.groupValues[1])
    }

    return value
}
