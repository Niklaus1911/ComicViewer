package com.comicviewer

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.comicviewer.ui.theme.AppColor
import com.comicviewer.ui.theme.AppThemePreset
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

const val MAX_BACKUP_BYTES = 8 * 1024 * 1024
private const val BACKUP_SCHEMA_VERSION = 1

enum class RestoreMode { Merge, Replace }

data class BackupThemePreferences(
    val language: AppLanguage,
    val themeMode: AppThemeMode,
    val dynamicColorEnabled: Boolean,
    val accentColor: AppColor,
    val themePreset: AppThemePreset,
    val amoledEnabled: Boolean,
)

data class AppBackupData(
    val libraryComics: List<LibraryComic>,
    val recentComics: List<RecentComic>,
    val libraryMetadata: List<LibraryComicMetadata>,
    val libraryDetails: List<LibraryComicDetails>,
    val readerPreferences: ReaderPreferences,
    val automationPreferences: LibraryAutomationPreferences,
    val themePreferences: BackupThemePreferences,
)

data class RestoreSummary(
    val libraryCount: Int,
    val historyCount: Int,
    val skippedSources: Int,
)

fun readBackupBytesBounded(input: InputStream): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) {
            val value = input.read()
            if (value < 0) break
            require(total < MAX_BACKUP_BYTES) { "Backup exceeds the supported size." }
            output.write(value)
            total += 1
            continue
        }
        require(total <= MAX_BACKUP_BYTES - read) { "Backup exceeds the supported size." }
        output.write(buffer, 0, read)
        total += read
    }
    return output.toByteArray()
}

fun collectAppBackupData(context: Context): AppBackupData = AppBackupData(
    libraryComics = loadLibraryComics(context),
    recentComics = loadRecentComics(context),
    libraryMetadata = loadLibraryMetadata(context),
    libraryDetails = loadLibraryComicDetails(context),
    readerPreferences = loadReaderPreferences(context),
    automationPreferences = loadLibraryAutomationPreferences(context),
    themePreferences = BackupThemePreferences(
        language = loadAppLanguage(context),
        themeMode = loadThemeMode(context),
        dynamicColorEnabled = loadDynamicColorEnabled(context),
        accentColor = loadAccentColor(context),
        themePreset = loadThemePreset(context),
        amoledEnabled = loadAmoledThemeEnabled(context),
    ),
)

fun encodeAppBackup(data: AppBackupData): ByteArray {
    val root = JSONObject()
        .put("schemaVersion", BACKUP_SCHEMA_VERSION)
        .put("createdAt", System.currentTimeMillis())
        .put("libraryComics", JSONArray(encodeLibraryComics(data.libraryComics)))
        .put("recentComics", JSONArray(encodeRecentComics(data.recentComics)))
        .put("libraryMetadata", JSONArray(encodeLibraryMetadata(data.libraryMetadata)))
        .put("libraryDetails", JSONArray(encodeLibraryComicDetails(data.libraryDetails)))
        .put("readerPreferences", data.readerPreferences.toJson())
        .put("automationPreferences", data.automationPreferences.toJson())
        .put("themePreferences", data.themePreferences.toJson())
    val bytes = root.toString().toByteArray(Charsets.UTF_8)
    require(bytes.size <= MAX_BACKUP_BYTES) { "Backup exceeds the supported size." }
    return bytes
}

fun decodeAppBackup(bytes: ByteArray): AppBackupData {
    require(bytes.isNotEmpty() && bytes.size <= MAX_BACKUP_BYTES) { "Invalid backup size." }
    val root = JSONObject(bytes.toString(Charsets.UTF_8))
    require(root.optInt("schemaVersion", -1) == BACKUP_SCHEMA_VERSION) { "Unsupported backup version." }
    val libraryComics = root.requiredArray("libraryComics")
    val recentComics = root.requiredArray("recentComics")
    val libraryMetadata = root.requiredArray("libraryMetadata")
    val libraryDetails = root.requiredArray("libraryDetails")
    require(libraryComics.length() <= MAX_LIBRARY_COMICS) { "Too many library records." }
    require(recentComics.length() <= MAX_RECENT_COMICS) { "Too many history records." }
    require(libraryMetadata.length() <= MAX_LIBRARY_COMICS) { "Too many metadata records." }
    require(libraryDetails.length() <= MAX_LIBRARY_COMICS) { "Too many detail records." }
    return AppBackupData(
        libraryComics = decodeLibraryComics(libraryComics.toString()),
        recentComics = decodeRecentComics(recentComics.toString()),
        libraryMetadata = decodeLibraryMetadata(libraryMetadata.toString()),
        libraryDetails = decodeLibraryComicDetails(libraryDetails.toString()),
        readerPreferences = root.requiredObject("readerPreferences").toReaderPreferences(),
        automationPreferences = root.requiredObject("automationPreferences").toAutomationPreferences(),
        themePreferences = root.requiredObject("themePreferences").toThemePreferences(),
    )
}

suspend fun restoreAppBackup(
    context: Context,
    backup: AppBackupData,
    mode: RestoreMode,
): RestoreSummary = withContext(Dispatchers.IO) {
    val currentLibrary = loadLibraryComics(context)
    val currentHistory = loadRecentComics(context)
    val currentMetadata = loadLibraryMetadata(context)
    val currentDetails = loadLibraryComicDetails(context)
    val readableLibrary = backup.libraryComics.filter { context.canReadBackupSource(it.sourceType, it.path) }
    val readableHistory = backup.recentComics.filter { context.canReadBackupSource(it.sourceType, it.path) }
    val readableKeys = readableLibrary.mapTo(mutableSetOf()) { "${it.sourceType.storageName}|${it.path}" }
    val filteredMetadata = backup.libraryMetadata.filter { "${it.sourceType.storageName}|${it.path}" in readableKeys }
    val filteredDetails = backup.libraryDetails.filter { "${it.sourceType.storageName}|${it.path}" in readableKeys }

    val nextLibrary = if (mode == RestoreMode.Replace) {
        readableLibrary
    } else {
        readableLibrary.fold(currentLibrary) { current, comic -> upsertLibraryComic(current, comic) }
    }
    val nextHistory = if (mode == RestoreMode.Replace) {
        readableHistory
    } else {
        readableHistory.fold(currentHistory) { current, comic -> upsertRecentComic(current, comic) }
    }
    val nextMetadata = if (mode == RestoreMode.Replace) {
        filteredMetadata
    } else {
        filteredMetadata.fold(currentMetadata) { current, item -> upsertLibraryMetadata(current, item) }
    }
    val nextDetails = if (mode == RestoreMode.Replace) {
        filteredDetails
    } else {
        filteredDetails.fold(currentDetails) { current, item -> upsertLibraryComicDetails(current, item) }
    }
    val automationPreferences = backup.automationPreferences.validatedFor(context)

    saveLibraryComics(context, nextLibrary)
    saveRecentComics(context, nextHistory)
    saveLibraryMetadata(context, pruneLibraryMetadata(nextMetadata, nextLibrary))
    saveLibraryComicDetails(context, pruneLibraryComicDetails(nextDetails, nextLibrary))
    saveReaderPreferences(context, backup.readerPreferences)
    saveLibraryAutomationPreferences(context, automationPreferences)
    saveAppLanguage(context, backup.themePreferences.language)
    saveThemeMode(context, backup.themePreferences.themeMode)
    saveDynamicColorEnabled(context, backup.themePreferences.dynamicColorEnabled)
    saveAccentColor(context, backup.themePreferences.accentColor)
    saveThemePreset(context, backup.themePreferences.themePreset)
    saveAmoledThemeEnabled(context, backup.themePreferences.amoledEnabled)
    saveWelcomeSeen(context, true)

    if (mode == RestoreMode.Replace) {
        val previouslyReferencedDocumentUris = (currentLibrary.map { it.sourceType to it.path } +
            currentHistory.map { it.sourceType to it.path })
            .filter { (sourceType, _) -> sourceType.isDocumentSource() }
            .mapTo(mutableSetOf()) { (_, path) -> path }
        val retainedDocumentUris = (nextLibrary.map { it.sourceType to it.path } +
            nextHistory.map { it.sourceType to it.path })
            .filter { (sourceType, _) -> sourceType.isDocumentSource() }
            .mapTo(mutableSetOf()) { (_, path) -> path }
        loadPersistedStorageTreeUri(context)?.toString()?.let(retainedDocumentUris::add)
        automationPreferences.importFolderUri?.let(retainedDocumentUris::add)
        (previouslyReferencedDocumentUris - retainedDocumentUris).forEach { path ->
            releasePersistableReadPermission(context, path.toUri())
        }
    }

    RestoreSummary(
        libraryCount = nextLibrary.size,
        historyCount = nextHistory.size,
        skippedSources = (backup.libraryComics.size - readableLibrary.size) +
            (backup.recentComics.size - readableHistory.size),
    )
}

private fun Context.canReadBackupSource(sourceType: RecentComicSourceType, path: String): Boolean = when (sourceType) {
    RecentComicSourceType.LocalArchive -> File(path).let { it.isFile && it.canRead() }
    RecentComicSourceType.LocalFolder -> File(path).let { it.isDirectory && it.canRead() }
    RecentComicSourceType.DocumentArchive -> runCatching {
        contentResolver.openInputStream(path.toUri())?.use { true } ?: false
    }.getOrDefault(false)
    RecentComicSourceType.DocumentFolder -> canReadDocumentDirectory(this, path.toUri())
}

private fun RecentComicSourceType.isDocumentSource(): Boolean =
    this == RecentComicSourceType.DocumentArchive || this == RecentComicSourceType.DocumentFolder

private fun LibraryAutomationPreferences.validatedFor(context: Context): LibraryAutomationPreferences {
    val uri = importFolderUri?.let { runCatching { it.toUri() }.getOrNull() }
    val readable = uri != null && runCatching { DocumentFile.fromTreeUri(context, uri)?.canRead() == true }.getOrDefault(false)
    return if (readable) this else copy(importFolderUri = null, importFolderName = null, autoScanEnabled = false)
}

private fun ReaderPreferences.toJson(): JSONObject = JSONObject()
    .put("pageMode", pageMode.preferenceValue)
    .put("readingDirection", readingDirection.preferenceValue)
    .put("pageFitMode", pageFitMode.preferenceValue)
    .put("background", background.preferenceValue)
    .put("brightness", brightness ?: JSONObject.NULL)
    .put("marginDp", marginDp)
    .put("keepScreenOn", keepScreenOn)
    .put("showPageNumber", showPageNumber)

private fun JSONObject.toReaderPreferences(): ReaderPreferences {
    val brightness = if (isNull("brightness")) {
        null
    } else {
        optDouble("brightness", 0.5)
            .toFloat()
            .takeIf(Float::isFinite)
            ?.coerceIn(0.05f, 1f)
            ?: 0.5f
    }
    return ReaderPreferences(
        pageMode = enumValue(
            "pageMode",
            ReaderPageMode.SinglePage,
            ReaderPageMode.entries,
            ReaderPageMode::preferenceValue,
        ),
        readingDirection = enumValue(
            "readingDirection",
            ReaderReadingDirection.LeftToRight,
            ReaderReadingDirection.entries,
            ReaderReadingDirection::preferenceValue,
        ),
        pageFitMode = enumValue(
            "pageFitMode",
            ReaderPageFitMode.FitScreen,
            ReaderPageFitMode.entries,
            ReaderPageFitMode::preferenceValue,
        ),
        background = enumValue(
            "background",
            ReaderBackground.Black,
            ReaderBackground.entries,
            ReaderBackground::preferenceValue,
        ),
        brightness = brightness,
        marginDp = optInt("marginDp", 0).coerceIn(0, 32),
        keepScreenOn = optBoolean("keepScreenOn", true),
        showPageNumber = optBoolean("showPageNumber", true),
    )
}

private fun LibraryAutomationPreferences.toJson(): JSONObject = JSONObject()
    .put("importFolderUri", importFolderUri)
    .put("importFolderName", importFolderName)
    .put("autoScanEnabled", autoScanEnabled)
    .put("lastAutoScanAt", lastAutoScanAt)
    .put("autoDeleteTempFiles", autoDeleteTempFiles)

private fun JSONObject.toAutomationPreferences(): LibraryAutomationPreferences = LibraryAutomationPreferences(
    importFolderUri = optString("importFolderUri").takeIf { value ->
        value.isNotBlank() && value.length <= 4_096
    },
    importFolderName = optString("importFolderName").takeIf(String::isNotBlank)?.take(512),
    autoScanEnabled = optBoolean("autoScanEnabled", false),
    lastAutoScanAt = optLong("lastAutoScanAt", 0L).coerceAtLeast(0L),
    autoDeleteTempFiles = optBoolean("autoDeleteTempFiles", true),
)

private fun BackupThemePreferences.toJson(): JSONObject = JSONObject()
    .put("language", language.preferenceValue)
    .put("themeMode", themeMode.preferenceValue)
    .put("dynamicColorEnabled", dynamicColorEnabled)
    .put("accentColor", accentColor.name)
    .put("themePreset", themePreset.preferenceValue)
    .put("amoledEnabled", amoledEnabled)

private fun JSONObject.toThemePreferences(): BackupThemePreferences = BackupThemePreferences(
    language = enumValue("language", AppLanguage.System, AppLanguage.entries, AppLanguage::preferenceValue),
    themeMode = enumValue("themeMode", AppThemeMode.Dark, AppThemeMode.entries, AppThemeMode::preferenceValue),
    dynamicColorEnabled = optBoolean("dynamicColorEnabled", false),
    accentColor = runCatching { AppColor.valueOf(optString("accentColor")) }.getOrDefault(AppColor.PURPLE),
    themePreset = enumValue("themePreset", AppThemePreset.Default, AppThemePreset.entries, AppThemePreset::preferenceValue),
    amoledEnabled = optBoolean("amoledEnabled", false),
)

private fun JSONObject.requiredArray(key: String): JSONArray = optJSONArray(key) ?: error("Missing $key")
private fun JSONObject.requiredObject(key: String): JSONObject = optJSONObject(key) ?: error("Missing $key")

private fun <T> JSONObject.enumValue(
    key: String,
    defaultValue: T,
    values: List<T>,
    storageValue: (T) -> String,
): T {
    val stored = optString(key)
    return values.firstOrNull { storageValue(it) == stored } ?: defaultValue
}
