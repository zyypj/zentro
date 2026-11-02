package dev.zentro.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.zentro.app.Platform
import dev.zentro.app.data.AppData
import dev.zentro.app.data.Repository
import dev.zentro.app.data.SyncStatus
import dev.zentro.app.data.UpdateState
import dev.zentro.app.data.newId
import dev.zentro.shared.Format
import dev.zentro.shared.MercadoPagoStatus
import dev.zentro.shared.Person
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
    var addingPerson by remember { mutableStateOf(false) }
    var removingPerson by remember { mutableStateOf<Person?>(null) }
    var editingPerson by remember { mutableStateOf<Person?>(null) }
    var changingPassword by remember { mutableStateOf(false) }
    val updateState by platform.updates.state.collectAsState()

    ScrollableColumn(
        Modifier.widthIn(max = 760.dp),
        Modifier.padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 48.dp),
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
                val ok = status.error == null && data.outbox.isEmpty()
                StatusDot(if (ok) ZColors.Income else ZColors.Warning)
                Text(
                    when {
                        status.syncing -> "Salvando..."
                        ok -> "Tudo salvo"
                        else -> "Sem conexão agora. Suas alterações ficam guardadas e são enviadas quando a internet voltar."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
            }
            Row {
                TextButton(onClick = { changingPassword = true }) { Text("Trocar senha") }
                TextButton(onClick = {
                    repo.reuploadAll()
                    onMessage("Enviando seus dados de novo")
                }) { Text("Enviar meus dados de novo") }
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

        // ---------- pessoas ----------
        SectionTitle("Pessoas")
        ZCard {
            Text(
                "Agendas que você acompanha junto com a sua. Os compromissos delas aparecem com o nome e também te avisam.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                data.activePeople.forEach { person ->
                    InputChip(
                        selected = false,
                        onClick = { editingPerson = person },
                        label = { Text(person.name) },
                        leadingIcon = { StatusDot(ZColors.event(person.color)) },

                    )
                }
                AssistChip(onClick = { addingPerson = true }, label = { Text("Adicionar pessoa") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)) })
            }
        }

        // ---------- IAs ----------
        SectionTitle("Claude e ChatGPT")
        AiCard(repo, session.serverUrl, onMessage)

        // ---------- Telegram ----------
        SectionTitle("Telegram (aviso reserva)")
        TelegramCard(repo, data, onMessage)

        // ---------- Mercado Pago ----------
        SectionTitle("Mercado Pago")
        MercadoPagoCard(repo, data, session.serverUrl, tz, onMessage)

        // ---------- versão ----------
        SectionTitle("Versão")
        ZCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Zentro ${platform.updates.current}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when (val u = updateState) {
                            is UpdateState.Available -> "Versão ${u.info.version} disponível"
                            is UpdateState.Downloading -> "Baixando ${u.info.version}... ${(u.progress * 100).toInt()}%"
                            is UpdateState.Ready -> "Versão ${u.info.version} pronta para instalar"
                            is UpdateState.Failed -> u.message
                            UpdateState.None -> "Versão atual"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = {
                    scope.launch {
                        if (platform.updates.check() == null) onMessage("Você já está na versão mais recente")
                    }
                }) { Text("Procurar") }
            }
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = { scope.launch { repo.logout() } }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Rounded.ExitToApp, null, Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Sair da conta")
        }
        Text(
            "Zentro ${platform.updates.current}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp).align(Alignment.CenterHorizontally),
        )
    }

    if (changingPassword) {
        ChangePasswordDialog(repo, onDone = { msg -> changingPassword = false; msg?.let(onMessage) })
    }

    if (addingPerson) {
        AddPersonDialog(onDismiss = { addingPerson = false }) { name ->
            repo.savePerson(Person(id = newId(), name = name, color = (data.activePeople.size + 4) % ZColors.events.size))
        }
    }
    editingPerson?.let { person ->
        PersonDialog(
            person = person,
            onSave = {
                repo.savePerson(it)
                editingPerson = null
            },
            onRemove = {
                editingPerson = null
                removingPerson = person
            },
            onDismiss = { editingPerson = null },
        )
    }
    removingPerson?.let { person ->
        AlertDialog(
            onDismissRequest = { removingPerson = null },
            title = { Text("Remover ${person.name}?") },
            text = { Text("Os compromissos já marcados continuam na agenda, só sem o nome dela.") },
            confirmButton = {
                TextButton(onClick = {
                    repo.deletePerson(person.id)
                    removingPerson = null
                }) { Text("Remover") }
            },
            dismissButton = { TextButton(onClick = { removingPerson = null }) { Text("Cancelar") } },
        )
    }

    if (addingTime) {
        TimePickerModal(LocalTime(12, 0), { t ->
            val value = Format.time(kotlinx.datetime.LocalDateTime(kotlinx.datetime.LocalDate(2000, 1, 1), t))
            repo.updateSettings(SettingsPatch(reviewTimes = (data.settings.reviewTimes + value).distinct()))
        }, { addingTime = false })
    }
}

/** Editar uma pessoa: nome e cor dos compromissos dela. */
@Composable
private fun PersonDialog(person: Person, onSave: (Person) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(person.name) }
    var color by remember { mutableStateOf(person.color) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar pessoa") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.replace("\n", "") },
                    label = { Text("Nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Cor", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                ColorChoices(color, { color = it })
                TextButton(onClick = onRemove, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Remover ${person.name}", color = ZColors.Expense)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(person.copy(name = name.trim(), color = color)) }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun TelegramCard(repo: Repository, data: AppData, onMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var loading by remember { mutableStateOf(false) }
    var disabled by remember { mutableStateOf(false) }

    ZCard {
        Text(
            "Receba seus lembretes também no Telegram. Eles chegam no celular, no relógio e no PC, mesmo se o celular estiver desligado.",
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
                "O Telegram ainda não está disponível.",
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
                    "4. Cole abaixo.",
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
                    "Última importação: ${Format.shortDate(Schedule.toLocal(it, tz).date)} às ${Format.time(Schedule.toLocal(it, tz))}",
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
        }
    }
}

/**
 * Conecta o Claude e o ChatGPT às notas (via MCP). Depois de conectar, basta citar a nota numa
 * conversa — "faça o que está na nota Bed Wars" — e a IA lê e segue o que você escreveu.
 */
@Composable
private fun AiCard(repo: Repository, serverUrl: String, onMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var link by remember { mutableStateOf<dev.zentro.shared.AiLink?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun load(reset: Boolean) {
        busy = true
        scope.launch {
            repo.aiLink(reset).onSuccess {
                link = it
                if (reset) onMessage("Chave nova criada. A anterior parou de funcionar.")
            }.onFailure { onMessage(it.message ?: "Sem conexão") }
            busy = false
        }
    }

    ZCard {
        Text(
            "Deixe o Claude e o ChatGPT lerem suas notas. Depois é só pedir numa conversa: “faça o que está na nota Bed Wars”.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        val current = link
        if (current == null) {
            Button(enabled = !busy, onClick = { load(reset = false) }) { Text(if (busy) "Gerando..." else "Conectar") }
            return@ZCard
        }

        val claude = "claude mcp add --scope user --transport http zentro $serverUrl/mcp --header \"Authorization: Bearer ${current.token}\""
        val chatgpt = serverUrl + current.mcpPath

        Text("Claude Code", style = MaterialTheme.typography.titleSmall)
        Text(
            "Cole uma vez no terminal. Vale para todos os seus projetos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CopyBox(claude) {
            clipboard.setText(AnnotatedString(claude))
            onMessage("Comando copiado")
        }

        Spacer(Modifier.height(12.dp))
        Text("ChatGPT", style = MaterialTheme.typography.titleSmall)
        Text(
            "Em Configurações › Aplicativos e conectores › Criar, cole este endereço.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CopyBox(chatgpt) {
            clipboard.setText(AnnotatedString(chatgpt))
            onMessage("Endereço copiado")
        }
        if (!serverUrl.startsWith("https://")) {
            Text(
                "O ChatGPT só aceita endereços que começam com https://. Com o Claude Code funciona assim mesmo.",
                style = MaterialTheme.typography.bodySmall,
                color = ZColors.Warning,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Não compartilhe: quem tiver esta chave consegue ler suas notas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(enabled = !busy, onClick = { load(reset = true) }) { Text("Gerar chave nova") }
    }
}

@Composable
private fun CopyBox(text: String, onCopy: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
            maxLines = 3,
            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
        )
        TextButton(onClick = onCopy) { Text("Copiar") }
    }
}

/** Troca a senha. Esqueceu a atual? No console do servidor, digite "senha seu@email.com". */
@Composable
private fun ChangePasswordDialog(repo: Repository, onDone: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text("Trocar senha") },
        text = {
            Column {
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it; error = null },
                    label = { Text("Senha atual") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = new,
                    onValueChange = { new = it; error = null },
                    label = { Text("Nova senha") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = { Text(error ?: "Mínimo de 6 caracteres") },
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && current.isNotEmpty() && new.length >= 6, onClick = {
                busy = true
                scope.launch {
                    repo.changePassword(current, new)
                        .onSuccess { onDone("Senha trocada") }
                        .onFailure { error = it.message ?: "Não consegui trocar a senha" }
                    busy = false
                }
            }) { Text("Trocar") }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancelar") } },
    )
}
