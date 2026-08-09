package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import musicapp.feature.settings.generated.resources.*

@Composable
fun AboutSettingsSection(
    appVersion: String,
    appBuildInfo: String,
    gitCommitSha: String,
    onBack: (() -> Unit)?,
    onOpenLicenses: () -> Unit,
    onOpenRepository: () -> Unit,
    onOpenIssues: () -> Unit,
) {
    val unavailable = stringResource(Res.string.settings_unavailable)
    SettingsPageLayout(title = stringResource(Res.string.settings_about_title), onBack = onBack) {
        SettingsSection(title = stringResource(Res.string.settings_about_app)) {
            SettingsInfoRow(
                title = stringResource(Res.string.settings_about_name),
                value = "Tide Player",
            )
            SettingsInfoRow(
                title = stringResource(Res.string.settings_about_version),
                value = appVersion.ifBlank { unavailable },
            )
            SettingsInfoRow(
                title = stringResource(Res.string.settings_about_build),
                value = appBuildInfo.ifBlank { unavailable },
            )
            SettingsInfoRow(
                title = stringResource(Res.string.settings_about_commit),
                value = gitCommitSha.ifBlank { unavailable },
            )
        }
        SettingsSection(title = stringResource(Res.string.settings_about_links)) {
            SettingsInfoRow(
                title = stringResource(Res.string.settings_about_licenses),
                value = stringResource(Res.string.settings_licenses_title),
                onClick = onOpenLicenses,
            )
            SettingsInfoRow(
                title = stringResource(Res.string.settings_about_repository),
                value = APP_REPOSITORY_URL,
                onClick = onOpenRepository,
            )
            SettingsInfoRow(
                title = stringResource(Res.string.settings_about_issues),
                value = APP_ISSUES_URL,
                onClick = onOpenIssues,
            )
            SettingsInfoRow(
                title = stringResource(Res.string.settings_about_privacy),
                value = stringResource(Res.string.settings_about_privacy_summary),
            )
        }
    }
}

@Composable
fun LicensesSettingsScreen(onBack: (() -> Unit)?) {
    SettingsPageLayout(title = stringResource(Res.string.settings_licenses_title), onBack = onBack) {
        SettingsSection(title = stringResource(Res.string.settings_licenses_title)) {
            SettingsInfoRow(
                title = "Tide Player",
                value = stringResource(Res.string.settings_licenses_summary),
            )
        }
    }
}
