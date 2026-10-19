package com.oikos.fieldbook.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** Sem contexto (testes), as mensagens ficam em portugues. */
class UiLanguageTest {

    @Test
    fun `plural certo em portugues`() {
        assertEquals("1 foto", UiLanguage.count(1, "foto", "fotos", "photo", "photos", "foto", "fotos"))
        assertEquals("0 fotos", UiLanguage.count(0, "foto", "fotos", "photo", "photos", "foto", "fotos"))
        assertEquals("3 fotos", UiLanguage.count(3L, "foto", "fotos", "photo", "photos", "foto", "fotos"))
    }

    @Test
    fun `texto de arquivo segue o idioma do projeto`() {
        val pt = "Localidade não informada."
        val en = "Locality not reported."
        val es = "Localidad no informada."
        assertEquals(pt, inProjectLanguage("pt", pt, en, es))
        assertEquals(en, inProjectLanguage("en", pt, en, es))
        assertEquals(en, inProjectLanguage("en-US", pt, en, es))
        assertEquals(es, inProjectLanguage("es", pt, en, es))
        assertEquals(es, inProjectLanguage("es-419", pt, en, es))
        // Projeto antigo sem idioma: portugues, como antes.
        assertEquals(pt, inProjectLanguage("", pt, en, es))
    }

    @Test
    fun `automatico segue o celular e a escolha do app vale mais`() {
        assertEquals(AppLanguage.PT, AppLanguage.resolve("auto", "pt"))
        assertEquals(AppLanguage.ES, AppLanguage.resolve("auto", "es"))
        assertEquals(AppLanguage.EN, AppLanguage.resolve("auto", "en"))
        // Celular em frances ou alemao: ingles, como antes.
        assertEquals(AppLanguage.EN, AppLanguage.resolve("auto", "fr"))
        assertEquals(AppLanguage.EN, AppLanguage.resolve(null, "de"))
        assertEquals(AppLanguage.ES, AppLanguage.resolve("es", "pt"))
        assertEquals(AppLanguage.PT, AppLanguage.resolve("pt", "es"))
        assertEquals(AppLanguage.EN, AppLanguage.resolve("en", "es"))
    }

    @Test
    fun `numeros com virgula em portugues e espanhol`() {
        assertEquals("3,4", String.format(AppLanguage.PT.locale, "%.1f", 3.4))
        assertEquals("3,4", String.format(AppLanguage.ES.locale, "%.1f", 3.4))
        assertEquals("3.4", String.format(AppLanguage.EN.locale, "%.1f", 3.4))
    }
}
