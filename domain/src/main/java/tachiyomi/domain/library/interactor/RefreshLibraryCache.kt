package tachiyomi.domain.library.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.repository.MangaRepository

/**
 * Interactor for recomputing library aggregate columns on the mangas table.
 *
 * Aggregates (total_count, read_count, etc.) are stored directly on the mangas table
 * and maintained automatically by database triggers. This interactor can be used to:
 * - Force a full recompute after bulk operations
 * - Recompute for a specific manga after manual changes
 */
class RefreshLibraryCache(
    private val mangaRepository: MangaRepository,
) {
    /**
     * Recompute all aggregate columns for all favorite manga.
     * This is a relatively expensive operation and should be called sparingly.
     */
    suspend fun await() {
        logcat(LogPriority.INFO) { "RefreshLibraryCache: Recomputing all library aggregates" }
        mangaRepository.refreshLibraryCache()
        logcat(LogPriority.INFO) { "RefreshLibraryCache: Aggregates recomputed" }
    }

    /**
     * Recompute aggregates for a specific manga.
     * Use this after operations that modify a single manga's chapters/history.
     */
    suspend fun awaitForManga(mangaId: Long) {
        mangaRepository.refreshLibraryCacheForManga(mangaId)
    }
}
