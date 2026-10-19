package com.oikos.fieldbook.services

import com.oikos.fieldbook.util.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A incerteza vai para dwc:coordinateUncertaintyInMeters e decide, la na
 * frente, se o registro serve para modelagem. Estes testes travam a regra do
 * guia do GBIF (no minimo 2 x a precisao do GPS; 30 m se desconhecida).
 */
class GpsAveragingTest {

    private fun reading(lat: Double, lon: Double, accuracy: Double?, t: Long = 0L, alt: Double? = null) =
        GpsReading(lat, lon, alt, accuracy, t)

    @Test
    fun `leitura unica dobra a precisao como pede o GBIF`() {
        assertEquals(10.0, GpsAveraging.uncertaintyFor(5.0), 0.0)
        assertEquals(25.0, GpsAveraging.uncertaintyFor(12.3), 0.0)
    }

    @Test
    fun `precisao desconhecida usa 30 m`() {
        assertEquals(30.0, GpsAveraging.uncertaintyFor(null), 0.0)
        val fix = GpsAveraging.average(listOf(reading(-22.0, -45.0, null)))!!
        assertEquals(30.0, fix.uncertainty, 0.0)
    }

    @Test
    fun `media descarta leitura muito pior que a melhor`() {
        val readings = listOf(
            reading(-22.970000, -43.226000, 4.0, 0),
            reading(-22.970010, -43.226010, 5.0, 1000),
            reading(-22.970020, -43.226020, 6.0, 2000),
            // salto de 1 km com precisao ruim: tem de ficar de fora
            reading(-22.979000, -43.237000, 80.0, 3000)
        )
        val fix = GpsAveraging.average(readings)!!
        assertEquals(4, fix.received)
        assertEquals(3, fix.used)
        assertEquals(-22.97001, fix.latitude, 1e-6)
        assertEquals(-43.22601, fix.longitude, 1e-6)
        // mediana 5 m -> 2 x 5 = 10 m
        assertEquals(10.0, fix.uncertainty, 0.0)
        assertEquals(3000L, fix.durationMillis)
    }

    @Test
    fun `incerteza cobre a dispersao quando ela passa do dobro da precisao`() {
        // Duas leituras a ~111 m uma da outra, ambas dizendo 3 m de precisao.
        val fix = GpsAveraging.average(
            listOf(reading(-22.0, -45.0, 3.0), reading(-22.001, -45.0, 3.0))
        )!!
        assertTrue("incerteza ${fix.uncertainty} deveria cobrir ~56 m", fix.uncertainty >= 55.0)
    }

    @Test
    fun `semaforo segue os limiares`() {
        assertEquals(FixQuality.GOOD, GpsAveraging.quality(8.0))
        assertEquals(FixQuality.FAIR, GpsAveraging.quality(25.0))
        assertEquals(FixQuality.POOR, GpsAveraging.quality(45.0))
        assertEquals(FixQuality.POOR, GpsAveraging.quality(null))
    }

    @Test
    fun `sem leituras nao ha media`() {
        assertNull(GpsAveraging.average(emptyList()))
    }

    @Test
    fun `observacao descreve como a coordenada foi obtida`() {
        val fix = GpsAveraging.average(listOf(reading(-22.0, -45.0, 4.0, 0), reading(-22.0, -45.0, 6.0, 5000)))!!
        val texto = fix.remarks()
        assertTrue(texto.startsWith("Média de 2 leituras"))
        assertTrue(texto.contains("5.0 m"))
    }

    @Test
    fun `observacao em ingles para projeto em ingles`() {
        val fix = GpsAveraging.average(listOf(reading(-22.0, -45.0, 4.0, 0), reading(-22.0, -45.0, 6.0, 5000)))!!
        val text = fix.remarks(AppLanguage.EN)
        assertTrue(text.startsWith("Mean of 2 phone GNSS readings (2 received in 5 s)"))
        assertTrue(text.contains("accuracy (median) 5.0 m"))
        assertTrue(text.endsWith("uncertainty = max(2 × accuracy, spread)."))
    }

    @Test
    fun `observacao em espanhol para projeto em espanhol`() {
        val fix = GpsAveraging.average(listOf(reading(-22.0, -45.0, 4.0, 0), reading(-22.0, -45.0, 6.0, 5000)))!!
        val text = fix.remarks(AppLanguage.ES)
        assertTrue(text.startsWith("Promedio de 2 lecturas GNSS del celular (de 2 recibidas en 5 s)"))
        assertTrue(text.contains("(mediana) 5.0 m"))
        assertTrue(text.endsWith("incertidumbre = máx(2 × precisión, dispersión)."))
    }

    @Test
    fun `protocolo cita o guia do GBIF com DOI`() {
        assertTrue(GpsAveraging.GBIF_PROTOCOL.contains("10.35035/e09p-h128"))
        assertTrue(GpsAveraging.GBIF_PROTOCOL.startsWith("Zermoglio PF"))
    }
}
