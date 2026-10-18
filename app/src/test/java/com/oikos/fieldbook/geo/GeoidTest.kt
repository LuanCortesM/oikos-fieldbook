package com.oikos.fieldbook.geo

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Valores de referencia: a grade EGM2008 de 5' (GeographicLib), interpolada
 * nos mesmos pontos por tools/geoide/GeoideEgm2008.java. A grade do app (15')
 * precisa concordar com ela a menos de 0,3 m nesses lugares.
 */
class GeoidTest {
    private val geoid = File("src/main/assets/geoide_egm2008.bin").inputStream().use { Geoid(it) }

    @Test
    fun ondulacaoConfereComAGradeCompleta() {
        assertEquals(-5.81, geoid.undulation(-22.9068, -43.1729), 0.3)   // Rio de Janeiro
        assertEquals(-11.76, geoid.undulation(-3.119, -60.0217), 0.3)    // Manaus
        assertEquals(-12.71, geoid.undulation(-15.7939, -47.8828), 0.3)  // Brasilia
        assertEquals(-1.97, geoid.undulation(-22.7394, -45.5914), 0.3)   // Campos do Jordao (Mantiqueira)
        assertEquals(17.23, geoid.undulation(0.0, 0.0), 0.3)
        assertEquals(-28.37, geoid.undulation(27.9881, 86.925), 0.5)     // Everest
    }

    @Test
    fun altitudeDoGpsViraAltitudeSobreONivelDoMar() {
        // No Rio o geoide fica ~5,8 m abaixo do elipsoide: a altitude real e maior.
        assertEquals(105.81, geoid.aboveSeaLevel(100.0, -22.9068, -43.1729), 0.3)
    }

    @Test
    fun longitudeNegativaEPolosFuncionam() {
        assertEquals(geoid.undulation(-10.0, -50.0), geoid.undulation(-10.0, 310.0), 1e-9)
        assertEquals(-30.05, geoid.undulation(-89.9, 10.0), 0.5)
        geoid.undulation(90.0, 0.0)
        geoid.undulation(-90.0, 179.99)
    }
}
