package com.oikos.fieldbook.export

import com.oikos.fieldbook.data.OccurrenceEntity
import com.oikos.fieldbook.data.ProjectEntity
import com.oikos.fieldbook.data.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecimenLabelsTest {

    private val project = ProjectEntity(id = 1, name = "Bromélias do Arboreto", institution = "JBRJ")
    private val session = SessionEntity(
        id = 1, projectId = 1, eventId = "event-1", name = "Arboreto", eventDate = "2026-03-14",
        locality = "Jardim Botânico do Rio de Janeiro, arboreto", municipality = "Rio de Janeiro", stateProvince = "Rio de Janeiro",
        country = "Brasil", habitat = "Sub-bosque úmido", recordedBy = "L. S. C. Maciel",
        latitude = -22.97, longitude = -43.2264, altitude = 40.0
    )
    private val occurrence = OccurrenceEntity(
        id = 9, projectId = 1, sessionId = 1, occurrenceId = "BI-20260314-0009-AB12CD34",
        scientificName = "Vriesea neoglutinosa Mez", basisOfRecord = "PreservedSpecimen", recordNumber = "123",
        identifiedBy = "L. Freitas", dateIdentified = "2026-04-02", occurrenceRemarks = "Rupícola; inflorescência vermelha.",
        latitude = -22.97, longitude = -43.2264, coordinateUncertaintyInMeters = 12.0, altitude = 40.0, geodeticDatum = "SIRGAS 2000",
        dynamicFieldsJson = """{"family":"Bromeliaceae","scientificNameAuthorship":"Mez","eventDate":"2026-03-14"}"""
    )

    @Test
    fun etiquetaTemOConteudoDeUmaExsicata() {
        val label = SpecimenLabels.content(project, session, occurrence)
        assertEquals("JBRJ · Bromélias do Arboreto", label.header)
        assertEquals("BROMELIACEAE", label.family)
        assertEquals("Vriesea neoglutinosa", label.nameItalic)
        assertEquals("Mez", label.authorship)
        assertEquals("BI-20260314-0009-AB12CD34", label.code)
        val text = label.lines.joinToString("\n")
        assertTrue(text, text.contains("BRASIL: Rio de Janeiro: Rio de Janeiro. Jardim Botânico do Rio de Janeiro"))
        assertTrue(text, text.contains("22°58'12.00\"S 43°13'35.04\"W ±12 m (SIRGAS 2000)"))
        assertTrue(text, text.contains("Alt. 40 m"))
        assertTrue(text, text.contains("Hábitat: Sub-bosque úmido"))
        assertTrue(text, text.contains("Col. L. S. C. Maciel nº 123 · 14/03/2026"))
        assertTrue(text, text.contains("Det. L. Freitas, 02/04/2026"))
    }

    @Test
    fun soEspecimesViramEtiqueta() {
        assertTrue("PreservedSpecimen" in SpecimenLabels.specimenBases)
        assertTrue("HumanObservation" !in SpecimenLabels.specimenBases)
    }

    @Test
    fun dataForaDoPadraoFicaComoEsta() {
        assertEquals("14/03/2026", SpecimenLabels.formatDate("2026-03-14"))
        assertEquals("março de 2026", SpecimenLabels.formatDate("março de 2026"))
    }

    @Test
    fun coordenadaPropriaNaoHerdaAltitudeNemPrecisaoDaExpedicao() {
        // Ponto digitado de um GPS de mao, sem altitude nem precisao: antes a
        // etiqueta saia com a altitude da expedição.
        val gps = occurrence.copy(latitude = -22.5, longitude = -45.0, coordinateUncertaintyInMeters = null, altitude = null)
        val text = SpecimenLabels.content(project, session.copy(coordinateUncertaintyInMeters = 5.0), gps).lines.joinToString("\n")
        assertTrue(text, !text.contains("Alt."))
        assertTrue(text, !text.contains("±"))
    }

    @Test
    fun semCoordenadaPropriaUsaOPontoDaExpedicaoInteiro() {
        val semPonto = occurrence.copy(latitude = null, longitude = null, coordinateUncertaintyInMeters = null, altitude = null, geodeticDatum = "SIRGAS 2000")
        val expedicao = session.copy(coordinateUncertaintyInMeters = 8.0, geodeticDatum = "WGS84")
        val text = SpecimenLabels.content(project, expedicao, semPonto).lines.joinToString("\n")
        assertTrue(text, text.contains("±8 m (WGS84)"))
        assertTrue(text, text.contains("Alt. 40 m"))
    }

    @Test
    fun qualificadorDaIdentificacaoEntraNaEtiqueta() {
        val cf = occurrence.copy(dynamicFieldsJson = """{"family":"Bromeliaceae","scientificNameAuthorship":"Mez","identificationQualifier":"cf."}""")
        assertEquals("cf.", SpecimenLabels.content(project, session, cf).qualifier)
    }

    @Test
    fun paisEmBrancoNaoViraBrasil() {
        val text = SpecimenLabels.content(project, session.copy(country = ""), occurrence).lines.joinToString("\n")
        assertTrue(text, text.startsWith("Rio de Janeiro: Rio de Janeiro."))
    }

    @Test
    fun descricaoEALinhaQuePodeSerEncurtada() {
        val label = SpecimenLabels.content(project, session, occurrence)
        assertEquals("Rupícola; inflorescência vermelha.", label.lines[label.shrinkableLine])
    }
}
