package dev.zentro.app.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

// Um arquivo só (fonte variável); cada peso usa o eixo "wght" dela.
actual val InterFamily: FontFamily = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font("fonts/Inter.ttf", FontWeight(w), FontStyle.Normal, FontVariation.Settings(FontVariation.weight(w)))
    },
)
