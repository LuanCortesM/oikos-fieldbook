package com.oikos.fieldbook.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AppCitationTest {
    @Test
    fun abntTemVersaoEDataDeAcesso() {
        assertEquals(
            "MACIEL, L. S. C. Oikos FieldBook: caderno de campo digital. Versão 0.2.0. [S. l.]: Google Play, 2026. " +
                "Aplicativo para Android. Disponível em: https://play.google.com/store/apps/details?id=com.oikos.fieldbook. Acesso em: 18 out. 2026.",
            AppCitation.abnt("0.2.0", LocalDate.of(2026, 10, 18))
        )
    }

    @Test
    fun apaTemAnoVersaoETipo() {
        assertEquals(
            "Maciel, L. S. C. (2026). Oikos FieldBook (Versão 0.2.0) [Aplicativo móvel]. Google Play. https://play.google.com/store/apps/details?id=com.oikos.fieldbook",
            AppCitation.apa("0.2.0")
        )
    }

    @Test
    fun leiaMeTrazAsDuasFormas() {
        val text = AppCitation.readme("0.2.0", LocalDate.of(2026, 5, 2))
        assertTrue(text.contains("ABNT") && text.contains("APA") && text.contains("Acesso em: 2 maio 2026."))
    }
}
