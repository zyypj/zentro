package dev.zentro.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.zentro.shared.EventCategories
import dev.zentro.shared.Format
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

// ---------------------------------------------------------------------------------------------
// Texto
// ---------------------------------------------------------------------------------------------

/** Atalho para texto em Inter com tamanho e peso exatos do mockup. */
@Composable
fun ZText(
    text: String,
    size: TextUnit = 13.sp,
    weight: Int = 400,
    color: Color = ZPalette.Ink,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    tracking: Double = 0.0,
    align: TextAlign? = null,
    tabular: Boolean = false,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    Text(
        text,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
        style = TextStyle(
            fontFamily = InterFamily,
            fontSize = size,
            fontWeight = FontWeight(weight),
            letterSpacing = tracking.em,
            lineHeight = lineHeight,
            fontFeatureSettings = if (tabular) "tnum" else null,
        ),
    )
}

/** Rótulo pequeno em caixa alta (ex.: "CAPTURA RÁPIDA"). */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = ZPalette.Muted) {
    ZText(text.uppercase(), 10.5.sp, 700, color, modifier, maxLines = 1, tracking = 0.11)
}

/** Linha divisória fina. */
@Composable
fun HLine(modifier: Modifier = Modifier, color: Color = ZPalette.SoftLine) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** "Ver agenda completa →" */
@Composable
fun LinkAction(text: String, onClick: () -> Unit, color: Color = ZPalette.PurpleText, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        ZText(text, 11.5.sp, 650, color, maxLines = 1)
        Icon(Line.ArrowRight, null, tint = color, modifier = Modifier.size(13.dp))
    }
}

@Composable
fun CountPill(text: String, modifier: Modifier = Modifier, bg: Color = ZPalette.CardHigh, color: Color = ZPalette.Muted) {
    ZText(text, 10.5.sp, 500, color, modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 4.dp), maxLines = 1)
}

/** Cabeçalho de seção: título, contagem opcional e ação à direita. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: String? = null,
    countBg: Color = ZPalette.CardHigh,
    countColor: Color = ZPalette.Muted,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    size: TextUnit = 17.sp,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ZText(title, size, 700, tracking = -0.02, maxLines = 1)
        count?.let { CountPill(it, bg = countBg, color = countColor) }
        Spacer(Modifier.weight(1f))
        if (action != null && onAction != null) LinkAction(action, onAction)
    }
}

// ---------------------------------------------------------------------------------------------
// Etiquetas e categorias
// ---------------------------------------------------------------------------------------------

data class TagColors(val bg: Color, val text: Color, val dot: Color)

private val PurpleTag = TagColors(Color(0xFF241F3A), Color(0xFFB1A6F5), Color(0xFF8C7CDD))
private val BlueTag = TagColors(Color(0xFF1A2432), Color(0xFF8FB0DC), Color(0xFF7B9FD4))
private val GreenTag = TagColors(Color(0xFF1A2821), Color(0xFF8CC0A0), Color(0xFF7FB394))
private val NeutralTag = TagColors(ZPalette.CardHigh, ZPalette.Muted, Color(0xFF8C7CDD))

fun categoryColors(category: String): TagColors = when (category) {
    EventCategories.WORK -> BlueTag
    EventCategories.SCHOOL -> PurpleTag
    EventCategories.PERSONAL -> GreenTag
    else -> NeutralTag
}

fun categoryIcon(category: String): ImageVector = when (category) {
    EventCategories.WORK -> Line.Briefcase
    EventCategories.SCHOOL -> Line.Graduation
    EventCategories.PERSONAL -> Line.User
    else -> Line.Calendar
}

@Composable
fun Tag(text: String, colors: TagColors, modifier: Modifier = Modifier) {
    ZText(
        text, 10.5.sp, 550, colors.text,
        modifier.clip(RoundedCornerShape(5.dp)).background(colors.bg).padding(horizontal = 8.dp, vertical = 4.dp),
        maxLines = 1,
    )
}

/** Bolinha com anel, como na linha do tempo do mockup. */
@Composable
fun RingDot(color: Color, modifier: Modifier = Modifier, size: Dp = 13.dp) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        drawCircle(color.copy(alpha = 0.9f), radius = r - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        drawCircle(color, radius = r * 0.5f)
    }
}

// ---------------------------------------------------------------------------------------------
// Linha do tempo
// ---------------------------------------------------------------------------------------------

/** Um compromisso na linha do tempo: horário | bolinha e fio | título, detalhes e etiqueta. */
@Composable
fun TimelineRow(
    time: String,
    title: String,
    subtitle: String,
    dotColor: Color,
    last: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: (@Composable () -> Unit)? = null,
    timeWidth: Dp = 67.dp,
    inlineTag: Boolean = false,
    titleColor: Color = ZPalette.Ink,
) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        ZText(time, 12.sp, 600, ZPalette.Muted, Modifier.width(timeWidth).padding(top = 1.dp), maxLines = 1, tabular = true)
        Box(Modifier.width(25.dp).fillMaxHeight()) {
            if (!last) {
                Box(Modifier.align(Alignment.TopCenter).padding(top = 14.dp).width(1.dp).fillMaxHeight().background(ZPalette.Line))
            }
            RingDot(dotColor, Modifier.align(Alignment.TopCenter).padding(top = 2.dp))
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    ZText(title, 14.sp, 650, titleColor, maxLines = 2, tracking = -0.01)
                    Spacer(Modifier.height(6.dp))
                    if (inlineTag && tag != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ZText(subtitle, 11.sp, 400, ZPalette.Muted, maxLines = 1)
                            tag()
                        }
                    } else {
                        ZText(subtitle, 11.sp, 400, ZPalette.Muted, maxLines = 1)
                    }
                }
                if (!inlineTag) tag?.let { Box(Modifier.padding(start = 10.dp)) { it() } }
            }
            if (!last) {
                Spacer(Modifier.height(16.dp))
                HLine()
                Spacer(Modifier.height(17.dp))
            } else {
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Peças pequenas
// ---------------------------------------------------------------------------------------------

/** Caixinha com dia e mês ("05 / NOV") das contas. */
@Composable
fun DateBox(date: LocalDate, modifier: Modifier = Modifier, highlight: Color? = null) {
    Column(
        modifier.size(width = 38.dp, height = 42.dp).clip(RoundedCornerShape(7.dp)).border(1.dp, highlight ?: ZPalette.Line, RoundedCornerShape(7.dp)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ZText(date.dayOfMonth.toString().padStart(2, '0'), 15.sp, 750, highlight ?: ZPalette.Ink, lineHeight = 17.sp, tabular = true)
        ZText(Format.monthName(date.month).take(3).uppercase(), 9.sp, 650, ZPalette.Quiet)
    }
}

/** Ícone dentro de um quadradinho colorido (notas, contas). */
@Composable
fun IconTile(icon: ImageVector, bg: Color, tint: Color, modifier: Modifier = Modifier, size: Dp = 32.dp, iconSize: Dp = 16.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(7.dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Atalho de teclado ("Ctrl K"). */
@Composable
fun Kbd(text: String, modifier: Modifier = Modifier) {
    ZText(
        text, 10.sp, 500, ZPalette.Quiet,
        modifier.clip(RoundedCornerShape(4.dp)).border(1.dp, ZPalette.Line, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
        maxLines = 1,
    )
}

/** Botão principal pequeno ("+ Novo item"). */
@Composable
fun PrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector = Line.Plus) {
    Row(
        modifier.height(38.dp).clip(RoundedCornerShape(8.dp)).background(ZPalette.Purple).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(15.dp))
        ZText(text, 12.5.sp, 650, Color.White, maxLines = 1)
    }
}

/** Botão quadrado roxo com "+", do cabeçalho das telas no celular. */
@Composable
fun SquarePlus(onClick: () -> Unit, description: String, modifier: Modifier = Modifier) {
    Box(
        modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(ZPalette.Purple).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Line.Plus, description, tint = Color.White, modifier = Modifier.size(18.dp)) }
}

/** Sino com a bolinha de aviso. */
@Composable
fun BellButton(hasNews: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(Line.Bell, "Avisos", tint = ZPalette.Muted, modifier = Modifier.size(20.dp))
        if (hasNews) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 8.dp).size(7.dp).clip(CircleShape)
                    .background(ZPalette.Background).padding(1.5.dp).clip(CircleShape).background(Color(0xFFD9907B)),
            )
        }
    }
}

/** Campo de busca que parece um input mas abre a busca global (Ctrl+K). */
@Composable
fun SearchBoxButton(placeholder: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.height(38.dp).width(290.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF161619))
            .border(1.dp, ZPalette.Line, RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(start = 13.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Icon(Line.Search, null, tint = ZPalette.Quiet, modifier = Modifier.size(16.dp))
        ZText(placeholder, 12.5.sp, 400, ZPalette.Quiet, Modifier.weight(1f), maxLines = 1)
        Kbd("Ctrl K")
    }
}

/** Chip de filtro do mockup: roxo cheio quando escolhido, contorno quando não. */
@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, iconTint: Color? = null) {
    Row(
        modifier.height(30.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) ZPalette.Purple else Color.Transparent)
            .border(1.dp, if (selected) ZPalette.Purple else ZPalette.Line, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let { Icon(it, null, tint = if (selected) Color.White else iconTint ?: ZPalette.Muted, modifier = Modifier.size(14.dp)) }
        ZText(text, 11.5.sp, if (selected) 650 else 500, if (selected) Color.White else ZPalette.Muted, maxLines = 1)
    }
}

/** Painel com borda fina (cartões do mockup). */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    bg: Color = ZPalette.Card,
    border: Color = ZPalette.Line,
    padding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(bg).border(1.dp, border, RoundedCornerShape(12.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(padding),
    ) { content() }
}

// ---------------------------------------------------------------------------------------------
// Captura rápida, revisão e ideia
// ---------------------------------------------------------------------------------------------

/** Campo "escreva e pronto" da captura rápida. Enter (ou o botão) adiciona. */
@Composable
fun QuickCapture(placeholder: String, onSubmit: (String) -> Boolean, modifier: Modifier = Modifier, compact: Boolean = false) {
    var text by remember { mutableStateOf("") }
    fun submit() {
        if (text.isNotBlank() && onSubmit(text.trim())) text = ""
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = if (compact) 52.dp else 55.dp).clip(RoundedCornerShape(11.dp)).background(ZPalette.Card)
            .border(1.dp, ZPalette.Line, RoundedCornerShape(11.dp)).padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(31.dp).clip(RoundedCornerShape(8.dp)).background(ZPalette.PurpleSoft).clickable(onClick = ::submit),
            contentAlignment = Alignment.Center,
        ) { Icon(Line.Plus, "Adicionar", tint = ZPalette.PurpleText, modifier = Modifier.size(17.dp)) }
        BasicTextField(
            value = text,
            onValueChange = { text = it.replace("\n", "") },
            singleLine = !compact,
            maxLines = if (compact) 3 else 1,
            textStyle = TextStyle(fontFamily = InterFamily, fontSize = 13.sp, color = ZPalette.Ink),
            cursorBrush = SolidColor(ZPalette.Purple),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp).onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
                    submit()
                    true
                } else {
                    false
                }
            },
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) ZText(placeholder, 13.sp, 400, ZPalette.Quiet, maxLines = if (compact) 2 else 1)
                    inner()
                }
            },
        )
        if (!compact) {
            ZText(
                "↵ Adicionar", 11.sp, 500, ZPalette.Muted,
                Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF18181B)).border(1.dp, ZPalette.Line, RoundedCornerShape(6.dp))
                    .clickable(onClick = ::submit).padding(horizontal = 10.dp, vertical = 7.dp),
                maxLines = 1,
            )
        }
    }
}

/** Faixa "Revise sua agenda", com a borda roxa à esquerda. */
@Composable
fun ReviewStrip(title: String, subtitle: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val accent = Color(0xFF8775D8)
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(Color(0xFF1B1829))
            .border(1.dp, Color(0xFF29243F), RoundedCornerShape(7.dp))
            .drawBehind { drawRect(accent, Offset.Zero, Size(3.dp.toPx(), size.height)) }
            .padding(start = 17.dp, end = 17.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(Line.Check, Color(0xFF2A2446), ZPalette.PurpleText, size = 34.dp, iconSize = 18.dp)
        Column(Modifier.weight(1f)) {
            ZText(title, 12.5.sp, 650, ZPalette.Ink, maxLines = 1)
            ZText(subtitle, 12.sp, 400, ZPalette.Muted, maxLines = 2)
        }
        LinkAction(action, onAction)
    }
}

/** Cartão "Uma ideia antes que você esqueça?". */
@Composable
fun IdeaCard(onCreate: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(ZPalette.WarmBg)
            .border(1.dp, ZPalette.WarmLine, RoundedCornerShape(9.dp)).padding(horizontal = 18.dp, vertical = 20.dp),
    ) {
        Icon(Line.Bulb, null, tint = ZPalette.WarmAccent, modifier = Modifier.size(19.dp))
        Spacer(Modifier.height(13.dp))
        ZText("Uma ideia antes que você esqueça?", 13.sp, 650, ZPalette.WarmTitle)
        Spacer(Modifier.height(8.dp))
        ZText(
            "Anote agora. Organize depois na pasta certa, sem interromper seu dia.",
            11.sp, 400, ZPalette.WarmText, lineHeight = 19.sp,
        )
        Spacer(Modifier.height(14.dp))
        LinkAction("Criar uma anotação", onCreate, color = ZPalette.WarmAccent)
    }
}

// ---------------------------------------------------------------------------------------------
// Calendário do mês
// ---------------------------------------------------------------------------------------------

private val weekHeader = listOf("SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM")

/**
 * Calendário mensal compacto: hoje em roxo cheio, o dia escolhido com anel e um pontinho
 * nos dias que têm compromisso.
 */
@Composable
fun MonthCalendar(
    month: LocalDate,
    today: LocalDate,
    selected: LocalDate?,
    hasEvents: (LocalDate) -> Boolean,
    onSelect: (LocalDate) -> Unit,
    onMonth: (Int) -> Unit,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 36.dp,
    titleSize: TextUnit = 14.sp,
) {
    val first = LocalDate(month.year, month.month, 1)
    val start = first.minus(first.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal, DateTimeUnit.DAY)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZText(Format.monthYear(first), titleSize, 700, tracking = -0.015, modifier = Modifier.weight(1f))
            Box(Modifier.size(28.dp).clip(CircleShape).clickable { onMonth(-1) }, contentAlignment = Alignment.Center) {
                Icon(Line.ChevronLeft, "Mês anterior", tint = ZPalette.Muted, modifier = Modifier.size(15.dp))
            }
            Box(Modifier.size(28.dp).clip(CircleShape).clickable { onMonth(1) }, contentAlignment = Alignment.Center) {
                Icon(Line.ChevronRight, "Próximo mês", tint = ZPalette.Muted, modifier = Modifier.size(15.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Row {
            weekHeader.forEach { ZText(it, 10.sp, 700, ZPalette.Quiet, Modifier.weight(1f), align = TextAlign.Center) }
        }
        Spacer(Modifier.height(8.dp))
        for (week in 0 until 6) {
            Row(Modifier.height(rowHeight)) {
                for (d in 0 until 7) {
                    val date = start.plus(week * 7 + d, DateTimeUnit.DAY)
                    val inMonth = date.month == first.month
                    val isToday = date == today
                    val isSelected = date == selected && !isToday
                    Box(Modifier.weight(1f).fillMaxHeight().clickable { onSelect(date) }, contentAlignment = Alignment.Center) {
                        when {
                            isToday -> Box(Modifier.size(31.dp).clip(CircleShape).background(ZPalette.Purple))
                            isSelected -> Box(Modifier.size(31.dp).clip(CircleShape).border(1.5.dp, ZPalette.Purple, CircleShape))
                        }
                        ZText(
                            date.dayOfMonth.toString(), 12.sp, if (isToday) 700 else 450,
                            when {
                                isToday -> Color.White
                                !inMonth -> Color(0xFF45434B)
                                else -> Color(0xFFC9C7CF)
                            },
                            tabular = true,
                        )
                        if (inMonth && hasEvents(date)) {
                            Box(
                                Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).size(4.dp).clip(CircleShape)
                                    .background(if (isToday) Color.White else Color(0xFF9F90DE)),
                            )
                        }
                    }
                }
            }
        }
    }
}
