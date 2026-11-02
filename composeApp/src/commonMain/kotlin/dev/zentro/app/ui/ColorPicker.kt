package dev.zentro.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

/** Matiz (0–360), saturação e brilho (0–1) de uma cor. */
private fun Color.toHsv(): Triple<Float, Float, Float> {
    val maxC = max(red, max(green, blue))
    val minC = min(red, min(green, blue))
    val d = maxC - minC
    val h = when {
        d == 0f -> 0f
        maxC == red -> 60f * (((green - blue) / d).mod(6f))
        maxC == green -> 60f * ((blue - red) / d + 2f)
        else -> 60f * ((red - green) / d + 4f)
    }
    val s = if (maxC == 0f) 0f else d / maxC
    return Triple(h, s, maxC)
}

/** "#7C6CF2" */
fun Color.hex(): String = "#" + (toArgb() and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

private fun parseHex(text: String): Color? {
    val clean = text.trim().removePrefix("#")
    if (clean.length != 6) return null
    val rgb = clean.toIntOrNull(16) ?: return null
    return Color(0xFF000000.toInt() or rgb)
}

private val rainbow = listOf(
    Color(0xFFFF5E5E), Color(0xFFFFC75E), Color(0xFFB8F05E), Color(0xFF5EF0A0),
    Color(0xFF5ED8F0), Color(0xFF6E7BE0), Color(0xFFC77BD0), Color(0xFFFF5E5E),
)

/**
 * Escolha de cor: as cores da paleta e uma bolinha de arco-íris que abre o seletor de cor
 * personalizada. [selected] segue o formato de [ZColors.event] (índice da paleta ou cor própria).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorChoices(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    var custom by remember { mutableStateOf(false) }
    val isCustom = ZColors.isCustom(selected)
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ZColors.events.forEachIndexed { index, c ->
            Swatch(c, selected == index) { onSelect(index) }
        }
        if (isCustom) Swatch(ZColors.event(selected), true) { custom = true }
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(Brush.sweepGradient(rainbow)).clickable { custom = true },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(ZPalette.Card), contentAlignment = Alignment.Center) {
                Icon(Line.Plus, "Cor personalizada", tint = ZPalette.Ink, modifier = Modifier.size(12.dp))
            }
        }
    }
    if (custom) {
        CustomColorDialog(
            initial = ZColors.event(selected),
            onPick = { onSelect(ZColors.custom(it)) },
            onDismiss = { custom = false },
        )
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(color)
            .border(2.5.dp, if (selected) ZPalette.Ink else Color.Transparent, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Line.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
    }
}

/** Seletor de cor própria: matiz, saturação e brilho, ou o código hexadecimal. */
@Composable
fun CustomColorDialog(initial: Color, onPick: (Color) -> Unit, onDismiss: () -> Unit) {
    val start = remember { initial.toHsv() }
    var hue by remember { mutableStateOf(start.first) }
    var sat by remember { mutableStateOf(start.second.coerceAtLeast(0.15f)) }
    var value by remember { mutableStateOf(start.third.coerceAtLeast(0.35f)) }
    val color = Color.hsv(hue.coerceIn(0f, 360f), sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
    var hexText by remember { mutableStateOf(color.hex()) }

    fun setFrom(c: Color) {
        val (h, s, v) = c.toHsv()
        hue = h
        sat = s
        value = v
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ZPalette.CardHigh,
        title = { Text("Cor personalizada") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(color))
                    Column(Modifier.weight(1f)) {
                        Tag("Etiqueta", TagColors(color.copy(alpha = 0.16f), color, color))
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RingDot(color)
                            ZText("Como aparece na agenda", 12.sp, 400, ZPalette.Muted)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Label("Cor")
                GradientTrack(Brush.horizontalGradient(rainbow))
                Slider(hue, { hue = it; hexText = Color.hsv(it, sat, value).hex() }, valueRange = 0f..359.9f, colors = sliderColors(color))
                Label("Intensidade")
                GradientTrack(Brush.horizontalGradient(listOf(Color.hsv(hue, 0f, value), Color.hsv(hue, 1f, value))))
                Slider(sat, { sat = it; hexText = Color.hsv(hue, it, value).hex() }, valueRange = 0f..1f, colors = sliderColors(color))
                Label("Brilho")
                GradientTrack(Brush.horizontalGradient(listOf(Color.hsv(hue, sat, 0.2f), Color.hsv(hue, sat, 1f))))
                Slider(value, { value = it; hexText = Color.hsv(hue, sat, it).hex() }, valueRange = 0.2f..1f, colors = sliderColors(color))
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { t ->
                        hexText = t.take(7)
                        parseHex(t)?.let(::setFrom)
                    },
                    label = { Text("Código (ex.: #7C6CF2)") },
                    singleLine = true,
                    isError = parseHex(hexText) == null,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onPick(color)
                onDismiss()
            }) { Text("Usar esta cor") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun Label(text: String) {
    ZText(text, 11.5.sp, 600, ZPalette.Muted, Modifier.padding(top = 6.dp, bottom = 4.dp))
}

@Composable
private fun GradientTrack(brush: Brush) {
    Canvas(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) { drawRect(brush) }
}

@Composable
private fun sliderColors(color: Color) = SliderDefaults.colors(
    thumbColor = color,
    activeTrackColor = Color.Transparent,
    inactiveTrackColor = Color.Transparent,
)
