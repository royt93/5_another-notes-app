package com.mckimquyen.notes

import com.mckimquyen.notes.ui.search.SearchQueryCleaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreBusinessLogicUnitTest {

    @Test
    fun testSearchQueryCleanerBasic() {
        // Normal terms should be lowercased and appended with '*' wildcard
        assertEquals("hello* world*", SearchQueryCleaner.clean("Hello World"))
        assertEquals("notes*", SearchQueryCleaner.clean("Notes"))
    }

    @Test
    fun testSearchQueryCleanerVietnameseDiacritics() {
        // Vietnamese diacritics should remain intact, lowercased, and tokenized with wildcard
        assertEquals("tiếng* việt*", SearchQueryCleaner.clean("Tiếng Việt"))
        assertEquals("ghi* chú*", SearchQueryCleaner.clean("Ghi Chú"))
        assertEquals("đọc*", SearchQueryCleaner.clean("ĐỌC"))
    }

    @Test
    fun testSearchQueryCleanerSpecialSyntax() {
        // Negation using minus '-' should be allowed
        assertEquals("cat* -dog*", SearchQueryCleaner.clean("cat -dog"))

        // Unwanted characters like escapes, parenthesis, wildcards should be stripped
        assertEquals("hello*", SearchQueryCleaner.clean("hello*"))
        assertEquals("nested*", SearchQueryCleaner.clean("(nested)"))

        // Unbalanced quotes should be balanced automatically
        assertEquals("hello* \"world\"", SearchQueryCleaner.clean("hello \"world"))
    }

    @Test
    fun testWordCountLogic() {
        val wordSplitRegex = Regex("\\s+")

        val countWords = { text: String ->
            if (text.isEmpty()) 0 else text.split(wordSplitRegex).count { it.isNotEmpty() }
        }

        // Empty text
        assertEquals(0, countWords(""))

        // Normal text
        assertEquals(5, countWords("Hello this is a test"))

        // Multiple spaces, newlines, and tabs
        assertEquals(4, countWords("Multiple   spaces\nhere\ttoo"))

        // Leading and trailing spaces
        assertEquals(3, countWords("   trimmed spaces here   "))
    }

    @Test
    fun testTimelineStableIdGeneration() {
        val generateStableId = { dateLabel: String ->
            -(dateLabel.hashCode().toLong() and 0xFFFFFFFFL) - 1000L
        }

        // Test that IDs are stable (same input yields same ID)
        val id1 = generateStableId("June 27, 2026")
        val id2 = generateStableId("June 27, 2026")
        assertEquals(id1, id2)

        // Test that IDs are always negative and less than or equal to -1000L
        val dates = listOf("Today", "Yesterday", "January 1, 2025", "Some Random Date String")
        for (date in dates) {
            val id = generateStableId(date)
            assertTrue(id < 0)
            assertTrue(id <= -1000L)
        }
    }
}
