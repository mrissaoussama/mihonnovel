package mihon.domain.source.interactor

import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import mihon.domain.source.models.RemoteMangaUpdate
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager

class UpdateMangaFromRemote(
    private val sourceManager: SourceManager,
    private val chapterRepository: ChapterRepository,
    private val mangaRepository: MangaRepository,
    private val syncChaptersWithSource: SyncChaptersWithSource,
    private val updateManga: UpdateManga,
    private val coverCache: CoverCache,
    private val libraryPreferences: LibraryPreferences,
    private val downloadManager: DownloadManager,
) {
    suspend operator fun invoke(
        manga: Manga,
        fetchDetails: Boolean = false,
        fetchChapters: Boolean = false,
        manualFetch: Boolean = false,
        fetchWindow: Pair<Long, Long> = Pair(0, 0),
        onLibraryCacheUpdate: ((Long, (Manga) -> Manga) -> Unit)? = null,
    ): Result<RemoteMangaUpdate> {
        val source = sourceManager.getOrStub(manga.source)
        return invoke(
            source = source,
            manga = manga,
            fetchDetails = fetchDetails,
            fetchChapters = fetchChapters,
            manualFetch = manualFetch,
            onLibraryCacheUpdate = onLibraryCacheUpdate,
        )
    }

    suspend operator fun invoke(
        source: Source,
        manga: Manga,
        fetchDetails: Boolean = false,
        fetchChapters: Boolean = false,
        manualFetch: Boolean = false,
        fetchWindow: Pair<Long, Long> = Pair(0, 0),
        forceRefresh: Boolean = false,
        onLibraryCacheUpdate: ((Long, (Manga) -> Manga) -> Unit)? = null,
    ): Result<RemoteMangaUpdate> {
        return try {
            val chapters = if (forceRefresh) {
                emptyList()
            } else {
                chapterRepository.getChapterByMangaId(manga.id).sortedBy { it.sourceOrder }
            }
            val update = withIOContext {
                source.getMangaUpdate(
                    manga = manga.toSManga(),
                    chapters = chapters.map(Chapter::toSChapter),
                    fetchDetails = fetchDetails,
                    fetchChapters = fetchChapters,
                )
            }
            if (fetchDetails) {
                updateManga.awaitUpdateFromSource(
                    manga,
                    update.manga,
                    manualFetch,
                    coverCache,
                    libraryPreferences,
                    downloadManager,
                    onLibraryCacheUpdate,
                )
            }
            val newChapters = if (fetchChapters) {
                syncChaptersWithSource.await(
                    rawSourceChapters = update.chapters,
                    manga = manga,
                    source = source,
                    manualFetch = manualFetch,
                    fetchWindow = fetchWindow,
                )
            } else {
                emptyList()
            }
            val updatedManga = mangaRepository.getMangaById(manga.id)

            Result.success(RemoteMangaUpdate(manga = updatedManga, newChapters = newChapters))
        } catch (e: CancellationException) {
            // Must propagate, not be swallowed as a per-manga failure - CancellationException is
            // a RuntimeException and would otherwise match catch(Exception) below, breaking the
            // enclosing coroutineScope's structured concurrency on cancellation.
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.failure(e)
        } catch (e: LinkageError) {
            // Outdated/incompatible extensions throw LinkageError subtypes (NoClassDefFoundError,
            // NoSuchMethodError, AbstractMethodError, IncompatibleClassChangeError) rather than
            // Exception - e.g. an old extension still calling a JS-engine class the app no longer
            // bundles. Catching only Exception let one such extension crash the whole app
            // (library update, migration, etc.) instead of failing just this manga. LinkageError
            // specifically, not Throwable/Error: JVM-fatal errors like OutOfMemoryError must still
            // propagate instead of being swallowed mid-batch with the process limping along.
            logcat(LogPriority.ERROR, e)
            Result.failure(e)
        }
    }
}
