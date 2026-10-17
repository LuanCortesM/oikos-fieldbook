package com.oikos.fieldbook.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coordenada errada nao gera erro: so aparece como ponto no lugar errado. Estes
 * testes travam leitura, formatacao e arredondamento.
 */
class CoordinateUtilsTest {

    private fun close(expected: Double, actual: Double?, tolerance: Double = 1e-6) {
        assertNotNull("valor nulo, esperado $expected", actual)
        assertEquals(expected, actual!!, tolerance)
    }

    @Test
    fun `le graus minutos e segundos com simbolos`() {
        close(-(22 + 25 / 60.0 + 51.6 / 3600.0), CoordinateUtils.parseSexagesimal("22°25'51.6\"S", Axis.Latitude))
    }

    @Test
    fun `le graus minutos e segundos separados por espaco`() {
        close(-(22 + 25 / 60.0 + 51.6 / 3600.0), CoordinateUtils.parseSexagesimal("22 25 51.6 S", Axis.Latitude))
    }

    @Test
    fun `aceita O e L de oeste e leste`() {
        close(-(44 + 58.74 / 60.0), CoordinateUtils.parseSexagesimal("44 58.74 O", Axis.Longitude))
        close(44 + 58.74 / 60.0, CoordinateUtils.parseSexagesimal("44 58.74 L", Axis.Longitude))
    }

    @Test
    fun `hemisferio antes do numero e virgula decimal`() {
        close(-(22 + 25.86 / 60.0), CoordinateUtils.parseSexagesimal("S 22 25,86", Axis.Latitude))
    }

    @Test
    fun `sinal negativo sem letra`() {
        close(-(22 + 25.86 / 60.0), CoordinateUtils.parseSexagesimal("-22 25.86", Axis.Latitude))
    }

    @Test
    fun `rejeita minuto ou segundo acima de 59`() {
        assertNull(CoordinateUtils.parseSexagesimal("22 60 00 S", Axis.Latitude))
        assertNull(CoordinateUtils.parseSexagesimal("22 25 60 S", Axis.Latitude))
    }

    @Test
    fun `rejeita letra do eixo errado e latitude acima de 90`() {
        assertNull(CoordinateUtils.parseSexagesimal("22 25 51 W", Axis.Latitude))
        assertNull(CoordinateUtils.parseSexagesimal("44 58 44 S", Axis.Longitude))
        assertNull(CoordinateUtils.parseSexagesimal("91 00 00 N", Axis.Latitude))
    }

    @Test
    fun `rejeita grau fracionado seguido de minutos`() {
        assertNull(CoordinateUtils.parseSexagesimal("22.5 30 S", Axis.Latitude))
    }

    @Test
    fun `GMS nao imprime 60 segundos por arredondamento`() {
        // 22°59'59.999" arredonda para 23°00'00.00", nunca 22°59'60.00".
        val texto = CoordinateUtils.toDms(-(22 + 59 / 60.0 + 59.999 / 3600.0), Axis.Latitude)
        assertEquals("23°00'00.00\"S", texto)
    }

    @Test
    fun `GMS volta ao mesmo valor na leitura`() {
        val original = -22.9701417
        val texto = CoordinateUtils.toDms(original, Axis.Latitude)
        close(original, CoordinateUtils.parseSexagesimal(texto, Axis.Latitude), 1e-5)
    }

    @Test
    fun `GMD volta ao mesmo valor na leitura`() {
        val original = -43.2263983
        val texto = CoordinateUtils.toDmm(original, Axis.Longitude)
        assertTrue(texto.endsWith("W"))
        close(original, CoordinateUtils.parseSexagesimal(texto, Axis.Longitude), 1e-6)
    }

    @Test
    fun `verbatim informa o sistema conforme o formato`() {
        val dms = CoordinateUtils.verbatim(-22.970, -43.226, "DMS")
        assertEquals("degrees minutes seconds", dms?.second)
        val dmm = CoordinateUtils.verbatim(-22.970, -43.226, "DMM")
        assertEquals("degrees decimal minutes", dmm?.second)
        assertNull(CoordinateUtils.verbatim(-22.970, -43.226, "DD"))
        assertNull(CoordinateUtils.verbatim(null, -43.226, "DMS"))
    }

    @Test
    fun `incerteza do GPS arredonda para cima em metro inteiro`() {
        // O receptor entrega float: 12.302000045776367 virava a incerteza exportada.
        assertEquals(13.0, CoordinateUtils.roundUncertainty(12.302000045776367)!!, 0.0)
        assertEquals(5.0, CoordinateUtils.roundUncertainty(5.0)!!, 0.0)
        assertEquals(1.0, CoordinateUtils.roundUncertainty(0.3)!!, 0.0)
        assertNull(CoordinateUtils.roundUncertainty(null))
    }

    @Test
    fun `coordenada arredonda em sete casas`() {
        assertEquals(-43.2263983, CoordinateUtils.roundCoordinate(-43.22639834567), 0.0)
    }

    @Test
    fun `UTM ida e volta no sul do Brasil`() {
        val utm = CoordinateUtils.toUtm(-22.970, -43.226)
        assertNotNull(utm)
        assertEquals(23, utm!!.zone)
        assertEquals(UtmHemisphere.South, utm.hemisphere)
        val volta = CoordinateUtils.fromUtm(utm.zone, utm.hemisphere, utm.easting, utm.northing)
        close(-22.970, volta?.latitude, 1e-6)
        close(-43.226, volta?.longitude, 1e-6)
    }
}
