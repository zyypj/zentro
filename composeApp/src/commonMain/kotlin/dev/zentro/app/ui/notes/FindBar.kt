package dev.zentro.app.ui.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.zentro.app.ui.ZIcons

/** Barra de buscar (Ctrl+F) e substituir (Ctrl+R) que fica no topo da nota. */
@Composable
internal fun FindBar(
    query: String,
    onQuery: (String) -> Unit,
    replacement: String,
    onReplacement: (String) -> Unit,
    replaceOpen: Boolean,
    onToggleReplace: () -> Unit,
    position: Int,
    total: Int,
    status: String?,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
    focusRequester: FocusRequester,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = muted, modifier = Modifier.size(18.dp))
                CompactField(
                    value = query,
                    onValueChange = onQuery,
                    placeholder = "Buscar na nota",
                    onEnter = { shift -> if (shift) onPrev() else onNext() },
                    modifier = Modifier.weight(1f).padding(start = 8.dp).focusRequester(focusRequester),
                )
                Text(
                    when {
                        query.isEmpty() -> ""
                        total == 0 -> "Nada"
                        else -> "${position + 1} de $total"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (query.isNotEmpty() && total == 0) MaterialTheme.colorScheme.error else muted,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                SmallIcon(onPrev, total > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Anterior (Shift+Enter)") }
                SmallIcon(onNext, total > 0) { Icon(Icons.Rounded.KeyboardArrowDown, "Próxima (Enter)") }
                SmallIcon(onToggleReplace) {
                    Icon(
                        ZIcons.FindReplace,
                        "Substituir (Ctrl+R)",
                        tint = if (replaceOpen) MaterialTheme.colorScheme.primary else muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
                SmallIcon(onClose) { Icon(Icons.Rounded.Close, "Fechar (Esc)") }
            }
            if (replaceOpen) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(18.dp))
                    CompactField(
                        value = replacement,
                        onValueChange = onReplacement,
                        placeholder = "Substituir por",
                        onEnter = { onReplace() },
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    TextButton(onClick = onReplace, enabled = total > 0) { Text("Trocar") }
                    TextButton(onClick = onReplaceAll, enabled = total > 0) { Text("Todas") }
                }
            }
            status?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(start = 26.dp, bottom = 6.dp))
            }
        }
    }
}

@Composable
private fun SmallIcon(onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp), content = content)
}

@Composable
private fun CompactField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onEnter: (shift: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.replace("\n", "")) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.padding(vertical = 10.dp).onPreviewKeyEvent { e ->
            val enter = e.key == Key.Enter || e.key == Key.NumPadEnter
            if (enter && e.type == KeyEventType.KeyDown) onEnter(e.isShiftPressed)
            enter
        },
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
            inner()
        },
    )
}
