package com.nexusnotes.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryTest {

    @Test
    fun termsAreCleanedLowercasedAndDeduplicated() {
        assertEquals(listOf("hello", "world"), SearchQuery.terms("  Hello  WORLD hello "))
        assertEquals(listOf("e", "mail"), SearchQuery.terms("e-mail"))
    }

    @Test
    fun ftsSyntaxCannotBeInjected() {
        assertEquals("\"title*\" \"x*\" \"or*\" \"y*\"", SearchQuery.toFtsMatch("title:x OR \"y\"*"))
        assertNull(SearchQuery.toFtsMatch("*** \" ( ) -"))
        assertNull(SearchQuery.toFtsMatch("   "))
    }

    @Test
    fun banglaTermsArePrefixSearches() {
        assertEquals("\"নোট*\" \"কাজ*\"", SearchQuery.toFtsMatch("নোট কাজ"))
    }

    @Test
    fun atMostEightTerms() {
        assertEquals(8, SearchQuery.terms((1..20).joinToString(" ") { "w$it" }).size)
    }

    @Test
    fun snippetCentersOnFirstMatchAndHighlights() {
        val body = "a ".repeat(100) + "the Needle is here " + "b ".repeat(100)
        val s = SearchQuery.snippet(body, listOf("needle"), maxLength = 60)
        assertTrue(s.text.startsWith("…"))
        assertTrue(s.text.endsWith("…"))
        assertEquals(1, s.highlights.size)
        val r = s.highlights.first()
        assertEquals("Needle", s.text.substring(r.first, r.last + 1))
    }

    @Test
    fun snippetWithoutMatchShowsStart() {
        val s = SearchQuery.snippet("first line\nsecond", listOf("zzz"))
        assertEquals("first line second", s.text)
        assertTrue(s.highlights.isEmpty())
    }

    @Test
    fun overlappingHighlightsMerge() {
        assertEquals(listOf(0..4), SearchQuery.highlightRanges("notebook", listOf("note", "teb")))
        assertEquals(listOf(0..2, 4..6), SearchQuery.highlightRanges("নোট নোট", listOf("নোট")))
    }

    @Test
    fun emptyBody() {
        assertEquals("", SearchQuery.snippet("   ", listOf("a")).text)
    }
}
