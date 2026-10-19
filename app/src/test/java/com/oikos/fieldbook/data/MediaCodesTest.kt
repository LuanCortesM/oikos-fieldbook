package com.oikos.fieldbook.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaCodesTest {

    @Test
    fun iniciaisDoPrimeiroColetor() {
        assertEquals("LSCM", MediaCodes.initials("Luan da Silva Cortes Maciel"))
        assertEquals("LSCM", MediaCodes.initials("Maciel, L. S. C."))
        assertEquals("MS", MediaCodes.initials("Maria Souza | João Silva"))
        assertEquals("MS", MediaCodes.initials("  ; Maria Souza; João Silva"))
        assertEquals("AE", MediaCodes.initials("Ângela Érica"))
        assertEquals("JA", MediaCodes.initials("José d'Ávila"))
        assertEquals("A", MediaCodes.initials("Ana"))
        assertEquals("", MediaCodes.initials(""))
        assertEquals("", MediaCodes.initials(" | "))
    }

    @Test
    fun nomeMuitoLongoFicaComSeisIniciais() {
        assertEquals("ABCDEF", MediaCodes.initials("Ana Bia Carla Dora Eva Fia Gil"))
    }

    @Test
    fun semColetorUsaMed() {
        assertEquals("MED", MediaCodes.prefix(""))
        assertEquals("LSCM", MediaCodes.prefix("Luan da Silva Cortes Maciel"))
    }

    @Test
    fun formatoDosNumeros() {
        assertEquals("LSCM-0001", MediaCodes.format("LSCM", 1))
        assertEquals("LSCM-12345", MediaCodes.format("LSCM", 12345))
        assertEquals("MED-000123", MediaCodes.legacy(123))
        assertEquals("MED000123", MediaCodes.fileToken("MED-000123"))
        assertEquals("LSCM0001b", MediaCodes.fileToken("LSCM-0001b"))
    }
}
