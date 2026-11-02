package dev.zentro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zentro.app.data.SyncStatus

/** Algo que dá para criar pelo botão "Novo item". */
enum class NewItem(val label: String, val icon: ImageVector) {
    EVENT("Compromisso", Line.Calendar),
    BILL("Conta a pagar", Line.Repeat),
    NOTE("Anotação", Line.Note),
    TRANSACTION("Movimentação", Line.TrendUp),
    CARD_PURCHASE("Gasto no cartão", Line.Card),
}

/** Situação da sincronização em uma frase, com a cor da bolinha. */
fun syncLine(status: SyncStatus, pending: Int): Pair<String, Color> = when {
    status.sessionExpired -> "Sessão expirada" to ZColors.Expense
    status.syncing -> "Sincronizando…" to ZColors.Warning
    status.error != null && pending > 0 -> "$pending alteração(ões) esperando conexão" to ZColors.Warning
    status.error != null -> "Sem conexão com o servidor" to ZColors.Warning
    pending > 0 -> "$pending alteração(ões) para enviar" to ZColors.Warning
    else -> "Tudo sincronizado" to Color(0xFF6AAE8C)
}

fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.let { parts ->
        when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(2)
            else -> "${parts.first().first()}${parts.last().first()}"
        }
    }.uppercase()

@Composable
fun Avatar(name: String, size: Int = 34) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color(0xFF2C2924)), contentAlignment = Alignment.Center) {
        ZText(initials(name), (size * 0.36).sp, 700, Color(0xFFCDBFA9))
    }
}

// ---------------------------------------------------------------------------------------------
// Desktop
// ---------------------------------------------------------------------------------------------

/** Barra lateral do desktop: marca, navegação, pastas das notas, sincronização e perfil. */
@Composable
fun DesktopSidebar(
    tab: Tab,
    onTab: (Tab) -> Unit,
    counts: Map<Tab, Int>,
    folders: List<Pair<String, Int>>,
    selectedFolder: String?,
    onFolder: (String) -> Unit,
    onNewFolder: () -> Unit,
    version: String,
    status: SyncStatus,
    pending: Int,
    userName: String,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.width(258.dp).fillMaxHeight().background(ZPalette.Sidebar)
            .drawBehind { drawRect(ZPalette.Line, Offset(size.width - 1.dp.toPx(), 0f), Size(1.dp.toPx(), size.height)) }
            .padding(start = 15.dp, end = 15.dp, top = 25.dp, bottom = 20.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ZentroWordmark(34.dp, 23)
            Spacer(Modifier.weight(1f))
            ZText("v$version", 11.sp, 400, ZPalette.Quiet)
        }
        Spacer(Modifier.height(35.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Eyebrow("Espaço de trabalho", Modifier.padding(start = 17.dp, bottom = 13.dp), ZPalette.Quiet)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Tab.entries.forEach { t ->
                    NavItem(t.label, t.icon, tab == t && (t != Tab.NOTES || selectedFolder == null), counts[t]) { onTab(t) }
                }
            }
            HLine(Modifier.padding(horizontal = 14.dp, vertical = 24.dp), ZPalette.Line)
            Eyebrow("Suas pastas", Modifier.padding(start = 17.dp, bottom = 10.dp), ZPalette.Quiet)
            folders.forEach { (name, count) ->
                val selected = tab == Tab.NOTES && selectedFolder == name
                Row(
                    Modifier.fillMaxWidth().height(37.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (selected) ZPalette.PurpleSoft else Color.Transparent)
                        .clickable { onFolder(name) }.padding(start = 17.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Icon(Line.Folder, null, tint = if (selected) ZPalette.PurpleText else ZPalette.Quiet, modifier = Modifier.size(16.dp))
                    ZText(name, 13.sp, if (selected) 600 else 450, if (selected) ZPalette.PurpleText else Color(0xFFA9A6B0), Modifier.weight(1f), maxLines = 1)
                    ZText(count.toString(), 11.sp, 500, ZPalette.Quiet)
                }
            }
            ZText(
                "+ Nova pasta", 12.sp, 500, Color(0xFF8E85A8),
                Modifier.padding(start = 13.dp, top = 10.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onNewFolder).padding(4.dp),
            )
        }
        val (syncText, syncColor) = syncLine(status, pending)
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(syncColor))
            ZText(syncText, 11.sp, 400, ZPalette.Muted, maxLines = 1)
        }
        HLine(color = ZPalette.Line)
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(9.dp)).clickable(onClick = onProfile).padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Avatar(userName)
            Column(Modifier.weight(1f)) {
                ZText(userName.ifBlank { "Você" }, 12.sp, 650, maxLines = 1)
                ZText("Conta pessoal", 11.sp, 400, ZPalette.Quiet, maxLines = 1)
            }
            Icon(Line.ChevronRight, null, tint = ZPalette.Quiet, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, count: Int?, onClick: () -> Unit) {
    val accent = ZPalette.Purple
    Row(
        Modifier.fillMaxWidth().height(45.dp).clip(RoundedCornerShape(9.dp))
            .background(if (selected) ZPalette.PurpleSoft else Color.Transparent)
            .drawBehind {
                if (selected) drawRect(accent, Offset(0f, 11.dp.toPx()), Size(3.dp.toPx(), size.height - 22.dp.toPx()))
            }
            .clickable(onClick = onClick).padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Icon(icon, null, tint = if (selected) ZPalette.PurpleText else Color(0xFF85828D), modifier = Modifier.size(19.dp))
        ZText(label, 14.sp, if (selected) 650 else 550, if (selected) ZPalette.PurpleText else Color(0xFFB3B0BA), Modifier.weight(1f), maxLines = 1)
        if (count != null && count > 0) ZText(count.toString(), 11.sp, 600, ZPalette.Quiet)
    }
}

/** Barra de cima do desktop: caminho, busca (Ctrl+K), avisos e "Novo item". */
@Composable
fun DesktopToolbar(
    crumbs: List<String>,
    onSearch: () -> Unit,
    hasNews: Boolean,
    onBell: () -> Unit,
    bellPopup: @Composable () -> Unit,
    onNewItem: (NewItem) -> Unit,
    canAddCardPurchase: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().height(76.dp)
            .drawBehind { drawRect(ZPalette.Line, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx())) }
            .padding(start = 48.dp, end = 41.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            crumbs.forEachIndexed { i, c ->
                if (i > 0) Icon(Line.ChevronRight, null, tint = ZPalette.Quiet, modifier = Modifier.size(14.dp))
                val last = i == crumbs.lastIndex
                ZText(c, 12.5.sp, if (last) 650 else 400, if (last) Color(0xFFC9C6D0) else ZPalette.Quiet, maxLines = 1)
            }
        }
        SearchBoxButton("Buscar no Zentro", onSearch)
        Box {
            BellButton(hasNews, onBell)
            bellPopup()
        }
        Box {
            var menu by remember { mutableStateOf(false) }
            PrimaryAction("Novo item", { menu = true })
            NewItemMenu(menu, { menu = false }, onNewItem, canAddCardPurchase)
        }
    }
}

@Composable
fun NewItemMenu(expanded: Boolean, onDismiss: () -> Unit, onPick: (NewItem) -> Unit, canAddCardPurchase: Boolean) {
    DropdownMenu(expanded, onDismiss, containerColor = ZPalette.CardHigh) {
        NewItem.entries.filter { it != NewItem.CARD_PURCHASE || canAddCardPurchase }.forEach { item ->
            DropdownMenuItem(
                text = { ZText(item.label, 13.sp, 500) },
                leadingIcon = { Icon(item.icon, null, tint = ZPalette.Muted, modifier = Modifier.size(18.dp)) },
                onClick = {
                    onDismiss()
                    onPick(item)
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Celular
// ---------------------------------------------------------------------------------------------

/** Barra de abas do celular, como no mockup: ícone e nome, roxo na aba atual. */
@Composable
fun MobileTabBar(tab: Tab, onTab: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(ZPalette.Sidebar)
            .drawBehind { drawRect(ZPalette.Line, Offset.Zero, Size(size.width, 1.dp.toPx())) }
            .navigationBarsPadding().height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { t ->
            val selected = t == tab
            val color = if (selected) ZPalette.PurpleText else Color(0xFF7D7A85)
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable { onTab(t) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(if (t == Tab.FINANCE) Line.Chart else t.icon, t.label, tint = color, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(4.dp))
                ZText(t.label, 10.5.sp, if (selected) 650 else 500, color, maxLines = 1)
            }
        }
    }
}

/** Cabeçalho das telas no celular: título grande e ações à direita. */
@Composable
fun MobileHeader(title: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ZText(title, 30.sp, 700, tracking = -0.035, modifier = Modifier.weight(1f), maxLines = 1)
        actions()
    }
}

/** Fundo para telas que ocupam a área de conteúdo inteira. */
@Composable
fun ScreenSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().background(ZPalette.Background)) { content() }
}
