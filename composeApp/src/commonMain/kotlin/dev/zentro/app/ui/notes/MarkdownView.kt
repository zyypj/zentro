package dev.zentro.app.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import dev.zentro.shared.Markdown
import dev.zentro.shared.MdBlock
import dev.zentro.shared.MdSpan

/** Visualização da nota: Markdown renderizado com tarefas clicáveis, links entre notas e tags. */
@Composable
fun MarkdownView(
    content: String,
    existingTitles: Set<String>,
    onToggleTask: (Int) -> Unit,
    onOpenLink: (String) -> Unit,
    onTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(content) { Markdown.parse(content) }
    val colors = MaterialTheme.colorScheme
    val linkColor = colors.primary
    val missingColor = colors.primary.copy(alpha = 0.55f)
    val tagColor = Color(0xFF22C38E)
    val codeBg = colors.surfaceContainerHighest

    fun styled(text: String): AnnotatedString = buildAnnotatedString {
        for (span in Markdown.inline(text)) {
            when (span) {
                is MdSpan.Text -> append(span.text)
                is MdSpan.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
                is MdSpan.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(span.text) }
                is MdSpan.Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(span.text) }
                is MdSpan.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) { append(" ${span.text} ") }
                is MdSpan.Link -> {
                    val exists = span.target.trim().lowercase() in existingTitles
                    val style = SpanStyle(
                        color = if (exists) linkColor else missingColor,
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (exists) null else TextDecoration.Underline,
                    )
                    withLink(LinkAnnotation.Clickable("note:${span.target}", TextLinkStyles(style)) { onOpenLink(span.target) }) {
                        append(span.label)
                    }
                }
                is MdSpan.Tag -> withLink(
                    LinkAnnotation.Clickable("tag:${span.name}", TextLinkStyles(SpanStyle(color = tagColor, fontWeight = FontWeight.Medium))) { onTag(span.name.lowercase()) },
                ) { append("#${span.name}") }
                is MdSpan.Url -> withLink(LinkAnnotation.Url(span.url, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))) {
                    append(span.url)
                }
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Heading -> Text(
                    styled(block.text),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    modifier = Modifier.padding(top = if (block.level <= 2) 10.dp else 4.dp),
                )
                is MdBlock.Paragraph -> Text(styled(block.text), style = MaterialTheme.typography.bodyLarge)
                is MdBlock.Bullet -> ListLine(block.indent, "•") { Text(styled(block.text), style = MaterialTheme.typography.bodyLarge) }
                is MdBlock.Numbered -> ListLine(block.indent, "${block.number}.") { Text(styled(block.text), style = MaterialTheme.typography.bodyLarge) }
                is MdBlock.Task -> Row(
                    Modifier.padding(start = (block.indent * 20).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = block.done, onCheckedChange = { onToggleTask(block.line) }, modifier = Modifier.size(32.dp))
                    Text(
                        styled(block.text),
                        style = MaterialTheme.typography.bodyLarge.copy(textDecoration = if (block.done) TextDecoration.LineThrough else null),
                        modifier = Modifier.alpha(if (block.done) 0.5f else 1f).padding(start = 4.dp),
                    )
                }
                is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(colors.primary.copy(alpha = 0.6f)))
                    Text(
                        styled(block.text),
                        style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                is MdBlock.Code -> Text(
                    block.text,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(codeBg).padding(12.dp),
                )
                MdBlock.Divider -> HorizontalDivider(Modifier.padding(vertical = 8.dp), color = colors.outlineVariant)
            }
        }
    }
}

@Composable
private fun ListLine(indent: Int, marker: String, content: @Composable () -> Unit) {
    Row(Modifier.padding(start = (indent * 20).dp)) {
        Text(marker, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
        content()
    }
}
