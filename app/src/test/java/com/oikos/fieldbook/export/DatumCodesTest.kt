package com.oikos.fieldbook.export

import org.junit.Assert.assertEquals
import org.junit.Test

class DatumCodesTest {

    @Test
    fun datumsDoAppSaemComOCodigoEpsg() {
        assertEquals("EPSG:4674", DatumCodes.forExport("SIRGAS 2000"))
        assertEquals("EPSG:4674", DatumCodes.forExport("sirgas2000"))
        assertEquals("EPSG:4326", DatumCodes.forExport("WGS84"))
        assertEquals("EPSG:4326", DatumCodes.forExport("WGS 84"))
    }

    @Test
    fun outroValorSaiComoEsta() {
        assertEquals("EPSG:4674", DatumCodes.forExport("EPSG:4674"))
        assertEquals("SAD69", DatumCodes.forExport(" SAD69 "))
        assertEquals("", DatumCodes.forExport(""))
    }

    @Test
    fun textoParaPessoasMostraNomeECodigo() {
        assertEquals("SIRGAS 2000 (EPSG:4674)", DatumCodes.withCode("SIRGAS 2000"))
        assertEquals("SAD69", DatumCodes.withCode("SAD69"))
        assertEquals("", DatumCodes.withCode(""))
    }
}
