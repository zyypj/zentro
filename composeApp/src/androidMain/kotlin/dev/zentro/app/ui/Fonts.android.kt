package dev.zentro.app.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.zentro.app.R

// Um arquivo só (fonte variável); cada peso usa o eixo "wght" dela.
actual val InterFamily: FontFamily = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(R.font.inter, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)
