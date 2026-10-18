package com.oikos.fieldbook.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Repetir campo errado e pior que nao repetir: um identificador copiado quebra a
 * unicidade, e uma familia herdada de outro nome vira erro silencioso no GBIF.
 */
class QuickEntryTest {

    private val previous = mapOf(
        "occurrenceID" to "TB-20261017-0003-AB12CD34",
        "recordNumber" to "P03",
        "date" to "2026-10-17",
        "time" to "07:42",
        "formType" to "Fauna",
        "occurrenceType" to "Animal observado",
        "scientificName" to "Boana faber (Wied, 1821)",
        "dyn:family" to "Hylidae",
        "dyn:nameAccordingTo" to "Catálogo Taxonômico da Fauna do Brasil (v1.57)",
        "recordedBy" to "L. Maciel",
        "lat" to "-22.97",
        "lon" to "-43.23",
        "accuracy" to "8",
        "dyn:georeferenceProtocol" to "GBIF 2020",
        "dyn:habitat" to "Brejo na borda da mata",
        "dyn:eventDate" to "2026-10-17",
        "count" to ""
    )

    private val labels = mapOf("habitat" to "Habitat")

    @Test
    fun opcoesSoComOQueORegistroAnteriorTem() {
        val options = QuickEntry.options(previous) { labels[it] ?: it }
        val keys = options.map { it.key }
        assertTrue("recordedBy" in keys)
        assertTrue("location" in keys)
        assertTrue("dyn:habitat" in keys)
        assertFalse("contagem vazia nao e opcao", "count" in keys)
        assertEquals("-22.97, -43.23", options.first { it.key == "location" }.preview)
        assertEquals("Habitat", options.first { it.key == "dyn:habitat" }.label)
    }

    @Test
    fun identificadorDataEHoraNuncaSaoOferecidosNemCopiados() {
        val options = QuickEntry.options(previous) { it }.map { it.key }
        listOf("occurrenceID", "recordNumber", "date", "time", "dyn:eventDate").forEach { assertFalse(it, it in options) }
        val carried = QuickEntry.carried(previous, setOf("occurrenceID", "date", "dyn:eventDate", "recordedBy"))
        assertEquals(mapOf("recordedBy" to "L. Maciel"), carried)
    }

    @Test
    fun nomeVemComOsDadosDoTaxonEFamiliaVaziaLimpa() {
        val carried = QuickEntry.carried(previous, setOf("scientificName"))
        assertEquals("Boana faber (Wied, 1821)", carried["scientificName"])
        assertEquals("Hylidae", carried["dyn:family"])
        // Campos do grupo que estavam vazios entram vazios: nada herdado de antes.
        assertEquals("", carried["dyn:genus"])
    }

    @Test
    fun coordenadaVemComProtocoloEIncerteza() {
        val carried = QuickEntry.carried(previous, setOf("location"))
        assertEquals("-22.97", carried["lat"])
        assertEquals("-43.23", carried["lon"])
        assertEquals("8", carried["accuracy"])
        assertEquals("GBIF 2020", carried["dyn:georeferenceProtocol"])
        assertFalse("recordedBy" in carried)
    }

    @Test
    fun campoExtraDoProjetoERepetidoPelaChave() {
        assertEquals(mapOf("dyn:habitat" to "Brejo na borda da mata"), QuickEntry.carried(previous, setOf("dyn:habitat")))
    }

    @Test
    fun escolhaDeCampoQueSumiuNaoQuebra() {
        assertEquals(mapOf("dyn:substrate" to ""), QuickEntry.carried(previous, setOf("dyn:substrate")))
        assertEquals(emptyMap<String, String>(), QuickEntry.carried(previous, setOf("campoInexistente")))
    }
}
