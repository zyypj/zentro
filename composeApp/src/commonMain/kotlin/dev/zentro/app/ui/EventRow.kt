package dev.zentro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import dev.zentro.shared.Person
import dev.zentro.shared.Recurrence
import dev.zentro.shared.Schedule
import kotlinx.datetime.TimeZone

/** Linha de um compromisso: horário, faixa colorida, título e ícones de crítico/repetição. */
@Composable
fun EventRow(
    event: Event,
    start: Long,
    tz: TimeZone,
    now: Long,
    person: Person? = null,
    onTogglePaid: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val bill = event.billAmountCents
    val paid = start in event.paid
    val local = Schedule.toLocal(start, tz)
    val end = event.endAt?.let { start + (it - event.startAt) }
    val past = (end ?: start) < now && (bill == null || paid)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (person != null) {
                    PersonPill(person)
                    Spacer(Modifier.width(6.dp))
                }
                Text(event.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val subtitle = if (bill != null) "${Format.money(bill)} · ${event.billCategory}" + if (!paid && start < Schedule.startOfDay(Schedule.today(tz, now), tz)) " · atrasada" else "" else buildList {
                if (ongoing) add("Agora")
                if (event.reminders.isEmpty()) add("Sem aviso")
                else add("Avisa " + event.reminders.sortedDescending().joinToString(", ") { shortOffset(it) })
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
        if (bill != null && onTogglePaid != null) {
            if (paid) {
                Text(
                    "✓ Pago",
                    style = MaterialTheme.typography.labelLarge,
                    color = ZColors.Income,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onTogglePaid(false) }.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            } else {
                Text(
                    "Pagar",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary)
                        .clickable { onTogglePaid(true) }.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
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

private fun shortOffset(minutes: Int): String = when {
    minutes <= 0 -> "na hora"
    minutes % 1440 == 0 -> "${minutes / 1440}d antes"
    minutes % 60 == 0 -> "${minutes / 60}h antes"
    else -> "$minutes min antes"
}

/** Etiqueta com o nome de quem é o compromisso (quando não é seu). */
@Composable
fun PersonPill(person: Person) {
    val color = ZColors.event(person.color)
    Text(
        person.name,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
