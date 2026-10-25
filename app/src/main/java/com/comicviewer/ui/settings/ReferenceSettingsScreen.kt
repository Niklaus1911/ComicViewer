package com.comicviewer.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comicviewer.ui.common.tabScreen
import com.comicviewer.AppBuildInfo
import com.comicviewer.AppLanguage
import com.comicviewer.AppThemeMode
import com.comicviewer.LibraryAutomationPreferences
import com.comicviewer.R
import com.comicviewer.ReaderPageMode
import com.comicviewer.ReaderPreferences
import com.comicviewer.ReaderReadingDirection
import com.comicviewer.resolvedReaderPreferences
import com.comicviewer.billing.ProBillingState
import com.comicviewer.ui.app.ThemeUiState
import com.comicviewer.ui.common.ReferenceCardShape
import com.comicviewer.ui.common.ReferenceScreenHeader
import com.comicviewer.ui.common.ReferenceScreenHorizontalPadding
import com.comicviewer.ui.library.ProUpgradeDialog
import com.comicviewer.ui.reader.ReaderSettingsDialog
import com.comicviewer.ui.theme.AppColor
import com.comicviewer.ui.theme.AppThemePreset

@Composable
fun ReferenceSettingsScreen(
    selectedLanguage: AppLanguage,
    themeUiState: ThemeUiState,
    readerPreferences: ReaderPreferences,
    automationPreferences: LibraryAutomationPreferences,
    proBillingState: ProBillingState,
    isDebugBuild: Boolean,
    debugProUnlocked: Boolean,
    buildInfo: AppBuildInfo,
    onBuyPro: () -> Unit,
    onRestorePro: () -> Unit,
    onDebugProUnlockedChanged: (Boolean) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onAccentColorSelected: (AppColor) -> Unit,
    onThemePresetSelected: (AppThemePreset) -> Unit,
    onAmoledThemeChanged: (Boolean) -> Unit,
    onReaderPreferencesChanged: (ReaderPreferences) -> Unit,
    onChooseImportFolder: () -> Unit,
    onAutomationPreferencesChanged: (LibraryAutomationPreferences) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTheme by remember { mutableStateOf(false) }
    var showReader by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showTapZones by remember { mutableStateOf(false) }
    var showPro by remember { mutableStateOf(false) }
    val activeReaderPreferences = resolvedReaderPreferences(
        readerPreferences = readerPreferences,
        isProUnlocked = proBillingState.isProUnlocked,
    )

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.tabScreen)
                .verticalScroll(rememberScrollState()),
        ) {
            ReferenceScreenHeader(title = stringResource(R.string.settings_title))
            SettingsSection(title = stringResource(R.string.settings_appearance_title)) {
                SettingsReferenceRow(
                    icon = Icons.Filled.Palette,
                    title = stringResource(R.string.settings_theme_mode_title),
                    subtitle = stringResource(themeUiState.themeMode.labelRes()),
                    showDivider = false,
                    onClick = { showTheme = true },
                )
                SettingsReferenceRow(
                    icon = Icons.Filled.Language,
                    title = stringResource(R.string.settings_language_title),
                    subtitle = stringResource(selectedLanguage.labelRes()),
                    onClick = { showLanguage = true },
                )
            }
            SettingsSection(title = stringResource(R.string.settings_section_reading)) {
                SettingsReferenceRow(
                    icon = Icons.Filled.AutoStories,
                    title = stringResource(R.string.settings_default_reading_mode),
                    subtitle = stringResource(activeReaderPreferences.pageMode.labelRes()),
                    showDivider = false,
                    onClick = { showReader = true },
                )
                SettingsReferenceRow(
                    icon = Icons.Filled.SwapHoriz,
                    title = stringResource(R.string.settings_default_direction),
                    subtitle = stringResource(activeReaderPreferences.readingDirection.labelRes()),
                    onClick = { showReader = true },
                )
                SettingsReferenceRow(
                    icon = Icons.Filled.TouchApp,
                    title = stringResource(R.string.settings_tap_zones),
                    subtitle = stringResource(R.string.settings_tap_zones_summary),
                    onClick = { showTapZones = true },
                )
                SettingsReferenceToggleRow(
                    icon = Icons.Filled.ScreenLockPortrait,
                    title = stringResource(R.string.reader_keep_screen_on),
                    checked = readerPreferences.keepScreenOn,
                    onCheckedChange = { onReaderPreferencesChanged(readerPreferences.copy(keepScreenOn = it)) },
                )
                SettingsReferenceToggleRow(
                    icon = Icons.Filled.Visibility,
                    title = stringResource(R.string.reader_show_page_number),
                    checked = readerPreferences.showPageNumber,
                    onCheckedChange = { onReaderPreferencesChanged(readerPreferences.copy(showPageNumber = it)) },
                )
            }
            SettingsSection(title = stringResource(R.string.settings_section_library)) {
                SettingsReferenceRow(
                    icon = Icons.Filled.Folder,
                    title = stringResource(R.string.settings_import_folder),
                    subtitle = automationPreferences.importFolderName ?: stringResource(R.string.settings_import_folder_not_set),
                    showDivider = false,
                    onClick = onChooseImportFolder,
                )
                SettingsReferenceToggleRow(
                    icon = Icons.Filled.HistoryToggleOff,
                    title = stringResource(R.string.settings_auto_scan_folder),
                    checked = automationPreferences.autoScanEnabled,
                    enabled = automationPreferences.importFolderUri != null,
                    onCheckedChange = {
                        onAutomationPreferencesChanged(automationPreferences.copy(autoScanEnabled = it))
                    },
                )
                SettingsReferenceToggleRow(
                    icon = Icons.Filled.CleaningServices,
                    title = stringResource(R.string.settings_auto_delete_temp),
                    subtitle = stringResource(R.string.settings_auto_delete_temp_description),
                    checked = automationPreferences.autoDeleteTempFiles,
                    onCheckedChange = {
                        onAutomationPreferencesChanged(automationPreferences.copy(autoDeleteTempFiles = it))
                    },
                )
                SettingsReferenceRow(
                    icon = Icons.Filled.Backup,
                    title = stringResource(R.string.settings_backup_restore),
                    onClick = { showBackup = true },
                )
            }
            SettingsSection(title = stringResource(R.string.app_name)) {
                SettingsReferenceRow(
                    icon = Icons.Filled.WorkspacePremium,
                    title = stringResource(if (proBillingState.isProUnlocked) R.string.pro_unlocked else R.string.pro_upgrade),
                    subtitle = stringResource(
                        if (proBillingState.isProUnlocked) R.string.pro_unlocked_message else R.string.pro_upgrade_message,
                    ),
                    showDivider = false,
                    onClick = { showPro = true },
                )
                SettingsReferenceRow(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.settings_about_title),
                    subtitle = "${buildInfo.versionName} (${buildInfo.versionCode})",
                    onClick = { showAbout = true },
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showTheme) {
        ThemeSettingsDialog(
            themeUiState = themeUiState,
            isProUnlocked = proBillingState.isProUnlocked,
            onThemeModeSelected = onThemeModeSelected,
            onDynamicColorChanged = onDynamicColorChanged,
            onAccentColorSelected = onAccentColorSelected,
            onThemePresetSelected = { preset ->
                if (preset.proOnly && !proBillingState.isProUnlocked) showPro = true else onThemePresetSelected(preset)
            },
            onAmoledThemeChanged = onAmoledThemeChanged,
            onDismiss = { showTheme = false },
        )
    }
    if (showReader) {
        ReaderSettingsDialog(
            readerPreferences = readerPreferences,
            activeReaderPreferences = activeReaderPreferences,
            isProUnlocked = proBillingState.isProUnlocked,
            onReaderPreferencesChanged = onReaderPreferencesChanged,
            onRequestPro = { showPro = true },
            onDismiss = { showReader = false },
        )
    }
    if (showLanguage) {
        LanguageSettingsDialog(
            selected = selectedLanguage,
            onSelected = onLanguageSelected,
            onDismiss = { showLanguage = false },
        )
    }
    if (showBackup) {
        AlertDialog(
            onDismissRequest = { showBackup = false },
            title = { Text(stringResource(R.string.settings_backup_restore)) },
            text = { Text(stringResource(R.string.settings_backup_description)) },
            confirmButton = {
                TextButton(onClick = {
                    showBackup = false
                    onExportBackup()
                }) { Text(stringResource(R.string.settings_export_backup)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBackup = false
                    onImportBackup()
                }) { Text(stringResource(R.string.settings_import_backup)) }
            },
        )
    }
    if (showTapZones) {
        AlertDialog(
            onDismissRequest = { showTapZones = false },
            title = { Text(stringResource(R.string.settings_tap_zones)) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        R.string.reader_previous_page,
                        R.string.reader_toggle_controls,
                        R.string.reader_next_page,
                    ).forEach { label ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                stringResource(label),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 28.dp, horizontal = 4.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTapZones = false }) { Text(stringResource(R.string.ok)) } },
        )
    }
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(stringResource(R.string.settings_about_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.settings_build_info_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    SettingsBuildInfoRow(
                        stringResource(R.string.settings_build_info_version_name),
                        buildInfo.versionName,
                    )
                    SettingsBuildInfoRow(
                        stringResource(R.string.settings_build_info_version_code),
                        buildInfo.versionCode.toString(),
                    )
                    SettingsBuildInfoRow(
                        stringResource(R.string.settings_build_info_build_time),
                        buildInfo.buildTimestamp,
                    )
                    SettingsBuildInfoRow(
                        stringResource(R.string.settings_build_info_application_id),
                        buildInfo.applicationId,
                    )
                    SettingsBuildInfoRow(
                        stringResource(R.string.settings_build_info_build_type),
                        buildInfo.buildType,
                    )
                    SettingsBuildInfoRow(
                        stringResource(R.string.settings_build_info_debug),
                        stringResource(
                            if (buildInfo.debug) R.string.settings_build_info_yes else R.string.settings_build_info_no,
                        ),
                    )
                    SettingsBuildInfoRow(
                        stringResource(R.string.settings_build_info_min_sdk),
                        buildInfo.minSdk.toString(),
                    )
                    SettingsBuildInfoRow(
                        stringResource(R.string.settings_build_info_target_sdk),
                        buildInfo.targetSdk.toString(),
                    )
                    SettingsBuildInfoRow(
                        stringResource(R.string.settings_build_info_compile_sdk),
                        buildInfo.compileSdk,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.ok)) } },
        )
    }
    if (showPro) {
        ProUpgradeDialog(
            billingState = proBillingState,
            onBuyPro = onBuyPro,
            onRestorePro = onRestorePro,
            isDebugBuild = isDebugBuild,
            debugProUnlocked = debugProUnlocked,
            onDebugProUnlockedChanged = onDebugProUnlockedChanged,
            onDismiss = { showPro = false },
        )
    }
}

@Composable
private fun SettingsBuildInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1.25f),
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ReferenceScreenHorizontalPadding),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
        )
        Surface(
            shape = ReferenceCardShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsReferenceRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    showDivider: Boolean = true,
    onClick: () -> Unit,
) {
    SettingsRowDivider(visible = showDivider)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsRowIcon(icon)
        SettingsRowText(title = title, subtitle = subtitle, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsReferenceToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    enabled: Boolean = true,
    showDivider: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsRowDivider(visible = showDivider)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsRowIcon(icon)
        SettingsRowText(
            title = title,
            subtitle = subtitle,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun SettingsRowIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp),
    )
}

@Composable
private fun SettingsRowText(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsRowDivider(visible: Boolean) {
    if (visible) {
        HorizontalDivider(
            modifier = Modifier.padding(start = 54.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun ThemeSettingsDialog(
    themeUiState: ThemeUiState,
    isProUnlocked: Boolean,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onAccentColorSelected: (AppColor) -> Unit,
    onThemePresetSelected: (AppThemePreset) -> Unit,
    onAmoledThemeChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_appearance_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeModeSelected(mode) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = themeUiState.themeMode == mode, onClick = { onThemeModeSelected(mode) })
                        Text(stringResource(mode.labelRes()))
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    CompactToggle(
                        label = stringResource(R.string.settings_dynamic_color_title),
                        checked = themeUiState.dynamicColorEnabled,
                        onCheckedChange = onDynamicColorChanged,
                    )
                }
                CompactToggle(
                    label = stringResource(R.string.settings_amoled_title),
                    checked = themeUiState.amoledEnabled,
                    onCheckedChange = onAmoledThemeChanged,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppColor.entries.forEach { color ->
                        val colorLabel = stringResource(color.labelRes())
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(color.swatch, CircleShape)
                                .selectable(
                                    selected = themeUiState.accentColor == color,
                                    role = Role.RadioButton,
                                    onClick = { onAccentColorSelected(color) },
                                )
                                .semantics { contentDescription = colorLabel },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (themeUiState.accentColor == color) {
                                Surface(modifier = Modifier.size(13.dp), shape = CircleShape, color = Color.White) {}
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppThemePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = themeUiState.themePreset == preset,
                            onClick = { onThemePresetSelected(preset) },
                            leadingIcon = if (preset.proOnly && !isProUnlocked) {
                                { Icon(Icons.Filled.WorkspacePremium, null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            label = { Text(stringResource(preset.labelRes())) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
private fun CompactToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LanguageSettingsDialog(
    selected: AppLanguage,
    onSelected: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language_title)) },
        text = {
            Column {
                AppLanguage.entries.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(language) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == language, onClick = { onSelected(language) })
                        Text(stringResource(language.labelRes()))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

private fun AppThemeMode.labelRes(): Int = when (this) {
    AppThemeMode.System -> R.string.settings_theme_system
    AppThemeMode.Light -> R.string.settings_theme_light
    AppThemeMode.Dark -> R.string.settings_theme_dark
}

private fun ReaderPageMode.labelRes(): Int = when (this) {
    ReaderPageMode.SinglePage -> R.string.reader_single_page
    ReaderPageMode.TwoPage -> R.string.reader_two_page
    ReaderPageMode.Vertical -> R.string.reader_vertical_scroll
}

private fun ReaderReadingDirection.labelRes(): Int = when (this) {
    ReaderReadingDirection.LeftToRight -> R.string.reader_direction_ltr
    ReaderReadingDirection.RightToLeft -> R.string.reader_direction_rtl
}

private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.System -> R.string.settings_language_system
    AppLanguage.English -> R.string.settings_language_english
    AppLanguage.Italian -> R.string.settings_language_italian
}

private fun AppColor.labelRes(): Int = when (this) {
    AppColor.BLUE -> R.string.settings_accent_blue
    AppColor.GREEN -> R.string.settings_accent_green
    AppColor.RED -> R.string.settings_accent_red
    AppColor.PURPLE -> R.string.settings_accent_purple
    AppColor.ORANGE -> R.string.settings_accent_orange
}

private fun AppThemePreset.labelRes(): Int = when (this) {
    AppThemePreset.Default -> R.string.settings_theme_preset_default
    AppThemePreset.Ink -> R.string.settings_theme_preset_ink
    AppThemePreset.Paper -> R.string.settings_theme_preset_paper
    AppThemePreset.Neon -> R.string.settings_theme_preset_neon
    AppThemePreset.Aurora -> R.string.settings_theme_preset_aurora
}
