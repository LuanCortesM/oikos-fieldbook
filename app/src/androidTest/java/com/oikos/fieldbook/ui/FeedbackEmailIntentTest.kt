package com.oikos.fieldbook.ui

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** O e-mail de relato chega ao app de e-mail inteiro: destino, assunto e texto. */
@RunWith(AndroidJUnit4::class)
class FeedbackEmailIntentTest {

    @Test
    fun relatoChegaInteiroAoAppDeEmail() {
        val subject = FeedbackEmail.subject("0.2.1", "Dúvida")
        // "&", "?", "#" e "%" cortavam ou estragavam o texto quando ele ia no mailto:.
        val body = FeedbackEmail.body(
            text = "Ação & reação? Linha 1\nLinha 2 #3 100% ok",
            appVersion = "0.2.1", appBuild = 11, androidRelease = "16", androidSdk = 36,
            device = "Google Pixel", deviceLabel = "Aparelho", languageLabel = "Idioma do app", languageCode = "pt"
        )
        val intent = FeedbackEmail.intent(subject, body)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertArrayEquals(arrayOf("herpetomantiqueira@gmail.com"), intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
        assertEquals(subject, intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals(body, intent.getStringExtra(Intent.EXTRA_TEXT))
        // So apps de e-mail: o seletor e um mailto: sem nada mais.
        assertEquals(Intent.ACTION_SENDTO, intent.selector?.action)
        assertEquals("mailto:", intent.selector?.data.toString())
    }
}
