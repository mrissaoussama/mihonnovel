package eu.kanade.tachiyomi

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import mihon.domain.manga.model.toDomainManga
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

/**
 * Tests for distinguishing Manga vs Novel entries in the domain model.
 *
 * These cover the [Manga.isNovel] flag, [SManga.toDomainManga] helper,
 * default values, and status/update-strategy propagation.
 */
class MangaNovelTest {

    // ──────────────────────────────── Manga.create() defaults ─────────────────────────────────

    @Nested
    inner class DefaultValues {

        @Test
        fun `Manga-create defaults isNovel to false`() {
            Manga.create().isNovel shouldBe false
        }

        @Test
        fun `Manga-create defaults title to empty string`() {
            Manga.create().title shouldBe ""
        }

        @Test
        fun `Manga-create defaults updateStrategy to ALWAYS_UPDATE`() {
            Manga.create().updateStrategy shouldBe UpdateStrategy.ALWAYS_UPDATE
        }

        @Test
        fun `Manga-create defaults status to 0`() {
            // 0 corresponds to SManga.UNKNOWN
            Manga.create().status shouldBe 0L
        }

        @Test
        fun `Manga-create defaults favorite to false`() {
            Manga.create().favorite shouldBe false
        }
    }

    // ─────────────────────────────── isNovel flag behaviour ───────────────────────────────────

    @Nested
    inner class IsNovelFlag {

        @Test
        fun `copy with isNovel=true marks entry as novel`() {
            val novel = Manga.create().copy(isNovel = true, title = "Overlord")
            novel.isNovel shouldBe true
            novel.title shouldBe "Overlord"
        }

        @Test
        fun `copy with isNovel=false marks entry as manga`() {
            val manga = Manga.create().copy(isNovel = false, title = "One Piece")
            manga.isNovel shouldBe false
            manga.title shouldBe "One Piece"
        }

        @Test
        fun `novel and manga with same title are distinguishable by isNovel`() {
            val novel = Manga.create().copy(title = "Same Title", isNovel = true)
            val manga = Manga.create().copy(title = "Same Title", isNovel = false)

            novel.isNovel shouldNotBe manga.isNovel
            novel.title shouldBe manga.title
        }
    }

    // ──────────────────────────── SManga.toDomainManga conversion ─────────────────────────────

    @Nested
    inner class SMangaToDomainMangaConversion {

        private val SOURCE_ID = 123456789L

        private fun smanga(
            title: String = "Test",
            url: String = "/test",
            author: String? = null,
            artist: String? = null,
            description: String? = null,
            status: Int = SManga.UNKNOWN,
            thumbnailUrl: String? = null,
        ) = SManga.create().apply {
            this.title = title
            this.url = url
            this.author = author
            this.artist = artist
            this.description = description
            this.status = status
            this.thumbnail_url = thumbnailUrl
        }

        @Test
        fun `toDomainManga without novel flag defaults isNovel to false`() {
            val domain = smanga(title = "Dragon Ball").toDomainManga(SOURCE_ID)
            domain.isNovel shouldBe false
            domain.title shouldBe "Dragon Ball"
        }

        @Test
        fun `toDomainManga with isNovel=true produces novel entry`() {
            val domain = smanga(title = "Re:Zero").toDomainManga(SOURCE_ID, isNovel = true)
            domain.isNovel shouldBe true
            domain.source shouldBe SOURCE_ID
        }

        @Test
        fun `toDomainManga preserves author and artist`() {
            val domain = smanga(author = "Kubo Tite", artist = "Kubo Tite")
                .toDomainManga(SOURCE_ID)
            domain.author shouldBe "Kubo Tite"
            domain.artist shouldBe "Kubo Tite"
        }

        @Test
        fun `toDomainManga propagates ONGOING status`() {
            val domain = smanga(status = SManga.ONGOING).toDomainManga(SOURCE_ID)
            domain.status shouldBe SManga.ONGOING.toLong()
        }

        @Test
        fun `toDomainManga propagates COMPLETED status`() {
            val domain = smanga(status = SManga.COMPLETED).toDomainManga(SOURCE_ID)
            domain.status shouldBe SManga.COMPLETED.toLong()
        }

        @Test
        fun `toDomainManga sets source id correctly`() {
            val domain = smanga().toDomainManga(SOURCE_ID)
            domain.source shouldBe SOURCE_ID
        }

        @Test
        fun `toDomainManga preserves thumbnail url`() {
            val url = "https://example.com/cover.jpg"
            val domain = smanga(thumbnailUrl = url).toDomainManga(SOURCE_ID)
            domain.thumbnailUrl shouldBe url
        }

        @Test
        fun `toDomainManga handles null thumbnail url`() {
            val domain = smanga(thumbnailUrl = null).toDomainManga(SOURCE_ID)
            domain.thumbnailUrl shouldBe null
        }
    }

    // ──────────────────────────────── Novel-specific semantics ────────────────────────────────

    @Nested
    inner class NovelSemantics {

        @Test
        fun `novel list can be filtered from mixed collection`() {
            val entries = listOf(
                Manga.create().copy(title = "Bleach", isNovel = false),
                Manga.create().copy(title = "Sword Art Online", isNovel = true),
                Manga.create().copy(title = "Naruto", isNovel = false),
                Manga.create().copy(title = "No Game No Life", isNovel = true),
            )

            val novels = entries.filter { it.isNovel }
            val manga = entries.filter { !it.isNovel }

            novels.size shouldBe 2
            manga.size shouldBe 2
            novels.map { it.title } shouldBe listOf("Sword Art Online", "No Game No Life")
            manga.map { it.title } shouldBe listOf("Bleach", "Naruto")
        }

        @Test
        fun `novel count and manga count sum to total`() {
            val entries = (1..5).map { Manga.create().copy(isNovel = it % 2 == 0) }
            val novelCount = entries.count { it.isNovel }
            val mangaCount = entries.count { !it.isNovel }
            novelCount + mangaCount shouldBe entries.size
        }
    }
}
