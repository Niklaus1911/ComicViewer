package com.comicviewer.ui.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comicviewer.AppBuildInfo
import com.comicviewer.AppLanguage
import com.comicviewer.AppThemeMode
import com.comicviewer.R
import com.comicviewer.billing.ProBillingState
import com.comicviewer.resolvedThemePreset
import com.comicviewer.ui.library.ProUpgradeDialog
import com.comicviewer.ui.app.ThemeUiState
import com.comicviewer.ui.theme.AppColor
import com.comicviewer.ui.theme.AppThemePreset

@Composable
fun SettingsScreen(
    selectedLanguage: AppLanguage,
    themeUiState: ThemeUiState,
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
    modifier: Modifier = Modifier,
) {
    var aboutExpanded by rememberSaveable { mutableStateOf(false) }
    var showProDialog by rememberSaveable { mutableStateOf(false) }
    val effectiveThemePreset = resolvedThemePreset(
        themePreset = themeUiState.themePreset,
        isProUnlocked = proBillingState.isProUnlocked,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 32.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AppearanceCard(
            themeUiState = themeUiState.copy(themePreset = effectiveThemePreset),
            onThemeModeSelected = onThemeModeSelected,
            onDynamicColorChanged = onDynamicColorChanged,
            onAccentColorSelected = onAccentColorSelected,
            onAmoledThemeChanged = onAmoledThemeChanged,
        )

        ThemePresetCard(
            selectedPreset = effectiveThemePreset,
            defaultAccentColor = themeUiState.accentColor,
            isProUnlocked = proBillingState.isProUnlocked,
            onThemePresetSelected = onThemePresetSelected,
            onRequestPro = { showProDialog = true },
        )

        LanguageCard(
            selectedLanguage = selectedLanguage,
            onLanguageSelected = onLanguageSelected,
        )

        AboutCard(
            buildInfo = buildInfo,
            expanded = aboutExpanded,
            onToggle = { aboutExpanded = !aboutExpanded },
        )
    }

    if (showProDialog) {
        ProUpgradeDialog(
            billingState = proBillingState,
            onBuyPro = onBuyPro,
            onRestorePro = onRestorePro,
            isDebugBuild = isDebugBuild,
            debugProUnlocked = debugProUnlocked,
            onDebugProUnlockedChanged = onDebugProUnlockedChanged,
            onDismiss = { showProDialog = false },
        )
    }
}

@Composable
private fun AppearanceCard(
    themeUiState: ThemeUiState,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onAccentColorSelected: (AppColor) -> Unit,
    onAmoledThemeChanged: (Boolean) -> Unit,
) {
    // Material You (wallpaper-based) coloring only exists on Android 12+; below that the manual
    // accent picker is always the source of truth.
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicColorActive = supportsDynamicColor && themeUiState.dynamicColorEnabled
    val accentEnabled = !dynamicColorActive && themeUiState.themePreset == AppThemePreset.Default

    SettingsCard(
        title = stringResource(R.string.settings_appearance_title),
        icon = Icons.Filled.Palette,
    ) {
        SettingsLabel(text = stringResource(R.string.settings_theme_mode_title))
        Spacer(modifier = Modifier.height(8.dp))
        ThemeModeSelector(
            selected = themeUiState.themeMode,
            onSelected = onThemeModeSelected,
        )

        if (supportsDynamicColor) {
            Spacer(modifier = Modifier.height(20.dp))
            SettingsToggleRow(
                title = stringResource(R.string.settings_dynamic_color_title),
                description = stringResource(R.string.settings_dynamic_color_description),
                checked = themeUiState.dynamicColorEnabled,
                onCheckedChange = onDynamicColorChanged,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        SettingsLabel(text = stringResource(R.string.settings_accent_color_title))
        Spacer(modifier = Modifier.height(12.dp))
        AccentColorRow(
            selected = themeUiState.accentColor,
            enabled = accentEnabled,
            onSelected = onAccentColorSelected,
        )

        Spacer(modifier = Modifier.height(20.dp))
        SettingsToggleRow(
            title = stringResource(R.string.settings_amoled_title),
            description = stringResource(R.string.settings_amoled_description),
            checked = themeUiState.amoledEnabled,
            onCheckedChange = onAmoledThemeChanged,
        )
    }
}

@Composable
private fun ThemePresetCard(
    selectedPreset: AppThemePreset,
    defaultAccentColor: AppColor,
    isProUnlocked: Boolean,
    onThemePresetSelected: (AppThemePreset) -> Unit,
    onRequestPro: () -> Unit,
) {
    SettingsCard(
        title = stringResource(R.string.settings_pro_themes_title),
        icon = Icons.Filled.Palette,
    ) {
        SettingsLabel(text = stringResource(R.string.settings_theme_preset_title))
        Spacer(modifier = Modifier.height(8.dp))
        AppThemePreset.entries.forEach { preset ->
            val locked = preset.proOnly && !isProUnlocked
            ThemePresetRow(
                preset = preset,
                defaultAccentColor = defaultAccentColor,
                selected = selectedPreset == preset,
                locked = locked,
                onClick = {
                    if (locked) onRequestPro() else onThemePresetSelected(preset)
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ThemePresetRow(
    preset: AppThemePreset,
    defaultAccentColor: AppColor,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemePresetPreview(
            preset = preset,
            defaultAccentColor = defaultAccentColor,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(preset.labelRes()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (locked) {
                    Spacer(modifier = Modifier.width(8.dp))
                    ProBadge()
                }
            }
            Text(
                text = stringResource(preset.descriptionRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ThemePresetPreview(
    preset: AppThemePreset,
    defaultAccentColor: AppColor,
) {
    val colors = if (preset == AppThemePreset.Default) {
        listOf(defaultAccentColor.swatch, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary)
    } else {
        listOf(preset.primarySwatch, preset.secondarySwatch, preset.tertiarySwatch)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            colors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(14.dp, 28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(color),
                )
            }
        }
    }
}

@Composable
private fun ProBadge() {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.WorkspacePremium,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = stringResource(R.string.pro_badge),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun ThemeModeSelector(
    selected: AppThemeMode,
    onSelected: (AppThemeMode) -> Unit,
) {
    val modes = AppThemeMode.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                icon = {
                    Icon(
                        imageVector = mode.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                    )
                },
                label = {
                    Text(
                        text = stringResource(mode.labelRes()),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun AccentColorRow(
    selected: AppColor,
    enabled: Boolean,
    onSelected: (AppColor) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppColor.entries.forEach { color ->
            val isSelected = selected == color
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.swatch)
                    .clickable(enabled = enabled) { onSelected(color) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        ),
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = stringResource(color.nameRes()),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageCard(
    selectedLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
) {
    SettingsCard(
        title = stringResource(R.string.settings_language_title),
        icon = Icons.Filled.Translate,
    ) {
        LanguageOption(
            text = stringResource(R.string.settings_language_system),
            selected = selectedLanguage == AppLanguage.System,
            onClick = { onLanguageSelected(AppLanguage.System) },
        )
        LanguageOption(
            text = stringResource(R.string.settings_language_english),
            selected = selectedLanguage == AppLanguage.English,
            onClick = { onLanguageSelected(AppLanguage.English) },
        )
        LanguageOption(
            text = stringResource(R.string.settings_language_italian),
            selected = selectedLanguage == AppLanguage.Italian,
            onClick = { onLanguageSelected(AppLanguage.Italian) },
        )
    }
}

@Composable
private fun AboutCard(
    buildInfo: AppBuildInfo,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "aboutChevron",
    )
    SettingsCardSurface {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.settings_about_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${buildInfo.versionName} (${buildInfo.versionCode})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.settings_about_hide else R.string.settings_about_show,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(chevronRotation),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                ) {
                    BuildTimestampBadge(timestamp = buildInfo.buildTimestamp)
                    Spacer(modifier = Modifier.height(12.dp))
                    BuildInfoSection(buildInfo = buildInfo)
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsCardSurface(modifier = modifier) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun SettingsCardSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        // A hairline keeps cards visible against the pure-black AMOLED background, where the
        // container color collapses to black.
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        content = content,
    )
}

@Composable
private fun SettingsLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun LanguageOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun BuildInfoSection(buildInfo: AppBuildInfo) {
    BuildInfoRow(
        label = stringResource(R.string.settings_build_info_version_name),
        value = buildInfo.versionName,
    )
    BuildInfoRow(
        label = stringResource(R.string.settings_build_info_version_code),
        value = buildInfo.versionCode.toString(),
    )
    BuildInfoRow(
        label = stringResource(R.string.settings_build_info_build_time),
        value = buildInfo.buildTimestamp,
    )
    BuildInfoRow(
        label = stringResource(R.string.settings_build_info_application_id),
        value = buildInfo.applicationId,
    )
    BuildInfoRow(
        label = stringResource(R.string.settings_build_info_build_type),
        value = buildInfo.buildType,
    )
    BuildInfoRow(
        label = stringResource(R.string.settings_build_info_debug),
        value = stringResource(
            if (buildInfo.debug) R.string.settings_build_info_yes else R.string.settings_build_info_no,
        ),
    )
    BuildInfoRow(
        label = stringResource(R.string.settings_build_info_min_sdk),
        value = buildInfo.minSdk.toString(),
    )
    BuildInfoRow(
        label = stringResource(R.string.settings_build_info_target_sdk),
        value = buildInfo.targetSdk.toString(),
    )
    BuildInfoRow(
        label = stringResource(R.string.settings_build_info_compile_sdk),
        value = buildInfo.compileSdk,
    )
}

@Composable
private fun BuildTimestampBadge(timestamp: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = stringResource(R.string.settings_build_timestamp_badge, timestamp),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun BuildInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun AppThemeMode.labelRes(): Int = when (this) {
    AppThemeMode.System -> R.string.settings_theme_system
    AppThemeMode.Light -> R.string.settings_theme_light
    AppThemeMode.Dark -> R.string.settings_theme_dark
}

private fun AppThemeMode.icon(): ImageVector = when (this) {
    AppThemeMode.System -> Icons.Filled.BrightnessAuto
    AppThemeMode.Light -> Icons.Filled.LightMode
    AppThemeMode.Dark -> Icons.Filled.DarkMode
}

private fun AppThemePreset.labelRes(): Int = when (this) {
    AppThemePreset.Default -> R.string.settings_theme_preset_default
    AppThemePreset.Ink -> R.string.settings_theme_preset_ink
    AppThemePreset.Paper -> R.string.settings_theme_preset_paper
    AppThemePreset.Neon -> R.string.settings_theme_preset_neon
    AppThemePreset.Aurora -> R.string.settings_theme_preset_aurora
}

private fun AppThemePreset.descriptionRes(): Int = when (this) {
    AppThemePreset.Default -> R.string.settings_theme_preset_default_description
    AppThemePreset.Ink -> R.string.settings_theme_preset_ink_description
    AppThemePreset.Paper -> R.string.settings_theme_preset_paper_description
    AppThemePreset.Neon -> R.string.settings_theme_preset_neon_description
    AppThemePreset.Aurora -> R.string.settings_theme_preset_aurora_description
}

private fun AppColor.nameRes(): Int = when (this) {
    AppColor.BLUE -> R.string.settings_accent_blue
    AppColor.GREEN -> R.string.settings_accent_green
    AppColor.RED -> R.string.settings_accent_red
    AppColor.PURPLE -> R.string.settings_accent_purple
    AppColor.ORANGE -> R.string.settings_accent_orange
}
