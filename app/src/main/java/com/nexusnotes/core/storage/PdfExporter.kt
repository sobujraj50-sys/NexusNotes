package com.nexusnotes.core.storage

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.nexusnotes.domain.backup.ExportNote
import java.io.OutputStream
import java.text.DateFormat
import java.util.Date

/**
 * A4 PDF with Android's own PdfDocument (no library). Each note starts on a new page; long notes
 * flow over several pages. Text is shaped by Android, so Bangla renders correctly.
 * Markdown is printed as written (not styled); images are not embedded.
 */
object PdfExporter {
    private const val PAGE_W = 595 // A4 in points
    private const val PAGE_H = 842
    private const val MARGIN = 48f

    fun write(notes: List<ExportNote>, out: OutputStream) {
        val doc = PdfDocument()
        val width = (PAGE_W - 2 * MARGIN).toInt()
        val title = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val meta = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.DKGRAY }
        val body = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.BLACK }
        val footer = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = Color.GRAY }
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = MARGIN

        fun finishPage() {
            page?.let {
                it.canvas.drawText("Nexus Notes · $pageNo", MARGIN, PAGE_H - MARGIN / 2, footer)
                doc.finishPage(it)
            }
            page = null
        }

        fun newPage() {
            finishPage()
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            y = MARGIN
        }

        fun layout(text: CharSequence, paint: TextPaint): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .build()

        /** Draws a layout, continuing on new pages as needed. */
        fun draw(l: StaticLayout) {
            val bottom = PAGE_H - MARGIN
            var line = 0
            while (line < l.lineCount) {
                if (page == null) newPage()
                val top = l.getLineTop(line)
                var end = line
                while (end < l.lineCount && y + (l.getLineBottom(end) - top) <= bottom) end++
                if (end == line) {
                    if (y > MARGIN) { newPage(); continue }
                    end = line + 1 // a single line taller than a page: draw it anyway
                }
                val c = page!!.canvas
                c.save()
                c.translate(MARGIN, y - top)
                c.clipRect(0f, top.toFloat(), width.toFloat(), l.getLineBottom(end - 1).toFloat())
                l.draw(c)
                c.restore()
                y += l.getLineBottom(end - 1) - top
                line = end
            }
        }

        if (notes.isEmpty()) {
            newPage()
            draw(layout("No notes to export.", body))
        }
        notes.forEach { n ->
            newPage()
            draw(layout(n.title.ifBlank { "Untitled" }, title))
            y += 4
            val info = buildString {
                append("Updated ").append(dateFormat.format(Date(n.updatedAt)))
                n.folderPath?.let { append(" · ").append(it) }
                if (n.tags.isNotEmpty()) append(" · #").append(n.tags.joinToString(" #"))
            }
            draw(layout(info, meta))
            y += 14
            if (n.body.isNotEmpty()) draw(layout(n.body, body))
            if (n.attachmentNames.isNotEmpty()) {
                y += 10
                draw(layout("Attachments: " + n.attachmentNames.joinToString(", "), meta))
            }
        }
        finishPage()
        doc.writeTo(out)
        doc.close()
    }
}
