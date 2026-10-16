package dev.zentro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zentro.app.data.ApiClient
import dev.zentro.app.data.ApiFailure
import dev.zentro.app.data.Repository
import dev.zentro.shared.ServerInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ZentroLogo(size: Int = 64) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.3).dp))
            .background(Brush.linearGradient(listOf(Color(0xFF8B7CF6), Color(0xFF5B4BDB)))),
        contentAlignment = Alignment.Center,
    ) {
        Text("Z", color = Color.White, fontWeight = FontWeight.Black, fontSize = (size * 0.5).sp)
    }
}

@Composable
fun LoginScreen(repo: Repository) {
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<ServerInfo?>(null) }
    var checking by remember { mutableStateOf(false) }

    // Verifica o servidor enquanto o usuário digita, para dar retorno imediato.
    LaunchedEffect(server) {
        info = null
        if (server.length < 4) return@LaunchedEffect
        delay(600)
        checking = true
        info = try {
            ApiClient(server, null).info()
        } catch (_: ApiFailure) {
            null
        }
        checking = false
        if (info?.registrationOpen == false) register = false
    }

    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ZentroLogo()
            Spacer(Modifier.height(16.dp))
            Text("Zentro", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Sua agenda e suas finanças, sempre com você.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = server,
                onValueChange = { server = it.trim() },
                label = { Text("Endereço") },
                placeholder = { Text("ex.: 123.45.67.89:8080") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                supportingText = {
                    when {
                        checking -> Text("Procurando...")
                        info != null -> Text("✓ Encontrado", color = ZColors.Income)
                        server.length >= 4 -> Text("Não encontrado")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !register, onClick = { register = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                    Text("Entrar")
                }
                SegmentedButton(
                    selected = register,
                    onClick = { register = true },
                    enabled = info?.registrationOpen != false,
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Criar conta") }
            }
            Spacer(Modifier.height(8.dp))

            if (register) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Seu nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim() },
                label = { Text("E-mail") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = if (register) ({ Text("Mínimo de 6 caracteres") }) else null,
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                enabled = !loading && server.isNotBlank() && email.isNotBlank() && password.isNotBlank(),
                onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        error = repo.login(server, email, password, name, register)
                        loading = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(if (register) "Criar conta" else "Entrar")
                }
            }
        }
    }
}
