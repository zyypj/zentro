package dev.zentro.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Barra fina e discreta que fica mais visível ao passar o mouse, no estilo do app. */
@Composable
private fun zentroScrollbarStyle(): ScrollbarStyle {
    val onSurface = MaterialTheme.colorScheme.onSurface
    return ScrollbarStyle(
        minimalHeight = 40.dp,
        thickness = 8.dp,
        shape = RoundedCornerShape(4.dp),
        hoverDurationMillis = 250,
        unhoverColor = onSurface.copy(alpha = 0.18f),
        hoverColor = onSurface.copy(alpha = 0.45f),
    )
}

@Composable
actual fun AppScrollbar(state: ScrollState, modifier: Modifier) {
    VerticalScrollbar(rememberScrollbarAdapter(state), modifier.padding(vertical = 4.dp, horizontal = 2.dp), style = zentroScrollbarStyle())
}

@Composable
actual fun AppScrollbar(state: LazyListState, modifier: Modifier) {
    VerticalScrollbar(rememberScrollbarAdapter(state), modifier.padding(vertical = 4.dp, horizontal = 2.dp), style = zentroScrollbarStyle())
}

actual val ScrollbarGap: Dp = 14.dp
