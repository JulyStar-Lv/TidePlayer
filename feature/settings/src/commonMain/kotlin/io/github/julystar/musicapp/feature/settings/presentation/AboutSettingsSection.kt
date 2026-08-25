package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import musicapp.feature.settings.generated.resources.*
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference

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
        SmallTitle(
            text = stringResource(Res.string.settings_about_app),
            insideMargin = settingsSectionTitleMargin,
        )
        Card {
            BasicComponent(
                title = stringResource(Res.string.settings_about_name),
                summary = "Tide Player",
            )
            BasicComponent(
                title = stringResource(Res.string.settings_about_version),
                summary = appVersion.ifBlank { unavailable },
            )
            BasicComponent(
                title = stringResource(Res.string.settings_about_build),
                summary = appBuildInfo.ifBlank { unavailable },
            )
            BasicComponent(
                title = stringResource(Res.string.settings_about_commit),
                summary = gitCommitSha.ifBlank { unavailable },
            )
        }
        SmallTitle(
            text = stringResource(Res.string.settings_about_links),
            insideMargin = settingsSectionTitleMargin,
        )
        Card {
            ArrowPreference(
                title = stringResource(Res.string.settings_about_licenses),
                summary = stringResource(Res.string.settings_licenses_title),
                onClick = onOpenLicenses,
            )
            ArrowPreference(
                title = stringResource(Res.string.settings_about_repository),
                summary = APP_REPOSITORY_URL,
                onClick = onOpenRepository,
            )
            ArrowPreference(
                title = stringResource(Res.string.settings_about_issues),
                summary = APP_ISSUES_URL,
                onClick = onOpenIssues,
            )
            BasicComponent(
                title = stringResource(Res.string.settings_about_privacy),
                summary = stringResource(Res.string.settings_about_privacy_summary),
            )
        }
    }
}

@Composable
fun LicensesSettingsScreen(onBack: (() -> Unit)?) {
    SettingsPageLayout(title = stringResource(Res.string.settings_licenses_title), onBack = onBack) {
        SmallTitle(
            text = stringResource(Res.string.settings_licenses_title),
            insideMargin = settingsSectionTitleMargin,
        )
        Card {
            BasicComponent(
                title = stringResource(Res.string.settings_app_display_name),
                summary = stringResource(Res.string.settings_licenses_summary),
            )
        }
    }
}
