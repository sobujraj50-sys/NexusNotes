package com.nexusnotes.domain.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownFormatterTest {

    private fun edit(text: String, start: Int, end: Int = start) = TextEdit(text, start, end)

    // ---- inline ----

    @Test
    fun boldWrapsSelection() {
        val r = MarkdownFormatter.apply(edit("hello world", 0, 5), FormatAction.BOLD)
        assertEquals("**hello** world", r.text)
        assertEquals(2, r.selectionStart)
        assertEquals(7, r.selectionEnd)
    }

    @Test
    fun boldToggleOffWhenMarkersOutside() {
        val r = MarkdownFormatter.apply(edit("**hello** world", 2, 7), FormatAction.BOLD)
        assertEquals("hello world", r.text)
        assertEquals(0, r.selectionStart)
        assertEquals(5, r.selectionEnd)
    }

    @Test
    fun boldToggleOffWhenMarkersSelected() {
        val r = MarkdownFormatter.apply(edit("**hello** world", 0, 9), FormatAction.BOLD)
        assertEquals("hello world", r.text)
    }

    @Test
    fun boldWithNoSelectionPutsCursorInside() {
        val r = MarkdownFormatter.apply(edit("ab", 1), FormatAction.BOLD)
        assertEquals("a****b", r.text)
        assertEquals(3, r.selectionStart)
        assertEquals(3, r.selectionEnd)
    }

    @Test
    fun italicInsideBoldWrapsInsteadOfUnwrappingBold() {
        val r = MarkdownFormatter.apply(edit("**hello**", 2, 7), FormatAction.ITALIC)
        assertEquals("***hello***", r.text)
        val back = MarkdownFormatter.apply(r, FormatAction.ITALIC)
        assertEquals("**hello**", back.text)
    }

    @Test
    fun italicToggle() {
        val r = MarkdownFormatter.apply(edit("x", 0, 1), FormatAction.ITALIC)
        assertEquals("*x*", r.text)
        assertEquals("x", MarkdownFormatter.apply(r, FormatAction.ITALIC).text)
    }

    @Test
    fun underlineStrikeAndCode() {
        assertEquals("<u>a</u>", MarkdownFormatter.apply(edit("a", 0, 1), FormatAction.UNDERLINE).text)
        assertEquals("~~a~~", MarkdownFormatter.apply(edit("a", 0, 1), FormatAction.STRIKETHROUGH).text)
        assertEquals("`a`", MarkdownFormatter.apply(edit("a", 0, 1), FormatAction.INLINE_CODE).text)
        assertEquals("a", MarkdownFormatter.apply(edit("<u>a</u>", 3, 4), FormatAction.UNDERLINE).text)
    }

    @Test
    fun banglaTextIsWrappedIntact() {
        val r = MarkdownFormatter.apply(edit("আমার নোট", 0, 4), FormatAction.BOLD)
        assertEquals("**আমার** নোট", r.text)
    }

    // ---- lines ----

    @Test
    fun headingAppliedToCurrentLine() {
        val r = MarkdownFormatter.apply(edit("one\ntwo\nthree", 5), FormatAction.H1)
        assertEquals("one\n# two\nthree", r.text)
        assertEquals(7, r.selectionStart)
    }

    @Test
    fun headingLevelReplacesOtherHeading() {
        assertEquals("## Title", MarkdownFormatter.apply(edit("# Title", 3), FormatAction.H2).text)
    }

    @Test
    fun headingToggleOff() {
        assertEquals("Title", MarkdownFormatter.apply(edit("# Title", 4), FormatAction.H1).text)
    }

    @Test
    fun bulletOnMultipleLinesSkipsBlank() {
        val r = MarkdownFormatter.apply(edit("a\n\nb", 0, 4), FormatAction.BULLET)
        assertEquals("- a\n\n- b", r.text)
        assertEquals(0, r.selectionStart)
        assertEquals(r.text.length, r.selectionEnd)
    }

    @Test
    fun bulletToggleOffWhenAllHaveIt() {
        assertEquals("a\nb", MarkdownFormatter.apply(edit("- a\n- b", 0, 7), FormatAction.BULLET).text)
    }

    @Test
    fun numberedListNumbersSequentially() {
        assertEquals("1. a\n2. b\n3. c", MarkdownFormatter.apply(edit("a\nb\nc", 0, 5), FormatAction.NUMBERED).text)
    }

    @Test
    fun taskReplacesBullet() {
        assertEquals("- [ ] buy milk", MarkdownFormatter.apply(edit("- buy milk", 5), FormatAction.TASK).text)
    }

    @Test
    fun bulletOnTaskLineConvertsToBullet() {
        assertEquals("- buy", MarkdownFormatter.apply(edit("- [ ] buy", 7), FormatAction.BULLET).text)
    }

    @Test
    fun quoteKeepsIndent() {
        assertEquals("  > hi", MarkdownFormatter.apply(edit("  hi", 3), FormatAction.QUOTE).text)
    }

    @Test
    fun selectionEndingAtLineStartExcludesNextLine() {
        val r = MarkdownFormatter.apply(edit("a\nb\nc", 0, 2), FormatAction.BULLET)
        assertEquals("- a\nb\nc", r.text)
    }

    @Test
    fun emptyDocumentHeading() {
        val r = MarkdownFormatter.apply(edit("", 0), FormatAction.H1)
        assertEquals("# ", r.text)
        assertEquals(2, r.selectionStart)
    }

    // ---- blocks ----

    @Test
    fun linkWithoutSelectionSelectsPlaceholder() {
        val r = MarkdownFormatter.apply(edit("", 0), FormatAction.LINK)
        assertEquals("[link text](https://)", r.text)
        assertEquals("link text", r.text.substring(r.selectionStart, r.selectionEnd))
    }

    @Test
    fun linkWithSelectionPutsCursorInUrl() {
        val r = MarkdownFormatter.apply(edit("see docs", 4, 8), FormatAction.LINK)
        assertEquals("see [docs](https://)", r.text)
        assertEquals(r.text.length - 1, r.selectionStart)
    }

    @Test
    fun codeBlockWrapsLines() {
        val r = MarkdownFormatter.apply(edit("val x = 1", 2), FormatAction.CODE_BLOCK)
        assertEquals("```\nval x = 1\n```", r.text)
    }

    @Test
    fun tableInsertedOnNewParagraph() {
        val r = MarkdownFormatter.apply(edit("intro", 5), FormatAction.TABLE)
        assertEquals("intro\n\n| Column 1 | Column 2 |\n| --- | --- |\n|  |  |", r.text)
        assertEquals(' ', r.text[r.selectionStart])
    }

    // ---- typing helpers ----

    private fun enter(text: String, pos: Int): TextEdit? {
        val old = TextEdit(text, pos)
        val new = TextEdit(text.substring(0, pos) + "\n" + text.substring(pos), pos + 1)
        return MarkdownFormatter.continueListOnEnter(old, new)
    }

    @Test
    fun enterContinuesBullet() {
        val r = enter("- milk", 6)!!
        assertEquals("- milk\n- ", r.text)
        assertEquals(9, r.selectionStart)
    }

    @Test
    fun enterContinuesNumbered() {
        assertEquals("1. a\n2. ", enter("1. a", 4)!!.text)
        assertEquals("9) a\n10) ", enter("9) a", 4)!!.text)
    }

    @Test
    fun enterContinuesTaskUnchecked() {
        assertEquals("- [x] done\n- [ ] ", enter("- [x] done", 10)!!.text)
    }

    @Test
    fun enterContinuesIndentAndQuote() {
        assertEquals("  - a\n  - ", enter("  - a", 5)!!.text)
        assertEquals("> q\n> ", enter("> q", 3)!!.text)
    }

    @Test
    fun enterOnEmptyItemEndsList() {
        val r = enter("- a\n- ", 6)!!
        assertEquals("- a\n", r.text)
        assertEquals(4, r.selectionStart)
    }

    @Test
    fun enterOutsideListDoesNothing() {
        assertNull(enter("plain", 5))
        assertNull(enter("- a", 0))
    }

    @Test
    fun nonEnterEditsAreIgnored() {
        val old = TextEdit("- a", 3)
        assertNull(MarkdownFormatter.continueListOnEnter(old, TextEdit("- ab", 4)))
    }

    @Test
    fun toggleTaskFlipsCheckbox() {
        val text = "intro\n- [ ] one\n  * [x] two"
        assertEquals("intro\n- [x] one\n  * [x] two", MarkdownFormatter.toggleTask(text, 1))
        assertEquals("intro\n- [ ] one\n  * [ ] two", MarkdownFormatter.toggleTask(text, 2))
        assertNull(MarkdownFormatter.toggleTask(text, 0))
        assertNull(MarkdownFormatter.toggleTask(text, 9))
    }

    @Test
    fun findIsCaseInsensitiveAndNonOverlapping() {
        assertEquals(listOf(0..1, 6..7), MarkdownFormatter.findMatches("Ab xx ab", "ab"))
        assertEquals(listOf(0..1, 2..3), MarkdownFormatter.findMatches("aaaa", "aa"))
        assertEquals(emptyList<IntRange>(), MarkdownFormatter.findMatches("abc", ""))
        assertEquals(2, MarkdownFormatter.findMatches("নোট নোট", "নোট").size)
    }
}
