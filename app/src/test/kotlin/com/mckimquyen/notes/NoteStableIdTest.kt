package com.mckimquyen.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteStableIdTest {

    private fun generateStableId(dateLabel: String): Long {
        return -(dateLabel.hashCode().toLong() and 0xFFFFFFFFL) - 1000L
    }

    @Test
    fun testStableIdIsAlwaysNegativeAndLessThan1000() {
        val dates = listOf(
            "January 1, 2026",
            "February 28, 2026",
            "December 31, 2026",
            "Today",
            "Yesterday",
            "",
            "A".repeat(100)
        )

        for (date in dates) {
            val id = generateStableId(date)
            // Should be negative and less than or equal to -1000L
            assertTrue("ID for '$date' ($id) should be negative", id < 0)
            assertTrue("ID for '$date' ($id) should be <= -1000L", id <= -1000L)
        }
    }

    @Test
    fun testDifferentDatesProduceDifferentIds() {
        val date1 = "June 27, 2026"
        val date2 = "June 28, 2026"

        val id1 = generateStableId(date1)
        val id2 = generateStableId(date2)

        assertNotEquals(id1, id2)
    }

    @Test
    fun testSameDateProducesIdenticalId() {
        val date = "June 27, 2026"

        val id1 = generateStableId(date)
        val id2 = generateStableId(date)

        assertEquals(id1, id2)
    }
}
