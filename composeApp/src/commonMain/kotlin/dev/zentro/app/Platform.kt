package dev.zentro.app

import androidx.compose.runtime.Composable
import dev.zentro.app.data.UpdateManager

/** O que muda entre Android e desktop e precisa aparecer na interface comum. */
interface Platform {
    val name: String

    /** Atualização automática a partir do GitHub. */
    val updates: UpdateManager

    /** Lista de verificação "seus avisos estão garantidos?" com botões para corrigir cada item. */
    @Composable
    fun ReliabilityChecklist()

    /** Dispara um aviso de teste em poucos segundos, pelo mesmo caminho dos avisos reais. */
    fun testNotification()
}
