package dev.zentro.app.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.zentro.shared.Notes
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
    loadImage: suspend (String) -> ImageBitmap?,
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
                is MdBlock.Table -> MdTable(block, ::styled)
                is MdBlock.Image -> NoteImage(block.src, block.alt, loadImage)
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

/** Tabela: cabeçalho destacado, linhas alternadas e rolagem lateral quando é larga demais. */
@Composable
private fun MdTable(table: MdBlock.Table, styled: (String) -> AnnotatedString) {
    val colors = MaterialTheme.colorScheme
    // Largura de cada coluna pelo texto mais longo dela (com limites para não ficar gigante).
    val widths = remember(table) {
        table.header.indices.map { col ->
            val longest = (listOf(table.header[col]) + table.rows.map { it[col] }).maxOf { Notes.plain(it).length }
            (longest * 8 + 28).coerceIn(72, 320).dp
        }
    }
    fun align(col: Int) = when (table.aligns[col]) {
        MdBlock.Align.CENTER -> TextAlign.Center
        MdBlock.Align.RIGHT -> TextAlign.End
        MdBlock.Align.LEFT -> TextAlign.Start
    }
    Box(
        Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
            .horizontalScroll(rememberScrollState()),
    ) {
        Column {
            Row(Modifier.background(colors.surfaceContainerHigh)) {
                table.header.forEachIndexed { col, cell ->
                    Text(
                        styled(cell),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = align(col),
                        modifier = Modifier.width(widths[col]).padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
            table.rows.forEachIndexed { index, row ->
                HorizontalDivider(color = colors.outlineVariant)
                Row(Modifier.background(if (index % 2 == 1) colors.surfaceContainerLow else Color.Transparent)) {
                    row.forEachIndexed { col, cell ->
                        Text(
                            styled(cell),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = align(col),
                            modifier = Modifier.width(widths[col]).padding(horizontal = 12.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Imagem da nota, carregada do aparelho ou do servidor. Toque para ver em tela cheia. */
@Composable
private fun NoteImage(src: String, alt: String, loadImage: suspend (String) -> ImageBitmap?) {
    var loading by remember(src) { mutableStateOf(true) }
    val bitmap by produceState<ImageBitmap?>(null, src) {
        value = runCatching { loadImage(src) }.getOrNull()
        loading = false
    }
    var zoom by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(vertical = 6.dp)) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = alt.ifBlank { null },
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { zoom = true },
            )
        } else {
            Box(
                Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (loading) "Carregando imagem…" else "Imagem indisponível agora",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        if (alt.isNotBlank()) {
            Text(alt, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
    val image = bitmap
    if (zoom && image != null) {
        Dialog(onDismissRequest = { zoom = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).clickable { zoom = false }, contentAlignment = Alignment.Center) {
                Image(image, alt.ifBlank { null }, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(16.dp))
            }
        }
    }
}
