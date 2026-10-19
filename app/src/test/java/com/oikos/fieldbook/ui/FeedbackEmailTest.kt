package com.oikos.fieldbook.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackEmailTest {

    @Test
    fun `assunto traz versao e tipo do relato`() {
        assertEquals("Oikos FieldBook 0.2.1 · Problema", FeedbackEmail.subject("0.2.1", "Problema"))
    }

    @Test
    fun `corpo traz o texto e so os dados tecnicos do aparelho`() {
        val body = FeedbackEmail.body(
            text = "  O app fechou ao salvar a foto.\n",
            appVersion = "0.2.1",
            appBuild = 11,
            androidRelease = "16",
            androidSdk = 36,
            device = "Google Pixel 9 ",
            deviceLabel = "Aparelho",
            languageLabel = "Idioma do app",
            languageCode = "pt"
        )
        assertEquals(
            "O app fechou ao salvar a foto.\n\n—\n" +
                "Oikos FieldBook 0.2.1 (11)\n" +
                "Android 16 (API 36)\n" +
                "Aparelho: Google Pixel 9\n" +
                "Idioma do app: pt",
            body
        )
    }

    @Test
    fun `endereco e o contato publico do app`() {
        assertEquals("herpetomantiqueira@gmail.com", FeedbackEmail.ADDRESS)
        assertTrue(FeedbackEmail.ADDRESS.contains('@'))
        assertFalse(FeedbackEmail.ADDRESS.contains(' '))
    }
}
