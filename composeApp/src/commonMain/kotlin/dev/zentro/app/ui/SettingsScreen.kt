package dev.zentro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.zentro.app.Platform
import dev.zentro.app.data.AppData
import dev.zentro.app.data.Repository
import dev.zentro.app.data.SyncStatus
import dev.zentro.shared.Format
import dev.zentro.shared.MercadoPagoStatus
import dev.zentro.shared.Schedule
import dev.zentro.shared.SettingsPatch
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(repo: Repository, platform: Platform, data: AppData, status: SyncStatus, tz: TimeZone, onMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val session = data.session ?: return
    var addingTime by remember { mutableStateOf(false) }

    Column(
        Modifier.widthIn(max = 760.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 48.dp),
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium)

        // ---------- conta ----------
        SectionTitle("Conta")
        ZCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(session.user.name, style = MaterialTheme.typography.titleMedium)
                    Text(session.user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (status.online) ZColors.Income else if (status.error != null) ZColors.Warning else MaterialTheme.colorScheme.outline)
                Text(
                    buildString {
                        append(if (status.online) "Conectado em tempo real" else "Sem conexão em tempo real")
                        data.lastSyncOk?.let { append(" · sincronizado às ${Format.time(Schedule.toLocal(it, tz))}") }
                        if (data.outbox.isNotEmpty()) append(" · ${data.outbox.size} pendente(s)")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                if (status.syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            status.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = ZColors.Warning, modifier = Modifier.padding(top = 4.dp))
            }
            Text(session.serverUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = { scope.launch { if (repo.sync()) onMessage("Tudo sincronizado") } }) {
                Icon(ZIcons.Sync, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Sincronizar agora")
            }
        }

        // ---------- confiabilidade dos avisos ----------
        SectionTitle("Avisos garantidos")
        ZCard {
            platform.ReliabilityChecklist()
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { platform.testNotification(); onMessage("Aviso de teste em 5 segundos") }) {
                Icon(Icons.Rounded.Notifications, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Testar aviso")
            }
        }

        // ---------- lembrete diário ----------
        SectionTitle("Cobrar atualização da agenda")
        ZCard {
            Text(
                "Se você ainda não mexeu na agenda no dia, o Zentro te lembra nestes horários:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                data.settings.reviewTimes.forEach { time ->
                    InputChip(
                        selected = false,
                        onClick = { repo.updateSettings(SettingsPatch(reviewTimes = data.settings.reviewTimes - time)) },
                        label = { Text(time) },
                        trailingIcon = { Icon(Icons.Rounded.Close, "Remover $time", Modifier.size(16.dp)) },
                    )
                }
                if (data.settings.reviewTimes.size < 6) {
                    AssistChip(onClick = { addingTime = true }, label = { Text("Adicionar horário") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)) })
                }
            }
        }

        // ---------- Telegram ----------
        SectionTitle("Telegram (aviso reserva)")
        TelegramCard(repo, data, onMessage)

        // ---------- Mercado Pago ----------
        SectionTitle("Mercado Pago")
        MercadoPagoCard(repo, data, session.serverUrl, tz, onMessage)

        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = { scope.launch { repo.logout() } }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Rounded.ExitToApp, null, Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Sair da conta")
        }
        Text(
            "Zentro · ${platform.name}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp).align(Alignment.CenterHorizontally),
        )
    }

    if (addingTime) {
        TimePickerModal(LocalTime(12, 0), { t ->
            val value = Format.time(kotlinx.datetime.LocalDateTime(kotlinx.datetime.LocalDate(2000, 1, 1), t))
            repo.updateSettings(SettingsPatch(reviewTimes = (data.settings.reviewTimes + value).distinct()))
        }, { addingTime = false })
    }
}

@Composable
private fun TelegramCard(repo: Repository, data: AppData, onMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var loading by remember { mutableStateOf(false) }
    var disabled by remember { mutableStateOf(false) }

    ZCard {
        Text(
            "Mesmo com o celular desligado ou sem bateria, o servidor manda seus lembretes pelo Telegram — chega no celular, no relógio e no PC.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        when {
            data.settings.telegramLinked -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = ZColors.Income)
                    Text("Telegram conectado", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp).weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        scope.launch { onMessage(repo.telegramTest().fold({ "Mensagem enviada no Telegram" }, { it.message ?: "Falhou" })) }
                    }) { Text("Enviar teste") }
                    TextButton(onClick = { scope.launch { repo.telegramUnlink() } }) { Text("Desconectar") }
                }
            }
            disabled -> Text(
                "O servidor ainda não tem um bot do Telegram. Defina TELEGRAM_BOT_TOKEN no painel do servidor para ativar.",
                style = MaterialTheme.typography.bodySmall,
                color = ZColors.Warning,
            )
            else -> Button(
                enabled = !loading,
                onClick = {
                    loading = true
                    scope.launch {
                        repo.telegramLink().onSuccess { link ->
                            when {
                                !link.enabled -> disabled = true
                                link.url != null -> {
                                    uri.openUri(link.url!!)
                                    onMessage("Toque em INICIAR no Telegram para concluir")
                                }
                            }
                        }.onFailure { onMessage(it.message ?: "Falhou") }
                        loading = false
                    }
                },
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Conectar Telegram")
            }
        }
    }
}

@Composable
private fun MercadoPagoCard(repo: Repository, data: AppData, serverUrl: String, tz: TimeZone, onMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<MercadoPagoStatus?>(null) }

    LaunchedEffect(data.settings.mercadoPagoConnected) {
        status = repo.mercadoPago().getOrNull()
    }

    ZCard {
        if (!data.settings.mercadoPagoConnected) {
            Text(
                "Importe automaticamente o que entra e sai da sua conta Mercado Pago.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "1. Acesse mercadopago.com.br/developers/panel\n" +
                    "2. Crie uma aplicação (qualquer nome)\n" +
                    "3. Em \"Credenciais de produção\", copie o Access Token (APP_USR-...)\n" +
                    "4. Cole abaixo. O token fica só no seu servidor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = token,
                onValueChange = { token = it.trim() },
                label = { Text("Access Token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(enabled = token.length > 20 && !busy, onClick = {
                busy = true
                scope.launch {
                    repo.mercadoPagoConnect(token).onSuccess {
                        status = it
                        token = ""
                        onMessage(it.message ?: "Mercado Pago conectado")
                    }.onFailure { onMessage(it.message ?: "Falhou") }
                    busy = false
                }
            }) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Conectar")
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = ZColors.Income)
                Text("Mercado Pago conectado", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp))
            }
            data.settings.mercadoPagoLastSync?.let {
                Text(
                    "Última importação: ${Format.shortDate(Schedule.toLocal(it, tz).date)} às ${Format.time(Schedule.toLocal(it, tz))} · atualiza sozinho a cada 15 min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        repo.mercadoPagoSync().onSuccess { onMessage(it.message ?: "Atualizado") }.onFailure { onMessage(it.message ?: "Falhou") }
                        busy = false
                    }
                }) { Text(if (busy) "Importando..." else "Importar agora") }
                TextButton(onClick = { scope.launch { repo.mercadoPagoDisconnect() } }) { Text("Desconectar") }
            }
            status?.webhookPath?.let { path ->
                val url = serverUrl + path
                Spacer(Modifier.height(8.dp))
                Text(
                    "Opcional, para importar na hora: cadastre esta URL em Webhooks › Pagamentos no painel do Mercado Pago:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { clipboard.setText(AnnotatedString(url)); onMessage("URL copiada") }) {
                    Text(url, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
        }
    }
}
