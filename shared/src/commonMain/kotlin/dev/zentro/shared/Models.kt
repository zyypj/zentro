package dev.zentro.shared

import kotlinx.serialization.Serializable

/** Como um compromisso se repete. */
@Serializable
enum class Recurrence { NONE, DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY }

/**
 * Um compromisso da agenda. Todos os horários são epoch em milissegundos (UTC);
 * a conversão para o fuso do usuário acontece só na exibição e no cálculo de recorrência.
 *
 * O [id] é gerado pelo cliente (UUID), o que torna toda escrita idempotente:
 * o app pode reenviar a mesma operação quantas vezes precisar sem duplicar nada.
 */
@Serializable
data class Event(
    val id: String,
    val title: String,
    val notes: String = "",
    val startAt: Long,
    val endAt: Long? = null,
    val allDay: Boolean = false,
    /** Minutos antes do início em que cada aviso dispara (0 = na hora). */
    val reminders: List<Int> = listOf(15, 0),
    /** Compromisso crítico: toca como despertador, em tela cheia, até ser confirmado. */
    val critical: Boolean = false,
    val recurrence: Recurrence = Recurrence.NONE,
    val color: Int = 0,
    /** De quem é o compromisso: null = seu; senão o id de uma [Person] (ex.: a agenda da namorada). */
    val personId: String? = null,
    /** Conta a pagar: valor em centavos (null = compromisso comum). */
    val billAmountCents: Long? = null,
    val billCategory: String = "Contas",
    /** Categoria do compromisso (ver [EventCategories]); vazio = sem categoria. */
    val category: String = "",
    /** Inícios das ocorrências já pagas — essas não avisam mais. */
    val paid: List<Long> = emptyList(),
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
)

/** Categorias dos compromissos, usadas nas etiquetas e nos filtros da agenda. */
object EventCategories {
    const val WORK = "Trabalho"
    const val SCHOOL = "Faculdade"
    const val PERSONAL = "Pessoal"
    val all = listOf(WORK, SCHOOL, PERSONAL)
}

/** Alguém cuja agenda você acompanha junto com a sua (namorada, filho, sócio...). */
@Serializable
data class Person(
    val id: String,
    val name: String,
    val color: Int = 4,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
)

/**
 * Uma anotação em Markdown. Links entre notas usam `[[Título]]`, tags usam `#tag` e
 * tarefas usam `- [ ]`, como no Obsidian. [folder] aceita subpastas com "/" (ex.: "Faculdade/Cálculo").
 */
@Serializable
data class Note(
    val id: String,
    val title: String,
    val content: String = "",
    val folder: String = Notes.DEFAULT_FOLDER,
    val icon: String = "",
    val pinned: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
enum class TxType { INCOME, EXPENSE }

@Serializable
enum class TxSource { MANUAL, MERCADO_PAGO }

/** Uma movimentação financeira. Valores sempre em centavos para não perder precisão. */
@Serializable
data class Transaction(
    val id: String,
    val type: TxType,
    val amountCents: Long,
    val description: String,
    val category: String = Categories.OTHER,
    val date: Long,
    val source: TxSource = TxSource.MANUAL,
    val externalId: String? = null,
    /** Compra no cartão de crédito: não mexe no saldo agora, entra nas faturas do cartão. */
    val cardId: String? = null,
    /** Em quantas parcelas a compra no cartão foi feita. */
    val installments: Int = 1,
    /** Este lançamento é o pagamento da fatura indicada (ver [Cards.invoiceKey]). */
    val invoiceKey: String? = null,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
)

/**
 * Um cartão de crédito. A fatura fecha no [closingDay] (compras a partir desse dia vão para a
 * fatura seguinte) e vence no [dueDay]. Dias maiores que o mês (ex.: 31 em fevereiro) viram o último dia.
 */
@Serializable
data class CreditCard(
    val id: String,
    val name: String,
    val closingDay: Int = 3,
    val dueDay: Int = 10,
    /** Limite em centavos (opcional, só para mostrar quanto já foi usado). */
    val limitCents: Long? = null,
    val color: Int = 0,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class UserSettings(
    val timezone: String = DEFAULT_TIMEZONE,
    /** Horários (HH:mm) em que o app cobra a atualização da agenda, se ela ainda não foi feita no dia. */
    val reviewTimes: List<String> = listOf("09:00", "20:00"),
    /** Último dia (yyyy-MM-dd) em que a agenda foi atualizada ou revisada. */
    val lastReviewDate: String? = null,
    val telegramLinked: Boolean = false,
    val mercadoPagoConnected: Boolean = false,
    val mercadoPagoLastSync: Long? = null,
    val updatedAt: Long = 0,
)

const val DEFAULT_TIMEZONE = "America/Sao_Paulo"

object Categories {
    const val OTHER = "Outros"
    val expense = listOf("Alimentação", "Mercado", "Transporte", "Moradia", "Contas", "Saúde", "Lazer", "Compras", "Educação", OTHER)
    val income = listOf("Salário", "Freela", "Vendas", "Pix recebido", "Investimentos", OTHER)
}
