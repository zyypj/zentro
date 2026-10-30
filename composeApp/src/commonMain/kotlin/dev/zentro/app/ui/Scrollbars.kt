package dev.zentro.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Barra de rolagem arrastável no PC. No celular não aparece (lá se rola com o dedo). */
@Composable
expect fun AppScrollbar(state: ScrollState, modifier: Modifier = Modifier)

@Composable
expect fun AppScrollbar(state: LazyListState, modifier: Modifier = Modifier)

/** Espaço à direita das listas para a barra não ficar por cima dos cartões. */
expect val ScrollbarGap: Dp

/** Coluna rolável com barra de rolagem à direita. [contentModifier] vale para o conteúdo (padding etc.). */
@Composable
fun ScrollableColumn(
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(state).then(contentModifier), content = content)
        AppScrollbar(state, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

/** LazyColumn com barra de rolagem à direita. */
@Composable
fun ScrollableLazyColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    Box(modifier) {
        LazyColumn(
            Modifier.fillMaxWidth().padding(end = ScrollbarGap),
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            content = content,
        )
        AppScrollbar(state, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}
