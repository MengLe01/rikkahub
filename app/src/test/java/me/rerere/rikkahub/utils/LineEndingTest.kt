package me.rerere.rikkahub.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class LineEndingTest {
    @Test
    fun `crlf should be normalized to lf`() {
        assertEquals("first\nsecond", "first\r\nsecond".normalizeLineEndings())
    }

    @Test
    fun `lone carriage return should be removed`() {
        assertEquals("firstsecond", "first\rsecond".normalizeLineEndings())
    }
}
