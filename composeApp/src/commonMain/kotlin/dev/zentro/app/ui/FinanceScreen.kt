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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.sp
import dev.zentro.shared.Bills
import dev.zentro.shared.Cards
import dev.zentro.shared.CreditCard
import dev.zentro.shared.Finance
import dev.zentro.shared.Event
import dev.zentro.shared.Format
import dev.zentro.shared.Schedule
import dev.zentro.shared.Transaction
import dev.zentro.shared.TxSource
import dev.zentro.shared.TxType
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

@Composable
fun FinanceScreen(
    onNewTransaction: () -> Unit,
    transactions: List<Transaction>,
    events: List<Event>,
    tz: TimeZone,
    now: Long,
    onQuickAdd: (String) -> Boolean,
    onOpen: (Transaction) -> Unit,
    onOpenBill: (Event) -> Unit,
    onTogglePaid: (Event, Long, Boolean) -> Unit,
    onNewBill: () -> Unit,
    cards: List<CreditCard>,
    onOpenCard: (CreditCard) -> Unit,
    onNewCard: () -> Unit,
    onPayInvoice: (Cards.Invoice, Boolean) -> Unit,
    onAdjustBalance: (Long) -> Unit,
) {
    val today = Schedule.today(tz, now)
    val thisMonth = LocalDate(today.year, today.month, 1)
    var month by remember { mutableStateOf(thisMonth) }
    var adjusting by remember { mutableStateOf(false) }
    val inMonth = remember(transactions, month) {
        transactions.filter {
            val d = Schedule.toLocal(it.date, tz).date
            d.year == month.year && d.month == month.month
        }.sortedByDescending { it.date }
    }
    // O saldo continua de um mês para o outro; compras no cartão só pesam quando a fatura é paga.
    val summaryMonth = remember(transactions, month) { Finance.month(transactions, month, tz) }
    val income = summaryMonth.income
    val expense = summaryMonth.expense
    val byCategory = remember(transactions, month) { Finance.spendingByCategory(transactions, month, tz).take(5) }
    val spent = byCategory.sumOf { it.second }
    val history = remember(transactions, today) { Finance.history(transactions, today, tz) }
    val cardNames = remember(cards) { cards.associate { it.id to it.name } }
    val grouped = remember(inMonth) { inMonth.groupBy { Schedule.toLocal(it.date, tz).date } }
    val bills = remember(events, month, now) { Bills.inMonth(events, month, tz, now) }

    val summary = @Composable { Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZText(Format.monthYear(month), 17.sp, 700, tracking = -0.02, modifier = Modifier.weight(1f))
            MonthArrow(Line.ChevronLeft, "Mês anterior") { month = month.minus(1, DateTimeUnit.MONTH) }
            MonthArrow(Line.ChevronRight, "Próximo mês") { month = month.plus(1, DateTimeUnit.MONTH) }
        }
        Spacer(Modifier.height(12.dp))
        Panel(Modifier.fillMaxWidth(), padding = 20.dp) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ZText(
                        when {
                            month == thisMonth -> "Saldo atual"
                            month < thisMonth -> "Saldo no fim de ${Format.monthName(month.month)}"
                            else -> "Saldo previsto"
                        },
                        11.5.sp, 400, ZPalette.Muted, Modifier.weight(1f),
                    )
                    if (month == thisMonth) LinkAction("Ajustar", { adjusting = true })
                }
                Spacer(Modifier.height(6.dp))
                ZText(
                    Format.money(summaryMonth.closing), 32.sp, 700,
                    if (summaryMonth.closing < 0) ZColors.Expense else ZPalette.Ink, tracking = -0.045, tabular = true, maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                ZText(
                    "${Format.money(summaryMonth.opening)} do mês anterior  ·  ${Format.money(summaryMonth.result, signed = true)} neste mês",
                    11.sp, 400, ZPalette.Quiet,
                )
                Spacer(Modifier.height(18.dp))
                MoneyPair(income, expense)
            }
        }
        Spacer(Modifier.height(14.dp))
        QuickCapture("Ex.: 45,90 almoço  ·  +1200 salário", onQuickAdd)
        BillsSection(bills, tz, now, onOpenBill, onTogglePaid, onNewBill)
        CardsSection(cards, transactions, tz, now, onOpenCard, onNewCard, onPayInvoice)
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
                    val fraction = if (spent > 0) cents.toFloat() / spent else 0f
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(ZColors.event(i)))
                    }
                }
            }
        }
        BalanceHistory(history, today) { month = it }
    } }

    val list = @Composable { modifier: Modifier, header: Boolean ->
        ScrollableLazyColumn(modifier, contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                items(txs, key = { it.id }) { tx -> TransactionRow(tx, tx.cardId?.let { cardNames[it] ?: "Cartão" }) { onOpen(tx) } }
            }
        }
    }

    if (LocalWide.current) {
        Row(Modifier.fillMaxSize()) {
            ScrollableColumn(
                Modifier.weight(1f).widthIn(max = 560.dp).fillMaxHeight(),
                Modifier.padding(start = 48.dp, end = 24.dp, top = 32.dp, bottom = 40.dp),
            ) { summary() }
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp, end = 40.dp, top = 30.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                    ZText("Movimentações", 17.sp, 700, tracking = -0.02, modifier = Modifier.weight(1f))
                    PrimaryAction("Nova", onNewTransaction)
                }
                list(Modifier.weight(1f), false)
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            MobileHeader("Finanças") { SquarePlus(onNewTransaction, "Nova movimentação") }
            list(Modifier.padding(horizontal = 18.dp).weight(1f), true)
        }
    }

    if (adjusting) {
        AdjustBalanceDialog(remember(transactions) { Finance.balanceAt(transactions, now + 1) }, onAdjustBalance) { adjusting = false }
    }
}

@Composable
private fun MonthArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = ZPalette.Muted, modifier = Modifier.size(16.dp))
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
fun TransactionRow(tx: Transaction, cardName: String? = null, onClick: () -> Unit) {
    val income = tx.type == TxType.INCOME
    val onCard = tx.cardId != null
    val color = when {
        onCard -> MaterialTheme.colorScheme.onSurfaceVariant
        income -> ZColors.Income
        else -> ZColors.Expense
    }
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(
                when {
                    onCard -> ZIcons.CreditCard
                    income -> ZIcons.TrendingUp
                    else -> ZIcons.TrendingDown
                },
                null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(tx.description, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    onCard && tx.installments > 1 ->
                        "$cardName · ${tx.installments}x de ${Format.money(Cards.split(tx.amountCents, tx.installments).first())}"
                    onCard -> "$cardName · ${tx.category}"
                    else -> tx.category + if (tx.source == TxSource.MERCADO_PAGO) " · Mercado Pago" else ""
                },
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

/** Contas do mês: o que já foi pago, o que vence e o que está atrasado. */
@Composable
private fun BillsSection(
    bills: List<Bills.Item>,
    tz: TimeZone,
    now: Long,
    onOpen: (Event) -> Unit,
    onTogglePaid: (Event, Long, Boolean) -> Unit,
    onNew: () -> Unit,
) {
    val toPay = bills.filter { !it.paid }.sumOf { it.event.billAmountCents ?: 0 }
    val paid = bills.filter { it.paid }.sumOf { it.event.billAmountCents ?: 0 }
    SectionTitle("Contas do mês", action = {
        androidx.compose.material3.TextButton(onClick = onNew) { Text("+ Conta") }
    })
    if (bills.isEmpty()) {
        ZCard(onClick = onNew) {
            Text("Nenhuma conta neste mês", style = MaterialTheme.typography.titleSmall)
            Text(
                "Cadastre as contas fixas e o Zentro te lembra antes de vencer. Ex.: Hospedagem todo dia 5 49,90",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    ZCard {
        Row {
            Column(Modifier.weight(1f)) {
                Text("A pagar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Format.money(toPay), style = MaterialTheme.typography.titleMedium, color = if (toPay > 0) ZColors.Warning else MaterialTheme.colorScheme.onSurface)
            }
            Column(Modifier.weight(1f)) {
                Text("Pago", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Format.money(paid), style = MaterialTheme.typography.titleMedium, color = ZColors.Income)
            }
        }
        Spacer(Modifier.height(8.dp))
        val today = Schedule.today(tz, now)
        bills.forEach { item ->
            val due = Schedule.toLocal(item.due, tz).date
            val days = today.daysUntil(due)
            val label = when {
                item.paid -> "Pago"
                item.overdue -> "Atrasada há ${-days} dia(s)"
                days == 0 -> "Vence hoje"
                days == 1 -> "Vence amanhã"
                else -> "Vence dia ${due.dayOfMonth}"
            }
            val color = when {
                item.paid -> ZColors.Income
                item.overdue -> ZColors.Expense
                days <= 1 -> ZColors.Warning
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onOpen(item.event) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Checkbox(checked = item.paid, onCheckedChange = { onTogglePaid(item.event, item.due, it) })
                Column(Modifier.weight(1f)) {
                    Text(item.event.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(label, style = MaterialTheme.typography.bodySmall, color = color)
                }
                Text(Format.money(item.event.billAmountCents ?: 0), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
