package dev.zentro.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotesTest {
    private fun note(id: String, title: String, content: String = "", folder: String = Notes.DEFAULT_FOLDER) =
        Note(id = id, title = title, content = content, folder = folder, updatedAt = id.hashCode().toLong())

    @Test
    fun linksAndAliases() {
        val links = Notes.links("Veja [[Projeto Zentro]] e [[Cálculo I|a matéria]].\n```\n[[ignorado]]\n```")
        assertEquals(listOf("Projeto Zentro", "Cálculo I"), links.map { it.target })
        assertEquals("a matéria", links[1].label)
    }

    @Test
    fun tagsIgnoreHeadingsCodeAndNumbers() {
        val content = "# Título\n## Sub\nEstudar #faculdade e #Projetos/zentro, não C# nem #123.\n```\n#codigo\n```"
        assertEquals(setOf("faculdade", "projetos/zentro"), Notes.tags(content))
    }

    @Test
    fun toggleTasksKeepsTheRestOfTheNote() {
        val content = "Lista\n- [ ] comprar café\n- [x] pagar luz"
        assertEquals(listOf(false, true), Notes.tasks(content).map { it.done })
        val toggled = Notes.toggleTask(content, 1)
        assertEquals("Lista\n- [x] comprar café\n- [x] pagar luz", toggled)
        assertEquals(content, Notes.toggleTask(toggled, 1))
    }

    @Test
    fun backlinksAndGraph() {
        val a = note("a", "Projeto Zentro", "ideias #projeto")
        val b = note("b", "Reunião segunda", "Falamos do [[projeto zentro]]")
        val c = note("c", "Aula", "nada aqui")
        assertEquals(listOf("b"), Notes.backlinks(listOf(a, b, c), a).map { it.id })
        assertEquals(listOf("b" to "a"), Notes.graph(listOf(a, b, c)))
    }

    @Test
    fun searchRanksTitleAboveContent() {
        val a = note("a", "Cálculo", "derivadas")
        val b = note("b", "Resumo", "revisar cálculo antes da prova")
        val hits = Notes.search(listOf(b, a), "cálculo")
        assertEquals(listOf("a", "b"), hits.map { it.note.id })
        assertTrue(hits[1].snippet.contains("cálculo"))
        assertEquals(listOf("a"), Notes.search(listOf(a, b.copy(content = "#calc")), "Cálculo").map { it.note.id })
    }

    @Test
    fun pendingLinkDetection() {
        assertEquals("Proj", Notes.pendingLink("veja [[Proj"))
        assertEquals("", Notes.pendingLink("veja [["))
        assertNull(Notes.pendingLink("veja [[Projeto]] e"))
        assertNull(Notes.pendingLink("sem link"))
    }

    @Test
    fun foldersIncludeParentsAndDefaults() {
        val folders = Notes.folders(listOf(note("a", "x", folder = "Faculdade/Cálculo"), note("b", "y", folder = "Leituras")))
        assertTrue("Faculdade" in folders && "Faculdade/Cálculo" in folders && "Leituras" in folders && "Trabalho" in folders)
        assertTrue(Notes.inFolder(note("a", "x", folder = "Faculdade/Cálculo"), "Faculdade"))
        assertFalse(Notes.inFolder(note("a", "x", folder = "Faculdades"), "Faculdade"))
    }

    @Test
    fun markdownBlocks() {
        val blocks = Markdown.parse("# Título\nlinha 1\nlinha 2\n\n- item\n- [x] feito\n1. primeiro\n> citação\n---\n```\ncode\n```")
        assertEquals(
            listOf(
                MdBlock.Heading(1, "Título"),
                MdBlock.Paragraph("linha 1\nlinha 2"),
                MdBlock.Bullet("item", 0),
                MdBlock.Task(5, "feito", true, 0),
                MdBlock.Numbered(1, "primeiro", 0),
                MdBlock.Quote("citação"),
                MdBlock.Divider,
                MdBlock.Code("code"),
            ),
            blocks,
        )
    }

    @Test
    fun markdownInlineSpans() {
        val spans = Markdown.inline("**forte** e *itálico* com `código`, [[Nota|link]], #tag e https://zentro.dev")
        assertEquals(MdSpan.Bold("forte"), spans[0])
        assertEquals(MdSpan.Italic("itálico"), spans[2])
        assertEquals(MdSpan.Code("código"), spans[4])
        assertEquals(MdSpan.Link("Nota", "link"), spans[6])
        assertEquals(MdSpan.Tag("tag"), spans[8])
        assertEquals(MdSpan.Url("https://zentro.dev"), spans[10])
    }

    @Test
    fun versions() {
        assertTrue(Version.isNewer("v1.1.0", "1.0.0"))
        assertTrue(Version.isNewer("1.0.10", "1.0.9"))
        assertFalse(Version.isNewer("v1.0.0", "1.0.0"))
        assertFalse(Version.isNewer("lixo", "1.0.0"))
        assertEquals(Version(2, 0, 0), Version.parse("2"))
    }
}
