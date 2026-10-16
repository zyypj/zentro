package dev.zentro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.zentro.shared.Event
import dev.zentro.shared.Format
import dev.zentro.shared.Recurrence
import dev.zentro.shared.Schedule
import kotlinx.datetime.TimeZone

/** Linha de um compromisso: horário, faixa colorida, título e ícones de crítico/repetição. */
@Composable
fun EventRow(event: Event, start: Long, tz: TimeZone, now: Long, onClick: () -> Unit) {
    val local = Schedule.toLocal(start, tz)
    val end = event.endAt?.let { start + (it - event.startAt) }
    val past = (end ?: start) < now
    val ongoing = start <= now && end != null && end > now
    val color = ZColors.event(event.color)

    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .background(if (ongoing) color.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .alpha(if (past) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(52.dp)) {
            Text(
                if (event.allDay) "Dia" else Format.time(local),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (event.allDay) "todo" else end?.let { Format.time(Schedule.toLocal(it, tz)) } ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(event.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = buildList {
                if (ongoing) add("Agora")
                if (event.reminders.isNotEmpty()) add(event.reminders.min().let { Schedule.describeOffset(it).replaceFirstChar { c -> c.uppercase() } })
                else add("Sem aviso")
                if (event.notes.isNotBlank()) add(event.notes.lineSequence().first())
            }.joinToString(" · ")
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (event.recurrence != Recurrence.NONE) {
                Icon(ZIcons.Repeat, "Repete", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (event.critical) {
                Icon(Icons.Rounded.Warning, "Crítico", Modifier.size(18.dp), tint = ZColors.Expense)
            }
        }
    }
}
