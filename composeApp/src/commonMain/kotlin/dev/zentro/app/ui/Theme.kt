package dev.zentro.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ZColors {
    val Violet = Color(0xFF7C6CF2)
    val VioletDeep = Color(0xFF5B4BDB)
    val Income = Color(0xFF22C38E)
    val Expense = Color(0xFFFF6B6B)
    val Warning = Color(0xFFFFB547)

    /** Cores que o usuário pode escolher para cada compromisso. */
    val events = listOf(
        Color(0xFF7C6CF2), Color(0xFF4DA3FF), Color(0xFF22C38E),
        Color(0xFFFFB547), Color(0xFFFF6B9A), Color(0xFF8E96A3),
    )

    fun event(index: Int): Color = events[index.mod(events.size)]
}

private val Dark = darkColorScheme(
    primary = ZColors.Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2B2550),
    onPrimaryContainer = Color(0xFFDCD6FF),
    secondary = ZColors.Income,
    secondaryContainer = Color(0xFF243044),
    onSecondaryContainer = Color(0xFFD5E3FF),
    background = Color(0xFF0D0F13),
    onBackground = Color(0xFFE9EAEE),
    surface = Color(0xFF0D0F13),
    onSurface = Color(0xFFE9EAEE),
    surfaceVariant = Color(0xFF1C2028),
    onSurfaceVariant = Color(0xFF9AA1AE),
    surfaceContainerLowest = Color(0xFF0A0C0F),
    surfaceContainerLow = Color(0xFF12151A),
    surfaceContainer = Color(0xFF161A20),
    surfaceContainerHigh = Color(0xFF1C2028),
    surfaceContainerHighest = Color(0xFF242934),
    outline = Color(0xFF3A404D),
    outlineVariant = Color(0xFF262B35),
    error = ZColors.Expense,
)

private val Light = lightColorScheme(
    primary = ZColors.VioletDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E3FF),
    onPrimaryContainer = Color(0xFF231A6B),
    secondary = Color(0xFF13A374),
    secondaryContainer = Color(0xFFE3ECFF),
    onSecondaryContainer = Color(0xFF14284D),
    background = Color(0xFFF4F5F9),
    onBackground = Color(0xFF16181D),
    surface = Color(0xFFF4F5F9),
    onSurface = Color(0xFF16181D),
    surfaceVariant = Color(0xFFE8EAF0),
    onSurfaceVariant = Color(0xFF5F6673),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFD),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEEF0F5),
    surfaceContainerHighest = Color(0xFFE6E8EF),
    outline = Color(0xFFC3C8D2),
    outlineVariant = Color(0xFFE2E5EB),
    error = Color(0xFFE5484D),
)

private val ZTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val ZShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val MoneyStyle = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)

@Composable
fun ZentroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = ZTypography,
        shapes = ZShapes,
        content = content,
    )
}
