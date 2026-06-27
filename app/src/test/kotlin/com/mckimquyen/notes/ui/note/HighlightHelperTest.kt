package com.mckimquyen.notes.ui.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightHelperTest {

    @Test
    fun testFindHighlightsInStringSingleMatch() {
        val text = "This is a simple notes application."
        val query = "simple"
        val highlights = HighlightHelper.findHighlightsInString(text, query)

        assertEquals(1, highlights.size)
        assertEquals(10, highlights[0].first)
        assertEquals(16, highlights[0].last) // indexOf + length
    }

    @Test
    fun testFindHighlightsInStringCaseInsensitive() {
        val text = "Kotlin is Awesome. kotlin is modern."
        val query = "Kotlin"
        val highlights = HighlightHelper.findHighlightsInString(text, query)

        assertEquals(2, highlights.size)
        assertEquals(0, highlights[0].first)
        assertEquals(19, highlights[1].first)
    }

    @Test
    fun testFindHighlightsInStringRespectsMaxLimit() {
        val text = "banana banana banana"
        val query = "banana"
        val highlights = HighlightHelper.findHighlightsInString(text, query, max = 2)

        assertEquals(2, highlights.size)
        assertEquals(0, highlights[0].first)
        assertEquals(7, highlights[1].first)
    }

    @Test
    fun testFindHighlightsInStringWithQuotes() {
        val text = "He said \"hello\" to the world."
        val query = "\"hello\"" // Quoted query
        val highlights = HighlightHelper.findHighlightsInString(text, query)

        // Quoted query should strip the outer quotes and match "hello"
        assertEquals(1, highlights.size)
        assertEquals(9, highlights[0].first)
        assertEquals(14, highlights[0].last)
    }

    @Test
    fun testGetStartEllipsizedTextNoShiftWhenUnderThreshold() {
        val text = "Short note context"
        val highlights = mutableListOf(0..5)
        val result = HighlightHelper.getStartEllipsizedText(
            text = text,
            highlights = highlights,
            startEllipsisThreshold = 10,
            startEllipsisDistance = 3
        )

        assertEquals(text, result.content)
        assertEquals(0..5, result.highlights[0])
    }

    @Test
    fun testGetStartEllipsizedTextAppliesShiftWhenOverThreshold() {
        val text = "This is a very long note content that starts far away"
        val highlights = mutableListOf(40..45) // "starts"
        val result = HighlightHelper.getStartEllipsizedText(
            text = text,
            highlights = highlights,
            startEllipsisThreshold = 10,
            startEllipsisDistance = 5
        )

        // Text should be ellipsized and highlights shifted
        assertTrue(result.content.startsWith("\u2026\uFEFF"))
        assertEquals(highlights.size, result.highlights.size)
        assertTrue(result.highlights[0].first < 40)
    }
}
