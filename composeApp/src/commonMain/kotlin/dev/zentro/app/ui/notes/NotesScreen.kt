package dev.zentro.app.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zentro.app.data.newId
import dev.zentro.app.ui.AdaptiveSheet
import dev.zentro.app.ui.FilterPill
import dev.zentro.app.ui.HLine
import dev.zentro.app.ui.IconTile
import dev.zentro.app.ui.InterFamily
import dev.zentro.app.ui.Line
import dev.zentro.app.ui.LinkAction
import dev.zentro.app.ui.LocalWide
import dev.zentro.app.ui.MobileHeader
import dev.zentro.app.ui.Panel
import dev.zentro.app.ui.ScrollableColumn
import dev.zentro.app.ui.SectionHeader
import dev.zentro.app.ui.SquarePlus
import dev.zentro.app.ui.ZPalette
import dev.zentro.app.ui.ZText
import dev.zentro.app.ui.editedText
import dev.zentro.shared.Note
import dev.zentro.shared.NoteTemplates
import dev.zentro.shared.Notes
import dev.zentro.shared.Schedule
import kotlinx.datetime.TimeZone

private enum class NotesView(val label: String) { ALL("Todas"), PINNED("Fixadas"), TASKS("Tarefas"), GRAPH("Mapa") }

/**
 * Anotações. A pasta escolhida e a nota aberta ficam no App, porque a barra lateral do desktop
 * e a busca global também abrem pastas e notas.
 */
@Composable
fun NotesScreen(
    folder: String?,
    onFolder: (String?) -> Unit,
    openId: String?,
    onOpenId: (String?) -> Unit,
    notes: List<Note>,
    tz: TimeZone,
    now: Long,
    onSave: (Note) -> Unit,
    onDelete: (String) -> Unit,
    onMessage: (String) -> Unit,
    media: NoteMedia,
) {
    val wide = LocalWide.current
    var view by remember { mutableStateOf(NotesView.ALL) }
    var tag by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var choosingTemplate by remember { mutableStateOf(false) }
    var allFolders by remember { mutableStateOf(false) }

    val byId = remember(notes) { notes.associateBy { it.id } }
    val openNote = openId?.let { byId[it] }
    val folders = remember(notes) {
        Notes.folders(notes).filter { '/' !in it }.map { f -> f to notes.count { Notes.inFolder(it, f) } }
    }
    val filtered = remember(notes, view, folder, tag, query) {
        val base = notes.filter { n ->
            (folder == null || Notes.inFolder(n, folder)) &&
                (tag == null || tag in Notes.tags(n.content)) &&
                (view != NotesView.PINNED || n.pinned)
        }
        if (query.isBlank()) base.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt }).map { it to Notes.preview(it.content) }
        else Notes.search(base, query).map { it.note to it.snippet }
    }

    fun create(content: String, title: String = "", folderName: String? = null, icon: String = "") {
        val note = Note(
            id = newId(),
            title = title,
            content = content,
            folder = folderName ?: folder ?: Notes.DEFAULT_FOLDER,
            icon = icon,
            createdAt = Schedule.now(),
        )
        onSave(note)
        onOpenId(note.id)
        view = NotesView.ALL
    }

    /** Clique num [[link]]: abre a nota com esse título, ou cria na hora (como no Obsidian). */
    fun openByTitle(title: String) {
        val existing = Notes.findByTitle(notes, title)
        if (existing != null) onOpenId(existing.id)
        else {
            create("", title = title.trim(), folderName = openNote?.folder)
            onMessage("Nota \"${title.trim()}\" criada")
        }
    }

    fun openDaily() {
        val today = Schedule.today(tz, now)
        val existing = notes.firstOrNull { it.title == Notes.dailyTitle(today) && it.folder == Notes.DAILY_FOLDER }
        if (existing != null) onOpenId(existing.id)
        else create(NoteTemplates.daily(today), title = Notes.dailyTitle(today), folderName = Notes.DAILY_FOLDER, icon = "📅")
    }

    fun toggleTask(note: Note, line: Int) = onSave(note.copy(content = Notes.toggleTask(note.content, line)))

    val editor = @Composable { modifier: Modifier, note: Note, back: (() -> Unit)? ->
        Box(modifier) {
            NoteEditor(
                note = note,
                allNotes = notes,
                onSave = onSave,
                onDelete = {
                    onDelete(it)
                    onOpenId(null)
                    onMessage("Nota excluída")
                },
                onOpenLink = ::openByTitle,
                onOpenNote = { onOpenId(it.id) },
                onTag = {
                    tag = it
                    view = NotesView.ALL
                    if (!wide) onOpenId(null)
                },
                media = media,
                onBack = back,
            )
        }
    }

    val searchField = @Composable {
        SearchField(query, { query = it }, if (folder != null) "Buscar em $folder" else "Buscar em Notas")
    }

    val viewPills = @Composable {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NotesView.entries.forEach { v ->
                FilterPill(v.label, view == v && tag == null, {
                    view = v
                    tag = null
                })
            }
            tag?.let { t -> FilterPill("#$t ✕", true, { tag = null }) }
        }
    }

    val noteList: @Composable ColumnScope.(Boolean) -> Unit = { compact ->
        if (filtered.isEmpty()) {
            ZText(
                if (query.isBlank()) "Nenhuma nota aqui. Toque em + para criar (há modelos de reunião, aula e projeto)." else "Nada encontrado. Tente outra palavra ou #tag.",
                12.5.sp, 400, ZPalette.Muted, Modifier.padding(vertical = 12.dp),
            )
        }
        filtered.forEachIndexed { i, (note, snippet) ->
            NoteCard(note, snippet, i, selected = note.id == openId, compact = compact, tz = tz, now = now, onToggleTask = { toggleTask(note, it) }) {
                onOpenId(note.id)
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                when (view) {
                    NotesView.TASKS, NotesView.GRAPH -> Column(Modifier.weight(1f).fillMaxHeight()) {
                        Row(Modifier.padding(start = 48.dp, end = 32.dp, top = 28.dp, bottom = 12.dp)) { viewPills() }
                        if (view == NotesView.TASKS) {
                            TasksView(notes, ::toggleTask, { onOpenId(it.id); view = NotesView.ALL }, Modifier.weight(1f).padding(start = 32.dp, end = 32.dp))
                        } else {
                            NoteGraph(notes, onOpen = { onOpenId(it.id); view = NotesView.ALL }, Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                    else -> {
                        ScrollableColumn(
                            Modifier.width(380.dp).fillMaxHeight()
                                .drawBehind { drawRect(ZPalette.Line, Offset(size.width - 1.dp.toPx(), 0f), Size(1.dp.toPx(), size.height)) },
                            Modifier.padding(start = 28.dp, end = 22.dp, top = 26.dp, bottom = 32.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    ZText(folder ?: "Notas", 22.sp, 700, tracking = -0.03, maxLines = 1)
                                    ZText("${filtered.size} nota${if (filtered.size == 1) "" else "s"}", 11.sp, 400, ZPalette.Quiet)
                                }
                                IconAction(Line.Calendar, "Nota de hoje", ::openDaily)
                                SquarePlus({ choosingTemplate = true }, "Nova nota")
                            }
                            Spacer(Modifier.height(16.dp))
                            searchField()
                            Spacer(Modifier.height(12.dp))
                            viewPills()
                            Spacer(Modifier.height(16.dp))
                            noteList(true)
                        }
                        if (openNote != null) {
                            editor(Modifier.weight(1f).fillMaxHeight(), openNote, null)
                        } else {
                            Column(
                                Modifier.weight(1f).fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                IconTile(Line.Note, ZPalette.CardHigh, ZPalette.Muted, size = 44.dp, iconSize = 22.dp)
                                Spacer(Modifier.height(14.dp))
                                ZText("Escolha uma nota", 15.sp, 650)
                                ZText("Ou crie uma nova com o +.", 12.sp, 400, ZPalette.Muted)
                            }
                        }
                    }
                }
            }
        } else {
            if (openNote != null) {
                editor(Modifier.fillMaxSize(), openNote) { onOpenId(null) }
            } else {
                ScrollableColumn(Modifier.fillMaxSize(), Modifier.padding(bottom = 24.dp)) {
                    MobileHeader(folder ?: "Notas") {
                        if (folder != null) IconAction(Line.Close, "Todas as notas") { onFolder(null) }
                        SquarePlus({ choosingTemplate = true }, "Nova nota")
                    }
                    Column(Modifier.padding(horizontal = 18.dp)) {
                        searchField()
                        if (folder == null && query.isBlank()) {
                            SectionHeader(
                                "Pastas", Modifier.padding(top = 22.dp, bottom = 12.dp), size = 15.sp,
                                action = if (folders.size > 3) (if (allFolders) "Menos" else "Ver todas") else null,
                                onAction = { allFolders = !allFolders },
                            )
                            (if (allFolders) folders else folders.take(3)).chunked(3).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                                    row.forEach { (name, count) -> FolderCard(name, count, Modifier.weight(1f)) { onFolder(name) } }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ZText(if (query.isBlank()) "Recentes" else "Resultados", 15.sp, 700, modifier = Modifier.weight(1f))
                            LinkAction("Nota de hoje", ::openDaily)
                        }
                        Spacer(Modifier.height(10.dp))
                        viewPills()
                        Spacer(Modifier.height(12.dp))
                        when (view) {
                            NotesView.TASKS -> TasksView(notes, ::toggleTask, { onOpenId(it.id) }, Modifier.fillMaxWidth())
                            NotesView.GRAPH -> NoteGraph(notes, onOpen = { onOpenId(it.id) }, Modifier.fillMaxWidth().height(420.dp))
                            else -> noteList(false)
                        }
                    }
                }
            }
        }
    }

    if (choosingTemplate) {
        val today = Schedule.today(tz, now)
        AdaptiveSheet(onDismiss = { choosingTemplate = false }) {
            ZText("Nova nota", 18.sp, 700)
            ZText(
                "Comece com um modelo pronto" + (folder?.let { " em \"$it\"" } ?: ""),
                13.sp, 400, ZPalette.Muted,
            )
            Spacer(Modifier.height(12.dp))
            NoteTemplates.all.forEach { template ->
                TemplateRow(template.icon, template.name, template.folder?.let { "Vai para $it" } ?: "Pasta atual") {
                    choosingTemplate = false
                    create(template.body(today), folderName = folder ?: template.folder, icon = if (template.name == "Em branco") "" else template.icon)
                }
            }
            TemplateRow("📅", "Nota de hoje", "Diário: foco, anotações e gratidão do dia") {
                choosingTemplate = false
                openDaily()
            }
        }
    }
}

@Composable
private fun IconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = ZPalette.Muted, modifier = Modifier.size(19.dp))
    }
}

/** Campo de busca das notas, no estilo do mockup. */
@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String) {
    Row(
        Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF161619))
            .border(1.dp, ZPalette.Line, RoundedCornerShape(10.dp)).padding(start = 13.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Line.Search, null, tint = ZPalette.Quiet, modifier = Modifier.size(16.dp))
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.replace("\n", "")) },
            singleLine = true,
            textStyle = TextStyle(fontFamily = InterFamily, fontSize = 13.sp, color = ZPalette.Ink),
            cursorBrush = SolidColor(ZPalette.Purple),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) ZText(placeholder, 13.sp, 400, ZPalette.Quiet, maxLines = 1)
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).clickable { onChange("") }, contentAlignment = Alignment.Center) {
                Icon(Line.Close, "Limpar", tint = ZPalette.Muted, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun FolderCard(name: String, count: Int, modifier: Modifier, onClick: () -> Unit) {
    Panel(modifier, onClick = onClick, padding = 12.dp) {
        Column {
            Icon(Line.Folder, null, tint = ZPalette.Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(10.dp))
            ZText(name, 12.5.sp, 650, maxLines = 1)
            ZText("$count nota${if (count == 1) "" else "s"}", 10.5.sp, 400, ZPalette.Quiet, maxLines = 1)
        }
    }
}

@Composable
private fun TemplateRow(icon: String, name: String, subtitle: String, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = onClick, bg = ZPalette.CardHigh, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 22.sp)
            Column(Modifier.padding(start = 14.dp)) {
                ZText(name, 13.5.sp, 650)
                ZText(subtitle, 11.sp, 400, ZPalette.Muted)
            }
        }
    }
}

/**
 * Uma nota na lista: fixadas em roxo com o alfinete; notas com tarefas mostram as primeiras
 * (dá para marcar ali mesmo); as outras mostram o começo do texto.
 */
@Composable
private fun NoteCard(
    note: Note,
    snippet: String,
    index: Int,
    selected: Boolean,
    compact: Boolean,
    tz: TimeZone,
    now: Long,
    onToggleTask: (Int) -> Unit,
    onClick: () -> Unit,
) {
    val tasks = remember(note.content) { Notes.tasks(note.content).filter { it.text.isNotBlank() } }
    val highlight = note.pinned || selected
    Panel(
        Modifier.fillMaxWidth(),
        onClick = onClick,
        bg = if (highlight) ZPalette.PurpleSoft else ZPalette.Card,
        border = if (highlight) ZPalette.PurpleLine else ZPalette.Line,
        padding = 14.dp,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (note.icon.isNotEmpty()) {
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(7.dp)).background(ZPalette.CardHigh), contentAlignment = Alignment.Center) {
                        Text(note.icon, fontSize = 15.sp)
                    }
                } else {
                    val warm = index % 3 == 1
                    IconTile(
                        if (tasks.isNotEmpty()) Line.Check else Line.Note,
                        if (highlight) Color(0xFF2E2850) else if (warm) Color(0xFF2A2518) else ZPalette.CardHigh,
                        if (highlight) ZPalette.PurpleText else if (warm) ZPalette.WarmAccent else ZPalette.Muted,
                    )
                }
                Column(Modifier.weight(1f)) {
                    ZText(note.title.ifEmpty { "Sem título" }, 13.5.sp, 650, maxLines = 1)
                    ZText("${note.folder} · ${editedText(note.updatedAt, now, tz).replaceFirstChar { it.uppercase() }}", 11.sp, 400, ZPalette.Muted, maxLines = 1)
                }
                Icon(
                    if (note.pinned) Line.Pin else Line.ChevronRight,
                    if (note.pinned) "Fixada" else null,
                    tint = if (note.pinned) ZPalette.PurpleText else ZPalette.Quiet,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (!compact || !highlight) {
                if (tasks.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    tasks.take(3).forEach { task ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 34.dp)) {
                            Checkbox(
                                checked = task.done,
                                onCheckedChange = { onToggleTask(task.line) },
                                modifier = Modifier.size(30.dp),
                                colors = CheckboxDefaults.colors(checkedColor = ZPalette.Purple, uncheckedColor = Color(0xFF55525C)),
                            )
                            ZText(Notes.plain(task.text), 12.sp, 400, if (task.done) ZPalette.Quiet else Color(0xFFC9C6D0), Modifier.padding(start = 4.dp), maxLines = 1)
                        }
                    }
                    if (tasks.size > 3) ZText("+${tasks.size - 3} tarefa(s)", 11.sp, 400, ZPalette.Quiet, Modifier.padding(start = 46.dp, top = 2.dp))
                } else if (snippet.isNotBlank() && !note.pinned) {
                    Spacer(Modifier.height(8.dp))
                    ZText(snippet, 12.sp, 400, ZPalette.Muted, maxLines = 2, lineHeight = 18.sp)
                }
            }
        }
    }
}

/** Todas as tarefas abertas de todas as notas, num lugar só. Marcar aqui marca na nota. */
@Composable
private fun TasksView(notes: List<Note>, onToggle: (Note, Int) -> Unit, onOpen: (Note) -> Unit, modifier: Modifier) {
    val groups = remember(notes) {
        notes.map { it to Notes.tasks(it.content).filter { t -> !t.done && t.text.isNotBlank() } }
            .filter { it.second.isNotEmpty() }
            .sortedByDescending { it.first.updatedAt }
    }
    Column(modifier) {
        ZText("${groups.sumOf { it.second.size }} tarefa(s) aberta(s)", 15.sp, 700, modifier = Modifier.padding(vertical = 8.dp))
        if (groups.isEmpty()) {
            ZText("Tudo feito! Tarefas criadas com - [ ] em qualquer nota aparecem aqui.", 12.5.sp, 400, ZPalette.Muted)
        }
        groups.forEach { (note, tasks) ->
            Row(
                Modifier.padding(top = 14.dp, bottom = 4.dp).clip(RoundedCornerShape(6.dp)).clickable { onOpen(note) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ZText("${note.icon.ifEmpty { "📝" }} ${note.title.ifEmpty { "Sem título" }}", 12.5.sp, 650, ZPalette.PurpleText)
            }
            tasks.forEach { task ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = false,
                        onCheckedChange = { onToggle(note, task.line) },
                        colors = CheckboxDefaults.colors(uncheckedColor = Color(0xFF55525C)),
                    )
                    ZText(Notes.plain(task.text), 13.sp, 400, ZPalette.Ink)
                }
            }
            HLine(Modifier.padding(top = 8.dp))
        }
    }
}
