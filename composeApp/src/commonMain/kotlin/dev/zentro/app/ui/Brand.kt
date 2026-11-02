package dev.zentro.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Desenho do "Z" da marca, num quadrado de 100×100: barra de cima com a ponta esquerda
 * arredondada, diagonal grossa e barra de baixo com a ponta direita arredondada.
 */
object ZMark {
    val GradientStart = Color(0xFF7550DC)
    val GradientMid = Color(0xFF4B23AE)
    val GradientEnd = Color(0xFF8E4BE9)
    val Glyph = Color(0xFFEEEBFD)
    val GlyphShade = Color(0xFFC6BBF4)

    /** Contorno do Z em coordenadas 0..100. */
    fun glyph(scale: Float, offset: Offset = Offset.Zero): Path {
        fun p(x: Float, y: Float) = Offset(offset.x + x * scale, offset.y + y * scale)
        val path = Path()
        // Barra de cima: canto superior esquerdo bem arredondado.
        path.addRoundRect(
            RoundRect(
                left = p(17f, 18f).x, top = p(17f, 18f).y, right = p(78f, 36f).x, bottom = p(78f, 36f).y,
                topLeftCornerRadius = CornerRadius(14f * scale), bottomLeftCornerRadius = CornerRadius(0f),
                topRightCornerRadius = CornerRadius(0f), bottomRightCornerRadius = CornerRadius(0f),
            ),
        )
        // Barra de baixo: canto inferior direito arredondado.
        path.addRoundRect(
            RoundRect(
                left = p(24f, 64f).x, top = p(24f, 64f).y, right = p(84f, 82f).x, bottom = p(84f, 82f).y,
                topLeftCornerRadius = CornerRadius(0f), bottomLeftCornerRadius = CornerRadius(0f),
                topRightCornerRadius = CornerRadius(0f), bottomRightCornerRadius = CornerRadius(14f * scale),
            ),
        )
        return path
    }

    fun DrawScope.drawGlyph(origin: Offset, scale: Float, color: Brush) {
        drawPath(glyph(scale, origin), color)
        // Diagonal com pontas redondas, ligando as duas barras.
        drawLine(
            color,
            start = Offset(origin.x + 75f * scale, origin.y + 27f * scale),
            end = Offset(origin.x + 25f * scale, origin.y + 73f * scale),
            strokeWidth = 18f * scale,
            cap = StrokeCap.Round,
        )
    }

    fun DrawScope.drawMark(size: Size, flat: Color? = null) {
        val s = size.minDimension / 100f
        val square = Path().apply {
            addRoundRect(RoundRect(0f, 0f, 100f * s, 100f * s, CornerRadius(26f * s)))
        }
        val bg = if (flat != null) Brush.linearGradient(listOf(flat, flat))
        else Brush.linearGradient(listOf(GradientStart, GradientMid, GradientEnd), Offset.Zero, Offset(100f * s, 100f * s))
        drawPath(square, bg)
        clipPath(square) {
            drawGlyph(
                Offset.Zero,
                s,
                Brush.linearGradient(listOf(Glyph, Glyph, GlyphShade), Offset(20f * s, 20f * s), Offset(80f * s, 85f * s)),
            )
        }
    }
}

/** Ícone da marca: quadrado roxo com o Z claro. */
@Composable
fun ZentroMark(size: Dp = 34.dp, modifier: Modifier = Modifier, flat: Color? = null) {
    Canvas(modifier.size(size)) { with(ZMark) { drawMark(this@Canvas.size, flat) } }
}

/** Ícone + nome. [lowercase] segue o mockup do desktop ("zentro"); o celular usa "Zentro". */
@Composable
fun ZentroWordmark(markSize: Dp = 34.dp, textSize: Int = 23, lowercase: Boolean = true, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((markSize.value * 0.33f).dp)) {
        ZentroMark(markSize)
        Text(
            if (lowercase) "zentro" else "Zentro",
            fontFamily = InterFamily,
            fontWeight = FontWeight.Bold,
            fontSize = textSize.sp,
            letterSpacing = (-0.055).em,
            color = ZPalette.Ink,
        )
    }
}

/** Mantido para telas antigas (login): agora desenha a marca nova. */
@Composable
fun ZentroLogo(size: Int = 64) = ZentroMark(size.dp)
