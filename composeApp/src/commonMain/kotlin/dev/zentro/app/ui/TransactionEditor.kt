package dev.zentro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zentro.app.data.newId
import dev.zentro.shared.Categories
import dev.zentro.shared.Format
import dev.zentro.shared.QuickInput
import dev.zentro.shared.Schedule
import dev.zentro.shared.Transaction
import dev.zentro.shared.TxSource
import dev.zentro.shared.TxType
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

data class TxEditRequest(val transaction: Transaction?, val type: TxType = TxType.EXPENSE)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionEditor(
    request: TxEditRequest,
    tz: TimeZone,
    onSave: (Transaction) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val original = request.transaction
    var type by remember { mutableStateOf(original?.type ?: request.type) }
    var amount by remember { mutableStateOf(original?.let { Format.money(it.amountCents).removePrefix("R$ ") } ?: "") }
    var description by remember { mutableStateOf(original?.description ?: "") }
    var category by remember { mutableStateOf(original?.category) }
    var date by remember { mutableStateOf(Schedule.toLocal(original?.date ?: Schedule.now(), tz).date) }
    var showDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val categories = if (type == TxType.INCOME) Categories.income else Categories.expense
    val accent = if (type == TxType.INCOME) ZColors.Income else ZColors.Expense

    LaunchedEffect(Unit) { if (original == null) runCatching { focus.requestFocus() } }

    fun save() {
        val cents = Format.parseMoney(amount)
        if (cents == null || cents <= 0) {
            error = "Informe o valor"
            return
        }
        val finalCategory = category ?: QuickInput.guessCategory(description, type)
        // Mantém o horário original; lançamentos novos ficam ao meio-dia para não "pular" de dia com fuso.
        val time = original?.let { Schedule.toLocal(it.date, tz).time } ?: LocalTime(12, 0)
        onSave(
            Transaction(
                id = original?.id ?: newId(),
                type = type,
                amountCents = cents,
                description = description.trim().ifEmpty { finalCategory },
                category = finalCategory,
                date = Schedule.toMillis(LocalDateTime(date, time), tz),
                source = original?.source ?: TxSource.MANUAL,
                externalId = original?.externalId,
            ),
        )
        onDismiss()
    }

    AdaptiveSheet(onDismiss) {
        Text(if (original == null) "Nova movimentação" else "Editar movimentação", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = type == TxType.EXPENSE,
                onClick = { type = TxType.EXPENSE; category = null },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                icon = { Icon(ZIcons.TrendingDown, null, Modifier.size(18.dp)) },
            ) { Text("Saída") }
            SegmentedButton(
                selected = type == TxType.INCOME,
                onClick = { type = TxType.INCOME; category = null },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                icon = { Icon(ZIcons.TrendingUp, null, Modifier.size(18.dp)) },
            ) { Text("Entrada") }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = { v -> amount = v.filter { it.isDigit() || it == ',' || it == '.' }; error = null },
            prefix = { Text("R$ ", style = MaterialTheme.typography.headlineSmall) },
            placeholder = { Text("0,00", style = MaterialTheme.typography.headlineSmall) },
            textStyle = MaterialTheme.typography.headlineSmall.merge(MoneyStyle).copy(color = accent, fontSize = 28.sp),
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Descrição") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        val suggested = QuickInput.guessCategory(description, type)
        ChipGroup("Categoria") {
            categories.forEach { c ->
                FilterChip(selected = (category ?: suggested) == c, onClick = { category = c }, label = { Text(c) })
            }
        }

        Spacer(Modifier.height(12.dp))
        LabeledRow("Data", Format.shortDate(date) + "/" + date.year, { showDate = true }, Icons.Rounded.DateRange)

        if (original?.source == TxSource.MERCADO_PAGO) {
            Text(
                "Importado do Mercado Pago",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().height(28.dp),
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (original != null) {
                OutlinedButton(onClick = { onDelete(original.id); onDismiss() }) {
                    Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Excluir")
                }
            }
            Button(
                onClick = ::save,
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier.weight(1f).height(48.dp),
            ) { Text("Salvar") }
        }
    }

    if (showDate) DatePickerModal(date, { date = it }, { showDate = false })
}
