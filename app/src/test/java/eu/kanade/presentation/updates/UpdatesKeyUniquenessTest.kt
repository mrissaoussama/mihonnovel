package eu.kanade.presentation.updates

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * Tests that the LazyColumn key format for updates items produces unique keys,
 * avoiding duplicate key crashes when the same manga+chapter can appear multiple times
 * (e.g., after re-download or server-side updates with different fetch timestamps).
 */
@Execution(ExecutionMode.CONCURRENT)
class UpdatesKeyUniquenessTest {

    /**
     * Simulates the key generation logic from UpdatesUiItem.kt
     */
    private fun generateKey(mangaId: Long, chapterId: Long, dateFetch: Long): String {
        return "updates-$mangaId-$chapterId-$dateFetch"
    }

    @Test
    fun `same manga and chapter with different fetch times produce unique keys`() {
        val key1 = generateKey(mangaId = 1, chapterId = 100, dateFetch = 1000L)
        val key2 = generateKey(mangaId = 1, chapterId = 100, dateFetch = 2000L)

        key1 shouldNotBe key2
    }

    @Test
    fun `different chapters of same manga produce unique keys`() {
        val key1 = generateKey(mangaId = 1, chapterId = 100, dateFetch = 1000L)
        val key2 = generateKey(mangaId = 1, chapterId = 101, dateFetch = 1000L)

        key1 shouldNotBe key2
    }

    @Test
    fun `same chapter of different manga produce unique keys`() {
        val key1 = generateKey(mangaId = 1, chapterId = 100, dateFetch = 1000L)
        val key2 = generateKey(mangaId = 2, chapterId = 100, dateFetch = 1000L)

        key1 shouldNotBe key2
    }

    @Test
    fun `identical entries produce same key`() {
        val key1 = generateKey(mangaId = 1, chapterId = 100, dateFetch = 1000L)
        val key2 = generateKey(mangaId = 1, chapterId = 100, dateFetch = 1000L)

        key1 shouldBe key2
    }

    @Test
    fun `batch of updates with various duplicates all have unique keys`() {
        data class UpdateEntry(val mangaId: Long, val chapterId: Long, val dateFetch: Long)

        val entries = listOf(
            UpdateEntry(1, 100, 1000),
            UpdateEntry(1, 100, 2000), // Same manga+chapter, different fetch time
            UpdateEntry(1, 101, 1000),
            UpdateEntry(2, 100, 1000),
            UpdateEntry(2, 100, 3000), // Same manga+chapter, different fetch time
            UpdateEntry(3, 200, 1000),
        )

        val keys = entries.map { generateKey(it.mangaId, it.chapterId, it.dateFetch) }.toSet()
        keys shouldHaveSize entries.size
    }
}
