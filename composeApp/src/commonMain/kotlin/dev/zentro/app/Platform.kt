package dev.zentro.app

import androidx.compose.runtime.Composable

/** O que muda entre Android e desktop e precisa aparecer na interface comum. */
interface Platform {
    val name: String

    /** Lista de verificação "seus avisos estão garantidos?" com botões para corrigir cada item. */
    @Composable
    fun ReliabilityChecklist()

    /** Dispara um aviso de teste em poucos segundos, pelo mesmo caminho dos avisos reais. */
    fun testNotification()
}
