package dev.zentro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.zentro.shared.Format
import dev.zentro.shared.Schedule
import dev.zentro.shared.Transaction
import dev.zentro.shared.TxSource
import dev.zentro.shared.TxType
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus

@Composable
fun FinanceScreen(
    transactions: List<Transaction>,
    tz: TimeZone,
    now: Long,
    onQuickAdd: (String) -> Boolean,
    onOpen: (Transaction) -> Unit,
) {
    val today = Schedule.today(tz, now)
    var month by remember { mutableStateOf(LocalDate(today.year, today.month, 1)) }
    val inMonth = remember(transactions, month) {
        transactions.filter {
            val d = Schedule.toLocal(it.date, tz).date
            d.year == month.year && d.month == month.month
        }.sortedByDescending { it.date }
    }
    val income = inMonth.filter { it.type == TxType.INCOME }.sumOf { it.amountCents }
    val expense = inMonth.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents }
    val byCategory = remember(inMonth) {
        inMonth.filter { it.type == TxType.EXPENSE }.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amountCents } }
            .entries.sortedByDescending { it.value }.take(5)
    }
    val grouped = remember(inMonth) { inMonth.groupBy { Schedule.toLocal(it.date, tz).date } }

    val summary = @Composable { Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Format.monthYear(month), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { month = month.minus(1, DateTimeUnit.MONTH) }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Mês anterior")
            }
            IconButton(onClick = { month = month.plus(1, DateTimeUnit.MONTH) }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Próximo mês")
            }
        }
        Spacer(Modifier.height(8.dp))
        ZCard(color = MaterialTheme.colorScheme.primaryContainer) {
            Text("Saldo do mês", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            Text(
                Format.money(income - expense),
                style = MaterialTheme.typography.displaySmall.merge(MoneyStyle),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Totals("Entradas", income, ZColors.Income, ZIcons.TrendingUp, Modifier.weight(1f))
                Totals("Saídas", expense, ZColors.Expense, ZIcons.TrendingDown, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
        QuickAddField("Ex.: 45,90 almoço  ·  +1200 salário", onQuickAdd)
        if (byCategory.isNotEmpty()) {
            SectionTitle("Onde foi o dinheiro")
            ZCard {
                byCategory.forEachIndexed { i, (category, cents) ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Row {
                        Text(category, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(Format.money(cents), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    val fraction = if (expense > 0) cents.toFloat() / expense else 0f
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(ZColors.event(i)))
                    }
                }
            }
        }
    } }

    val list = @Composable { modifier: Modifier, header: Boolean ->
        LazyColumn(modifier, contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (header) item { summary() }
            if (inMonth.isEmpty()) {
                item {
                    EmptyState(ZIcons.Wallet, "Sem movimentações", "Use o campo acima ou o botão + para registrar entradas e saídas.")
                }
            }
            grouped.forEach { (date, txs) ->
                item(key = "h-$date") {
                    SectionTitle(if (date == today) "Hoje" else Format.longDate(date))
                }
                items(txs, key = { it.id }) { tx -> TransactionRow(tx) { onOpen(tx) } }
            }
        }
    }

    if (LocalWide.current) {
        Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).widthIn(max = 480.dp)) { summary() }
            list(Modifier.weight(1f).fillMaxHeight(), false)
        }
    } else {
        list(Modifier.padding(horizontal = 20.dp).padding(top = 16.dp), true)
    }
}

@Composable
private fun Totals(label: String, cents: Long, color: androidx.compose.ui.graphics.Color, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Row(
        modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Format.money(cents), style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}

@Composable
fun TransactionRow(tx: Transaction, onClick: () -> Unit) {
    val income = tx.type == TxType.INCOME
    val color = if (income) ZColors.Income else ZColors.Expense
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(if (income) ZIcons.TrendingUp else ZIcons.TrendingDown, null, tint = color, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(tx.description, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                tx.category + if (tx.source == TxSource.MERCADO_PAGO) " · Mercado Pago" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            Format.money(if (income) tx.amountCents else -tx.amountCents, signed = true),
            style = MaterialTheme.typography.titleSmall,
            color = color,
        )
    }
}
