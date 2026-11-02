package dev.zentro.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Tons do tema grafite (escuro) da identidade visual do Zentro. */
object ZPalette {
    val Background = Color(0xFF111113)
    val Aside = Color(0xFF131316)
    val Sidebar = Color(0xFF18181B)
    val Card = Color(0xFF1C1C20)
    val CardHigh = Color(0xFF232328)
    val CardHighest = Color(0xFF2A2A30)
    val Line = Color(0xFF2A2A30)
    val SoftLine = Color(0xFF212126)
    val Ink = Color(0xFFECEBF0)
    val Muted = Color(0xFF8E8B96)
    val Quiet = Color(0xFF5F5C67)

    val Purple = Color(0xFF7C6CF2)
    val PurpleText = Color(0xFFB4A9FF)
    val PurpleSoft = Color(0xFF24203A)
    val PurpleLine = Color(0xFF332C55)

    /** Cartão de ideia (tom quente, como no mockup). */
    val WarmBg = Color(0xFF1E1B17)
    val WarmLine = Color(0xFF2E2A22)
    val WarmTitle = Color(0xFFE6DFD3)
    val WarmText = Color(0xFF9C9385)
    val WarmAccent = Color(0xFFC9A86A)
}

object ZColors {
    val Violet = ZPalette.Purple
    val VioletDeep = Color(0xFF6554C4)
    val Income = Color(0xFF5FBF8E)
    val Expense = Color(0xFFE38877)
    val Warning = Color(0xFFE2B05A)

    /**
     * Paleta para compromissos, cartões e pessoas. Novas cores entram sempre no fim, para os
     * índices já salvos continuarem com a mesma cor.
     */
    val events = listOf(
        Color(0xFF8C7CDD), Color(0xFF7B9FD4), Color(0xFF7FB394),
        Color(0xFFE2B05A), Color(0xFFDB8BA9), Color(0xFF8E8B96),
        Color(0xFF5FB7B4), Color(0xFFE38877), Color(0xFF6E7BE0),
        Color(0xFFA6C46A), Color(0xFFE89A5B), Color(0xFFC77BD0),
        Color(0xFF5EA6E6), Color(0xFFD9C35C), Color(0xFFB08968),
        Color(0xFFF07FA0),
    )

    /**
     * Cor salva num item: um número pequeno é o índice da paleta; uma cor personalizada é guardada
     * como ARGB opaco, que como Int é sempre negativo.
     */
    fun event(index: Int): Color = if (isCustom(index)) Color(index) else events[index.mod(events.size)]

    fun isCustom(value: Int): Boolean = value < 0

    /** Guarda uma cor escolhida no seletor (sempre opaca, para cair no formato de cor própria). */
    fun custom(color: Color): Int = color.toArgb() or (0xFF shl 24)
}

private val Dark = darkColorScheme(
    primary = ZPalette.Purple,
    onPrimary = Color.White,
    primaryContainer = ZPalette.PurpleSoft,
    onPrimaryContainer = Color(0xFFD9D3FF),
    inversePrimary = ZColors.VioletDeep,
    secondary = ZColors.Income,
    onSecondary = Color(0xFF0B1A12),
    secondaryContainer = ZPalette.CardHigh,
    onSecondaryContainer = ZPalette.Ink,
    tertiary = ZPalette.WarmAccent,
    background = ZPalette.Background,
    onBackground = ZPalette.Ink,
    surface = ZPalette.Background,
    onSurface = ZPalette.Ink,
    surfaceVariant = ZPalette.CardHigh,
    onSurfaceVariant = ZPalette.Muted,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFF0D0D0F),
    surfaceContainerLow = ZPalette.Sidebar,
    surfaceContainer = ZPalette.Card,
    surfaceContainerHigh = ZPalette.CardHigh,
    surfaceContainerHighest = ZPalette.CardHighest,
    inverseSurface = ZPalette.Ink,
    inverseOnSurface = ZPalette.Background,
    outline = Color(0xFF3A3A42),
    outlineVariant = ZPalette.Line,
    error = ZColors.Expense,
    errorContainer = Color(0xFF3A211D),
    onErrorContainer = Color(0xFFF4C9BF),
    scrim = Color(0xFF000000),
)

private fun TextStyle.inter(size: Int, weight: Int, tracking: Double = 0.0, line: Double? = null) = copy(
    fontFamily = InterFamily,
    fontSize = size.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.em,
    lineHeight = line?.let { (size * it).sp } ?: lineHeight,
)

private val ZTypography = Typography().run {
    copy(
        displayLarge = displayLarge.inter(48, 700, -0.05, 1.15),
        displayMedium = displayMedium.inter(40, 700, -0.05, 1.15),
        displaySmall = displaySmall.inter(32, 700, -0.04, 1.2),
        headlineLarge = headlineLarge.inter(42, 700, -0.05, 1.2),
        headlineMedium = headlineMedium.inter(28, 700, -0.035, 1.2),
        headlineSmall = headlineSmall.inter(22, 700, -0.025, 1.25),
        titleLarge = titleLarge.inter(18, 700, -0.02, 1.3),
        titleMedium = titleMedium.inter(15, 650, -0.012, 1.35),
        titleSmall = titleSmall.inter(14, 600, -0.01, 1.4),
        bodyLarge = bodyLarge.inter(14, 400, 0.0, 1.55),
        bodyMedium = bodyMedium.inter(13, 400, 0.0, 1.5),
        bodySmall = bodySmall.inter(11, 400, 0.005, 1.5),
        labelLarge = labelLarge.inter(13, 600, 0.0, 1.4),
        labelMedium = labelMedium.inter(11, 650, 0.0, 1.4),
        labelSmall = labelSmall.inter(10, 750, 0.14, 1.4),
    )
}

private val ZShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/** Números de dinheiro: Inter com algarismos de largura fixa, para as colunas alinharem. */
val MoneyStyle = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Bold, letterSpacing = (-0.03).em, fontFeatureSettings = "tnum")

/** Sempre no tema grafite (escuro), como na identidade visual. */
@Composable
fun ZentroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Dark,
        typography = ZTypography,
        shapes = ZShapes,
        content = content,
    )
}
