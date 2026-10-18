package com.oikos.fieldbook.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AppCitationTest {
    private val day = LocalDate.of(2026, 10, 18)

    @Test
    fun abntTemDoiEDataDeAcesso() {
        assertEquals(
            "MACIEL, L. S. C. Oikos FieldBook. Versão 0.2.0. [S. l.]: Zenodo, 2026. Aplicativo para Android. DOI: https://doi.org/10.5281/zenodo.20526746. " +
                "Disponível em: https://play.google.com/store/apps/details?id=com.oikos.fieldbook. Acesso em: 18 out. 2026.",
            AppCitation.abnt("0.2.0", day)
        )
    }

    @Test
    fun vancouverTemDataDeConsulta() {
        assertEquals(
            "Maciel LSC. Oikos FieldBook [software]. Version 0.2.0. [place unknown]: Zenodo; 2026 [cited 2026 Oct 18]. " +
                "Available from: https://doi.org/10.5281/zenodo.20526746",
            AppCitation.vancouver("0.2.0", day)
        )
    }

    @Test
    fun apaTemAnoVersaoETipo() {
        assertEquals(
            "Maciel, L. S. C. (2026). Oikos FieldBook (Version 0.2.0) [Computer software]. Zenodo. https://doi.org/10.5281/zenodo.20526746",
            AppCitation.apa("0.2.0")
        )
    }

    @Test
    fun leiaMeTrazAsTresFormas() {
        val text = AppCitation.readme("0.2.0", LocalDate.of(2026, 5, 2))
        listOf("ABNT", "Vancouver", "APA", "Acesso em: 2 maio 2026.", "[cited 2026 May 2]", "10.5281/zenodo.20526746").forEach {
            assertTrue(it, text.contains(it))
        }
    }
}
