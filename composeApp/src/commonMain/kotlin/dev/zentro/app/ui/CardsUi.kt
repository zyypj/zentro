package dev.zentro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.zentro.app.data.newId
import dev.zentro.shared.Cards
import dev.zentro.shared.CreditCard
import dev.zentro.shared.Finance
import dev.zentro.shared.Format
import dev.zentro.shared.Schedule
import dev.zentro.shared.Transaction
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil

private fun ddmm(date: LocalDate) = Format.shortDate(date)

/** Uma frase curta com a situação da fatura. */
fun invoiceStatusText(invoice: Cards.Invoice, today: LocalDate): String = when (invoice.status(today)) {
    Cards.Status.PAID -> "Fatura de ${Format.monthName(invoice.month.month)} paga"
    Cards.Status.OPEN -> "Fatura aberta · fecha ${ddmm(invoice.closing)}"
    Cards.Status.CLOSED -> when (val days = today.daysUntil(invoice.due)) {
        0 -> "Fechada · vence hoje"
        1 -> "Fechada · vence amanhã"
        else -> "Fechada · vence ${ddmm(invoice.due)} (em $days dias)"
    }
    Cards.Status.OVERDUE -> "Atrasada · venceu ${ddmm(invoice.due)}"
}

@Composable
fun invoiceStatusColor(invoice: Cards.Invoice, today: LocalDate): Color = when (invoice.status(today)) {
    Cards.Status.PAID -> ZColors.Income
    Cards.Status.OVERDUE -> ZColors.Expense
    Cards.Status.CLOSED -> if (today.daysUntil(invoice.due) <= 3) ZColors.Warning else MaterialTheme.colorScheme.onSurfaceVariant
    Cards.Status.OPEN -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun CardBadge(card: CreditCard, size: Int = 38) {
    val color = ZColors.event(card.color)
    Box(Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Icon(ZIcons.CreditCard, null, tint = color, modifier = Modifier.size((size / 2).dp))
    }
}

/** Resumo dos cartões na tela de Finanças: a fatura que importa agora de cada um. */
@Composable
fun CardsSection(
    cards: List<CreditCard>,
    transactions: List<Transaction>,
    tz: TimeZone,
    now: Long,
    onOpen: (CreditCard) -> Unit,
    onNew: () -> Unit,
    onPay: (Cards.Invoice, Boolean) -> Unit,
) {
    SectionTitle("Cartões de crédito", action = { TextButton(onClick = onNew) { Text("+ Cartão") } })
    if (cards.isEmpty()) {
        ZCard(onClick = onNew) {
            Text("Nenhum cartão", style = MaterialTheme.typography.titleSmall)
            Text(
                "Cadastre seus cartões para lançar cada gasto e as parcelas, e saber quanto vem em cada fatura.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val today = Schedule.today(tz, now)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cards.forEach { card ->
            val invoice = remember(card, transactions, today) { Cards.current(card, transactions, today, tz) }
            val status = invoice.status(today)
            ZCard(onClick = { onOpen(card) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardBadge(card)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(card.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(invoiceStatusText(invoice, today), style = MaterialTheme.typography.bodySmall, color = invoiceStatusColor(invoice, today))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Format.money(invoice.totalCents), style = MaterialTheme.typography.titleMedium.merge(MoneyStyle))
                        if (!invoice.paid && invoice.totalCents > 0 && status != Cards.Status.OPEN) {
                            FilledTonalButton(onClick = { onPay(invoice, true) }, modifier = Modifier.height(32.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
                                Text("Pagar", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Detalhes de um cartão: faturas mês a mês, com cada gasto e parcela. */
@Composable
fun CardSheet(
    card: CreditCard,
    transactions: List<Transaction>,
    tz: TimeZone,
    now: Long,
    onEdit: () -> Unit,
    onAddPurchase: () -> Unit,
    onOpenTx: (Transaction) -> Unit,
    onPay: (Cards.Invoice, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = Schedule.today(tz, now)
    var month by remember(card.id) { mutableStateOf(Cards.current(card, transactions, today, tz).month) }
    val invoice = remember(card, transactions, month) { Cards.invoice(card, month, transactions, tz) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    AdaptiveSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardBadge(card, 44)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(card.name, style = MaterialTheme.typography.titleLarge)
                Text("Fecha dia ${card.closingDay} · vence dia ${card.dueDay}", style = MaterialTheme.typography.bodySmall, color = muted)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Editar cartão", tint = muted) }
        }

        card.limitCents?.takeIf { it > 0 }?.let { limit ->
            val used = remember(card, transactions) { Cards.usedLimit(card, transactions, tz) }
            Spacer(Modifier.height(16.dp))
            Row {
                Text("Limite usado", style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.weight(1f))
                Text("${Format.money(used)} de ${Format.money(limit)}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(6.dp))
            val fraction = (used.toFloat() / limit).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                Box(
                    Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(3.dp))
                        .background(if (fraction > 0.9f) ZColors.Expense else ZColors.event(card.color)),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = Finance.addMonths(month, -1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Fatura anterior") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Fatura de ${Format.monthYear(month).replaceFirstChar { it.lowercase() }}", style = MaterialTheme.typography.titleSmall)
                Text("fecha ${ddmm(invoice.closing)} · vence ${ddmm(invoice.due)}", style = MaterialTheme.typography.bodySmall, color = muted)
            }
            IconButton(onClick = { month = Finance.addMonths(month, 1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Próxima fatura") }
        }

        ZCard(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(invoiceStatusText(invoice, today), style = MaterialTheme.typography.bodySmall, color = invoiceStatusColor(invoice, today))
            Text(Format.money(invoice.totalCents), style = MaterialTheme.typography.headlineMedium.merge(MoneyStyle))
            Spacer(Modifier.height(8.dp))
            val payment = invoice.payment
            if (payment == null) {
                Button(onClick = { onPay(invoice, true) }, enabled = invoice.totalCents > 0, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Marcar como paga")
                }
                if (invoice.totalCents > 0) {
                    Text(
                        "Lança ${Format.money(invoice.totalCents)} como saída e desconta do saldo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Paga em ${ddmm(Schedule.toLocal(payment.date, tz).date)} · ${Format.money(payment.amountCents)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ZColors.Income,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onPay(invoice, false) }) { Text("Desfazer") }
                }
                if (payment.amountCents != invoice.totalCents && invoice.totalCents > 0) {
                    TextButton(onClick = { onPay(invoice, true) }) { Text("A fatura mudou: atualizar pagamento para ${Format.money(invoice.totalCents)}") }
                }
            }
        }

        SectionTitle("Gastos nesta fatura", action = { TextButton(onClick = onAddPurchase) { Text("+ Gasto") } })
        if (invoice.items.isEmpty()) {
            Text("Nenhum gasto nesta fatura.", style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        invoice.items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onOpenTx(item.tx) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.tx.description, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val date = ddmm(Schedule.toLocal(item.tx.date, tz).date)
                    Text(
                        if (item.count > 1) "$date · parcela ${item.number} de ${item.count}" else "$date · ${item.tx.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                }
                Text(Format.money(item.amountCents), style = MaterialTheme.typography.titleSmall)
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onAddPurchase, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Gasto no cartão")
        }
    }
}

/** Cadastro do cartão: nome, dia em que a fatura fecha, dia em que vence e limite. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardEditor(card: CreditCard?, onSave: (CreditCard) -> Unit, onDelete: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(card?.name ?: "") }
    var closing by remember { mutableStateOf(card?.closingDay?.toString() ?: "") }
    var due by remember { mutableStateOf(card?.dueDay?.toString() ?: "") }
    var limit by remember { mutableStateOf(card?.limitCents?.let { Format.money(it).removePrefix("R$ ") } ?: "") }
    var color by remember { mutableStateOf(card?.color ?: 0) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save() {
        val closingDay = closing.toIntOrNull()
        val dueDay = due.toIntOrNull()
        error = when {
            name.isBlank() -> "Dê um nome ao cartão (ex.: Nubank)"
            closingDay == null || closingDay !in 1..31 -> "O dia do fechamento vai de 1 a 31"
            dueDay == null || dueDay !in 1..31 -> "O dia do vencimento vai de 1 a 31"
            else -> null
        }
        if (error != null) return
        onSave(
            CreditCard(
                id = card?.id ?: newId(),
                name = name.trim(),
                closingDay = closingDay!!,
                dueDay = dueDay!!,
                limitCents = Format.parseMoney(limit)?.takeIf { it > 0 },
                color = color,
            ),
        )
        onDismiss()
    }

    AdaptiveSheet(onDismiss) {
        Text(if (card == null) "Novo cartão" else "Editar cartão", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = { Text("Nome do cartão") },
            placeholder = { Text("Ex.: Nubank") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                value = closing,
                onValueChange = { v -> closing = v.filter { it.isDigit() }.take(2); error = null },
                label = { Text("Fecha no dia") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = due,
                onValueChange = { v -> due = v.filter { it.isDigit() }.take(2); error = null },
                label = { Text("Vence no dia") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Compras a partir do dia do fechamento vão para a fatura seguinte.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        OutlinedTextField(
            value = limit,
            onValueChange = { v -> limit = v.filter { it.isDigit() || it == ',' || it == '.' } },
            label = { Text("Limite (opcional)") },
            prefix = { Text("R$ ") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        ChipGroup("Cor") {
            ZColors.events.indices.forEach { i ->
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(ZColors.event(i)).clickable { color = i },
                    contentAlignment = Alignment.Center,
                ) {
                    if (i == color) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (card != null) {
                OutlinedButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Excluir")
                }
            }
            Button(onClick = ::save, modifier = Modifier.weight(1f).height(48.dp)) { Text("Salvar") }
        }
    }

    if (confirmDelete && card != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir ${card.name}?") },
            text = { Text("Os gastos lançados neste cartão também serão apagados. Faturas já pagas continuam no saldo.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(card.id)
                    onDismiss()
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

/** Acerta o saldo com o que o banco mostra: o Zentro lança a diferença como "Ajuste de saldo". */
@Composable
fun AdjustBalanceDialog(current: Long, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    var negative by remember { mutableStateOf(current < 0) }
    var text by remember { mutableStateOf(Format.money(kotlin.math.abs(current)).removePrefix("R$ ")) }
    val cents = Format.parseMoney(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustar saldo") },
        text = {
            Column {
                Text(
                    "Quanto você tem hoje somando suas contas? O Zentro lança a diferença como \"${Finance.ADJUSTMENT}\".",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it == ',' || it == '.' } },
                    prefix = { Text(if (negative) "- R$ " else "R$ ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { negative = !negative }) { Text(if (negative) "O saldo é positivo" else "O saldo está negativo") }
            }
        },
        confirmButton = {
            TextButton(enabled = cents != null, onClick = {
                onSave(if (negative) -(cents ?: 0) else cents ?: 0)
                onDismiss()
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Com quanto de saldo terminou cada mês. */
@Composable
fun BalanceHistory(history: List<Finance.Month>, today: LocalDate, onPick: (LocalDate) -> Unit) {
    if (history.isEmpty()) return
    val current = Finance.firstOfMonth(today)
    SectionTitle("Saldo no fim de cada mês")
    ZCard {
        history.take(12).forEachIndexed { i, m ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onPick(m.month) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(Format.monthYear(m.month), style = MaterialTheme.typography.bodyLarge)
                    if (m.month == current) {
                        Text("Mês em andamento", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        Format.money(m.closing),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (m.closing < 0) ZColors.Expense else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        Format.money(m.result, signed = true) + " no mês",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (m.result < 0) ZColors.Expense else ZColors.Income,
                    )
                }
            }
        }
    }
}

/** Abre o cadastro de um cartão (null = novo). */
data class CardEditRequest(val card: CreditCard?)
