package com.oikos.fieldbook.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Texto novo na tela entra em englishText; este teste lembra de pôr o
 * espanhol junto, para nenhuma tela aparecer metade em portugues.
 */
class SpanishTextsTest {

    @Test
    fun `todo texto da tela tem espanhol`() {
        val faltando = englishText.keys - spanishText.keys
        assertTrue("sem espanhol: $faltando", faltando.isEmpty())
        val sobrando = spanishText.keys - englishText.keys
        assertTrue("chave que nao existe em ingles: $sobrando", sobrando.isEmpty())
    }

    @Test
    fun `nenhum texto vazio nem com letra que so existe em portugues`() {
        spanishText.forEach { (pt, es) ->
            assertTrue(pt, es.isNotBlank())
            assertTrue("$pt -> $es", es.none { it in "ãõçêÃÕÇÊ" })
        }
    }

    @Test
    fun `aspas e reticencias iguais ao original`() {
        spanishText.forEach { (pt, es) ->
            assertEquals("$pt -> $es", pt.count { it == '"' }, es.count { it == '"' })
            assertEquals("$pt -> $es", pt.endsWith("..."), es.endsWith("..."))
            assertEquals("$pt -> $es", pt.endsWith("?"), es.endsWith("?"))
        }
    }

    @Test
    fun `pergunta em espanhol abre com sinal invertido`() {
        spanishText.values.filter { it.endsWith("?") }.forEach { es ->
            assertTrue(es, es.contains('¿'))
        }
    }
}
