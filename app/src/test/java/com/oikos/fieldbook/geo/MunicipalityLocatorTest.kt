package com.oikos.fieldbook.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Usa o arquivo real do app (malha municipal 2022 do IBGE). Os pontos sao
 * centros de cidade, longe de divisas, para o teste nao depender da precisao
 * da malha.
 */
class MunicipalityLocatorTest {

    companion object {
        private lateinit var locator: MunicipalityLocator

        @BeforeClass
        @JvmStatic
        fun load() {
            locator = MunicipalityLocator(File("src/main/assets/municipios.bin").readBytes())
        }
    }

    private fun report(lat: Double, lon: Double, uf: String = "", municipality: String = "") =
        CoordinateQuality.check(lat, lon, locator.check(lat, lon), locator::locate, uf, municipality)

    @Test
    fun malhaTemTodosOsMunicipios() {
        assertEquals(5570, locator.size)
        assertEquals("Paraty", locator.byCode(3303807)?.name)
    }

    @Test
    fun centrosDeCidadeCaemNoMunicipioCerto() {
        assertEquals(Municipality(3304557, "Rio de Janeiro", "RJ"), locator.locate(-22.9068, -43.1729))
        assertEquals("Paraty", locator.locate(-23.2178, -44.7131)?.name)
        assertEquals("Brasília", locator.locate(-15.7939, -47.8828)?.name)
        assertEquals("Manaus", locator.locate(-3.1190, -60.0217)?.name)
        assertEquals("Porto Alegre", locator.locate(-30.0346, -51.2177)?.name)
        assertEquals("AM", locator.locate(-3.1190, -60.0217)?.uf)
    }

    @Test
    fun marEOutroPaisFicamForaDaMalha() {
        assertNull(locator.locate(-23.5, -40.0))
        assertNull(locator.locate(48.8566, 2.3522))
        val far = report(-23.512, -40.031)
        assertEquals(CoordinateQuality.Level.INFO, far.worst)
    }

    @Test
    fun pertoDaCostaApontaOMunicipioMaisProximo() {
        // Baia de Guanabara, fora da malha mas a poucos km do Rio.
        val check = locator.check(-22.86, -43.15)
        if (check.municipality == null) {
            assertNotNull(check.nearest)
            assertTrue(check.nearestDistanceMeters!! < 20_000)
        }
    }

    @Test
    fun latitudeComSinalTrocadoEApontada() {
        val result = report(22.9068, -43.1729)
        assertEquals(CoordinateQuality.Level.ERROR, result.worst)
        assertTrue(result.issues.first().message, result.issues.first().message.contains("latitude de sinal trocado") && result.issues.first().message.contains("Rio de Janeiro – RJ"))
    }

    @Test
    fun latitudeELongitudeInvertidasSaoApontadas() {
        val result = report(-43.1729, -22.9068)
        assertTrue(result.issues.joinToString { it.message }, result.issues.any { it.message.contains("invertidas") && it.message.contains("Rio de Janeiro") })
    }

    @Test
    fun zeroZeroEErro() {
        val result = report(0.0, 0.0)
        assertEquals(CoordinateQuality.Level.ERROR, result.worst)
    }

    @Test
    fun coordenadaArredondadaEAvisada() {
        assertTrue(report(-22.0, -45.0).issues.any { it.message.contains("arredondada") })
    }

    @Test
    fun estadoDaExpedicaoDiferenteDoPonto() {
        val result = report(-23.2178, -44.7131, uf = "São Paulo")
        assertTrue(result.issues.any { it.message.contains("São Paulo") && it.message.contains("Paraty – RJ") })
        assertTrue(report(-23.2178, -44.7131, uf = "RJ", municipality = "paraty").issues.isEmpty())
    }

    @Test
    fun municipioVizinhoApareceDentroDoRaio() {
        val check = locator.check(-22.9068, -43.1729, nearMeters = 30_000.0)
        assertEquals("Rio de Janeiro", check.municipality?.name)
        assertNotNull(check.nearest)
        assertEquals("RJ", check.nearest?.uf)
    }

    @Test
    fun ufAceitaNomeOuSigla() {
        assertEquals("SP", CoordinateQuality.ufCode("São Paulo"))
        assertEquals("RJ", CoordinateQuality.ufCode("rj"))
        assertEquals("DF", CoordinateQuality.ufCode("Distrito Federal"))
        assertNull(CoordinateQuality.ufCode("Buenos Aires"))
    }
}
