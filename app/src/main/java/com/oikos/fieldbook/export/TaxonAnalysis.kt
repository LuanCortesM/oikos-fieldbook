package com.oikos.fieldbook.export

import com.oikos.fieldbook.data.OccurrenceEntity
import com.oikos.fieldbook.data.SessionEntity
import com.oikos.fieldbook.util.parseFlatJsonMap
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Analise do projeto para a tela de Analise e as abas de analise da
 * exportacao: matriz taxon x expedicao, riqueza, abundancia, frequencia e
 * constancia, diversidade, similaridade entre expedicoes, familias, meses,
 * horarios, altitude, sexo e fase de vida. So le os registros; nenhum muda.
 *
 * Entram os registros com nome cientifico que nao sao de ausencia; registro
 * sem contagem de individuos vale 1 nas somas. Os indices de diversidade e o
 * Bray-Curtis dependem da contagem, entao ficam sem valor onde algum registro
 * nao tem contagem, em vez de usar o 1. Cada expedicao e uma unidade amostral,
 * inclusive a que nao teve registro. A que so agrupa outras (citada como
 * parentEventID) e nao tem registro proprio fica de fora: senao contaria
 * como uma expedicao vazia e baixaria a frequencia de todos os taxons.
 */
internal class TaxonAnalysis(sessions: List<SessionEntity>, occurrences: List<OccurrenceEntity>) {

    /**
     * Indices de um conjunto de abundancias, com logaritmo natural (como o
     * vegan do R): Shannon H', Simpson 1 - soma(p^2), Pielou J' = H'/ln S e
     * Berger-Parker (individuos do mais abundante / total). Os dois ultimos
     * sao o numero efetivo de especies: quantas especies igualmente
     * abundantes dariam o mesmo Shannon (exp H') e o mesmo Simpson (1/soma p^2).
     */
    class Diversity(
        val shannon: Double,
        val simpson: Double,
        /** Sem valor com um taxon so (ln 1 = 0). */
        val pielou: Double?,
        val bergerParker: Double,
        val effectiveShannon: Double,
        val effectiveSimpson: Double
    )

    /** Constancia de Dajoz: mais de 50% das expedicoes, de 25% a 50%, menos de 25%. */
    enum class Constancy { CONSTANT, ACCESSORY, ACCIDENTAL }

    class Expedition(
        val session: SessionEntity,
        val records: Int,
        val individuals: Int,
        val richness: Int,
        val newTaxa: Int,
        val cumulativeRichness: Int,
        val recordsWithoutCount: Int,
        /** Sem valor se algum registro da expedicao nao tem contagem. */
        val diversity: Diversity?
    )

    class Taxon(
        val scientificName: String,
        val family: String,
        val vernacularName: String,
        /** Individuos em cada expedicao, na ordem de [TaxonAnalysis.expeditions]. */
        val individualsByExpedition: List<Int>,
        /** Registros em cada expedicao: a presenca vale mesmo com contagem zero. */
        val recordsByExpedition: List<Int>,
        val records: Int,
        val expeditionsWithTaxon: Int,
        val minimumAltitude: Double?,
        val maximumAltitude: Double?,
        /** Registros por valor do vocabulario Darwin Core ("male", "adult"), do mais comum ao menos. */
        val sexes: List<Pair<String, Int>>,
        val lifeStages: List<Pair<String, Int>>
    ) {
        val individuals: Int get() = individualsByExpedition.sum()
    }

    class Family(val name: String, val taxa: Int, val individuals: Int, val records: Int)

    /** Mes do ano (1 a 12) ou hora do dia (0 a 23). [expeditions] so vale para mes. */
    class Period(val value: Int, val records: Int, val individuals: Int, val richness: Int, val expeditions: Int)

    /** Similaridade entre duas expedicoes, de 0 (nada em comum) a 1 (iguais). */
    class Similarity(val shared: Int, val jaccard: Double?, val sorensen: Double?, val brayCurtis: Double?)

    /** Em ordem de data: e a ordem da riqueza acumulada. */
    val expeditions: List<Expedition>
    val taxa: List<Taxon>
    val recordsUsed: Int
    val totalIndividuals: Int
    val absenceRecords: Int
    val recordsWithoutName: Int
    val groupingExpeditions: Int
    val recordsWithoutCount: Int
    /** Do projeto inteiro; sem valor se algum registro nao tem contagem. */
    val diversity: Diversity?
    /** Da familia com mais taxons para a com menos; taxons sem familia ficam em [taxaWithoutFamily]. */
    val families: List<Family>
    val taxaWithoutFamily: Int
    /** Doze meses, de janeiro a dezembro, somando todos os anos. */
    val months: List<Period>
    val recordsWithoutDate: Int
    /** Vinte e quatro horas, pela hora do registro (eventTime). */
    val hours: List<Period>
    val recordsWithoutTime: Int

    init {
        val parents = sessions.map { it.parentEventId.trim() }.filter { it.isNotEmpty() }.toSet()
        val sessionsWithRecords = occurrences.map { it.sessionId }.toSet()
        val units = sessions
            .filter { it.eventId !in parents || it.id in sessionsWithRecords }
            .sortedWith(compareBy({ it.eventDate.isBlank() }, { it.eventDate }, { it.startTime }, { it.id }))
        groupingExpeditions = sessions.size - units.size
        val column = units.withIndex().associate { (index, session) -> session.id to index }
        val sessionById = units.associateBy { it.id }

        val inUnits = occurrences.filter { it.sessionId in column }
        absenceRecords = inUnits.count { isAbsence(it) }
        val present = inUnits.filterNot { isAbsence(it) }
        recordsWithoutName = present.count { it.scientificName.isBlank() }
        val used = present.filter { it.scientificName.isNotBlank() }.sortedBy { it.id }
        recordsUsed = used.size
        recordsWithoutCount = used.count { it.individualCount == null }
        val dynamic = used.associate { it.id to parseFlatJsonMap(it.dynamicFieldsJson) }

        taxa = used.groupBy { key(it.scientificName) }.values.map { records ->
            val individuals = IntArray(units.size)
            val recordsHere = IntArray(units.size)
            records.forEach { record ->
                val index = column.getValue(record.sessionId)
                individuals[index] += individualsOf(record)
                recordsHere[index]++
            }
            val altitudes = records.mapNotNull { altitudeOf(it, sessionById[it.sessionId]) }
            Taxon(
                scientificName = mostCommon(records.map { tidy(it.scientificName) }),
                family = mostCommon(records.map { dynamic[it.id]?.get("family").orEmpty().trim() }),
                vernacularName = mostCommon(records.map { it.vernacularName.trim() }),
                individualsByExpedition = individuals.toList(),
                recordsByExpedition = recordsHere.toList(),
                records = records.size,
                expeditionsWithTaxon = recordsHere.count { it > 0 },
                minimumAltitude = altitudes.minOrNull(),
                maximumAltitude = altitudes.maxOrNull(),
                sexes = tally(records.map { it.sex }),
                lifeStages = tally(records.map { it.lifeStage })
            )
        }.sortedWith(compareBy({ it.family.isBlank() }, { it.family.lowercase(Locale.ROOT) }, { it.scientificName.lowercase(Locale.ROOT) }))
        totalIndividuals = taxa.sumOf { it.individuals }
        diversity = if (recordsWithoutCount > 0) null else diversityOf(taxa.map { it.individuals })

        val byColumn = used.groupBy { column.getValue(it.sessionId) }
        val seenSoFar = mutableSetOf<String>()
        expeditions = units.mapIndexed { index, session ->
            val records = byColumn[index].orEmpty()
            val names = records.map { key(it.scientificName) }.toSet()
            val newTaxa = names.count { it !in seenSoFar }
            seenSoFar += names
            val withoutCount = records.count { it.individualCount == null }
            Expedition(
                session, records.size, records.sumOf { individualsOf(it) }, names.size, newTaxa, seenSoFar.size, withoutCount,
                if (withoutCount > 0) null else diversityOf(taxa.map { it.individualsByExpedition[index] })
            )
        }

        families = taxa.filter { it.family.isNotBlank() }
            .groupBy { it.family.lowercase(Locale.ROOT) }
            .values.map { group -> Family(group.first().family, group.size, group.sumOf { it.individuals }, group.sumOf { it.records }) }
            .sortedWith(compareByDescending<Family> { it.taxa }.thenByDescending { it.individuals }.thenBy { it.name.lowercase(Locale.ROOT) })
        taxaWithoutFamily = taxa.count { it.family.isBlank() }

        // Mes pela data do registro; sem ela, a da expedicao (como no Darwin Core exportado).
        val monthOf = used.associate { record ->
            val date = dynamic[record.id]?.get("eventDate").orEmpty().ifBlank { sessionById[record.sessionId]?.eventDate.orEmpty() }
            record.id to monthOf(date)
        }
        recordsWithoutDate = monthOf.values.count { it == null }
        val expeditionsByMonth = units.mapNotNull { monthOf(it.eventDate) }.groupingBy { it }.eachCount()
        months = (1..12).map { month ->
            val records = used.filter { monthOf[it.id] == month }
            Period(month, records.size, records.sumOf { individualsOf(it) }, records.map { key(it.scientificName) }.toSet().size, expeditionsByMonth[month] ?: 0)
        }

        val hourOf = used.associate { it.id to hourOf(dynamic[it.id]?.get("eventTime").orEmpty()) }
        recordsWithoutTime = hourOf.values.count { it == null }
        hours = (0..23).map { hour ->
            val records = used.filter { hourOf[it.id] == hour }
            Period(hour, records.size, records.sumOf { individualsOf(it) }, records.map { key(it.scientificName) }.toSet().size, 0)
        }
    }

    /** Expedicoes com o taxon / total de expedicoes x 100. */
    fun frequencyPercent(taxon: Taxon): Double =
        if (expeditions.isEmpty()) 0.0 else taxon.expeditionsWithTaxon * 100.0 / expeditions.size

    /** Individuos do taxon / total de individuos x 100. */
    fun relativeAbundancePercent(taxon: Taxon): Double =
        if (totalIndividuals == 0) 0.0 else taxon.individuals * 100.0 / totalIndividuals

    fun constancy(taxon: Taxon): Constancy {
        val percent = frequencyPercent(taxon)
        return when {
            percent > 50.0 -> Constancy.CONSTANT
            percent >= 25.0 -> Constancy.ACCESSORY
            else -> Constancy.ACCIDENTAL
        }
    }

    /**
     * Jaccard = a / (a + b + c) e Sorensen = 2a / (2a + b + c), com a = taxons
     * nas duas expedicoes e b, c = taxons so em uma. Bray-Curtis = 1 - soma|x - y| /
     * soma(x + y) com os individuos, sem valor se alguma das duas tem registro
     * sem contagem. Expedicao sem taxon nao tem similaridade com nada.
     */
    fun similarity(first: Int, second: Int): Similarity {
        val inFirst = taxa.map { it.recordsByExpedition[first] > 0 }
        val inSecond = taxa.map { it.recordsByExpedition[second] > 0 }
        if (inFirst.none { it } || inSecond.none { it }) return Similarity(0, null, null, null)
        val shared = taxa.indices.count { inFirst[it] && inSecond[it] }
        val onlyFirst = taxa.indices.count { inFirst[it] && !inSecond[it] }
        val onlySecond = taxa.indices.count { !inFirst[it] && inSecond[it] }
        val counted = expeditions[first].recordsWithoutCount == 0 && expeditions[second].recordsWithoutCount == 0
        val total = taxa.sumOf { it.individualsByExpedition[first] + it.individualsByExpedition[second] }
        val difference = taxa.sumOf { abs(it.individualsByExpedition[first] - it.individualsByExpedition[second]) }
        return Similarity(
            shared = shared,
            jaccard = shared.toDouble() / (shared + onlyFirst + onlySecond),
            sorensen = 2.0 * shared / (2 * shared + onlyFirst + onlySecond),
            brayCurtis = if (counted && total > 0) 1.0 - difference.toDouble() / total else null
        )
    }

    private companion object {
        fun isAbsence(occurrence: OccurrenceEntity) = occurrence.occurrenceStatus.trim().equals("absent", ignoreCase = true)

        fun individualsOf(occurrence: OccurrenceEntity) = occurrence.individualCount?.coerceAtLeast(0) ?: 1

        fun tidy(name: String) = ScientificName.tidy(name)

        fun key(name: String) = ScientificName.key(name)

        /** Grafia mais usada; no empate, a do registro mais antigo. */
        fun mostCommon(values: List<String>): String =
            values.filter { it.isNotBlank() }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key.orEmpty()

        /** Valores preenchidos e quantas vezes, do mais comum ao menos. */
        fun tally(values: List<String>): List<Pair<String, Int>> =
            values.map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
                .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }

        /**
         * Altitude como sai em minimumElevationInMeters: a do registro; sem
         * coordenada propria, a do ponto da expedicao.
         */
        fun altitudeOf(occurrence: OccurrenceEntity, session: SessionEntity?): Double? =
            occurrence.altitude ?: if (occurrence.latitude == null || occurrence.longitude == null) session?.altitude else null

        private val YEAR_MONTH = Regex("^\\d{4}-(\\d{2})")
        private val HOUR_MINUTE = Regex("^(\\d{1,2}):\\d{2}")

        fun monthOf(date: String): Int? = YEAR_MONTH.find(date.trim())?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..12 }

        fun hourOf(time: String): Int? = HOUR_MINUTE.find(time.trim())?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..23 }

        fun diversityOf(abundances: List<Int>): Diversity? {
            val present = abundances.filter { it > 0 }
            val total = present.sum()
            if (total == 0) return null
            val proportions = present.map { it.toDouble() / total }
            // + 0.0: com um taxon so a conta da -0.0, que saia "-0" na planilha.
            val shannon = -proportions.sumOf { it * ln(it) } + 0.0
            val dominance = proportions.sumOf { it * it }
            return Diversity(
                shannon = shannon,
                simpson = 1.0 - dominance,
                pielou = if (present.size > 1) shannon / ln(present.size.toDouble()) else null,
                bergerParker = present.max().toDouble() / total,
                effectiveShannon = exp(shannon),
                effectiveSimpson = 1.0 / dominance
            )
        }
    }
}

/**
 * Leitura do nome cientifico como foi escrito, para agrupar e para mostrar.
 *
 * [key] tira a autoria e a diferenca de caixa: "Boana faber", "boana  faber"
 * e "Boana faber (Wied-Neuwied, 1821)" sao o mesmo taxon. O nome escolhido do
 * checklist vem com autor e o digitado nao: sem isso a mesma especie contava
 * duas vezes na riqueza. O epiteto vai ate a primeira palavra que nao comeca
 * com minuscula; "var." ou "subsp." seguidos de epiteto voltam a contar, e o
 * que vem depois de "sp." fica inteiro, porque e o rotulo da morfoespecie
 * (sp. 1, sp. A).
 *
 * [runs] marca o que vai em italico: genero e epitetos. Autor, "cf.", "sp.",
 * "var." e nomes acima de genero (Hylidae) ficam sem italico.
 */
internal object ScientificName {

    class Run(val text: String, val italic: Boolean)

    private class Word(val text: String, val inKey: Boolean, val italic: Boolean)

    private val WHITESPACE = Regex("\\s+")
    private val OPEN_NAMES = setOf("sp", "sp.", "spp", "spp.")
    private val RANK_MARKERS = setOf("subsp.", "ssp.", "var.", "subvar.", "f.", "forma")
    private val QUALIFIERS = OPEN_NAMES + setOf("cf", "cf.", "aff", "aff.", "gr.", "nr.", "s.l.", "s.str.")
    private val ABOVE_GENUS = listOf("idae", "inae", "oidea", "aceae", "oideae", "ales", "formes", "phyta", "mycota", "opsida", "mycetes")

    fun tidy(name: String) = name.trim().replace(WHITESPACE, " ")

    fun key(name: String): String = words(name).filter { it.inKey }.joinToString(" ") { it.text }.lowercase(Locale.ROOT)

    fun runs(name: String): List<Run> {
        val runs = mutableListOf<Run>()
        words(name).forEachIndexed { index, word ->
            val last = runs.lastOrNull()
            when {
                last == null -> runs += Run(word.text, word.italic)
                last.italic == word.italic -> runs[runs.lastIndex] = Run("${last.text} ${word.text}", word.italic)
                // O espaco fica fora do italico.
                word.italic -> {
                    runs[runs.lastIndex] = Run("${last.text} ", false)
                    runs += Run(word.text, true)
                }
                else -> runs += Run(" ${word.text}", false)
            }
        }
        return runs
    }

    private fun words(name: String): List<Word> {
        val original = tidy(name).split(' ').filter { it.isNotEmpty() }
        if (original.isEmpty()) return emptyList()
        // "BOANA FABER" e lido como "Boana faber": senao o epiteto parecia autor.
        val parts = if (original.none { word -> word.any { it.isLowerCase() } }) {
            original.mapIndexed { index, word -> word.lowercase(Locale.ROOT).let { if (index == 0) it.replaceFirstChar { c -> c.titlecase(Locale.ROOT) } else it } }
        } else {
            original
        }
        val result = mutableListOf(Word(original[0], inKey = true, italic = false))
        var authorship = false
        var index = 1
        while (index < parts.size) {
            val part = parts[index]
            val lower = part.lowercase(Locale.ROOT)
            val next = parts.getOrNull(index + 1)
            when {
                !authorship && result.last { it.inKey }.text.lowercase(Locale.ROOT) in OPEN_NAMES -> {
                    original.subList(index, original.size).forEach { result += Word(it, inKey = true, italic = false) }
                    break
                }
                lower in RANK_MARKERS && next != null && next.first().isLowerCase() -> {
                    result += Word(original[index], inKey = true, italic = false)
                    result += Word(original[index + 1], inKey = true, italic = true)
                    authorship = false
                    index++
                }
                !authorship && part == "×" -> result += Word(part, inKey = true, italic = false)
                !authorship && part.first().isLowerCase() -> result += Word(original[index], inKey = true, italic = lower !in QUALIFIERS)
                else -> {
                    authorship = true
                    result += Word(original[index], inKey = false, italic = false)
                }
            }
            index++
        }
        val genus = parts[0]
        val italicGenus = result.size > 1 && result[1].inKey && genus.first().isUpperCase() &&
            ABOVE_GENUS.none { genus.lowercase(Locale.ROOT).endsWith(it) }
        return result.mapIndexed { position, word ->
            when {
                position == 0 -> Word(word.text, inKey = true, italic = italicGenus)
                word.italic && !italicGenus -> Word(word.text, word.inKey, italic = false)
                else -> word
            }
        }
    }
}
