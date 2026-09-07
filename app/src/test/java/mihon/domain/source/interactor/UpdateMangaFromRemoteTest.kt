package mihon.domain.source.interactor

import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager

class UpdateMangaFromRemoteTest {

    private class FakeSource(private val remoteManga: SManga) : Source {
        override val id = 1L
        override val name = "Fake"
        override suspend fun getPopularManga(page: Int): MangasPage = throw UnsupportedOperationException()
        override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()
        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
            throw UnsupportedOperationException()
        override suspend fun getPageList(chapter: SChapter): List<Page> = throw UnsupportedOperationException()
        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ) = SMangaUpdate(manga = if (fetchDetails) remoteManga else manga, chapters = chapters)
    }

    private fun updateMangaFromRemote(
        updateManga: UpdateManga,
        localManga: Manga,
    ): UpdateMangaFromRemote {
        val chapterRepository = mockk<ChapterRepository>(relaxed = true)
        coEvery { chapterRepository.getChapterByMangaId(any()) } returns emptyList()
        val mangaRepository = mockk<MangaRepository>(relaxed = true)
        coEvery { mangaRepository.getMangaById(any()) } returns localManga

        return UpdateMangaFromRemote(
            sourceManager = mockk<SourceManager>(relaxed = true),
            chapterRepository = chapterRepository,
            mangaRepository = mangaRepository,
            syncChaptersWithSource = mockk<SyncChaptersWithSource>(relaxed = true),
            updateManga = updateManga,
            coverCache = mockk<CoverCache>(relaxed = true),
            libraryPreferences = mockk<LibraryPreferences>(relaxed = true),
            downloadManager = mockk<DownloadManager>(relaxed = true),
        )
    }

    @Test
    fun `manual refresh delegates metadata merge to the shared CustomMangaInfo-aware UpdateManga`() = runBlocking {
        val localManga = Manga.create().copy(id = 1L, favorite = true)
        val remoteManga = SManga.create().apply {
            title = "Remote title"
            author = "Remote author"
        }
        val updateManga = mockk<UpdateManga>(relaxed = true)

        updateMangaFromRemote(updateManga, localManga).invoke(
            source = FakeSource(remoteManga),
            manga = localManga,
            fetchDetails = true,
            fetchChapters = false,
            manualFetch = true,
        )

        coVerify(exactly = 1) {
            updateManga.awaitUpdateFromSource(localManga, remoteManga, true, any(), any(), any(), null)
        }
    }

    @Test
    fun `metadata merge is skipped entirely when details were not requested`() = runBlocking {
        val localManga = Manga.create().copy(id = 1L, favorite = true)
        val remoteManga = SManga.create().apply { title = "Remote title" }
        val updateManga = mockk<UpdateManga>(relaxed = true)

        updateMangaFromRemote(updateManga, localManga).invoke(
            source = FakeSource(remoteManga),
            manga = localManga,
            fetchDetails = false,
            fetchChapters = false,
        )

        coVerify(exactly = 0) {
            updateManga.awaitUpdateFromSource(any(), any(), any(), any(), any(), any(), any())
        }
    }
}
