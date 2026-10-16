package dev.zentro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.zentro.app.data.AppData
import dev.zentro.shared.Event
import dev.zentro.shared.Format
import dev.zentro.shared.Schedule
import dev.zentro.shared.TxType
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

@Composable
fun TodayScreen(
    data: AppData,
    tz: TimeZone,
    now: Long,
    onQuickEvent: (String) -> Boolean,
    onOpenEvent: (Event?, LocalDate, LocalTime?) -> Unit,
    onReviewed: () -> Unit,
    onOpenFinance: () -> Unit,
) {
    val today = Schedule.today(tz, now)
    val tomorrow = today.plus(1, DateTimeUnit.DAY)
    val events = data.activeEvents
    val todayItems = remember(events, today) { Schedule.agendaFor(events, today, tz) }
    val tomorrowItems = remember(events, tomorrow) { Schedule.agendaFor(events, tomorrow, tz) }
    val needsReview = Schedule.needsReview(data.settings.copy(timezone = tz.id), now)
    val hour = Schedule.toLocal(now, tz).hour
    val greeting = when (hour) {
        in 5..11 -> "Bom dia"
        in 12..17 -> "Boa tarde"
        else -> "Boa noite"
    }
    val firstName = data.session?.user?.name?.substringBefore(' ').orEmpty()

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(max = 760.dp),
    ) {
        item { Column {
            Text(
                Format.longDate(today).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (firstName.isEmpty()) "$greeting!" else "$greeting, $firstName!",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(16.dp))
            QuickAddField("Ex.: Dentista amanhã 14h", onQuickEvent)
        } }

        if (needsReview) {
            item { ReviewBanner(todayItems.size, onReviewed) }
        }

        item {
            SectionTitle("Hoje · ${todayItems.size}")
        }
        if (todayItems.isEmpty()) {
            item {
                EmptyState(Icons.Rounded.DateRange, "Dia livre", "Nada marcado para hoje. Escreva acima para adicionar algo rápido.")
            }
        } else {
            items(todayItems, key = { "t-${it.first.id}-${it.second}" }) { (event, start) ->
                EventRow(event, start, tz, now) { onOpenEvent(event, today, null) }
            }
        }

        if (tomorrowItems.isNotEmpty()) {
            item { SectionTitle("Amanhã · ${tomorrowItems.size}") }
            items(tomorrowItems, key = { "a-${it.first.id}-${it.second}" }) { (event, start) ->
                EventRow(event, start, tz, now) { onOpenEvent(event, tomorrow, null) }
            }
        }

        item {
            Column {
                SectionTitle("Finanças do mês")
                MonthSummaryCard(data, tz, now, onOpenFinance)
            }
        }
    }
}

@Composable
private fun ReviewBanner(count: Int, onReviewed: () -> Unit) {
    ZCard(Modifier.padding(top = 8.dp), color = ZColors.Warning.copy(alpha = 0.14f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(ZColors.Warning.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Edit, null, tint = ZColors.Warning)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Revise sua agenda de hoje", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (count == 0) "Nada marcado ainda. Tem algo para hoje?" else "Você tem $count compromisso(s). Está tudo certo?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onReviewed,
            colors = ButtonDefaults.buttonColors(containerColor = ZColors.Warning, contentColor = Color(0xFF1A1300)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Está tudo certo")
        }
    }
}

@Composable
fun MonthSummaryCard(data: AppData, tz: TimeZone, now: Long, onClick: () -> Unit) {
    val month = Schedule.today(tz, now)
    val txs = data.activeTransactions.filter {
        val d = Schedule.toLocal(it.date, tz).date
        d.year == month.year && d.month == month.month
    }
    val income = txs.filter { it.type == TxType.INCOME }.sumOf { it.amountCents }
    val expense = txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents }
    ZCard(onClick = onClick) {
        Text("Saldo de ${Format.monthName(month.month)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            Format.money(income - expense),
            style = MaterialTheme.typography.headlineSmall.merge(MoneyStyle),
            color = if (income - expense < 0) ZColors.Expense else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MoneyPill(ZIcons.TrendingUp, Format.money(income), ZColors.Income)
            MoneyPill(ZIcons.TrendingDown, Format.money(expense), ZColors.Expense)
        }
    }
}

@Composable
fun MoneyPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
