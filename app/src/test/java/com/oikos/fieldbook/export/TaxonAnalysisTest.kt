package com.oikos.fieldbook.export

import com.oikos.fieldbook.data.OccurrenceEntity
import com.oikos.fieldbook.data.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contagens da aba de analise: so le os registros, nunca muda nenhum. */
class TaxonAnalysisTest {

    private fun session(id: Long, date: String, name: String = "Campanha $id", parent: String = "", altitude: Double? = null) =
        SessionEntity(id = id, projectId = 1, eventId = "EV-$id", name = name, eventDate = date, parentEventId = parent, altitude = altitude)

    private var nextId = 0L
    private fun record(
        sessionId: Long, name: String, count: Int? = null, status: String = "present", family: String = "", vernacular: String = "",
        date: String = "", time: String = "", altitude: Double? = null, latitude: Double? = null, sex: String = "", lifeStage: String = ""
    ): OccurrenceEntity {
        val dynamic = listOf("family" to family, "eventDate" to date, "eventTime" to time).filter { it.second.isNotBlank() }
        return OccurrenceEntity(
            id = ++nextId, projectId = 1, sessionId = sessionId, occurrenceId = "OCC-$nextId", scientificName = name,
            individualCount = count, occurrenceStatus = status, vernacularName = vernacular,
            altitude = altitude, latitude = latitude, longitude = latitude?.let { -45.0 }, sex = sex, lifeStage = lifeStage,
            dynamicFieldsJson = dynamic.joinToString(",", "{", "}") { (key, value) -> "\"$key\":\"$value\"" }
        )
    }

    @Test
    fun matrizSomaIndividuosPorExpedicaoEmOrdemDeData() {
        // A expedicao 1 e a mais nova: a coluna dela vem depois.
        val sessions = listOf(session(1, "2026-04-10"), session(2, "2026-03-14"))
        val analysis = TaxonAnalysis(sessions, listOf(
            record(1, "Boana faber", count = 3),
            record(2, "Boana faber", count = 2),
            record(2, "Boana faber"),
            record(2, "Rhinella ornata", count = 5)
        ))
        assertEquals(listOf(2L, 1L), analysis.expeditions.map { it.session.id })
        val faber = analysis.taxa.single { it.scientificName == "Boana faber" }
        assertEquals(listOf(3, 3), faber.individualsByExpedition) // 2 + 1 (sem contagem vale 1) e 3
        assertEquals(6, faber.individuals)
        assertEquals(3, faber.records)
        assertEquals(2, faber.expeditionsWithTaxon)
        assertEquals(100.0, analysis.frequencyPercent(faber), 1e-9)
        assertEquals(11, analysis.totalIndividuals)
        assertEquals(6 * 100.0 / 11, analysis.relativeAbundancePercent(faber), 1e-9)
        val ornata = analysis.taxa.single { it.scientificName == "Rhinella ornata" }
        assertEquals(listOf(5, 0), ornata.individualsByExpedition)
        assertEquals(50.0, analysis.frequencyPercent(ornata), 1e-9)
    }

    @Test
    fun ausenciaENomeVazioFicamDeForaMasSaoContados() {
        val analysis = TaxonAnalysis(listOf(session(1, "2026-03-14")), listOf(
            record(1, "Boana faber", count = 2),
            record(1, "Scinax alter", status = "absent"),
            record(1, "Scinax alter", status = " Absent "),
            record(1, "  ")
        ))
        assertEquals(listOf("Boana faber"), analysis.taxa.map { it.scientificName })
        assertEquals(2, analysis.absenceRecords)
        assertEquals(1, analysis.recordsWithoutName)
        assertEquals(1, analysis.recordsUsed)
        assertEquals(2, analysis.totalIndividuals)
    }

    @Test
    fun grafiasDoMesmoNomeSaoUmTaxonComAGrafiaMaisUsada() {
        val analysis = TaxonAnalysis(listOf(session(1, "2026-03-14")), listOf(
            record(1, "boana faber"),
            record(1, "Boana  faber "),
            record(1, "Boana faber")
        ))
        val taxon = analysis.taxa.single()
        assertEquals("Boana faber", taxon.scientificName)
        assertEquals(3, taxon.records)
    }

    @Test
    fun morfoespeciesContamSeparadas() {
        val analysis = TaxonAnalysis(listOf(session(1, "2026-03-14")), listOf(
            record(1, "Scinax sp. 1"),
            record(1, "Scinax sp. 2"),
            record(1, "Scinax sp. A"),
            record(1, "Scinax sp. B"),
            record(1, "Scinax sp.")
        ))
        assertEquals(5, analysis.expeditions.single().richness)
    }

    @Test
    fun nomeComAutorENomeSemAutorSaoOMesmoTaxon() {
        // O checklist poe o autor; o nome digitado em campo nao.
        val analysis = TaxonAnalysis(listOf(session(1, "2026-03-14")), listOf(
            record(1, "Aplastodiscus arildae (Cruz & Peixoto, 1987)"),
            record(1, "Aplastodiscus arildae"),
            record(1, "Vriesea itatiaiae Wawra"),
            record(1, "Vriesea itatiaiae"),
            record(1, "Boana faber Wied-Neuwied, 1821"),
            record(1, "Boana faber"),
            record(1, "Boana faber"),
            record(1, "Boana cf. faber")
        ))
        assertEquals(
            mapOf(
                "Aplastodiscus arildae (Cruz & Peixoto, 1987)" to 2, // empate: fica a grafia do registro mais antigo
                "Boana cf. faber" to 1,
                "Boana faber" to 3,
                "Vriesea itatiaiae Wawra" to 2
            ),
            analysis.taxa.associate { it.scientificName to it.records }
        )
    }

    @Test
    fun variedadeESubespecieContinuamSeparadasDaEspecie() {
        val analysis = TaxonAnalysis(listOf(session(1, "2026-03-14")), listOf(
            record(1, "Quercus robur L."),
            record(1, "Quercus robur L. var. pedunculata (Ehrh.) Rchb."),
            record(1, "Quercus robur var. pedunculata"),
            record(1, "Salvator merianae"),
            record(1, "Salvator merianae subsp. alba Silva, 2020"),
            record(1, "Miconia albicans (Sw.) Triana f.")
        ))
        assertEquals(
            mapOf(
                "Miconia albicans (Sw.) Triana f." to 1, // "f." de filius, sem epiteto depois: e autor
                "Quercus robur L." to 1,
                "Quercus robur L. var. pedunculata (Ehrh.) Rchb." to 2,
                "Salvator merianae" to 1,
                "Salvator merianae subsp. alba Silva, 2020" to 1
            ),
            analysis.taxa.associate { it.scientificName to it.records }
        )
    }

    @Test
    fun expedicaoQueSoAgrupaOutrasNaoViraColunaMasAVaziaConta() {
        val sessions = listOf(
            session(1, "2026-03-01", name = "Campanha de março"),
            session(2, "2026-03-14", parent = "EV-1"),
            session(3, "2026-03-15", parent = "EV-1"),
            session(4, "2026-03-16", parent = "EV-1")
        )
        // A 4 nao teve registro: entra no total de expedicoes da frequencia.
        val analysis = TaxonAnalysis(sessions, listOf(record(2, "Boana faber"), record(3, "Boana faber")))
        assertEquals(listOf(2L, 3L, 4L), analysis.expeditions.map { it.session.id })
        assertEquals(1, analysis.groupingExpeditions)
        val taxon = analysis.taxa.single()
        assertEquals(2 * 100.0 / 3, analysis.frequencyPercent(taxon), 1e-9)
    }

    @Test
    fun expedicaoQueAgrupaMasTemRegistroProprioContinua() {
        val sessions = listOf(session(1, "2026-03-01"), session(2, "2026-03-14", parent = "EV-1"))
        val analysis = TaxonAnalysis(sessions, listOf(record(1, "Boana faber"), record(2, "Boana faber")))
        assertEquals(2, analysis.expeditions.size)
        assertEquals(0, analysis.groupingExpeditions)
    }

    @Test
    fun taxonsNovosERiquezaAcumuladaSeguemAData() {
        val sessions = listOf(session(1, "2026-03-14"), session(2, "2026-04-10"), session(3, ""))
        val analysis = TaxonAnalysis(sessions, listOf(
            record(1, "A a"), record(1, "B b"),
            record(2, "B b"), record(2, "C c"),
            record(3, "A a")
        ))
        assertEquals(listOf(1L, 2L, 3L), analysis.expeditions.map { it.session.id }) // sem data por ultimo
        assertEquals(listOf(2, 2, 1), analysis.expeditions.map { it.richness })
        assertEquals(listOf(2, 1, 0), analysis.expeditions.map { it.newTaxa })
        assertEquals(listOf(2, 3, 3), analysis.expeditions.map { it.cumulativeRichness })
        assertEquals(3, analysis.taxa.size)
    }

    @Test
    fun ordenaPorFamiliaEDepoisPorNomeComSemFamiliaNoFim() {
        val analysis = TaxonAnalysis(listOf(session(1, "2026-03-14")), listOf(
            record(1, "Zeta z"),
            record(1, "Scinax alter", family = "Hylidae"),
            record(1, "Rhinella ornata", family = "Bufonidae", vernacular = "sapo-cururu"),
            record(1, "Boana faber", family = "Hylidae")
        ))
        assertEquals(listOf("Rhinella ornata", "Boana faber", "Scinax alter", "Zeta z"), analysis.taxa.map { it.scientificName })
        assertEquals("sapo-cururu", analysis.taxa.first().vernacularName)
    }

    @Test
    fun contagemZeroFicaZeroENaoQuebraSemIndividuos() {
        val analysis = TaxonAnalysis(listOf(session(1, "2026-03-14")), listOf(record(1, "Boana faber", count = 0)))
        val taxon = analysis.taxa.single()
        assertEquals(0, taxon.individuals)
        assertEquals(1, taxon.expeditionsWithTaxon)
        assertEquals(0.0, analysis.relativeAbundancePercent(taxon), 1e-9)
    }

    private fun runs(name: String) = ScientificName.runs(name).map { it.text to it.italic }

    @Test
    fun italicoSoNoGeneroENoEpiteto() {
        assertEquals(listOf("Aplastodiscus arildae" to true, " (Cruz & Peixoto, 1987)" to false), runs("Aplastodiscus arildae (Cruz & Peixoto, 1987)"))
        assertEquals(listOf("Boana" to true, " cf. " to false, "faber" to true), runs("Boana cf. faber"))
        assertEquals(listOf("Scinax" to true, " sp. 1" to false), runs("Scinax sp. 1"))
        assertEquals(
            listOf("Quercus robur" to true, " L. var. " to false, "pedunculata" to true, " (Ehrh.) Rchb." to false),
            runs("Quercus robur L. var. pedunculata (Ehrh.) Rchb.")
        )
    }

    @Test
    fun familiaENomeSoltoNaoVaoEmItalico() {
        assertEquals(listOf("Hylidae" to false), runs("Hylidae"))
        assertEquals(listOf("Hylidae sp." to false), runs("Hylidae sp."))
        assertEquals(listOf("Anura" to false), runs("Anura"))
    }

    @Test
    fun nomeEmMaiusculasEOMesmoTaxon() {
        // Sem isso "FABER" parecia autor e o nome virava so o genero.
        assertEquals(ScientificName.key("Boana faber"), ScientificName.key("BOANA FABER"))
        assertEquals("boana", ScientificName.key("Boana Wied-Neuwied"))
    }

    /** Matriz de teste com contagem em tudo: 4 expedicoes x 5 taxons. */
    private fun counted(): TaxonAnalysis {
        val counts = mapOf(
            "A a" to listOf(5, 3, 0, 1), "B b" to listOf(2, 0, 4, 0), "C c" to listOf(0, 7, 1, 0),
            "D d" to listOf(1, 1, 1, 1), "E e" to listOf(0, 0, 0, 6)
        )
        val records = counts.flatMap { (name, perExpedition) ->
            perExpedition.mapIndexedNotNull { index, n -> if (n > 0) record(index + 1L, name, count = n) else null }
        }
        return TaxonAnalysis((1..4).map { session(it.toLong(), "2026-03-1$it") }, records)
    }

    private fun assertDiversity(expected: List<Double>, actual: TaxonAnalysis.Diversity?) {
        val got = actual ?: error("sem indices")
        listOf(got.shannon, got.simpson, got.pielou ?: Double.NaN, got.bergerParker, got.effectiveShannon, got.effectiveSimpson)
            .zip(expected).forEach { (value, reference) -> assertEquals(reference, value, 1e-9) }
    }

    /**
     * Valores do vegan 2.7.2 no R 4.5.2, com a mesma matriz: diversity()
     * para Shannon e Simpson, H'/log(S), max/soma, exp(H') e 1/(1 - Simpson).
     */
    @Test
    fun diversidadeBateComOVeganDoR() {
        val analysis = counted()
        val vegan = listOf(
            listOf(0.900256051269, 0.531250000000, 0.819448371873, 0.625000000000, 2.460232976289, 2.133333333333),
            listOf(0.859967281036, 0.512396694215, 0.782775952814, 0.636363636364, 2.363083374800, 2.050847457627),
            listOf(0.867563228481, 0.500000000000, 0.789690082143, 0.666666666667, 2.381101577952, 2.000000000000),
            listOf(0.735621939759, 0.406250000000, 0.669591945536, 0.750000000000, 2.086779440098, 1.684210526316)
        )
        analysis.expeditions.forEachIndexed { index, expedition -> assertDiversity(vegan[index], expedition.diversity) }
        assertDiversity(
            listOf(1.573572876476, 0.786042240588, 0.977715800230, 0.272727272727, 4.823852470111, 4.673819742489),
            analysis.diversity
        )
    }

    /** 1 - vegdist(): jaccard e bray binarios (Sorensen) e bray com abundancia, no vegan 2.7.2. */
    @Test
    fun similaridadeBateComOVeganDoR() {
        val analysis = counted()
        val vegan = mapOf(
            (0 to 1) to Triple(0.5, 0.666666666667, 0.421052631579),
            (0 to 2) to Triple(0.5, 0.666666666667, 0.428571428571),
            (0 to 3) to Triple(0.5, 0.666666666667, 0.250000000000),
            (1 to 2) to Triple(0.5, 0.666666666667, 0.235294117647),
            (1 to 3) to Triple(0.5, 0.666666666667, 0.210526315789),
            (2 to 3) to Triple(0.2, 0.333333333333, 0.142857142857)
        )
        vegan.forEach { (pair, reference) ->
            val similarity = analysis.similarity(pair.first, pair.second)
            assertEquals("$pair", reference.first, similarity.jaccard!!, 1e-9)
            assertEquals("$pair", reference.second, similarity.sorensen!!, 1e-9)
            assertEquals("$pair", reference.third, similarity.brayCurtis!!, 1e-9)
        }
        assertEquals(1, analysis.similarity(2, 3).shared)
    }

    @Test
    fun semContagemOsIndicesFicamSemValorEmVezDeUsarUm() {
        val analysis = TaxonAnalysis(listOf(session(1, "2026-03-14"), session(2, "2026-03-15")), listOf(
            record(1, "A a", count = 3), record(1, "B b"),
            record(2, "A a", count = 2), record(2, "C c", count = 1)
        ))
        assertNull(analysis.expeditions[0].diversity)
        assertNotNull(analysis.expeditions[1].diversity)
        assertNull(analysis.diversity)
        assertEquals(1, analysis.recordsWithoutCount)
        val similarity = analysis.similarity(0, 1)
        assertNull(similarity.brayCurtis)
        assertEquals(1.0 / 3, similarity.jaccard!!, 1e-9) // presenca nao depende de contagem
    }

    @Test
    fun umTaxonSoNaoTemPielouEExpedicaoVaziaNaoTemSimilaridade() {
        val analysis = TaxonAnalysis(listOf(session(1, "2026-03-14"), session(2, "2026-03-15")), listOf(record(1, "A a", count = 4)))
        val single = analysis.expeditions[0].diversity!!
        assertEquals(0.0, single.shannon, 1e-12)
        assertNull(single.pielou)
        assertEquals(1.0, single.bergerParker, 1e-12)
        assertNull(analysis.expeditions[1].diversity)
        val similarity = analysis.similarity(0, 1)
        assertNull(similarity.jaccard)
        assertNull(similarity.sorensen)
    }

    @Test
    fun constanciaDeDajoz() {
        // 5 expedicoes: em 3 (60%) e constante, em 2 (40%) acessorio, em 1 (20%) acidental.
        val sessions = (1..5).map { session(it.toLong(), "2026-03-1$it") }
        val analysis = TaxonAnalysis(sessions, listOf(
            record(1, "A a"), record(2, "A a"), record(3, "A a"),
            record(1, "B b"), record(2, "B b"),
            record(1, "C c")
        ))
        val byName = analysis.taxa.associateBy { it.scientificName }
        assertEquals(TaxonAnalysis.Constancy.CONSTANT, analysis.constancy(byName.getValue("A a")))
        assertEquals(TaxonAnalysis.Constancy.ACCESSORY, analysis.constancy(byName.getValue("B b")))
        assertEquals(TaxonAnalysis.Constancy.ACCIDENTAL, analysis.constancy(byName.getValue("C c")))
        // Exatamente 50% e acessorio; exatamente 25% tambem.
        val four = TaxonAnalysis((1..4).map { session(it.toLong(), "2026-03-1$it") }, listOf(record(1, "D d"), record(2, "D d"), record(3, "E e")))
        four.taxa.forEach { assertEquals(it.scientificName, TaxonAnalysis.Constancy.ACCESSORY, four.constancy(it)) }
    }

    @Test
    fun familiasDaMaisRicaParaAMenosESemFamiliaContadaAParte() {
        val analysis = TaxonAnalysis(listOf(session(1, "2026-03-14")), listOf(
            record(1, "Boana faber", count = 4, family = "Hylidae"),
            record(1, "Scinax alter", count = 1, family = "Hylidae"),
            record(1, "Rhinella ornata", count = 9, family = "Bufonidae"),
            record(1, "Zeta z")
        ))
        assertEquals(listOf("Hylidae", "Bufonidae"), analysis.families.map { it.name })
        assertEquals(listOf(2, 1), analysis.families.map { it.taxa })
        assertEquals(listOf(5, 9), analysis.families.map { it.individuals })
        assertEquals(1, analysis.taxaWithoutFamily)
    }

    @Test
    fun mesesSomamOsAnosEUsamADataDaExpedicaoSemDataPropria() {
        val analysis = TaxonAnalysis(listOf(session(1, "2025-10-20"), session(2, "2026-10-12"), session(3, "")), listOf(
            record(1, "A a", count = 2),
            record(2, "B b", count = 1),
            record(2, "A a", count = 1, date = "2026-11-01"),
            record(3, "C c")
        ))
        val october = analysis.months[9]
        assertEquals(10, october.value)
        assertEquals(2, october.records)
        assertEquals(3, october.individuals)
        assertEquals(2, october.richness)
        assertEquals(2, october.expeditions)
        assertEquals(1, analysis.months[10].records)
        assertEquals(1, analysis.recordsWithoutDate)
    }

    @Test
    fun horarioPelaHoraDoRegistro() {
        val analysis = TaxonAnalysis(listOf(session(1, "2026-03-14")), listOf(
            record(1, "A a", time = "19:40"),
            record(1, "B b", time = "19:05:12"),
            record(1, "C c", time = "7:15"),
            record(1, "D d", time = "25:00"),
            record(1, "E e")
        ))
        assertEquals(2, analysis.hours[19].records)
        assertEquals(1, analysis.hours[7].records)
        assertEquals(2, analysis.recordsWithoutTime)
    }

    @Test
    fun altitudeSexoEFaseDeVidaPorTaxon() {
        val analysis = TaxonAnalysis(listOf(session(1, "2026-03-14", altitude = 1200.0)), listOf(
            record(1, "A a", altitude = 1450.0, sex = "male", lifeStage = "adult"),
            record(1, "A a", sex = "male", lifeStage = "juvenile"),
            record(1, "A a", latitude = -22.4, sex = "Female", lifeStage = "adult"),
            record(1, "B b")
        ))
        val a = analysis.taxa.first { it.scientificName == "A a" }
        // Sem coordenada propria vale a altitude da expedicao; com coordenada e sem altitude, nada.
        assertEquals(1200.0, a.minimumAltitude!!, 0.0)
        assertEquals(1450.0, a.maximumAltitude!!, 0.0)
        assertEquals(listOf("male" to 2, "female" to 1), a.sexes)
        assertEquals(listOf("adult" to 2, "juvenile" to 1), a.lifeStages)
        assertTrue(analysis.taxa.first { it.scientificName == "B b" }.sexes.isEmpty())
    }

    @Test
    fun projetoSemExpedicaoNemRegistroNaoQuebra() {
        val analysis = TaxonAnalysis(emptyList(), emptyList())
        assertTrue(analysis.expeditions.isEmpty())
        assertTrue(analysis.taxa.isEmpty())
        assertEquals(0, analysis.totalIndividuals)
    }
}
