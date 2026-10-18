package com.oikos.fieldbook.export

import java.time.LocalDate

/**
 * Como citar o Oikos FieldBook: ABNT (Brasil), Vancouver (ciencias da saude e
 * biologicas) e APA (a mais usada em periodicos internacionais). Aparece na
 * tela Sobre e no COMO_CITAR.txt das exportacoes.
 *
 * Autor, titulo e tipo como estao registrados no Zenodo. O DOI e o de todas as
 * versoes (concept DOI): sempre leva a versao mais recente.
 */
object AppCitation {
    const val AUTHOR_ABNT = "MACIEL, L. S. C."
    const val AUTHOR_VANCOUVER = "Maciel LSC"
    const val AUTHOR_APA = "Maciel, L. S. C."
    const val TITLE = "Oikos FieldBook"
    const val DOI = "10.5281/zenodo.20526746"
    const val DOI_URL = "https://doi.org/$DOI"
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.oikos.fieldbook"

    /** Ano da versao publicada; atualizar junto com versionName a cada lancamento. */
    const val RELEASE_YEAR = 2026

    private val MONTHS_PT = listOf("jan.", "fev.", "mar.", "abr.", "maio", "jun.", "jul.", "ago.", "set.", "out.", "nov.", "dez.")
    private val MONTHS_EN = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /**
     * ABNT NBR 6023:2018, conferida no texto da norma:
     * - 7.20 (documento de acesso exclusivo em meio eletronico, que inclui
     *   programas de computador): autor, titulo, versao, local, data e
     *   descricao fisica do meio eletronico ("Aplicativo para Android");
     * - 8.4.4: local desconhecido como [S. l.] (S maiusculo no inicio dos
     *   dados de publicacao);
     * - DOI como elemento complementar, antes de "Disponivel em", que pode
     *   apontar outro endereco (exemplo da norma: DOI do artigo e
     *   "Disponivel em" no SciELO). Aqui o "Disponivel em" leva a Play Store;
     * - 6.6: documento online com "Disponivel em:" e "Acesso em:".
     * O titulo deve ir em destaque (6.7: negrito, italico ou sublinhado): a
     * tela mostra em negrito; no texto copiado, quem cola aplica o destaque.
     */
    fun abnt(version: String, accessed: LocalDate = LocalDate.now()): String =
        "$AUTHOR_ABNT $TITLE. Versão $version. [S. l.]: Zenodo, $RELEASE_YEAR. Aplicativo para Android. DOI: $DOI_URL. " +
            "Disponível em: $PLAY_URL. Acesso em: ${accessed.dayOfMonth} ${MONTHS_PT[accessed.monthValue - 1]} ${accessed.year}."

    /** Vancouver (NLM, Citing Medicine) para software na internet, com a data de consulta. */
    fun vancouver(version: String, accessed: LocalDate = LocalDate.now()): String =
        "$AUTHOR_VANCOUVER. $TITLE [software]. Version $version. [place unknown]: Zenodo; $RELEASE_YEAR " +
            "[cited ${accessed.year} ${MONTHS_EN[accessed.monthValue - 1]} ${accessed.dayOfMonth}]. Available from: $DOI_URL"

    /** APA 7 para software. */
    fun apa(version: String): String =
        "$AUTHOR_APA ($RELEASE_YEAR). $TITLE (Version $version) [Computer software]. Zenodo. $DOI_URL"

    /** As tres formas, na ordem em que aparecem no app. */
    fun all(version: String, accessed: LocalDate = LocalDate.now()): List<Pair<String, String>> = listOf(
        "ABNT" to abnt(version, accessed),
        "Vancouver" to vancouver(version, accessed),
        "APA" to apa(version)
    )

    /** Conteudo do COMO_CITAR.txt das exportacoes. */
    fun readme(version: String, exportedOn: LocalDate = LocalDate.now()): String = buildString {
        appendLine("Como citar o Oikos FieldBook")
        appendLine()
        appendLine("Estes dados foram registrados com o Oikos FieldBook, versão $version (DOI $DOI).")
        appendLine("Se usá-los em trabalho, relatório ou publicação, cite o aplicativo:")
        all(version, exportedOn).forEach { (style, text) ->
            appendLine()
            appendLine(style)
            appendLine(text)
        }
        appendLine()
        appendLine("Se os nomes científicos vieram de uma lista de espécies instalada no app,")
        append("cite também a lista (ela aparece na coluna nameAccordingTo).")
    }
}
