package dev.zentro.app.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zentro.app.data.newId
import dev.zentro.app.ui.AdaptiveSheet
import dev.zentro.app.ui.EmptyState
import dev.zentro.app.ui.LocalWide
import dev.zentro.app.ui.SectionTitle
import dev.zentro.app.ui.ZColors
import dev.zentro.app.ui.ZIcons
import dev.zentro.shared.Format
import dev.zentro.shared.Note
import dev.zentro.shared.NoteTemplates
import dev.zentro.shared.Notes
import dev.zentro.shared.Schedule
import kotlinx.datetime.TimeZone

private enum class NotesView(val label: String) { ALL("Todas"), PINNED("Fixadas"), TASKS("Tarefas"), GRAPH("Mapa") }

@Composable
fun NotesScreen(
    notes: List<Note>,
    tz: TimeZone,
    now: Long,
    onSave: (Note) -> Unit,
    onDelete: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val wide = LocalWide.current
    var view by remember { mutableStateOf(NotesView.ALL) }
    var folder by remember { mutableStateOf<String?>(null) }
    var tag by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var openId by remember { mutableStateOf<String?>(null) }
    var choosingTemplate by remember { mutableStateOf(false) }

    val byId = remember(notes) { notes.associateBy { it.id } }
    val openNote = openId?.let { byId[it] }
    val folders = remember(notes) { Notes.folders(notes) }
    val allTags = remember(notes) {
        notes.flatMap { Notes.tags(it.content) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(20)
    }
    val filtered = remember(notes, view, folder, tag, query) {
        val base = notes.filter { n ->
            (folder == null || Notes.inFolder(n, folder!!)) &&
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
        openId = note.id
        view = NotesView.ALL
    }

    /** Clique num [[link]]: abre a nota com esse título, ou cria na hora (como no Obsidian). */
    fun openByTitle(title: String) {
        val existing = Notes.findByTitle(notes, title)
        if (existing != null) openId = existing.id
        else {
            create("", title = title.trim(), folderName = openNote?.folder)
            onMessage("Nota \"${title.trim()}\" criada")
        }
    }

    fun openDaily() {
        val today = Schedule.today(tz, now)
        val existing = notes.firstOrNull { it.title == Notes.dailyTitle(today) && it.folder == Notes.DAILY_FOLDER }
        if (existing != null) openId = existing.id
        else create(NoteTemplates.daily(today), title = Notes.dailyTitle(today), folderName = Notes.DAILY_FOLDER, icon = "📅")
    }

    val editor = @Composable { modifier: Modifier, note: Note, back: (() -> Unit)? ->
        Box(modifier) {
            NoteEditor(
                note = note,
                allNotes = notes,
                onSave = onSave,
                onDelete = {
                    onDelete(it)
                    openId = null
                    onMessage("Nota excluída")
                },
                onOpenLink = ::openByTitle,
                onOpenNote = { openId = it.id },
                onTag = {
                    tag = it
                    view = NotesView.ALL
                    if (!wide) openId = null
                },
                onBack = back,
            )
        }
    }

    val list = @Composable { modifier: Modifier ->
        Column(modifier) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Notas", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = ::openDaily) { Icon(Icons.Rounded.DateRange, "Nota de hoje") }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar em todas as notas") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Limpar") } }) else null,
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedBorderColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (!wide) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NotesView.entries.forEach { v ->
                        FilterChip(selected = view == v && folder == null && tag == null, onClick = { view = v; folder = null; tag = null }, label = { Text(v.label) })
                    }
                    folders.filter { !it.contains('/') }.forEach { f ->
                        FilterChip(selected = folder == f, onClick = { folder = if (folder == f) null else f; view = NotesView.ALL }, label = { Text(f) })
                    }
                }
                if (tag != null) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Filtrando por ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TagPill(tag!!) { tag = null }
                        Text("  ✕", modifier = Modifier.clickable { tag = null }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            when {
                view == NotesView.TASKS && !wide -> TasksView(notes, onSave, onOpen = { openId = it.id }, Modifier.weight(1f))
                view == NotesView.GRAPH && !wide -> NoteGraph(notes, onOpen = { openId = it.id }, Modifier.weight(1f).fillMaxWidth())
                else -> LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (filtered.isEmpty()) {
                        item {
                            EmptyState(
                                ZIcons.Notes,
                                if (query.isBlank()) "Nenhuma nota aqui" else "Nada encontrado",
                                if (query.isBlank()) "Toque em + para criar. Use modelos de reunião, aula e projeto." else "Tente outra palavra ou #tag.",
                            )
                        }
                    }
                    items(filtered, key = { it.first.id }) { (note, snippet) ->
                        NoteCard(note, snippet, selected = note.id == openId, tz = tz, now = now) { openId = note.id }
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Sidebar(
                    view = view,
                    folder = folder,
                    tag = tag,
                    folders = folders,
                    tags = allTags,
                    counts = notes,
                    onView = { view = it; folder = null; tag = null },
                    onFolder = { folder = it; view = NotesView.ALL },
                    onTag = { tag = it; view = NotesView.ALL },
                    modifier = Modifier.width(220.dp).fillMaxHeight(),
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                when (view) {
                    NotesView.TASKS -> TasksView(notes, onSave, onOpen = { openId = it.id; view = NotesView.ALL }, Modifier.weight(1f).padding(24.dp))
                    NotesView.GRAPH -> NoteGraph(notes, onOpen = { openId = it.id; view = NotesView.ALL }, Modifier.weight(1f).fillMaxHeight())
                    else -> {
                        list(Modifier.width(340.dp).fillMaxHeight())
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        if (openNote != null) {
                            editor(Modifier.weight(1f).fillMaxHeight(), openNote, null)
                        } else {
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                EmptyState(ZIcons.Notes, "Escolha uma nota", "Ou crie uma nova. [[Links]] ligam as notas e #tags organizam tudo.")
                            }
                        }
                    }
                }
            }
        } else {
            if (openNote != null) editor(Modifier.fillMaxSize(), openNote) { openId = null }
            else list(Modifier.fillMaxSize())
        }

        if (openNote == null || wide) {
            ExtendedFloatingActionButton(
                onClick = { choosingTemplate = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Nota") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    if (choosingTemplate) {
        val today = Schedule.today(tz, now)
        AdaptiveSheet(onDismiss = { choosingTemplate = false }) {
            Text("Nova nota", style = MaterialTheme.typography.titleLarge)
            Text(
                "Comece com um modelo pronto" + (folder?.let { " em \"$it\"" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            (NoteTemplates.all.map { Triple(it.icon, it.name, it) }).forEach { (icon, name, template) ->
                TemplateRow(icon, name, template.folder?.let { "Vai para $it" } ?: "Pasta atual") {
                    choosingTemplate = false
                    create(template.body(today), folderName = folder ?: template.folder, icon = if (template.name == "Em branco") "" else icon)
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
private fun TemplateRow(icon: String, name: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 24.sp)
        Column(Modifier.padding(start = 14.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NoteCard(note: Note, snippet: String, selected: Boolean, tz: TimeZone, now: Long, onClick: () -> Unit) {
    val tags = remember(note.content) { Notes.tags(note.content).take(3) }
    val tasks = remember(note.content) { Notes.tasks(note.content) }
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(note.icon.ifEmpty { "📝" }, fontSize = 16.sp)
                Text(
                    note.title.ifEmpty { "Sem título" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                if (note.pinned) Icon(Icons.Rounded.Star, "Fixada", tint = ZColors.Warning, modifier = Modifier.size(16.dp))
            }
            if (snippet.isNotBlank()) {
                Text(
                    snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${note.folder} · ${relative(note.updatedAt, now, tz)}" +
                        if (tasks.isNotEmpty()) " · ☑ ${tasks.count { it.done }}/${tasks.size}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                tags.forEach { Text("#$it", style = MaterialTheme.typography.labelSmall, color = ZColors.Income) }
            }
        }
    }
}

@Composable
private fun Sidebar(
    view: NotesView,
    folder: String?,
    tag: String?,
    folders: List<String>,
    tags: List<String>,
    counts: List<Note>,
    onView: (NotesView) -> Unit,
    onFolder: (String) -> Unit,
    onTag: (String) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).verticalScroll(rememberScrollState()).padding(12.dp)) {
        val icons = mapOf(NotesView.ALL to ZIcons.Notes, NotesView.PINNED to Icons.Rounded.Star, NotesView.TASKS to Icons.Rounded.CheckCircle, NotesView.GRAPH to Icons.Rounded.Share)
        NotesView.entries.forEach { v ->
            SidebarItem(icons.getValue(v), v.label, selected = view == v && folder == null && tag == null) { onView(v) }
        }
        SectionTitle("Pastas", Modifier.padding(horizontal = 8.dp))
        folders.forEach { f ->
            val depth = f.count { it == '/' }
            val count = counts.count { Notes.inFolder(it, f) }
            SidebarItem(null, f.substringAfterLast('/'), selected = folder == f, indent = depth, trailing = if (count > 0) "$count" else null) { onFolder(f) }
        }
        if (tags.isNotEmpty()) {
            SectionTitle("Tags", Modifier.padding(horizontal = 8.dp))
            tags.forEach { t -> SidebarItem(null, "#$t", selected = tag == t, color = ZColors.Income) { onTag(t) } }
        }
    }
}

@Composable
private fun SidebarItem(
    icon: ImageVector?,
    label: String,
    selected: Boolean,
    indent: Int = 0,
    trailing: String? = null,
    color: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(start = (10 + indent * 14).dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
        } else {
            Text(if (color == Color.Unspecified) "📁" else "", fontSize = 13.sp)
            Spacer(Modifier.width(if (color == Color.Unspecified) 8.dp else 0.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Todas as tarefas abertas de todas as notas, num lugar só. Marcar aqui marca na nota. */
@Composable
private fun TasksView(notes: List<Note>, onSave: (Note) -> Unit, onOpen: (Note) -> Unit, modifier: Modifier) {
    val groups = remember(notes) {
        notes.map { it to Notes.tasks(it.content).filter { t -> !t.done && t.text.isNotBlank() } }
            .filter { it.second.isNotEmpty() }
            .sortedByDescending { it.first.updatedAt }
    }
    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)) {
        item {
            Text(
                "${groups.sumOf { it.second.size }} tarefa(s) aberta(s)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        if (groups.isEmpty()) {
            item { EmptyState(Icons.Rounded.CheckCircle, "Tudo feito!", "Tarefas criadas com - [ ] em qualquer nota aparecem aqui.") }
        }
        groups.forEach { (note, tasks) ->
            item(key = "h-${note.id}") {
                Text(
                    "${note.icon.ifEmpty { "📝" }} ${note.title.ifEmpty { "Sem título" }}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 14.dp, bottom = 2.dp).clickable { onOpen(note) },
                )
            }
            items(tasks, key = { "${note.id}-${it.line}" }) { task ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = false, onCheckedChange = { onSave(note.copy(content = Notes.toggleTask(note.content, task.line))) })
                    Text(Notes.plain(task.text), style = MaterialTheme.typography.bodyLarge.copy(textDecoration = TextDecoration.None))
                }
            }
        }
    }
}

private fun relative(millis: Long, now: Long, tz: TimeZone): String {
    val diff = now - millis
    return when {
        diff < 60_000 -> "agora"
        diff < 3_600_000 -> "há ${diff / 60_000} min"
        Schedule.today(tz, millis) == Schedule.today(tz, now) -> "hoje ${Format.time(Schedule.toLocal(millis, tz))}"
        diff < 2 * 86_400_000 -> "ontem"
        else -> Format.shortDate(Schedule.toLocal(millis, tz).date)
    }
}
