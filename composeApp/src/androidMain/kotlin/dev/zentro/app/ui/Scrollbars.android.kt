package dev.zentro.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// No celular rola-se com o dedo; uma barra fixa só ocuparia espaço.
@Composable
actual fun AppScrollbar(state: ScrollState, modifier: Modifier) = Unit

@Composable
actual fun AppScrollbar(state: LazyListState, modifier: Modifier) = Unit

actual val ScrollbarGap: Dp = 0.dp
