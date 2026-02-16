package tachiyomi.domain.manga.interactor

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository

class GetLibraryMangaTest {

    private lateinit var mangaRepository: MangaRepository
    private lateinit var getLibraryManga: GetLibraryManga

    private fun createManga(id: Long, title: String = "Manga $id"): Manga = Manga(
        id = id,
        source = 1L,
        favorite = true,
        lastUpdate = 0L,
        nextUpdate = 0L,
        fetchInterval = 0,
        dateAdded = 0L,
        viewerFlags = 0L,
        chapterFlags = 0L,
        coverLastModified = 0L,
        url = "/manga/$id",
        title = title,
        alternativeTitles = emptyList(),
        artist = null,
        author = null,
        description = null,
        genre = null,
        status = 0L,
        thumbnailUrl = null,
        updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
        initialized = true,
        lastModifiedAt = 0L,
        favoriteModifiedAt = null,
        version = 0L,
        notes = "",
        isNovel = false,
    )

    private fun createLibraryManga(
        id: Long,
        title: String = "Manga $id",
        totalChapters: Long = 10,
        readCount: Long = 0,
        bookmarkCount: Long = 0,
        categories: List<Long> = emptyList(),
        lastRead: Long = 0L,
    ): LibraryManga = LibraryManga(
        manga = createManga(id, title),
        categories = categories,
        totalChapters = totalChapters,
        readCount = readCount,
        bookmarkCount = bookmarkCount,
        latestUpload = 0L,
        chapterFetchedAt = 0L,
        lastRead = lastRead,
    )

    @BeforeEach
    fun setUp() {
        mangaRepository = mockk(relaxed = true)
        val library = listOf(
            createLibraryManga(1, "Novel A", totalChapters = 20, readCount = 5, bookmarkCount = 2),
            createLibraryManga(2, "Novel B", totalChapters = 50, readCount = 50, bookmarkCount = 0),
            createLibraryManga(3, "Novel C", totalChapters = 10, readCount = 0, bookmarkCount = 0),
        )
        coEvery { mangaRepository.getLibraryManga() } returns library
        every { mangaRepository.invalidateLibraryCache() } returns Unit
        getLibraryManga = GetLibraryManga(mangaRepository)
    }

    @Test
    fun `await returns cached library`() {
        runBlocking {
            val result = getLibraryManga.await()
            result shouldHaveSize 3
            result[0].manga.title shouldBe "Novel A"
        }
    }

    @Test
    fun `subscribe flow emits current state`() {
        runBlocking {
            getLibraryManga.await()
            val items = getLibraryManga.subscribe().first()
            items shouldHaveSize 3
        }
    }

    @Test
    fun `applyChapterUpdates updates read count for specific manga`() {
        runBlocking {
            getLibraryManga.await()
            getLibraryManga.applyChapterUpdates(mangaId = 1, readCount = 10)

            val updated = getLibraryManga.subscribe().first()
            updated.first { it.id == 1L }.readCount shouldBe 10
            updated.first { it.id == 2L }.readCount shouldBe 50
        }
    }

    @Test
    fun `applyChapterUpdates updates bookmark count`() {
        runBlocking {
            getLibraryManga.await()
            getLibraryManga.applyChapterUpdates(mangaId = 3, bookmarkCount = 5)

            val updated = getLibraryManga.subscribe().first()
            updated.first { it.id == 3L }.bookmarkCount shouldBe 5
        }
    }

    @Test
    fun `applyChapterUpdates updates total chapters`() {
        runBlocking {
            getLibraryManga.await()
            getLibraryManga.applyChapterUpdates(mangaId = 2, totalChapters = 55)

            val updated = getLibraryManga.subscribe().first()
            updated.first { it.id == 2L }.totalChapters shouldBe 55
        }
    }

    @Test
    fun `applyChapterUpdates with null keeps existing values`() {
        runBlocking {
            getLibraryManga.await()
            getLibraryManga.applyChapterUpdates(mangaId = 1, readCount = 15)

            val updated = getLibraryManga.subscribe().first()
            val manga = updated.first { it.id == 1L }
            manga.readCount shouldBe 15
            manga.totalChapters shouldBe 20
            manga.bookmarkCount shouldBe 2
        }
    }

    @Test
    fun `applyChapterUpdates on non-existent id is no-op`() {
        runBlocking {
            getLibraryManga.await()
            getLibraryManga.applyChapterUpdates(mangaId = 999, readCount = 10)

            val result = getLibraryManga.subscribe().first()
            result shouldHaveSize 3
        }
    }

    @Test
    fun `applyBatchChapterUpdates updates multiple manga at once`() {
        runBlocking {
            getLibraryManga.await()
            getLibraryManga.applyBatchChapterUpdates(
                mapOf(
                    1L to { copy(readCount = 20) },
                    3L to { copy(readCount = 5, bookmarkCount = 3) },
                ),
            )

            val updated = getLibraryManga.subscribe().first()
            updated.first { it.id == 1L }.readCount shouldBe 20
            updated.first { it.id == 3L }.readCount shouldBe 5
            updated.first { it.id == 3L }.bookmarkCount shouldBe 3
            updated.first { it.id == 2L }.readCount shouldBe 50
        }
    }

    @Test
    fun `applyBatchChapterUpdates with empty map is no-op`() {
        runBlocking {
            getLibraryManga.await()
            getLibraryManga.applyBatchChapterUpdates(emptyMap())

            val result = getLibraryManga.subscribe().first()
            result shouldHaveSize 3
            result.first { it.id == 1L }.readCount shouldBe 5
        }
    }

    @Test
    fun `applyCategoryUpdates adds categories`() {
        runBlocking {
            getLibraryManga.await()
            getLibraryManga.applyCategoryUpdates(
                mangaIds = listOf(1L),
                addCategories = listOf(10L, 20L),
                removeCategories = emptyList(),
            )

            val updated = getLibraryManga.subscribe().first()
            updated.first { it.id == 1L }.categories shouldBe listOf(10L, 20L)
        }
    }

    @Test
    fun `applyCategoryUpdates removes categories`() {
        runBlocking {
            getLibraryManga.await()
            getLibraryManga.applyCategoryUpdates(
                mangaIds = listOf(1L),
                addCategories = listOf(10L, 20L, 30L),
                removeCategories = emptyList(),
            )
            getLibraryManga.applyCategoryUpdates(
                mangaIds = listOf(1L),
                addCategories = emptyList(),
                removeCategories = listOf(20L),
            )

            val updated = getLibraryManga.subscribe().first()
            updated.first { it.id == 1L }.categories shouldBe listOf(10L, 30L)
        }
    }

    @Test
    fun `unreadCount equals totalChapters minus readCount`() {
        runBlocking {
            getLibraryManga.await()
            val items = getLibraryManga.subscribe().first()

            items.first { it.id == 1L }.unreadCount shouldBe 15
            items.first { it.id == 2L }.unreadCount shouldBe 0
            items.first { it.id == 3L }.unreadCount shouldBe 10
        }
    }

    @Test
    fun `unreadCount updates after applyChapterUpdates`() {
        runBlocking {
            getLibraryManga.await()
            getLibraryManga.applyChapterUpdates(mangaId = 1, readCount = 18)

            val items = getLibraryManga.subscribe().first()
            items.first { it.id == 1L }.unreadCount shouldBe 2
        }
    }

    @Test
    fun `notifyChanged re-emits current state without DB query`() {
        runBlocking {
            getLibraryManga.await()
            val before = getLibraryManga.subscribe().first()

            getLibraryManga.notifyChanged()

            val after = getLibraryManga.subscribe().first()
            after shouldHaveSize before.size
            after.map { it.id } shouldBe before.map { it.id }
        }
    }
}

/**
 * Tests for [GetLibraryManga] initialization behavior.
 * The init block loads library data directly from the mangas table
 */
class GetLibraryMangaInitTest {

    private fun createManga(id: Long, title: String = "Manga $id"): Manga = Manga(
        id = id,
        source = 1L,
        favorite = true,
        lastUpdate = 0L,
        nextUpdate = 0L,
        fetchInterval = 0,
        dateAdded = 0L,
        viewerFlags = 0L,
        chapterFlags = 0L,
        coverLastModified = 0L,
        url = "/manga/$id",
        title = title,
        alternativeTitles = emptyList(),
        artist = null,
        author = null,
        description = null,
        genre = null,
        status = 0L,
        thumbnailUrl = null,
        updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
        initialized = true,
        lastModifiedAt = 0L,
        favoriteModifiedAt = null,
        version = 0L,
        notes = "",
        isNovel = false,
    )

    private fun createLibraryManga(id: Long, title: String = "Manga $id"): LibraryManga =
        LibraryManga(
            manga = createManga(id, title),
            categories = emptyList(),
            totalChapters = 10,
            readCount = 0,
            bookmarkCount = 0,
            latestUpload = 0L,
            chapterFetchedAt = 0L,
            lastRead = 0L,
        )

    @Test
    fun `init loads library without cache integrity check`() {
        val repo = mockk<MangaRepository>(relaxed = true)
        coEvery { repo.getLibraryManga() } returns listOf(createLibraryManga(1))

        val interactor = GetLibraryManga(repo)

        runBlocking {
            kotlinx.coroutines.delay(500)
            val result = interactor.subscribe().first()
            result shouldHaveSize 1
        }
        coVerify(exactly = 0) { repo.refreshLibraryCache() }
        coVerify(exactly = 0) { repo.refreshLibraryCacheIncremental() }
    }

    @Test
    fun `init with empty library loads empty list`() {
        val repo = mockk<MangaRepository>(relaxed = true)
        coEvery { repo.getLibraryManga() } returns emptyList()

        val interactor = GetLibraryManga(repo)

        runBlocking {
            kotlinx.coroutines.delay(500)
            interactor.subscribe().first() shouldHaveSize 0
        }
        coVerify(exactly = 0) { repo.refreshLibraryCache() }
        coVerify(exactly = 0) { repo.refreshLibraryCacheIncremental() }
    }
}
