package com.oikos.fieldbook.util

import android.content.Context

/**
 * Idioma das mensagens que nascem fora da tela (ViewModel, servicos, erros):
 * o mesmo de Configuracoes > Idioma; "auto" segue o idioma do celular.
 * Sem contexto (testes de unidade), fica em portugues.
 */
object UiLanguage {
    @Volatile
    private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    val isPortuguese: Boolean
        get() {
            val context = app ?: return true
            return when (context.getSharedPreferences("oikos_app_settings", Context.MODE_PRIVATE).getString("app_language", "auto")) {
                "pt" -> true
                "en" -> false
                else -> context.resources.configuration.locales[0].language.equals("pt", ignoreCase = true)
            }
        }

    fun t(pt: String, en: String): String = if (isPortuguese) pt else en

    /** "1 foto" / "3 fotos" no idioma da tela. */
    fun count(n: Number, ptOne: String, ptMany: String, enOne: String, enMany: String): String {
        val one = n.toLong() == 1L
        return "$n " + if (isPortuguese) (if (one) ptOne else ptMany) else (if (one) enOne else enMany)
    }
}

/** Texto de arquivo exportado no idioma do projeto ("en..." ou portugues). */
fun inProjectLanguage(projectLanguage: String, pt: String, en: String): String =
    if (projectLanguage.startsWith("en", ignoreCase = true)) en else pt
