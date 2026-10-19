package com.oikos.fieldbook.checklist

/**
 * Grupos para filtrar as sugestoes de nome por projeto. Flora e Funga seguem a
 * divisao da propria lista (2o nivel de higherClassification); fauna, a classe
 * ou o filo. Nome sem grupo conhecido ("") passa em qualquer filtro.
 */
enum class NameGroup(val code: String, val pt: String, val en: String, val es: String, val flora: Boolean) {
    ANGIOSPERMS("angio", "Angiospermas", "Angiosperms", "Angiospermas", true),
    GYMNOSPERMS("gimno", "Gimnospermas", "Gymnosperms", "Gimnospermas", true),
    FERNS("samam", "Samambaias e licófitas", "Ferns and lycophytes", "Helechos y licófitas", true),
    BRYOPHYTES("brio", "Briófitas", "Bryophytes", "Briófitas", true),
    ALGAE("algas", "Algas", "Algae", "Algas", true),
    FUNGI("fungos", "Fungos", "Fungi", "Hongos", true),
    AMPHIBIANS("anfib", "Anfíbios", "Amphibians", "Anfibios", false),
    REPTILES("rept", "Répteis", "Reptiles", "Reptiles", false),
    BIRDS("aves", "Aves", "Birds", "Aves", false),
    MAMMALS("mamif", "Mamíferos", "Mammals", "Mamíferos", false),
    FISHES("peixes", "Peixes", "Fishes", "Peces", false),
    INSECTS("insetos", "Insetos", "Insects", "Insectos", false),
    ARACHNIDS("aracn", "Aracnídeos", "Arachnids", "Arácnidos", false),
    OTHER_ARTHROPODS("artrop", "Outros artrópodes", "Other arthropods", "Otros artrópodos", false),
    MOLLUSCS("molus", "Moluscos", "Molluscs", "Moluscos", false),
    OTHER_INVERTEBRATES("invert", "Outros invertebrados", "Other invertebrates", "Otros invertebrados", false);

    companion object {
        fun byCode(code: String): NameGroup? = entries.firstOrNull { it.code == code }

        /** "angio,aves" -> conjunto de codigos validos; vazio = todos. */
        fun parseSelection(value: String): Set<String> =
            value.split(',').map { it.trim() }.filter { byCode(it) != null }.toSet()

        private val FISH_CLASSES = setOf(
            "actinopterygii", "chondrichthyes", "elasmobranchii", "holocephali", "sarcopterygii",
            "actinistia", "dipnoi", "myxini", "petromyzonti", "cephalaspidomorphi", "teleostei"
        )
        private val GYMNOSPERM_CLASSES = setOf("pinopsida", "cycadopsida", "gnetopsida", "ginkgoopsida")
        private val FERN_CLASSES = setOf("polypodiopsida", "lycopodiopsida")
        private val BRYOPHYTE_PHYLA = setOf("bryophyta", "marchantiophyta", "anthocerotophyta")
        private val REPTILE_ORDERS = setOf("squamata", "testudines", "crocodylia")

        /** Grupo de um nome pela classificacao da lista; "" quando nao da para saber. */
        fun groupOf(kingdom: String, phylum: String, taxonClass: String, order: String, higherClassification: String): String {
            val flora = higherClassification.split(';').map { it.trim().lowercase() }
            if (flora.firstOrNull()?.startsWith("flora") == true && flora.size > 1) {
                when (flora[1]) {
                    "angiospermas" -> return ANGIOSPERMS.code
                    "gimnospermas" -> return GYMNOSPERMS.code
                    "samambaias e licófitas", "samambaias e licofitas" -> return FERNS.code
                    "briófitas", "briofitas" -> return BRYOPHYTES.code
                    "algas" -> return ALGAE.code
                    "fungos" -> return FUNGI.code
                }
            }
            val k = kingdom.trim().lowercase()
            val p = phylum.trim().lowercase()
            val c = taxonClass.trim().lowercase()
            return when (k) {
                "fungi" -> FUNGI.code
                "plantae" -> when {
                    c == "magnoliopsida" || c == "liliopsida" -> ANGIOSPERMS.code
                    c in GYMNOSPERM_CLASSES -> GYMNOSPERMS.code
                    c in FERN_CLASSES -> FERNS.code
                    p in BRYOPHYTE_PHYLA -> BRYOPHYTES.code
                    p == "tracheophyta" || p.isEmpty() -> ""
                    else -> ALGAE.code
                }
                "animalia" -> when {
                    c == "amphibia" -> AMPHIBIANS.code
                    c == "reptilia" || (c.isEmpty() && order.trim().lowercase() in REPTILE_ORDERS) -> REPTILES.code
                    c == "aves" -> BIRDS.code
                    c == "mammalia" -> MAMMALS.code
                    c in FISH_CLASSES -> FISHES.code
                    c == "insecta" -> INSECTS.code
                    c == "arachnida" -> ARACHNIDS.code
                    p == "arthropoda" -> OTHER_ARTHROPODS.code
                    p == "mollusca" -> MOLLUSCS.code
                    p == "chordata" && c.isEmpty() -> ""
                    p.isEmpty() -> ""
                    else -> OTHER_INVERTEBRATES.code
                }
                else -> ""
            }
        }
    }
}

/** Distribuicao de um taxon segundo a lista: estados, origem, endemismo e dominios. */
data class TaxonDistribution(
    /** Siglas dos estados ("RJ", "SP"...), em ordem alfabetica. */
    val states: List<String>,
    /** native, introduced, cultivated, naturalized, uncertain. */
    val establishment: List<String>,
    /** true = endemica do Brasil; false = nao endemica; null = a lista nao diz. */
    val endemic: Boolean?,
    /** Dominios fitogeograficos como a lista escreve ("Mata Atlântica"...). */
    val domains: List<String>
) {
    val isEmpty: Boolean get() = states.isEmpty() && establishment.isEmpty() && endemic == null && domains.isEmpty()

    companion object {
        /** Le os campos da extensao Distribution (uma linha ou varias ja juntas). */
        fun parse(locationIds: List<String>, establishmentMeans: List<String>, occurrenceRemarks: List<String>): TaxonDistribution {
            val states = locationIds.flatMap { it.split('|') }
                .map { it.trim().uppercase() }
                .mapNotNull { id -> Regex("^BR-([A-Z]{2})$").find(id)?.groupValues?.get(1) }
                .toSortedSet().toList()
            val establishment = establishmentMeans.flatMap { it.split('|') }
                .mapNotNull { normalizeEstablishment(it) }
                .distinct()
            var endemic: Boolean? = null
            val domains = sortedSetOf<String>()
            occurrenceRemarks.flatMap { it.split(" | ") }.forEach { remarks ->
                Regex("\"endemism\"\\s*:\\s*\"([^\"]*)\"").find(remarks)?.groupValues?.get(1)?.let { value ->
                    val v = value.lowercase()
                    if (v.startsWith("endemica") || v.startsWith("endêmica")) endemic = true
                    else if ((v.startsWith("não") || v.startsWith("nao")) && endemic != true) endemic = false
                }
                Regex("\"phytogeographicDomain\"\\s*:\\s*\\[([^\\]]*)\\]").find(remarks)?.groupValues?.get(1)?.let { list ->
                    list.split(',').map { it.trim().trim('"').trim() }.filter { it.isNotEmpty() }.forEach { domains += it }
                }
            }
            return TaxonDistribution(states, establishment, endemic, domains.toList())
        }

        fun normalizeEstablishment(value: String): String? = when (value.trim().lowercase()) {
            "" -> null
            "nativa", "nativo", "native" -> "native"
            "introduced", "introduzida", "introduzido", "exótica", "exotica" -> "introduced"
            "cultivada", "cultivado", "cultivated", "managed" -> "cultivated"
            "naturalizada", "naturalizado", "naturalised", "naturalized" -> "naturalized"
            "uncertain", "incerta", "incerto" -> "uncertain"
            else -> value.trim().lowercase()
        }
    }
}
