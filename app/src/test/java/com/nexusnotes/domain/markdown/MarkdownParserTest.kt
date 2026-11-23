package com.nexusnotes.domain.markdown

import com.nexusnotes.domain.markdown.MdInline.Bold
import com.nexusnotes.domain.markdown.MdInline.Code
import com.nexusnotes.domain.markdown.MdInline.Italic
import com.nexusnotes.domain.markdown.MdInline.Link
import com.nexusnotes.domain.markdown.MdInline.Strike
import com.nexusnotes.domain.markdown.MdInline.Text
import com.nexusnotes.domain.markdown.MdInline.Underline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    private fun inline(s: String) = MarkdownParser.parseInline(s)

    @Test
    fun plainText() {
        assertEquals(listOf(Text("hello world")), inline("hello world"))
    }

    @Test
    fun boldItalicStrikeUnderlineCode() {
        assertEquals(listOf(Text("a "), Bold(listOf(Text("b"))), Text(" c")), inline("a **b** c"))
        assertEquals(listOf(Italic(listOf(Text("i")))), inline("*i*"))
        assertEquals(listOf(Italic(listOf(Text("i")))), inline("_i_"))
        assertEquals(listOf(Strike(listOf(Text("s")))), inline("~~s~~"))
        assertEquals(listOf(Underline(listOf(Text("u")))), inline("<u>u</u>"))
        assertEquals(listOf(Code("x **y**")), inline("`x **y**`"))
    }

    @Test
    fun boldItalicCombined() {
        assertEquals(listOf(Bold(listOf(Italic(listOf(Text("x")))))), inline("***x***"))
        assertEquals(
            listOf(Bold(listOf(Text("a "), Italic(listOf(Text("b")))))),
            inline("**a *b***"),
        )
        assertEquals(
            listOf(Italic(listOf(Text("a "), Bold(listOf(Text("b"))), Text(" c")))),
            inline("*a **b** c*"),
        )
    }

    @Test
    fun snakeCaseIsNotItalic() {
        assertEquals("snake_case_name", MarkdownParser.plainText(inline("snake_case_name")))
        assertEquals(listOf(Text("snake_case_name")), inline("snake_case_name"))
    }

    @Test
    fun unmatchedMarkersStayAsText() {
        assertEquals("2 * 3 = 6 and **open", MarkdownParser.plainText(inline("2 * 3 = 6 and **open")))
    }

    @Test
    fun escapes() {
        assertEquals(listOf(Text("*not italic*")), inline("\\*not italic\\*"))
    }

    @Test
    fun links() {
        assertEquals(
            listOf(Text("see "), Link(listOf(Text("site")), "https://example.com"), Text("!")),
            inline("see [site](https://example.com)!"),
        )
        assertEquals(listOf(Text("[not a link]")), inline("[not a link]"))
    }

    @Test
    fun banglaInline() {
        assertEquals(listOf(Bold(listOf(Text("আমার"))), Text(" নোট")), inline("**আমার** নোট"))
    }

    @Test
    fun headingsAndParagraphs() {
        val blocks = MarkdownParser.parse("# Title\n\nline one\nline two\n\n### Sub ##")
        assertEquals(MdBlock.Heading(1, listOf(Text("Title"))), blocks[0])
        assertEquals(MdBlock.Paragraph(listOf(Text("line one\nline two"))), blocks[1])
        assertEquals(MdBlock.Heading(3, listOf(Text("Sub"))), blocks[2])
        assertEquals(3, blocks.size)
    }

    @Test
    fun hashWithoutSpaceIsNotHeading() {
        assertTrue(MarkdownParser.parse("#hashtag")[0] is MdBlock.Paragraph)
    }

    @Test
    fun listsWithSourceLines() {
        val blocks = MarkdownParser.parse("intro\n- a\n  - b\n1. one\n- [x] done\n- [ ] todo")
        assertEquals(MdBlock.Paragraph(listOf(Text("intro"))), blocks[0])
        assertEquals(MdBlock.ListItem(0, ListMarker.Bullet, listOf(Text("a")), 1), blocks[1])
        assertEquals(MdBlock.ListItem(1, ListMarker.Bullet, listOf(Text("b")), 2), blocks[2])
        assertEquals(MdBlock.ListItem(0, ListMarker.Ordered(1), listOf(Text("one")), 3), blocks[3])
        assertEquals(MdBlock.ListItem(0, ListMarker.Task(true), listOf(Text("done")), 4), blocks[4])
        assertEquals(MdBlock.ListItem(0, ListMarker.Task(false), listOf(Text("todo")), 5), blocks[5])
    }

    @Test
    fun emptyListItem() {
        assertEquals(MdBlock.ListItem(0, ListMarker.Bullet, emptyList(), 0), MarkdownParser.parse("- ")[0])
    }

    @Test
    fun quoteBlock() {
        val blocks = MarkdownParser.parse("> one\n> two\nafter")
        assertEquals(MdBlock.Quote(listOf(listOf(Text("one")), listOf(Text("two")))), blocks[0])
        assertEquals(MdBlock.Paragraph(listOf(Text("after"))), blocks[1])
    }

    @Test
    fun codeBlockKeepsContentVerbatim() {
        val blocks = MarkdownParser.parse("```kotlin\nval x = **1**\n\n# not heading\n```\ntext")
        assertEquals(MdBlock.CodeBlock("kotlin", "val x = **1**\n\n# not heading"), blocks[0])
        assertEquals(MdBlock.Paragraph(listOf(Text("text"))), blocks[1])
    }

    @Test
    fun unclosedCodeBlockRunsToEnd() {
        assertEquals(MdBlock.CodeBlock("", "a\nb"), MarkdownParser.parse("```\na\nb")[0])
    }

    @Test
    fun table() {
        val blocks = MarkdownParser.parse("| A | B |\n| --- | :-: |\n| 1 | **2** |\n| 3 |")
        val table = blocks[0] as MdBlock.Table
        assertEquals(listOf(listOf(Text("A")), listOf(Text("B"))), table.header)
        assertEquals(listOf(listOf(Text("1")), listOf(Bold(listOf(Text("2"))))), table.rows[0])
        assertEquals(listOf(listOf(Text("3")), emptyList<MdInline>()), table.rows[1])
    }

    @Test
    fun ruleAndDashListDistinguished() {
        assertEquals(MdBlock.Rule, MarkdownParser.parse("---")[0])
        assertEquals(MdBlock.Rule, MarkdownParser.parse("* * *")[0])
        assertTrue(MarkdownParser.parse("- item")[0] is MdBlock.ListItem)
    }

    @Test
    fun windowsLineEndings() {
        assertEquals(MdBlock.Heading(1, listOf(Text("T"))), MarkdownParser.parse("# T\r\nx")[0])
    }

    @Test
    fun emptyInputGivesNoBlocks() {
        assertTrue(MarkdownParser.parse("").isEmpty())
        assertTrue(MarkdownParser.parse("\n\n  \n").isEmpty())
    }

    @Test
    fun parserNeverDropsText() {
        val src = "Hello *world* with [link](x) and `code` ~~gone~~ <u>u</u>"
        assertEquals("Hello world with link and code gone u", MarkdownParser.plainText((MarkdownParser.parse(src)[0] as MdBlock.Paragraph).inlines))
    }
}
