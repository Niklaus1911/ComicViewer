package com.comicviewer.ui.reader

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.comicviewer.R
import com.comicviewer.ReaderBackground
import com.comicviewer.ReaderPageFitMode
import com.comicviewer.ReaderPageMode
import com.comicviewer.ReaderPreferences
import com.comicviewer.ReaderReadingDirection
import com.comicviewer.coercePageIndex
import kotlin.math.max
import kotlin.math.roundToInt

private val ReaderChromeColor = Color(0xFF101820)
private const val MAX_SCRUBBER_TICKS = 30
private const val MAX_READER_MARGIN_DP = 32
private val MAX_SCRUBBER_WIDTH = 640.dp

/** Keeps the screen on, applies reader brightness and hides the status bar while reading. */
@Composable
internal fun ReaderWindowEffect(preferences: ReaderPreferences) {
    val view = LocalView.current
    DisposableEffect(view, preferences.keepScreenOn, preferences.brightness) {
        val window = view.context.findActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val previousSystemBarsBehavior = controller.systemBarsBehavior
            val hadKeepScreenOn = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
            val previousBrightness = window.attributes.screenBrightness
            if (preferences.keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else if (!hadKeepScreenOn) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            preferences.brightness?.let { brightness ->
                window.attributes = window.attributes.apply {
                    screenBrightness = brightness.coerceIn(0.05f, 1f)
                }
            }
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())

            onDispose {
                controller.show(WindowInsetsCompat.Type.statusBars())
                controller.systemBarsBehavior = previousSystemBarsBehavior
                window.attributes = window.attributes.apply { screenBrightness = previousBrightness }
                if (!hadKeepScreenOn) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }
}

/**
 * Reader controls shared by the paged and vertical readers: a gradient top bar, a floating bottom
 * panel, and a small page indicator while the controls are hidden.
 */
@Composable
internal fun BoxScope.ReaderChrome(
    title: String,
    pageCount: Int,
    currentPageIndex: Int,
    progressPageIndex: Int,
    displayPageText: String,
    controlsVisible: Boolean,
    showPageNumber: Boolean,
    favorite: Boolean?,
    onToggleFavorite: () -> Unit,
    onCloseComic: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGrid: () -> Unit,
) {
    val progressPercent = readerProgressPercent(progressPageIndex, pageCount)
    AnimatedVisibility(
        visible = controlsVisible,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        ReaderTopBar(
            title = title,
            pageText = if (showPageNumber) "$progressPercent%  •  $displayPageText" else "$progressPercent%",
            onCloseComic = onCloseComic,
            favorite = favorite,
            onToggleFavorite = onToggleFavorite,
        )
    }
    AnimatedVisibility(
        visible = controlsVisible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        ReaderPageScrubber(
            currentPageIndex = currentPageIndex,
            pageCount = pageCount,
            onPageSelected = onPageSelected,
            pageText = displayPageText,
            showPageNumber = showPageNumber,
            progressPageIndex = progressPageIndex,
            onPrevious = onPrevious,
            onNext = onNext,
            onOpenSettings = onOpenSettings,
            onOpenGrid = onOpenGrid,
        )
    }
    AnimatedVisibility(
        visible = !controlsVisible && showPageNumber && pageCount > 0,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 10.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.5f),
            contentColor = Color.White.copy(alpha = 0.9f),
        ) {
            Text(
                text = displayPageText,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

internal fun readerProgressPercent(progressPageIndex: Int, pageCount: Int): Int =
    if (pageCount <= 0) {
        0
    } else {
        (((coercePageIndex(progressPageIndex, pageCount) + 1).toFloat() / pageCount) * 100).roundToInt().coerceIn(0, 100)
    }

@Composable
fun ReaderTopBar(
    title: String,
    pageText: String,
    onCloseComic: () -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean? = null,
    onToggleFavorite: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.82f),
                    0.6f to Color.Black.copy(alpha = 0.5f),
                    1f to Color.Transparent,
                ),
            ),
    ) {
        Row(
            modifier = Modifier
                .systemBarsPadding()
                .padding(start = 4.dp, top = 6.dp, end = 4.dp, bottom = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(onClick = onCloseComic) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.reader_close_action),
                    tint = Color.White,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pageText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.78f),
                )
            }
            if (favorite != null) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (favorite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = stringResource(
                            if (favorite) R.string.library_remove_favorite else R.string.library_add_favorite,
                        ),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
fun ReaderPageScrubber(
    currentPageIndex: Int,
    pageCount: Int,
    onPageSelected: (Int) -> Unit,
    pageText: String,
    showPageNumber: Boolean,
    progressPageIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGrid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clampedPageIndex = coercePageIndex(currentPageIndex, pageCount)
    var scrubberValue by remember(clampedPageIndex, pageCount) { mutableFloatStateOf(clampedPageIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val sliderMax = max(pageCount - 1, 0).toFloat()
    val chipText = when {
        dragging -> "${coercePageIndex(scrubberValue.roundToInt(), pageCount) + 1} / $pageCount"
        showPageNumber -> pageText
        else -> "${readerProgressPercent(progressPageIndex, pageCount)}%"
    }

    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .widthIn(max = MAX_SCRUBBER_WIDTH)
            .fillMaxWidth(),
        color = ReaderChromeColor.copy(alpha = 0.9f),
        contentColor = Color.White,
        shape = RoundedCornerShape(26.dp),
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 6.dp),
        ) {
            if (pageCount > 1) {
                Slider(
                    value = scrubberValue.coerceIn(0f, sliderMax),
                    onValueChange = { value ->
                        dragging = true
                        scrubberValue = value
                    },
                    onValueChangeFinished = {
                        dragging = false
                        onPageSelected(coercePageIndex(scrubberValue.roundToInt(), pageCount))
                    },
                    valueRange = 0f..sliderMax,
                    // Tick marks only help when there are few pages; long comics get a smooth track.
                    steps = if (pageCount <= MAX_SCRUBBER_TICKS) max(pageCount - 2, 0) else 0,
                    colors = SliderDefaults.colors(
                        inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                        inactiveTickColor = Color.White.copy(alpha = 0.35f),
                    ),
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, stringResource(R.string.reader_previous_page))
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Tune, stringResource(R.string.reader_settings))
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.1f),
                ) {
                    Text(
                        text = chipText,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                    )
                }
                IconButton(onClick = onOpenGrid) {
                    Icon(Icons.Filled.GridView, stringResource(R.string.reader_page_grid))
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, stringResource(R.string.reader_next_page))
                }
            }
        }
    }
}

/**
 * Reading settings. Inside the reader ([livePreview]) the sheet is shorter and has no scrim, so the
 * page above shows each change as it is made.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsDialog(
    readerPreferences: ReaderPreferences,
    activeReaderPreferences: ReaderPreferences,
    isProUnlocked: Boolean,
    onReaderPreferencesChanged: (ReaderPreferences) -> Unit,
    onRequestPro: () -> Unit,
    onDismiss: () -> Unit,
    livePreview: Boolean = false,
) {
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val maxSheetHeight = windowHeight * if (livePreview) 0.58f else 0.9f
    val vertical = activeReaderPreferences.pageMode == ReaderPageMode.Vertical
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        scrimColor = if (livePreview) Color.Transparent else BottomSheetDefaults.ScrimColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.reader_settings),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onReaderPreferencesChanged(ReaderPreferences()) }) {
                    Text(stringResource(R.string.reader_reset_short))
                }
            }

            ReaderSettingGroup(title = stringResource(R.string.reader_reading_mode)) {
                ReaderSegmentedChoice(
                    options = ReaderPageMode.entries,
                    selected = activeReaderPreferences.pageMode,
                    label = { stringResource(it.shortLabelRes()) },
                    locked = { !isProUnlocked && it == ReaderPageMode.TwoPage },
                    onSelected = { onReaderPreferencesChanged(readerPreferences.copy(pageMode = it)) },
                    onLocked = onRequestPro,
                )
            }
            ReaderSettingGroup(title = stringResource(R.string.reader_reading_direction), enabled = !vertical) {
                ReaderSegmentedChoice(
                    options = ReaderReadingDirection.entries,
                    selected = activeReaderPreferences.readingDirection,
                    label = { stringResource(it.labelRes()) },
                    locked = { !isProUnlocked && it != ReaderReadingDirection.LeftToRight },
                    enabled = !vertical,
                    onSelected = { onReaderPreferencesChanged(readerPreferences.copy(readingDirection = it)) },
                    onLocked = onRequestPro,
                )
            }
            ReaderSettingGroup(title = stringResource(R.string.reader_page_fit), enabled = !vertical) {
                ReaderSegmentedChoice(
                    options = ReaderPageFitMode.entries,
                    selected = activeReaderPreferences.pageFitMode,
                    label = { stringResource(it.shortLabelRes()) },
                    locked = { !isProUnlocked && it != ReaderPageFitMode.FitScreen },
                    enabled = !vertical,
                    onSelected = { onReaderPreferencesChanged(readerPreferences.copy(pageFitMode = it)) },
                    onLocked = onRequestPro,
                )
            }
            ReaderSettingGroup(
                title = stringResource(R.string.reader_background),
                locked = !isProUnlocked,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ReaderBackground.entries.forEach { background ->
                        val locked = !isProUnlocked && background != ReaderBackground.Black
                        BackgroundSwatch(
                            background = background,
                            selected = activeReaderPreferences.background == background,
                            locked = locked,
                            onClick = {
                                if (locked) {
                                    onRequestPro()
                                } else {
                                    onReaderPreferencesChanged(readerPreferences.copy(background = background))
                                }
                            },
                        )
                    }
                }
            }
            ReaderSettingGroup(title = stringResource(R.string.reader_brightness), locked = !isProUnlocked) {
                Row(
                    modifier = Modifier.clickable(enabled = !isProUnlocked, onClick = onRequestPro),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.BrightnessLow, null, modifier = Modifier.size(20.dp))
                    Slider(
                        value = (activeReaderPreferences.brightness ?: 0.5f).coerceIn(0.05f, 1f),
                        onValueChange = { onReaderPreferencesChanged(readerPreferences.copy(brightness = it)) },
                        valueRange = 0.05f..1f,
                        enabled = isProUnlocked,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Filled.BrightnessHigh, null, modifier = Modifier.size(20.dp))
                }
            }
            ReaderSettingGroup(title = stringResource(R.string.reader_margins), locked = !isProUnlocked) {
                Row(
                    modifier = Modifier.clickable(enabled = !isProUnlocked, onClick = onRequestPro),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(stringResource(R.string.reader_margins_none), style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = activeReaderPreferences.marginDp.toFloat(),
                        onValueChange = { onReaderPreferencesChanged(readerPreferences.copy(marginDp = it.roundToInt())) },
                        valueRange = 0f..MAX_READER_MARGIN_DP.toFloat(),
                        steps = 7,
                        enabled = isProUnlocked,
                        modifier = Modifier.weight(1f),
                    )
                    Text(stringResource(R.string.reader_margins_wide), style = MaterialTheme.typography.labelMedium)
                }
            }
            Column {
                ReaderSwitchRow(
                    label = stringResource(R.string.reader_keep_screen_on),
                    checked = activeReaderPreferences.keepScreenOn,
                    onCheckedChange = { onReaderPreferencesChanged(readerPreferences.copy(keepScreenOn = it)) },
                )
                ReaderSwitchRow(
                    label = stringResource(R.string.reader_show_page_number),
                    checked = activeReaderPreferences.showPageNumber,
                    onCheckedChange = { onReaderPreferencesChanged(readerPreferences.copy(showPageNumber = it)) },
                )
            }
        }
    }
}

@Composable
private fun ReaderSettingGroup(
    title: String,
    enabled: Boolean = true,
    locked: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            )
            if (locked) ProLockMark()
        }
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ReaderSegmentedChoice(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    locked: (T) -> Boolean,
    onSelected: (T) -> Unit,
    onLocked: () -> Unit,
    enabled: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            val isLocked = locked(option)
            SegmentedButton(
                selected = option == selected,
                onClick = { if (isLocked) onLocked() else onSelected(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                enabled = enabled,
                icon = {
                    if (isLocked) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = stringResource(R.string.pro_badge),
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                        )
                    } else {
                        SegmentedButtonDefaults.Icon(active = option == selected)
                    }
                },
            ) {
                Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun BackgroundSwatch(
    background: ReaderBackground,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(background.labelRes())
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(background.readerBackgroundColor())
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val markColor = if (background == ReaderBackground.White) Color.Black else Color.White
        when {
            selected -> Icon(Icons.Filled.Check, contentDescription = label, tint = markColor, modifier = Modifier.size(20.dp))
            locked -> Icon(
                Icons.Filled.Lock,
                contentDescription = label,
                tint = markColor.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
            else -> Unit
        }
    }
}

@Composable
private fun ReaderSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ProLockMark() {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(11.dp))
            Text(stringResource(R.string.pro_badge), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun ReaderReadingDirection.labelRes(): Int = when (this) {
    ReaderReadingDirection.LeftToRight -> R.string.reader_direction_ltr
    ReaderReadingDirection.RightToLeft -> R.string.reader_direction_rtl
}

private fun ReaderPageMode.shortLabelRes(): Int = when (this) {
    ReaderPageMode.SinglePage -> R.string.reader_mode_single_short
    ReaderPageMode.TwoPage -> R.string.reader_mode_two_short
    ReaderPageMode.Vertical -> R.string.reader_mode_vertical_short
}

private fun ReaderPageFitMode.shortLabelRes(): Int = when (this) {
    ReaderPageFitMode.FitScreen -> R.string.reader_fit_page_short
    ReaderPageFitMode.FitWidth -> R.string.reader_fit_width_short
    ReaderPageFitMode.FitHeight -> R.string.reader_fit_height_short
    ReaderPageFitMode.Original -> R.string.reader_fit_original_short
}

private fun ReaderBackground.labelRes(): Int = when (this) {
    ReaderBackground.Black -> R.string.reader_background_black
    ReaderBackground.DarkGray -> R.string.reader_background_dark_gray
    ReaderBackground.Sepia -> R.string.reader_background_sepia
    ReaderBackground.White -> R.string.reader_background_white
}
