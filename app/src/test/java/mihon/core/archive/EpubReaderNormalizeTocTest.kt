package mihon.core.archive

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EpubReaderNormalizeTocTest {

    private fun chapter(title: String, order: Int, depth: Int = 0) =
        EpubReader.EpubChapter(title = title, href = "s$order.xhtml", order = order, depth = depth)

    private fun normalizeFlat(vararg titles: String): List<String> =
        EpubReader.normalizeTableOfContents(
            titles.mapIndexed { index, title -> chapter(title, index) },
        ).map { it.title }

    private fun normalize(vararg entries: Pair<String, Int>): List<String> =
        EpubReader.normalizeTableOfContents(
            entries.mapIndexed { index, (title, depth) -> chapter(title, index, depth) },
        ).map { it.title }

    // Flat TOC (heuristic fallback)

    @Test
    fun `descriptive chapter titles are left untouched`() {
        // Regression: "Chapter N: Title" used to match the subsection regex and get prefixed with the
        // previous front-matter entry (e.g. "Table of Contents Page - Chapter 1: ...").
        val result = normalizeFlat(
            "Table of Contents Page",
            "Chapter 1: Operating Behind the Scenes",
            "Chapter 2: True Ability",
            "Postscript",
        )

        assertEquals(
            listOf(
                "Table of Contents Page",
                "Chapter 1: Operating Behind the Scenes",
                "Chapter 2: True Ability",
                "Postscript",
            ),
            result,
        )
    }

    @Test
    fun `bare subsection labels are still prefixed with the last primary title`() {
        val result = normalizeFlat("The Awakening", "Part 1", "Part 2")
        assertEquals(listOf("The Awakening", "The Awakening - Part 1", "The Awakening - Part 2"), result)
    }

    @Test
    fun `bare label with trailing punctuation still counts as a subsection`() {
        val result = normalizeFlat("Prologue", "Chapter 3.", "Chapter 4:")
        assertEquals(listOf("Prologue", "Prologue - Chapter 3.", "Prologue - Chapter 4:"), result)
    }

    @Test
    fun `roman numeral subsection labels are recognized`() {
        val result = normalizeFlat("The Return", "Act IV")
        assertEquals(listOf("The Return", "The Return - Act IV"), result)
    }

    @Test
    fun `subsection at the start with no primary is left as-is`() {
        val result = normalizeFlat("Part 1", "The Real Start")
        assertEquals(listOf("Part 1", "The Real Start"), result)
    }

    @Test
    fun `blank titles fall back to a positional chapter name`() {
        val result = normalizeFlat("Intro", "   ")
        assertEquals(listOf("Intro", "Chapter 2"), result)
    }

    @Test
    fun `empty toc returns empty`() {
        assertEquals(emptyList<String>(), EpubReader.normalizeTableOfContents(emptyList()))
    }

    // Nested TOC (structural, locale-agnostic)

    @Test
    fun `nested entries are prefixed with their parent title`() {
        val result = normalize(
            "Part One" to 0,
            "Chapter 1" to 1,
            "Chapter 2" to 1,
            "Part Two" to 0,
            "Chapter 3" to 1,
        )

        assertEquals(
            listOf(
                "Part One",
                "Part One - Chapter 1",
                "Part One - Chapter 2",
                "Part Two",
                "Part Two - Chapter 3",
            ),
            result,
        )
    }

    @Test
    fun `structural nesting works for non-english titles`() {
        // No keyword list involved, so German/Japanese labels normalize just like English ones.
        val result = normalize(
            "Erster Teil" to 0,
            "Kapitel 1" to 1,
            "第1章" to 1,
            "Zweiter Teil" to 0,
            "Kapitel 2" to 1,
        )

        assertEquals(
            listOf(
                "Erster Teil",
                "Erster Teil - Kapitel 1",
                "Erster Teil - 第1章",
                "Zweiter Teil",
                "Zweiter Teil - Kapitel 2",
            ),
            result,
        )
    }

    @Test
    fun `deep nesting joins the full ancestor chain`() {
        val result = normalize(
            "Book I" to 0,
            "Part A" to 1,
            "Chapter 1" to 2,
            "Part B" to 1,
            "Book II" to 0,
        )

        assertEquals(
            listOf(
                "Book I",
                "Book I - Part A",
                "Book I - Part A - Chapter 1",
                "Book I - Part B",
                "Book II",
            ),
            result,
        )
    }

    @Test
    fun `descriptive titles under a parent keep the parent prefix instead of heuristic mangling`() {
        // A nested TOC forces the structural path even though a sibling looks like a heuristic subsection.
        val result = normalize(
            "Volume 1" to 0,
            "Chapter 1: The Beginning" to 1,
            "Volume 2" to 0,
            "Chapter 1: The Sequel" to 1,
        )

        assertEquals(
            listOf(
                "Volume 1",
                "Volume 1 - Chapter 1: The Beginning",
                "Volume 2",
                "Volume 2 - Chapter 1: The Sequel",
            ),
            result,
        )
    }


    @Test
    fun `a lone root with no sibling is not used as an ancestor prefix for its descendants`() {
        val result = normalize(
            "Some Book Title" to 0,
            "Synopsis" to 1,
            "Chapter 1: Foo" to 1,
            "Chapter 2: Bar" to 1,
            "Chapter 3: Baz" to 1,
        )

        assertEquals(
            listOf("Some Book Title", "Synopsis", "Chapter 1: Foo", "Chapter 2: Bar", "Chapter 3: Baz"),
            result,
        )
    }

    @Test
    fun `a lone root nested several levels deep is still not prefixed when no level has a sibling`() {
        val result = normalize(
            "Some Book Title" to 0,
            "Front Matter" to 1,
            "Chapter 1: Foo" to 2,
        )

        assertEquals(listOf("Some Book Title", "Front Matter", "Chapter 1: Foo"), result)
    }

    // Depth gaps: an NCX/nav grouping node with no <navLabel>/<content> is skipped as an entry but its
    // children still get emitted at depth+1, so the first entry can start at depth > 0, or a jump can
    // skip a level. normalizeByDepth pads these gaps; computeSiblingPresence must not crash on them.

    @Test
    fun `toc whose first entry starts below depth zero does not crash`() {
        val result = normalize(
            "Orphan Chapter 1" to 1,
            "Orphan Chapter 2" to 1,
        )

        assertEquals(listOf("Orphan Chapter 1", "Orphan Chapter 2"), result)
    }

    @Test
    fun `toc with a mid-list jump of two depth levels does not crash`() {
        val result = normalize(
            "Book" to 0,
            "Deep Chapter" to 2,
        )

        assertEquals(listOf("Book", "Book - Deep Chapter"), result)
    }
}
