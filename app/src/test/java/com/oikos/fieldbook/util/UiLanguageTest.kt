package com.oikos.fieldbook.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** Sem contexto (testes), as mensagens ficam em portugues. */
class UiLanguageTest {

    @Test
    fun `plural certo em portugues`() {
        assertEquals("1 foto", UiLanguage.count(1, "foto", "fotos", "photo", "photos"))
        assertEquals("0 fotos", UiLanguage.count(0, "foto", "fotos", "photo", "photos"))
        assertEquals("3 fotos", UiLanguage.count(3L, "foto", "fotos", "photo", "photos"))
    }

    @Test
    fun `texto de arquivo segue o idioma do projeto`() {
        assertEquals("Localidade não informada.", inProjectLanguage("pt", "Localidade não informada.", "Locality not reported."))
        assertEquals("Locality not reported.", inProjectLanguage("en", "Localidade não informada.", "Locality not reported."))
        assertEquals("Locality not reported.", inProjectLanguage("en-US", "Localidade não informada.", "Locality not reported."))
    }
}
