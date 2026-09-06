package eu.kanade.domain.manga.interactor

import eu.kanade.tachiyomi.source.model.SManga
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.CustomMangaInfo.Companion.writeInto
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository

class UpdateMangaTest {

    private fun updateManga(mangaRepository: MangaRepository) = UpdateManga(
        mangaRepository = mangaRepository,
        fetchInterval = mockk(relaxed = true),
        getLibraryManga = mockk<GetLibraryManga>(relaxed = true),
    )

    private fun libraryPreferences() = mockk<LibraryPreferences>(relaxed = true).also {
        every { it.updateMangaMetadata.get() } returns true
        every { it.updateMangaTitles.get() } returns false
    }

    @Test
    fun `custom author, description, genre and status survive a refresh`() = runBlocking {
        val localManga = Manga.create().copy(
            id = 1L,
            favorite = true,
            author = "My author",
            description = "My description",
            genre = listOf("My genre"),
            status = 5L,
        )
        val memo = CustomMangaInfo(
            author = "My author",
            description = "My description",
            genre = listOf("My genre"),
            status = 5L,
        ).writeInto(JsonObject(emptyMap()))

        val remoteManga = SManga.create().apply {
            title = localManga.title
            author = "Scraped author"
            description = "Scraped description"
            genre = "Scraped genre"
            status = SManga.ONGOING
        }

        val mangaRepository = mockk<MangaRepository>(relaxed = true)
        coEvery { mangaRepository.getMemo(localManga.id) } returns memo
        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        updateManga(mangaRepository).awaitUpdateFromSource(
            localManga = localManga,
            remoteManga = remoteManga,
            manualFetch = true,
            coverCache = mockk(relaxed = true),
            libraryPreferences = libraryPreferences(),
            downloadManager = mockk(relaxed = true),
        )

        val update = updateSlot.captured
        assertEquals("My author", update.author)
        assertEquals("My description", update.description)
        assertEquals(listOf("My genre"), update.genre)
        assertEquals(5L, update.status)
    }

    @Test
    fun `fields flow through from source when no override is stored`() = runBlocking {
        val localManga = Manga.create().copy(id = 1L, favorite = true)
        val remoteManga = SManga.create().apply {
            title = "Title"
            author = "Scraped author"
            description = "Scraped description"
            genre = "Scraped genre"
            status = SManga.ONGOING
        }

        val mangaRepository = mockk<MangaRepository>(relaxed = true)
        coEvery { mangaRepository.getMemo(localManga.id) } returns JsonObject(emptyMap())
        val updateSlot = slot<MangaUpdate>()
        coEvery { mangaRepository.update(capture(updateSlot)) } returns true

        updateManga(mangaRepository).awaitUpdateFromSource(
            localManga = localManga,
            remoteManga = remoteManga,
            manualFetch = true,
            coverCache = mockk(relaxed = true),
            libraryPreferences = libraryPreferences(),
            downloadManager = mockk(relaxed = true),
        )

        val update = updateSlot.captured
        assertEquals("Scraped author", update.author)
        assertEquals("Scraped description", update.description)
        assertEquals(listOf("Scraped genre"), update.genre)
        assertEquals(SManga.ONGOING.toLong(), update.status)
    }
}
