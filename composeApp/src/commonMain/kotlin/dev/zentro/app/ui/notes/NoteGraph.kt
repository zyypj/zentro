package dev.zentro.app.ui.notes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zentro.app.ui.ZColors
import dev.zentro.shared.Note
import dev.zentro.shared.Notes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/** Posições (de -1 a 1) calculadas por um layout de forças simples, como o "graph view" do Obsidian. */
private fun layout(ids: List<String>, edges: List<Pair<Int, Int>>): List<Offset> {
    val n = ids.size
    if (n == 0) return emptyList()
    val random = Random(ids.joinToString().hashCode())
    val x = FloatArray(n) { random.nextFloat() * 2 - 1 }
    val y = FloatArray(n) { random.nextFloat() * 2 - 1 }
    val k = sqrt(4f / n)
    var temperature = 0.2f
    repeat(260) {
        val dx = FloatArray(n)
        val dy = FloatArray(n)
        for (i in 0 until n) for (j in i + 1 until n) {
            val ox = x[i] - x[j]
            val oy = y[i] - y[j]
            val d = max(0.01f, sqrt(ox * ox + oy * oy))
            val force = k * k / d
            dx[i] += ox / d * force; dy[i] += oy / d * force
            dx[j] -= ox / d * force; dy[j] -= oy / d * force
        }
        for ((a, b) in edges) {
            val ox = x[a] - x[b]
            val oy = y[a] - y[b]
            val d = max(0.01f, sqrt(ox * ox + oy * oy))
            val force = d * d / k
            dx[a] -= ox / d * force; dy[a] -= oy / d * force
            dx[b] += ox / d * force; dy[b] += oy / d * force
        }
        for (i in 0 until n) {
            // Gravidade leve para o centro, para notas soltas não fugirem da tela.
            dx[i] -= x[i] * 0.6f; dy[i] -= y[i] * 0.6f
            val d = max(0.01f, sqrt(dx[i] * dx[i] + dy[i] * dy[i]))
            x[i] += dx[i] / d * min(d, temperature)
            y[i] += dy[i] / d * min(d, temperature)
        }
        temperature *= 0.985f
    }
    val maxAbs = max(0.01f, (0 until n).maxOf { max(kotlin.math.abs(x[it]), kotlin.math.abs(y[it])) })
    return (0 until n).map { Offset(x[it] / maxAbs, y[it] / maxAbs) }
}

/** Mapa das notas e das ligações entre elas. Arraste para mover, pince para zoom, toque para abrir. */
@Composable
fun NoteGraph(notes: List<Note>, onOpen: (Note) -> Unit, modifier: Modifier = Modifier) {
    val ids = remember(notes) { notes.map { it.id } }
    val edges = remember(notes) {
        val index = ids.withIndex().associate { it.value to it.index }
        Notes.graph(notes).mapNotNull { (a, b) -> index[a]?.let { ia -> index[b]?.let { ib -> ia to ib } } }
    }
    val degree = remember(edges) { IntArray(ids.size).also { d -> edges.forEach { (a, b) -> d[a]++; d[b]++ } } }
    // Até 80 notas o cálculo leva poucos milissegundos; acima disso roda em segundo plano.
    val small = ids.size <= 80
    val immediate = remember(ids, edges) { if (small) layout(ids, edges) else null }
    val background by produceState(emptyList<Offset>(), ids, edges) {
        if (!small) value = withContext(Dispatchers.Default) { layout(ids, edges) }
    }
    val positions = immediate ?: background
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val folderColors = remember(notes) {
        notes.map { it.folder.substringBefore('/') }.distinct().withIndex().associate { it.value to ZColors.event(it.index) }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    Box(modifier) {
        if (notes.isEmpty()) {
            Text("Ligue notas umas às outras para ver o mapa.", color = colors.onSurfaceVariant, modifier = Modifier.align(Alignment.Center).padding(24.dp))
        }
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, panChange, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.4f, 4f)
                        pan += panChange
                    }
                }
                .pointerInput(positions, scale, pan) {
                    detectTapGestures { tap ->
                        val center = Offset(size.width / 2f, size.height / 2f) + pan
                        val radius = min(size.width, size.height) * 0.42f * scale
                        val hit = positions.withIndex().minByOrNull { (_, p) -> ((center + p * radius) - tap).getDistance() }
                        if (hit != null && ((center + hit.value * radius) - tap).getDistance() < 28.dp.toPx()) onOpen(notes[hit.index])
                    }
                },
        ) {
            if (positions.size != ids.size) return@Canvas
            val center = Offset(size.width / 2f, size.height / 2f) + pan
            val radius = min(size.width, size.height) * 0.42f * scale
            fun at(i: Int) = center + positions[i] * radius
            for ((a, b) in edges) drawLine(colors.outline.copy(alpha = 0.6f), at(a), at(b), strokeWidth = 1.5.dp.toPx())
            notes.forEachIndexed { i, note ->
                val r = (5 + min(degree[i], 8) * 1.6f).dp.toPx()
                drawCircle(folderColors[note.folder.substringBefore('/')] ?: colors.primary, r, at(i))
                if (scale > 0.7f || degree[i] > 1) {
                    val label = measurer.measure(note.title.ifEmpty { "Sem título" }.take(28), TextStyle(fontSize = 11.sp, color = colors.onSurfaceVariant))
                    drawText(label, topLeft = at(i) + Offset(-label.size.width / 2f, r + 4.dp.toPx()))
                }
            }
        }
    }
}
