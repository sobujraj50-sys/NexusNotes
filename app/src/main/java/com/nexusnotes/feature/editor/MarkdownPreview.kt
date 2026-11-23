package com.nexusnotes.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.nexusnotes.core.design.theme.spacing
import com.nexusnotes.domain.markdown.ListMarker
import com.nexusnotes.domain.markdown.MarkdownParser
import com.nexusnotes.domain.markdown.MdBlock
import com.nexusnotes.domain.markdown.MdInline

const val PREVIEW_TAG = "markdown_preview"

/** Links open only for these schemes; anything else is shown as plain text. */
private val SafeLinkSchemes = listOf("https://", "http://", "mailto:")

/**
 * Read-only rendering of a note's Markdown. Checkboxes can be toggled when [onToggleTask] is set.
 * Parsing happens once per text change, not per frame.
 */
@Composable
fun MarkdownPreview(
    markdown: String,
    onToggleTask: ((sourceLine: Int) -> Unit)?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    val spacing = MaterialTheme.spacing
    if (blocks.isEmpty()) {
        Box(modifier.padding(contentPadding).padding(spacing.lg)) {
            Text(
                "Nothing to preview yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        itemsIndexed(blocks) { _, block -> MarkdownBlockView(block, onToggleTask) }
    }
}

@Composable
private fun MarkdownBlockView(block: MdBlock, onToggleTask: ((Int) -> Unit)?) {
    val spacing = MaterialTheme.spacing
    val typography = MaterialTheme.typography
    when (block) {
        is MdBlock.Heading -> Text(
            text = rememberInline(block.inlines),
            style = when (block.level) {
                1 -> typography.headlineMedium
                2 -> typography.headlineSmall
                3 -> typography.titleLarge
                else -> typography.titleMedium
            },
            modifier = Modifier.padding(top = spacing.sm),
        )

        is MdBlock.Paragraph -> Text(rememberInline(block.inlines), style = typography.bodyLarge)

        is MdBlock.ListItem -> Row(
            modifier = Modifier.padding(start = (block.indent * 20).dp),
            verticalAlignment = if (block.marker is ListMarker.Task) Alignment.CenterVertically else Alignment.Top,
        ) {
            when (val marker = block.marker) {
                is ListMarker.Task -> Checkbox(
                    checked = marker.checked,
                    onCheckedChange = onToggleTask?.let { toggle -> { _: Boolean -> toggle(block.sourceLine) } },
                )
                is ListMarker.Ordered -> Text(
                    "${marker.number}.",
                    style = typography.bodyLarge,
                    modifier = Modifier.widthIn(min = 28.dp),
                )
                ListMarker.Bullet -> Text("•", style = typography.bodyLarge, modifier = Modifier.width(20.dp))
            }
            val done = (block.marker as? ListMarker.Task)?.checked == true
            Text(
                text = rememberInline(block.inlines),
                style = typography.bodyLarge.copy(
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                ),
                modifier = Modifier.weight(1f),
            )
        }

        is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
            Column(Modifier.padding(start = spacing.md)) {
                block.lines.forEach { line ->
                    Text(
                        rememberInline(line),
                        style = typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        is MdBlock.CodeBlock -> Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = block.code,
                fontFamily = FontFamily.Monospace,
                style = typography.bodyMedium,
                softWrap = false,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(spacing.md),
            )
        }

        is MdBlock.Table -> MarkdownTable(block)

        MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = spacing.sm))
    }
}

@Composable
private fun MarkdownTable(table: MdBlock.Table) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .horizontalScroll(rememberScrollState())
            .border(1.dp, outline, MaterialTheme.shapes.extraSmall),
    ) {
        TableRow(table.header, isHeader = true)
        table.rows.forEach { row ->
            HorizontalDivider(color = outline)
            TableRow(row, isHeader = false)
        }
    }
}

@Composable
private fun TableRow(cells: List<List<MdInline>>, isHeader: Boolean) {
    Row(
        Modifier
            .height(IntrinsicSize.Min)
            .then(if (isHeader) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh) else Modifier),
    ) {
        cells.forEachIndexed { index, cell ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
            Text(
                text = rememberInline(cell),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isHeader) FontWeight.SemiBold else null,
                ),
                modifier = Modifier
                    .widthIn(min = 96.dp, max = 240.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun rememberInline(inlines: List<MdInline>): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    return remember(inlines, linkColor, codeBackground) {
        buildAnnotatedString { appendInlines(inlines, linkColor, codeBackground) }
    }
}

private fun AnnotatedString.Builder.appendInlines(
    inlines: List<MdInline>,
    linkColor: Color,
    codeBackground: Color,
) {
    inlines.forEach { node ->
        when (node) {
            is MdInline.Text -> append(node.text)
            is MdInline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendInlines(node.children, linkColor, codeBackground)
            }
            is MdInline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendInlines(node.children, linkColor, codeBackground)
            }
            is MdInline.Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                appendInlines(node.children, linkColor, codeBackground)
            }
            is MdInline.Underline -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                appendInlines(node.children, linkColor, codeBackground)
            }
            is MdInline.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                append(node.text)
            }
            is MdInline.Link -> {
                val url = node.url
                if (SafeLinkSchemes.any { url.startsWith(it, ignoreCase = true) }) {
                    withLink(
                        LinkAnnotation.Url(
                            url,
                            TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                        ),
                    ) { appendInlines(node.children, linkColor, codeBackground) }
                } else {
                    appendInlines(node.children, linkColor, codeBackground)
                }
            }
        }
    }
}
