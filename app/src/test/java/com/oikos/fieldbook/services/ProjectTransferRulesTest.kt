package com.oikos.fieldbook.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regras puras da troca entre celulares (nome do projeto novo, comparacao de valores). */
class ProjectTransferRulesTest {

    @Test
    fun quemEnviouEOColetorMaisFrequenteDasExpedicoes() {
        assertEquals(
            "Ana Souza",
            ProjectTransfer.senderName(listOf("Ana Souza | Bruno Lima", "Bruno Lima", "Ana Souza"), listOf("Carla Dias"))
        )
        // Empate: vence o primeiro.
        assertEquals("Bruno Lima", ProjectTransfer.senderName(listOf("Bruno Lima", "Ana Souza"), emptyList()))
        // Expedicoes sem coletor: vale o das ocorrencias.
        assertEquals("Carla Dias", ProjectTransfer.senderName(listOf("", " "), listOf("Carla Dias")))
        assertEquals("", ProjectTransfer.senderName(emptyList(), emptyList()))
    }

    @Test
    fun nomeDoProjetoNovo() {
        assertEquals("Anuros (Ana Souza)", ProjectTransfer.newProjectName("Anuros", "Ana Souza"))
        assertEquals("Anuros", ProjectTransfer.newProjectName("Anuros", ""))
        // Pacote que volta para quem recebeu: o nome nao se repete.
        assertEquals("Anuros (Ana Souza)", ProjectTransfer.newProjectName("Anuros (Ana Souza)", "Ana Souza"))
    }

    @Test
    fun comparacaoDeValores() {
        assertTrue(ProjectTransfer.same(null, ""))
        assertTrue(ProjectTransfer.same(org.json.JSONObject.NULL, " "))
        assertTrue(ProjectTransfer.same(3, 3L))
        assertTrue(ProjectTransfer.same(-22.9701983, -22.9701983))
        assertTrue(ProjectTransfer.same(2, 2.0))
        assertFalse(ProjectTransfer.same(-22.9701983, -22.9701984))
        assertFalse(ProjectTransfer.same("male", "female"))
        assertFalse(ProjectTransfer.same(null, 0))
    }

    @Test
    fun valoresParaMostrar() {
        assertEquals("12", ProjectTransfer.text(12.0))
        assertEquals("0.00001", ProjectTransfer.text(1.0E-5))
        assertEquals("-22.9701983", ProjectTransfer.text(-22.9701983))
        assertEquals("", ProjectTransfer.text(null))
        assertEquals("female", ProjectTransfer.text("female"))
    }
}
