package com.oikos.fieldbook.export

import java.time.LocalDate

/**
 * Como citar o Oikos FieldBook, nos formatos mais usados no Brasil (ABNT) e
 * em periodicos (APA). Aparece na tela Sobre e no COMO_CITAR.txt das
 * exportacoes.
 */
object AppCitation {
    const val AUTHOR_ABNT = "MACIEL, L. S. C."
    const val AUTHOR_APA = "Maciel, L. S. C."
    const val TITLE = "Oikos FieldBook"
    const val SUBTITLE = "caderno de campo digital"
    const val URL = "https://play.google.com/store/apps/details?id=com.oikos.fieldbook"

    /** Ano da versao publicada; atualizar junto com versionName a cada lancamento. */
    const val RELEASE_YEAR = 2026

    private val MONTHS = listOf("jan.", "fev.", "mar.", "abr.", "maio", "jun.", "jul.", "ago.", "set.", "out.", "nov.", "dez.")

    /** ABNT NBR 6023 para aplicativo, com a data de acesso. */
    fun abnt(version: String, accessed: LocalDate = LocalDate.now()): String =
        "$AUTHOR_ABNT $TITLE: $SUBTITLE. Versão $version. [S. l.]: Google Play, $RELEASE_YEAR. " +
            "Aplicativo para Android. Disponível em: $URL. Acesso em: ${accessed.dayOfMonth} ${MONTHS[accessed.monthValue - 1]} ${accessed.year}."

    /** APA 7 para aplicativo movel. */
    fun apa(version: String): String =
        "$AUTHOR_APA ($RELEASE_YEAR). $TITLE (Versão $version) [Aplicativo móvel]. Google Play. $URL"

    /** Conteudo do COMO_CITAR.txt das exportacoes. */
    fun readme(version: String, exportedOn: LocalDate = LocalDate.now()): String = """
        Como citar o Oikos FieldBook

        Estes dados foram registrados com o Oikos FieldBook, versão $version.
        Se usá-los em trabalho, relatório ou publicação, cite o aplicativo:

        ABNT
        ${abnt(version, exportedOn)}

        APA
        ${apa(version)}

        Se os nomes científicos vieram de uma lista de espécies instalada no app,
        cite também a lista (ela aparece na coluna nameAccordingTo).
    """.trimIndent()
}
