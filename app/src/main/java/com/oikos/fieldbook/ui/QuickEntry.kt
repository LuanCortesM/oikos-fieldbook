package com.oikos.fieldbook.ui

/** Algo que pode ser repetido do registro anterior: um ou mais campos do formulario. */
data class RepeatOption(
    /** Chave guardada nas preferencias ("recordedBy", "location", "dyn:habitat"...). */
    val key: String,
    val label: String,
    /** Chaves do formulario copiadas juntas. */
    val formKeys: List<String>,
    /** Valor do registro anterior, para o pesquisador ver o que vai repetir. */
    val preview: String
)

/**
 * Entrada em sequencia: em transecto, parcela ou ponto de escuta, de um registro
 * para o outro muda quase so a especie. O pesquisador escolhe uma vez o que
 * repetir (coletor, ponto, habitat...) e os proximos registros ja nascem com
 * esses valores. Sem dependencia de Android, para teste na JVM.
 */
object QuickEntry {
    /** Vem junto com o nome escolhido na lista de especies. */
    val TAXON_DETAILS = listOf(
        "scientificNameAuthorship", "taxonRank", "family", "genus", "kingdom",
        "specificEpithet", "infraspecificEpithet", "nameAccordingTo", "verbatimIdentification"
    )

    /** Descreve como a coordenada foi obtida: so vale junto com ela. */
    val GEOREFERENCE_DETAILS = listOf("georeferenceProtocol", "georeferenceRemarks")

    /** Identificam o registro ou o momento: nunca sao repetidos. */
    val NEVER = setOf("occurrenceID", "recordNumber", "date", "time", "dyn:eventDate", "dyn:eventTime")

    private data class Group(val key: String, val label: String, val formKeys: List<String>)

    private val groups = listOf(
        Group("formType", "Grupo e tipo do registro", listOf("formType", "occurrenceType")),
        Group("scientificName", "Nome da espécie", listOf("scientificName") + TAXON_DETAILS.map { "dyn:$it" }),
        Group("vernacularName", "Nome popular", listOf("vernacularName")),
        Group("basis", "Natureza do registro", listOf("basis")),
        Group("status", "Status da ocorrência", listOf("status")),
        Group("count", "Número de indivíduos", listOf("count")),
        Group("sex", "Sexo", listOf("sex")),
        Group("lifeStage", "Fase de vida", listOf("lifeStage")),
        Group("behavior", "Comportamento", listOf("behavior")),
        Group("reproductive", "Condição reprodutiva", listOf("reproductive")),
        Group("recordedBy", "Quem coletou", listOf("recordedBy")),
        Group("identifiedBy", "Quem identificou", listOf("identifiedBy")),
        Group("dateIdentified", "Data da identificação", listOf("dateIdentified")),
        Group("location", "Coordenadas (mesmo ponto)", listOf("lat", "lon", "alt", "accuracy") + (GEOREFERENCE_DETAILS + "verticalDatum").map { "dyn:$it" }),
        Group("remarks", "Observações", listOf("remarks")),
        Group("fieldNotes", "Notas de campo", listOf("fieldNotes"))
    )

    private val groupedKeys = groups.flatMap { it.formKeys }.toSet()

    /**
     * O que o registro anterior tem para repetir, na ordem do formulario; os
     * campos extras do projeto vem depois, com o nome dado por [dynamicLabel].
     */
    fun options(previous: Map<String, String>, dynamicLabel: (String) -> String): List<RepeatOption> {
        val fixed = groups.mapNotNull { group ->
            val preview = when (group.key) {
                "location" -> listOf(previous["lat"].orEmpty(), previous["lon"].orEmpty()).filter { it.isNotBlank() }.joinToString(", ")
                "formType" -> listOf(previous["formType"].orEmpty(), previous["occurrenceType"].orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
                else -> previous[group.formKeys.first()].orEmpty()
            }
            if (preview.isBlank()) null else RepeatOption(group.key, group.label, group.formKeys, preview)
        }
        val extras = previous
            .filterKeys { it.startsWith("dyn:") && it !in groupedKeys && it !in NEVER }
            .filterValues { it.isNotBlank() }
            .map { (formKey, value) -> RepeatOption(formKey, dynamicLabel(formKey.removePrefix("dyn:")), listOf(formKey), value) }
            .sortedBy { it.label.lowercase() }
        return fixed + extras
    }

    fun formKeysFor(optionKey: String): List<String> =
        groups.firstOrNull { it.key == optionKey }?.formKeys
            ?: if (optionKey.startsWith("dyn:") && optionKey !in NEVER) listOf(optionKey) else emptyList()

    /**
     * Valores a copiar do registro anterior. Campos de um grupo vem todos, mesmo
     * vazios: repetir um nome digitado a mao nao pode herdar a familia do
     * registro de antes dele.
     */
    fun carried(previous: Map<String, String>, chosen: Set<String>): Map<String, String> =
        chosen.flatMap { formKeysFor(it) }.associateWith { previous[it].orEmpty() }
}
