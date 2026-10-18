package com.oikos.fieldbook.checklist

import java.text.Normalizer
import java.util.Locale

/** Icone do registro no alfinete do mapa. */
enum class MapIconGroup {
    FROG, FROG_BROMELIAD, REPTILE, SNAKE, BIRD, MAMMAL, INSECT, ARACHNID, FISH, CAMERA_TRAP, FLORA, FUNGI, OTHER
}

/** O minimo da classificacao de um nome para escolher o icone. */
data class TaxonGroupInfo(
    val kingdom: String = "",
    val taxonClass: String = "",
    val taxonOrder: String = "",
    val family: String = ""
)

/**
 * Regras do icone de cada registro no mapa. Sem Android, para teste na JVM.
 *
 * - Armadilha fotografica: camera, qualquer que seja o taxon.
 * - Fauna pela classe e ordem da lista instalada (Anura, Aves, Mammalia,
 *   Reptilia, Insecta, Arachnida, peixes); serpentes pela familia.
 * - Anuro e bromelia associados no mesmo registro, em qualquer direcao:
 *   perereca na bromelia.
 */
object MapIcons {
    /**
     * Familias de serpentes (Serpentes) do Catalogo Taxonomico da Fauna do
     * Brasil (ChecklistBank 281817, versao de 01/10/2026), mais algumas de fora
     * do Brasil, para registros de outros paises.
     */
    val SNAKE_FAMILIES = setOf(
        "Aniliidae", "Anomalepididae", "Boidae", "Colubridae", "Dipsadidae", "Elapidae", "Lamprophiidae",
        "Leptotyphlopidae", "Natricidae", "Pythonidae", "Tropidophiidae", "Typhlopidae", "Viperidae",
        "Acrochordidae", "Atractaspididae", "Cylindrophiidae", "Homalopsidae", "Pareidae", "Psammophiidae",
        "Uropeltidae", "Xenopeltidae", "Xenodermidae", "Loxocemidae", "Gerrhopilidae", "Xenotyphlopidae"
    )

    private val FISH_CLASSES = setOf(
        "Actinopterygii", "Chondrichthyes", "Elasmobranchii", "Holocephali", "Sarcopterygii", "Dipnoi",
        "Actinistia", "Myxini", "Petromyzonti", "Cephalaspidomorphi"
    )

    /** Chaves dos campos dinamicos que descrevem ambiente, substrato ou associacao. */
    val ASSOCIATION_KEYS = setOf(
        "microhabitat", "habitat", "substrate", "associatedTaxa", "associatedOccurrences", "behaviorNotes",
        "botanicalNotes", "plantHabit", "hostOrAssociation", "fungusMicrohabitat", "fungusSubstrate", "fungiNotes",
        "eventRemarks"
    )

    private val BROMELIAD_WORDS = Regex("brom[eé]li", RegexOption.IGNORE_CASE)
    private val ANURAN_WORDS = Regex(
        "(?<!\\p{L})(anuros?|anura|sapos?|rãs?|pererecas?|girinos?|frogs?|toads?|tadpoles?)(?!\\p{L})",
        RegexOption.IGNORE_CASE
    )
    /** "Vriesea itatiaiae", "Scinax cf. fuscovarius" ou so o genero. */
    private val NAME = Regex("(?<!\\p{L})([A-Z][a-z]{2,})(?:\\s+(?:cf\\.\\s+|aff\\.\\s+)?([a-z]{3,}))?")

    /** Grupo de um animal pela classificacao; nulo se nao da para dizer. */
    fun animalGroup(info: TaxonGroupInfo): MapIconGroup? {
        val cls = info.taxonClass.trim()
        val order = info.taxonOrder.trim()
        return when {
            order.equals("Anura", ignoreCase = true) -> MapIconGroup.FROG
            cls.equals("Reptilia", ignoreCase = true) ->
                if (SNAKE_FAMILIES.any { it.equals(info.family.trim(), ignoreCase = true) }) MapIconGroup.SNAKE else MapIconGroup.REPTILE
            cls.equals("Aves", ignoreCase = true) -> MapIconGroup.BIRD
            cls.equals("Mammalia", ignoreCase = true) -> MapIconGroup.MAMMAL
            cls.equals("Insecta", ignoreCase = true) -> MapIconGroup.INSECT
            cls.equals("Arachnida", ignoreCase = true) -> MapIconGroup.ARACHNID
            FISH_CLASSES.any { it.equals(cls, ignoreCase = true) } -> MapIconGroup.FISH
            else -> null
        }
    }

    /** Pista pelo campo livre "Grupo animal", quando a lista nao resolve o nome. */
    fun groupFromAnimalGroupText(text: String): MapIconGroup? {
        val t = plain(text)
        return when {
            t.isBlank() -> null
            Regex("anur|anfibi|sapo|perereca|(^|[^a-z])ras?([^a-z]|$)").containsMatchIn(t) -> MapIconGroup.FROG
            Regex("serpent|cobra").containsMatchIn(t) -> MapIconGroup.SNAKE
            Regex("reptil|lagart|quelon|jacare|tartarug").containsMatchIn(t) -> MapIconGroup.REPTILE
            Regex("(^|[^a-z])aves?([^a-z]|$)|passaro").containsMatchIn(t) -> MapIconGroup.BIRD
            Regex("mamifer|morceg|primata|roedor").containsMatchIn(t) -> MapIconGroup.MAMMAL
            Regex("inset|besour|borboleta|formiga|abelha").containsMatchIn(t) -> MapIconGroup.INSECT
            Regex("aracnid|aranha|escorpi|opiliao").containsMatchIn(t) -> MapIconGroup.ARACHNID
            Regex("peixe|ictio").containsMatchIn(t) -> MapIconGroup.FISH
            else -> null
        }
    }

    fun mentionsBromeliad(texts: List<String>): Boolean = texts.any { BROMELIAD_WORDS.containsMatchIn(it) }

    fun mentionsAnuran(texts: List<String>): Boolean = texts.any { ANURAN_WORDS.containsMatchIn(it) }

    /** Nomes cientificos citados no texto: binomios primeiro, depois generos; no maximo 12. */
    fun nameCandidates(texts: List<String>): List<String> {
        val binomials = LinkedHashSet<String>()
        val genera = LinkedHashSet<String>()
        texts.forEach { text ->
            NAME.findAll(text).forEach { match ->
                val genus = match.groupValues[1]
                val epithet = match.groupValues[2]
                if (epithet.isNotEmpty()) binomials += "$genus $epithet"
                genera += genus
            }
        }
        return (binomials + genera).take(12)
    }

    private fun plain(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace("\\p{Mn}+".toRegex(), "").lowercase(Locale.ROOT)
}
