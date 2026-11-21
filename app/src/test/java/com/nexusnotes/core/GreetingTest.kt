package com.nexusnotes.core

import com.nexusnotes.core.common.greetingForHour
import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {

    @Test
    fun greetingBoundaries() {
        assertEquals("Working late", greetingForHour(0))
        assertEquals("Working late", greetingForHour(4))
        assertEquals("Good morning", greetingForHour(5))
        assertEquals("Good morning", greetingForHour(11))
        assertEquals("Good afternoon", greetingForHour(12))
        assertEquals("Good afternoon", greetingForHour(16))
        assertEquals("Good evening", greetingForHour(17))
        assertEquals("Good evening", greetingForHour(21))
        assertEquals("Working late", greetingForHour(22))
        assertEquals("Working late", greetingForHour(23))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidHour() {
        greetingForHour(24)
    }
}
