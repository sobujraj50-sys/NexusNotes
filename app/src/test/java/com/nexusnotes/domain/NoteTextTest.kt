package com.nexusnotes.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTextTest {

    @Test
    fun wordCountEnglish() {
        assertEquals(0, NoteText.wordCount(""))
        assertEquals(0, NoteText.wordCount("   \n\t "))
        assertEquals(1, NoteText.wordCount("hello"))
        assertEquals(3, NoteText.wordCount("  one two\n\nthree  "))
    }

    @Test
    fun wordCountBangla() {
        assertEquals(3, NoteText.wordCount("আমি ভাত খাই"))
        assertEquals(4, NoteText.wordCount("নেক্সাস নোটস\nআমার নোট"))
    }

    @Test
    fun characterCountUsesCodePoints() {
        assertEquals(0, NoteText.characterCount(""))
        assertEquals(5, NoteText.characterCount("hello"))
        assertEquals(1, NoteText.characterCount("😀"))
        assertEquals(3, NoteText.characterCount("a😀b"))
    }

    @Test
    fun displayTitlePrefersTitle() {
        assertEquals("Groceries", NoteText.displayTitle("  Groceries ", "milk"))
    }

    @Test
    fun displayTitleFallsBackToFirstBodyLine() {
        assertEquals("first line", NoteText.displayTitle("", "\n\n  first line  \nsecond"))
    }

    @Test
    fun displayTitleUntitledWhenEmpty() {
        assertEquals("Untitled", NoteText.displayTitle(" ", " \n "))
    }

    @Test
    fun displayTitleIsShortened() {
        assertEquals(80, NoteText.displayTitle("", "x".repeat(200)).length)
    }

    @Test
    fun previewCollapsesWhitespace() {
        assertEquals("a b c", NoteText.preview("  a\n\nb\t c "))
        assertEquals(10, NoteText.preview("y".repeat(50), maxChars = 10).length)
    }
}
