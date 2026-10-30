package dev.zentro.app.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zentro.app.ui.LocalWide
import dev.zentro.app.ui.ScrollableColumn
import dev.zentro.app.ui.ZColors
import dev.zentro.app.ui.ZIcons
import dev.zentro.shared.Note
import dev.zentro.shared.Notes
import dev.zentro.shared.TextSearch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val emojis = listOf("📝", "💼", "🎓", "🚀", "💡", "📚", "🧠", "✅", "📅", "🤝", "💰", "❤️", "🏋️", "🎯", "🔧", "🎨", "🧪", "📌", "⭐", "🔥", "🌱", "✈️", "🏠", "🎵")

/**
 * Editor de uma nota. Abre já em visualização (Markdown renderizado) quando a nota tem conteúdo;
 * toque em editar para mudar. Salva sozinho enquanto você escreve.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteEditor(
    note: Note,
    allNotes: List<Note>,
    onSave: (Note) -> Unit,
    onDelete: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenNote: (Note) -> Unit,
    onTag: (String) -> Unit,
    media: NoteMedia,
    onBack: (() -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    var mediaStatus by remember { mutableStateOf<String?>(null) }
    var title by remember(note.id) { mutableStateOf(note.title) }
    var content by remember(note.id) { mutableStateOf(TextFieldValue(note.content)) }
    var folder by remember(note.id) { mutableStateOf(note.folder) }
    var icon by remember(note.id) { mutableStateOf(note.icon) }
    var pinned by remember(note.id) { mutableStateOf(note.pinned) }
    var editing by remember(note.id) { mutableStateOf(note.content.isBlank()) }
    var pickingFolder by remember { mutableStateOf(false) }
    var pickingIcon by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // ---------- rolagem, números de linha e buscar/substituir ----------
    val editorScroll = rememberScrollState()
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var fieldTop by remember { mutableStateOf(0f) }
    var viewportTop by remember { mutableStateOf(0f) }
    var viewportHeight by remember { mutableStateOf(0f) }
    var findOpen by remember { mutableStateOf(false) }
    var replaceOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var matchIndex by remember { mutableStateOf(0) }
    var findStatus by remember { mutableStateOf<String?>(null) }
    var findFocusTick by remember { mutableStateOf(0) }
    val findFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    val matches = remember(content.text, query) { TextSearch.matches(content.text, query) }
    val shownMatch = if (matches.isEmpty()) -1 else matchIndex.coerceIn(0, matches.lastIndex)

    val current = note.copy(title = title.trim(), content = content.text, folder = folder, icon = icon, pinned = pinned)
    val latest by rememberUpdatedState(current)
    var saved by remember(note.id) { mutableStateOf(note) }

    fun saveIfChanged() {
        val n = latest
        if (n.title != saved.title || n.content != saved.content || n.folder != saved.folder || n.icon != saved.icon || n.pinned != saved.pinned) {
            saved = n
            onSave(n)
        }
    }

    // Salvamento automático: 600 ms depois de parar de digitar, e ao sair da nota.
    LaunchedEffect(current) {
        delay(600)
        saveIfChanged()
    }
    DisposableEffect(note.id) { onDispose { saveIfChanged() } }

    val existingTitles = remember(allNotes) { allNotes.map { it.title.trim().lowercase() }.toSet() }
    val backlinks = remember(allNotes, note.id, title) { Notes.backlinks(allNotes, current) }
    val tags = remember(content.text) { Notes.tags(content.text).sorted() }
    val pendingLink = if (editing) Notes.pendingLink(content.text.take(content.selection.start)) else null
    val suggestions = remember(pendingLink, allNotes) {
        pendingLink?.let { Notes.linkSuggestions(allNotes, it, excludeId = note.id) }.orEmpty()
    }

    fun completeLink(target: String) {
        val cursor = content.selection.start
        val before = content.text.take(cursor)
        val start = before.lastIndexOf("[[")
        val after = content.text.drop(cursor).let { if (it.startsWith("]]")) it.drop(2) else it }
        val newText = before.take(start) + "[[" + target + "]]" + after
        content = TextFieldValue(newText, TextRange(start + target.length + 4))
    }

    /** Rola a nota até o trecho em [offset], se ele estiver fora da tela. */
    fun reveal(offset: Int) {
        val layout = textLayout ?: return
        if (offset > layout.layoutInput.text.length) return
        val y = fieldTop + layout.getLineTop(layout.getLineForOffset(offset)) - viewportTop
        if (y < 0f || y > viewportHeight - 80f) {
            scope.launch {
                editorScroll.animateScrollTo((editorScroll.value + y - viewportHeight / 3).toInt().coerceIn(0, editorScroll.maxValue))
            }
        }
    }

    fun selectMatch(text: String, found: List<IntRange>, index: Int) {
        if (found.isEmpty()) return
        val i = ((index % found.size) + found.size) % found.size
        matchIndex = i
        content = TextFieldValue(text, TextRange(found[i].first, found[i].last + 1))
        reveal(found[i].first)
    }

    fun openFind(withReplace: Boolean) {
        val sel = content.selection
        if (!sel.collapsed) {
            val picked = content.text.substring(sel.min, sel.max)
            if (picked.isNotBlank() && '\n' !in picked) query = picked.take(200)
        }
        editing = true
        findOpen = true
        if (withReplace) replaceOpen = true
        findStatus = null
        findFocusTick++
    }

    fun closeFind() {
        findOpen = false
        replaceOpen = false
        findStatus = null
    }

    fun replaceCurrent() {
        val range = matches.getOrNull(shownMatch) ?: return
        val (text, cursor) = TextSearch.replaceOne(content.text, range, replacement)
        val next = TextSearch.matches(text, query)
        content = TextFieldValue(text, TextRange(cursor))
        findStatus = null
        if (next.isNotEmpty()) selectMatch(text, next, TextSearch.nearest(next, cursor))
    }

    fun replaceEverything() {
        val (text, count) = TextSearch.replaceAll(content.text, query, replacement)
        if (count == 0) return
        content = TextFieldValue(text, TextRange(content.selection.min.coerceAtMost(text.length)))
        findStatus = if (count == 1) "1 trocada" else "$count trocadas"
    }

    // Ao digitar a busca, pula para a ocorrência mais perto do cursor.
    LaunchedEffect(query) {
        findStatus = null
        if (findOpen && matches.isNotEmpty()) selectMatch(content.text, matches, TextSearch.nearest(matches, content.selection.min))
    }
    LaunchedEffect(findFocusTick) {
        if (findFocusTick > 0) runCatching { findFocus.requestFocus() }
    }
    // Lendo a nota (sem foco em nenhum campo), os atalhos ainda precisam chegar aqui.
    LaunchedEffect(note.id) {
        if (!editing) runCatching { rootFocus.requestFocus() }
    }

    Column(
        Modifier.fillMaxSize().focusRequester(rootFocus).focusable().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val ctrl = e.isCtrlPressed || e.isMetaPressed
            when {
                ctrl && e.key == Key.F -> { openFind(withReplace = false); true }
                ctrl && (e.key == Key.R || e.key == Key.H) -> { openFind(withReplace = true); true }
                e.key == Key.Escape && findOpen -> { closeFind(); true }
                else -> false
            }
        },
    ) {
        // ---------- barra superior ----------
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = {
                    saveIfChanged()
                    onBack()
                }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar") }
            }
            Text(
                "📁 ${folder.ifEmpty { Notes.DEFAULT_FOLDER }}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(MaterialTheme.shapes.small).clickable { pickingFolder = true }.padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { if (findOpen) closeFind() else openFind(withReplace = false) }) {
                Icon(
                    Icons.Rounded.Search,
                    "Buscar na nota (Ctrl+F)",
                    tint = if (findOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { pinned = !pinned }) {
                Icon(Icons.Rounded.Star, if (pinned) "Desafixar" else "Fixar", tint = if (pinned) ZColors.Warning else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalIconButton(onClick = { editing = !editing }) {
                Icon(if (editing) ZIcons.Eye else Icons.Rounded.Edit, if (editing) "Visualizar" else "Editar")
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Rounded.Delete, "Excluir", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (findOpen) {
            FindBar(
                query = query,
                onQuery = { query = it },
                replacement = replacement,
                onReplacement = { replacement = it },
                replaceOpen = replaceOpen,
                onToggleReplace = { replaceOpen = !replaceOpen },
                position = shownMatch,
                total = matches.size,
                status = findStatus,
                onNext = { selectMatch(content.text, matches, shownMatch + 1) },
                onPrev = { selectMatch(content.text, matches, shownMatch - 1) },
                onReplace = ::replaceCurrent,
                onReplaceAll = ::replaceEverything,
                onClose = ::closeFind,
                focusRequester = findFocus,
            )
        }

        val wide = LocalWide.current
        ScrollableColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().onGloballyPositioned {
                viewportTop = it.positionInWindow().y
                viewportHeight = it.size.height.toFloat()
            },
            contentModifier = Modifier.imePadding().padding(horizontal = if (wide) 40.dp else 20.dp),
            state = editorScroll,
        ) {
            Box(Modifier.widthIn(max = 820.dp)) {
                Column {
                    // ---------- ícone e título ----------
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            icon.ifEmpty { "📝" },
                            fontSize = 30.sp,
                            modifier = Modifier.clip(MaterialTheme.shapes.small).clickable { pickingIcon = true }.padding(4.dp),
                        )
                        BasicTextField(
                            value = title,
                            onValueChange = { title = it.replace("\n", "") },
                            textStyle = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                            decorationBox = { inner ->
                                if (title.isEmpty()) {
                                    Text("Sem título", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                }
                                inner()
                            },
                        )
                    }

                    if (tags.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            tags.forEach { tag -> TagPill(tag) { onTag(tag) } }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // ---------- conteúdo ----------
                    if (editing) {
                        // Imagem sempre numa linha própria, para aparecer grande na nota.
                        fun insertBlock(line: String) {
                            val at = content.selection.start
                            val before = content.text.take(at)
                            val after = content.text.drop(at)
                            val prefix = if (before.isEmpty() || before.endsWith("\n")) "" else "\n"
                            val suffix = if (after.startsWith("\n")) "" else "\n"
                            content = TextFieldValue(before + prefix + line + suffix + after, TextRange(at + prefix.length + line.length + suffix.length))
                        }

                        fun addImage(source: suspend () -> Result<String>?) {
                            scope.launch {
                                mediaStatus = "Enviando imagem…"
                                val result = source()
                                mediaStatus = when {
                                    result == null -> null
                                    result.isSuccess -> null.also { insertBlock(result.getOrThrow()) }
                                    else -> "Não consegui enviar a imagem. Confira a internet e tente de novo."
                                }
                            }
                        }

                        MarkdownToolbar(content, onImage = { addImage(media.pick) }) { content = it }
                        mediaStatus?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        }
                        if (pendingLink != null) {
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                suggestions.forEach { s -> SuggestionChip(onClick = { completeLink(s.title) }, label = { Text("${s.icon.ifEmpty { "📝" }} ${s.title}") }) }
                                if (pendingLink.isNotBlank() && suggestions.none { it.title.equals(pendingLink.trim(), ignoreCase = true) }) {
                                    SuggestionChip(onClick = { completeLink(pendingLink.trim()) }, label = { Text("+ Criar \"${pendingLink.trim()}\"") })
                                }
                            }
                        }
                        // Números de linha à esquerda (no PC). Cada linha do texto conta uma vez,
                        // mesmo quando quebra em várias na tela, como nos editores de código.
                        val gutter = if (wide) 44.dp else 0.dp
                        val measurer = rememberTextMeasurer()
                        val cursorLine = remember(content.text, content.selection.start) {
                            content.text.take(content.selection.start).count { it == '\n' }
                        }
                        val numberStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        val numberColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        val currentColor = MaterialTheme.colorScheme.primary
                        val ruleColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        val strongMark = ZColors.Warning.copy(alpha = 0.55f)
                        val weakMark = ZColors.Warning.copy(alpha = 0.22f)
                        val highlight = remember(findOpen, matches, shownMatch, strongMark, weakMark) {
                            if (!findOpen || matches.isEmpty()) {
                                VisualTransformation.None
                            } else {
                                VisualTransformation { text ->
                                    val marked = buildAnnotatedString {
                                        append(text)
                                        matches.forEachIndexed { i, r ->
                                            if (r.last < text.length) {
                                                addStyle(SpanStyle(background = if (i == shownMatch) strongMark else weakMark), r.first, r.last + 1)
                                            }
                                        }
                                    }
                                    TransformedText(marked, OffsetMapping.Identity)
                                }
                            }
                        }
                        val topPad = 8.dp
                        BasicTextField(
                            value = content,
                            onValueChange = { content = it },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, lineHeight = 26.sp),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            visualTransformation = highlight,
                            onTextLayout = { textLayout = it },
                            modifier = Modifier.fillMaxWidth().drawBehind {
                                val layout = textLayout ?: return@drawBehind
                                if (gutter.value <= 0f) return@drawBehind
                                val gutterPx = gutter.toPx()
                                val top = topPad.toPx()
                                drawLine(ruleColor, Offset(gutterPx - 8.dp.toPx(), top), Offset(gutterPx - 8.dp.toPx(), size.height), strokeWidth = 1f)
                                val text = layout.layoutInput.text.text
                                var start = 0
                                var line = 0
                                while (true) {
                                    val visual = layout.getLineForOffset(start)
                                    val lineTop = layout.getLineTop(visual)
                                    val lineHeight = layout.getLineBottom(visual) - lineTop
                                    val number = measurer.measure(
                                        (line + 1).toString(),
                                        numberStyle.copy(color = if (line == cursorLine) currentColor else numberColor),
                                    )
                                    drawText(
                                        number,
                                        topLeft = Offset(
                                            gutterPx - 16.dp.toPx() - number.size.width,
                                            top + lineTop + (lineHeight - number.size.height) / 2,
                                        ),
                                    )
                                    val next = text.indexOf('\n', start)
                                    if (next < 0) break
                                    start = next + 1
                                    line++
                                }
                            }.heightIn(min = 320.dp).padding(start = gutter, top = topPad).onGloballyPositioned {
                                fieldTop = it.positionInWindow().y
                            }.onPreviewKeyEvent { event ->
                                // Ctrl+V com uma imagem copiada (ex.: um print): envia e insere na nota.
                                val paste = event.type == KeyEventType.KeyDown && event.key == Key.V && (event.isCtrlPressed || event.isMetaPressed)
                                if (paste && media.hasClipboardImage()) {
                                    addImage(media.paste)
                                    true
                                } else {
                                    false
                                }
                            },
                            decorationBox = { inner ->
                                if (content.text.isEmpty()) {
                                    Text(
                                        "Comece a escrever… Para pôr uma imagem, cole com Ctrl+V ou use o botão 🖼.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                                inner()
                            },
                        )
                    } else {
                        if (content.text.isBlank()) {
                            Text(
                                "Nota vazia. Toque no lápis para escrever.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { editing = true }.padding(vertical = 12.dp),
                            )
                        }
                        MarkdownView(
                            content = content.text,
                            existingTitles = existingTitles,
                            onToggleTask = { line -> content = TextFieldValue(Notes.toggleTask(content.text, line)) },
                            onOpenLink = { saveIfChanged(); onOpenLink(it) },
                            onTag = onTag,
                            loadImage = media.load,
                        )
                    }

                    // ---------- notas que citam esta ----------
                    if (backlinks.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(top = 28.dp, bottom = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            "MENCIONADA EM ${backlinks.size} NOTA(S)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        backlinks.forEach { other ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(MaterialTheme.shapes.medium).clickable {
                                    saveIfChanged()
                                    onOpenNote(other)
                                },
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("${other.icon.ifEmpty { "📝" }} ${other.title.ifEmpty { "Sem título" }}", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        Notes.preview(other.content),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }

    if (pickingFolder) {
        FolderPicker(
            folders = Notes.folders(allNotes),
            current = folder,
            onPick = { folder = it },
            onDismiss = { pickingFolder = false },
        )
    }
    if (pickingIcon) {
        AlertDialog(
            onDismissRequest = { pickingIcon = false },
            title = { Text("Ícone da nota") },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    emojis.forEach { e ->
                        Text(e, fontSize = 26.sp, modifier = Modifier.clip(MaterialTheme.shapes.small).clickable {
                            icon = e
                            pickingIcon = false
                        }.padding(6.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingIcon = false }) { Text("Fechar") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir esta nota?") },
            text = { Text("\"${title.ifEmpty { "Sem título" }}\" será apagada de todos os aparelhos.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    saved = latest // evita salvar de novo ao sair
                    onDelete(note.id)
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun TagPill(tag: String, onClick: () -> Unit) {
    Text(
        "#$tag",
        style = MaterialTheme.typography.labelMedium,
        color = ZColors.Income,
        modifier = Modifier.clip(MaterialTheme.shapes.small).background(ZColors.Income.copy(alpha = 0.12f)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Escolhe uma pasta existente ou cria uma nova (subpastas com "/", ex.: Faculdade/Cálculo). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderPicker(folders: List<String>, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var newFolder by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mover para pasta") },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf(Notes.DEFAULT_FOLDER) + folders).distinct().forEach { f ->
                        AssistChip(
                            onClick = {
                                onPick(f)
                                onDismiss()
                            },
                            label = { Text(if (f == current) "✓ $f" else f) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = newFolder,
                    onValueChange = { newFolder = it },
                    label = { Text("Nova pasta (ex.: Faculdade/Cálculo)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = newFolder.isNotBlank(), onClick = {
                onPick(newFolder.trim().trim('/'))
                onDismiss()
            }) { Text("Criar e mover") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Botões que inserem a sintaxe do Markdown para quem não quer decorar nada. */
@Composable
private fun MarkdownToolbar(value: TextFieldValue, onImage: () -> Unit, onChange: (TextFieldValue) -> Unit) {
    fun wrap(prefix: String, suffix: String = prefix) {
        val sel = value.selection
        val selected = value.text.substring(sel.min, sel.max)
        val text = value.text.replaceRange(sel.min, sel.max, prefix + selected + suffix)
        val cursor = if (selected.isEmpty()) sel.min + prefix.length else sel.min + prefix.length + selected.length + suffix.length
        onChange(TextFieldValue(text, TextRange(cursor)))
    }

    fun linePrefix(prefix: String) {
        val start = value.text.lastIndexOf('\n', (value.selection.min - 1).coerceAtLeast(0)).let { if (value.selection.min == 0) 0 else it + 1 }
        val text = value.text.substring(0, start) + prefix + value.text.substring(start)
        onChange(TextFieldValue(text, TextRange(value.selection.min + prefix.length)))
    }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ToolButton("H1") { linePrefix("# ") }
        ToolButton("H2") { linePrefix("## ") }
        ToolButton("B") { wrap("**") }
        ToolButton("I") { wrap("*") }
        ToolButton("•") { linePrefix("- ") }
        ToolButton("☐") { linePrefix("- [ ] ") }
        ToolButton("[[ ]]") { wrap("[[", "]]") }
        ToolButton("🖼", onClick = onImage)
        ToolButton("▦") { wrap("\n| Coluna 1 | Coluna 2 |\n|---|---|\n| ", " |  |\n") }
        ToolButton("#") { wrap("#", "") }
        ToolButton("❝") { linePrefix("> ") }
        ToolButton("</>") { wrap("`") }
        ToolButton("—") { wrap("\n---\n", "") }
    }
}

@Composable
private fun ToolButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.size(width = 48.dp, height = 40.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}
