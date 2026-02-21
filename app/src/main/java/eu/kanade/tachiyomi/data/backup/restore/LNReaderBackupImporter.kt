package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.restore.restorers.CategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import eu.kanade.tachiyomi.jsplugin.JsPluginManager
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.source.repository.StubSourceRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Imports LNReader backup files (.zip) into Tsundoku.
 *
 * LNReader backup format:
 * ```
 * backup.zip
 * ├── Version.json
 * ├── Category.json
 * ├── NovelAndChapters/
 * │   ├── {novelId}.json  (each contains novel info + chapters array)
 * │   └── ...
 * └── Setting.json
 * ```
 */
class LNReaderBackupImporter(
    private val context: Context,
    private val notifier: BackupNotifier? = null,
    private val jsPluginManager: JsPluginManager = Injekt.get(),
    private val categoriesRestorer: CategoriesRestorer = CategoriesRestorer(),
    private val mangaRestorer: MangaRestorer = MangaRestorer(),
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId = Injekt.get(),
    private val stubSourceRepository: StubSourceRepository = Injekt.get(),
) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }
    private val errors = mutableListOf<Pair<Date, String>>()

    @Serializable
    data class LNNovel(
        val id: Int = 0,
        val path: String = "",
        val pluginId: String = "",
        val name: String = "",
        val cover: String? = null,
        val summary: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val status: String? = null,
        val genres: String? = null,
        val inLibrary: Int = 0,
        val isLocal: Int = 0,
        val totalPages: Int = 0,
        val chapters: List<LNChapter> = emptyList(),
    )

    @Serializable
    data class LNChapter(
        val id: Int = 0,
        val novelId: Int = 0,
        val path: String = "",
        val name: String = "",
        val releaseTime: String? = null,
        val readTime: String? = null,
        val bookmark: Int = 0,
        val unread: Int = 1,
        val isDownloaded: Int = 0,
        val updatedTime: String? = null,
        val chapterNumber: Float? = null,
        val page: String = "",
        val progress: Int? = null,
        val position: Int? = null,
    )

    @Serializable
    data class LNCategory(
        val id: Int = 0,
        val name: String = "",
        val sort: Int = 0,
        val novelIds: List<Int> = emptyList(),
    )

    data class ImportResult(
        val novelCount: Int,
        val categoryCount: Int,
        val errorCount: Int,
        val logFile: File,
        val missingPlugins: List<String> = emptyList(),
        val skippedCount: Int = 0,
    )

    data class ImportOptions(
        val restoreNovels: Boolean = true,
        val restoreChapters: Boolean = true,
        val restoreCategories: Boolean = true,
        val restoreHistory: Boolean = true,
        val restorePlugins: Boolean = true,
        val restoreMissingPlugins: Boolean = false,
    )

    /**
     * Import an LNReader backup from the given URI.
     */
    suspend fun import(uri: Uri, options: ImportOptions = ImportOptions()): ImportResult {
        errors.clear()
        var novelCount = 0
        var categoryCount = 0
        var skippedCount = 0
        val missingPlugins = mutableSetOf<String>()

        try {
            // Step 1: Extract data only
            val (novels, categories, pluginZipBytes) = extractBackupData(uri)

            logcat(LogPriority.INFO) {
                "LNReaderImport: Found ${novels.size} novels, ${categories.size} categories (options: $options)"
            }

            // Step 2: Restore categories FIRST
            val backupCategories = categories.map { lnCat ->
                BackupCategory(
                    name = lnCat.name,
                    order = lnCat.sort.toLong(),
                    flags = 0,
                    contentType = Category.CONTENT_TYPE_NOVEL,
                )
            }
            if (options.restoreCategories) {
                categoriesRestorer(backupCategories)
                categoryCount = categories.size
                logcat(LogPriority.INFO) { "LNReaderImport: Restored $categoryCount categories" }
            }

            // Step 3: Install plugins
            if (options.restorePlugins && pluginZipBytes != null) {
                processDownloadZip(pluginZipBytes)
                kotlinx.coroutines.delay(500)
            }

            // Step 4: Build plugin mapping
            val pluginIdToSourceId = buildPluginMapping().toMutableMap()

            // Detect missing plugins and create stub sources if requested
            val requiredPlugins = novels.map { it.pluginId }.toSet()
            val actualMissingPlugins = requiredPlugins - pluginIdToSourceId.keys
            missingPlugins.addAll(actualMissingPlugins)

            if (actualMissingPlugins.isNotEmpty()) {
                if (options.restoreMissingPlugins) {
                    // Create stub sources for missing plugins
                    actualMissingPlugins.forEach { pluginId ->
                        val stubSourceId = generateStubSourceId(pluginId)
                        try {
                            stubSourceRepository.upsertStubSource(
                                id = stubSourceId,
                                lang = "unknown",
                                name = "$pluginId (Missing)",
                                isNovel = true,
                            )
                            pluginIdToSourceId[pluginId] = stubSourceId
                            logcat(LogPriority.INFO) { "LNReaderImport: Created stub source for missing plugin '$pluginId' with ID $stubSourceId" }
                        } catch (e: Exception) {
                            logcat(LogPriority.ERROR, e) { "LNReaderImport: Failed to create stub source for '$pluginId'" }
                            errors.add(Date() to "Failed to create stub source for '$pluginId': ${e.message}")
                        }
                    }
                } else {
                    logcat(LogPriority.WARN) { "LNReaderImport: Missing plugins: ${missingPlugins.joinToString()}" }
                    errors.add(
                        Date() to "Missing plugins (install these extensions first or enable 'Restore with missing plugins'): ${missingPlugins.joinToString()}",
                    )
                }
            }

            // Build category name -> novel IDs mapping for assignment
            val novelIdToCategoryNames = mutableMapOf<Int, MutableList<String>>()
            categories.forEach { cat ->
                cat.novelIds.forEach { novelId ->
                    novelIdToCategoryNames.getOrPut(novelId) { mutableListOf() }.add(cat.name)
                }
            }

            // Convert and restore novels
            if (options.restoreNovels) {
                coroutineScope {
                    novels.forEachIndexed { index, novel ->
                        ensureActive()
                        try {
                            // Local novels get LocalNovelSource regardless of pluginId
                            val sourceId = if (novel.isLocal != 0) {
                                1L // LocalNovelSource.ID
                            } else {
                                pluginIdToSourceId[novel.pluginId]
                            }
                            if (sourceId == null) {
                                skippedCount++
                                errors.add(Date() to "${novel.name}: Unknown plugin '${novel.pluginId}' - skipping (enable 'Restore with missing plugins' to import as stub)")
                                return@forEachIndexed
                            }

                            notifier?.showRestoreProgress(
                                novel.name,
                                index + 1,
                                novels.size,
                            )

                            val backupManga = convertNovel(
                                novel,
                                sourceId,
                                novelIdToCategoryNames,
                                backupCategories,
                                includeChapters = options.restoreChapters,
                                includeHistory = options.restoreHistory,
                                includeCategories = options.restoreCategories,
                            )

                            // Check if this novel already exists in the database
                            val existingManga = getMangaByUrlAndSourceId.await(novel.path, sourceId)
                            if (existingManga != null && novel.isLocal == 0) {
                                // Existing JS novel — skip metadata overwrite, only update chapters/history
                                logcat(LogPriority.INFO) {
                                    "LNReaderImport: Novel '${novel.name}' already exists (id=${existingManga.id}), updating chapters only"
                                }
                                mangaRestorer.restoreExistingChapters(existingManga, backupManga, backupCategories)
                                skippedCount++
                            } else {
                                mangaRestorer.restore(backupManga, backupCategories)
                            }
                            novelCount++
                            logcat(LogPriority.DEBUG) {
                                "LNReaderImport: Restored novel '${novel.name}' (${index + 1}/${novels.size})"
                            }
                        } catch (e: Exception) {
                            errors.add(Date() to "${novel.name} [${novel.pluginId}]: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "LNReaderImport: Failed to import backup" }
            errors.add(Date() to "Fatal error: ${e.message}")
        }

        val logFile = writeErrorLog()
        return ImportResult(novelCount, categoryCount, errors.size, logFile, missingPlugins.toList(), skippedCount)
    }

    private fun buildPluginMapping(): Map<String, Long> {
        val plugins = jsPluginManager.installedPlugins.value
        val mapping = plugins.associate { installed ->
            installed.plugin.id to installed.plugin.sourceId()
        }.toMutableMap()
        // Map LNReader's "local" pluginId to LocalNovelSource
        mapping["local"] = 1L // LocalNovelSource.ID
        return mapping
    }

    /**
     * Represents extracted backup data: novels, categories, and optional plugin zip bytes.
     */
    data class ExtractedBackup(
        val novels: List<LNNovel>,
        val categories: List<LNCategory>,
        val pluginZipBytes: ByteArray?,
    )

    private fun extractBackupData(uri: Uri): ExtractedBackup {
        val novels = mutableListOf<LNNovel>()
        var categories = emptyList<LNCategory>()
        var downloadZipBytes: ByteArray? = null

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name == "Category.json" -> {
                            val content = zip.bufferedReader().readText()
                            categories = json.decodeFromString<List<LNCategory>>(content)
                        }
                        name.startsWith("NovelAndChapters/") && name.endsWith(".json") -> {
                            val content = zip.bufferedReader().readText()
                            try {
                                val novel = json.decodeFromString<LNNovel>(content)
                                if (novel.name.isNotBlank()) {
                                    novels.add(novel)
                                }
                            } catch (e: Exception) {
                                logcat(LogPriority.WARN, e) { "LNReaderImport: Failed to parse $name" }
                                errors.add(Date() to "Parse error for $name: ${e.message}")
                            }
                        }
                        name == "download.zip" -> {
                            downloadZipBytes = zip.readBytes()
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        // Return extracted data
        return ExtractedBackup(novels, categories, downloadZipBytes)
    }

    private fun processDownloadZip(zipBytes: ByteArray) {
        try {
            ZipInputStream(zipBytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    // Process plugin JS files
                    val nameLower = name.lowercase()
                    if (nameLower.startsWith("plugins/") && nameLower.endsWith("/index.js") && !entry.isDirectory) {
                        val parts = name.removePrefix(name.substringBefore("/") + "/").split("/")
                        if (parts.size == 2) {
                            val pluginId = parts[0]
                            try {
                                val code = zip.bufferedReader().readText()
                                if (code.isNotBlank()) {
                                    kotlinx.coroutines.runBlocking {
                                        val installed = jsPluginManager.installPluginFromCode(pluginId, code)
                                        if (installed) {
                                            logcat(LogPriority.INFO) {
                                                "LNReaderImport: Installed plugin '$pluginId' from backup"
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                logcat(LogPriority.WARN, e) {
                                    "LNReaderImport: Failed to install plugin '$pluginId' from backup"
                                }
                                errors.add(Date() to "Failed to install plugin '$pluginId': ${e.message}")
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "LNReaderImport: Failed to process download.zip" }
            errors.add(Date() to "Failed to process download.zip: ${e.message}")
        }
    }

    private fun convertNovel(
        novel: LNNovel,
        sourceId: Long,
        novelIdToCategoryNames: Map<Int, List<String>>,
        backupCategories: List<BackupCategory>,
        includeChapters: Boolean = true,
        includeHistory: Boolean = true,
        includeCategories: Boolean = true,
    ): BackupManga {
        val backupChapters = if (includeChapters) {
            novel.chapters.mapIndexed { index, ch ->
                BackupChapter(
                    url = ch.path,
                    name = ch.name,
                    scanlator = null,
                    read = ch.readTime != null && ch.unread == 0,
                    bookmark = ch.bookmark != 0,
                    lastPageRead = ch.progress?.toLong() ?: 0L,
                    dateFetch = 0L,
                    dateUpload = parseDate(ch.releaseTime) ?: 0L,
                    chapterNumber = ch.chapterNumber ?: (index + 1).toFloat(),
                    sourceOrder = index.toLong(),
                )
            }
        } else {
            emptyList()
        }

        val backupHistory = if (includeHistory) {
            novel.chapters
                .filter { it.readTime != null }
                .map { ch ->
                    BackupHistory(
                        url = ch.path,
                        lastRead = parseDate(ch.readTime) ?: 0L,
                        readDuration = 0L,
                    )
                }
        } else {
            emptyList()
        }

        // Map novel ID to category orders (use the BackupCategory.order, not list index)
        val categoryOrders = if (includeCategories) {
            val categoryNames = novelIdToCategoryNames[novel.id].orEmpty()
            categoryNames.mapNotNull { name ->
                backupCategories.firstOrNull { it.name == name }?.order
            }
        } else {
            emptyList()
        }

        val status = when (novel.status?.lowercase()) {
            "ongoing" -> 1
            "completed" -> 2
            "licensed" -> 3
            "publishing finished" -> 4
            "cancelled" -> 5
            "on hiatus" -> 6
            else -> 0
        }

        return BackupManga(
            source = sourceId,
            url = novel.path,
            title = novel.name,
            artist = novel.artist,
            author = novel.author,
            description = novel.summary,
            genre = novel.genres?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
            status = status,
            thumbnailUrl = novel.cover?.let { cover ->
                if (cover.startsWith("/Novels/") || cover.startsWith("/storage/")) {
                    null
                } else {
                    cover
                }
            },
            favorite = novel.inLibrary != 0,
            chapters = backupChapters,
            categories = categoryOrders,
            history = backupHistory,
            dateAdded = System.currentTimeMillis(),
            isNovel = true,
        )
    }

    private fun parseDate(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            // Try ISO 8601 format
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(dateStr)?.time
        } catch (_: Exception) {
            try {
                // Try simple date format
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)?.time
            } catch (_: Exception) {
                try {
                    // Try timestamp as number
                    dateStr.toLongOrNull()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Generate a deterministic source ID for a missing plugin based on its plugin ID.
     * Uses a hash to ensure the same plugin ID always gets the same source ID.
     * The range is chosen to avoid conflicts with real sources (which typically use small IDs).
     */
    private fun generateStubSourceId(pluginId: String): Long {
        val hash = pluginId.hashCode()
        return 5_000_000_000L + (hash.toLong() and 0x7FFFFFFF)
    }

    private fun writeErrorLog(): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("lnreader_import_error.txt")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                file.bufferedWriter().use { out ->
                    errors.forEach { (date, message) ->
                        out.write("[${sdf.format(date)}] $message\n")
                    }
                }
                return file
            }
        } catch (_: Exception) { }
        return File("")
    }
}
