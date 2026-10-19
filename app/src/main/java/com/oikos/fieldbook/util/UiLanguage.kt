package com.oikos.fieldbook.util

import android.content.Context
import java.util.Locale

/** Idiomas do app: da tela (Configuracoes > Idioma) e dos dados de um projeto. */
enum class AppLanguage(val code: String) {
    PT("pt"), EN("en"), ES("es");

    /** Para formatar numeros e datas: virgula decimal em portugues e espanhol. */
    val locale: Locale
        get() = when (this) {
            PT -> Locale.forLanguageTag("pt-BR")
            EN -> Locale.US
            ES -> Locale.forLanguageTag("es")
        }

    companion object {
        /**
         * Idioma da tela: o escolhido no app ou, no automatico, o do celular
         * (portugues e espanhol pelo proprio idioma; qualquer outro, ingles).
         */
        fun resolve(preference: String?, deviceLanguage: String): AppLanguage = when (preference) {
            "pt" -> PT
            "en" -> EN
            "es" -> ES
            else -> when (deviceLanguage.lowercase()) {
                "pt" -> PT
                "es" -> ES
                else -> EN
            }
        }

        /** Idioma dos dados e das exportacoes de um projeto ("pt", "en", "es"). */
        fun ofProject(projectLanguage: String): AppLanguage = when {
            projectLanguage.startsWith("en", ignoreCase = true) -> EN
            projectLanguage.startsWith("es", ignoreCase = true) -> ES
            else -> PT
        }
    }
}

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

    /** So para testes: simula o idioma escolhido na tela. */
    @Volatile
    internal var testOverride: AppLanguage? = null

    val current: AppLanguage
        get() {
            testOverride?.let { return it }
            val context = app ?: return AppLanguage.PT
            val preference = context.getSharedPreferences("oikos_app_settings", Context.MODE_PRIVATE).getString("app_language", "auto")
            return AppLanguage.resolve(preference, context.resources.configuration.locales[0].language)
        }

    val isPortuguese: Boolean get() = current == AppLanguage.PT

    /** O mesmo texto nos tres idiomas; o espanhol e obrigatorio para nenhuma mensagem ficar sem traducao. */
    fun t(pt: String, en: String, es: String): String = when (current) {
        AppLanguage.PT -> pt
        AppLanguage.EN -> en
        AppLanguage.ES -> es
    }

    /** "1 foto" / "3 fotos" no idioma da tela. */
    fun count(n: Number, ptOne: String, ptMany: String, enOne: String, enMany: String, esOne: String, esMany: String): String {
        val one = n.toLong() == 1L
        return "$n " + when (current) {
            AppLanguage.PT -> if (one) ptOne else ptMany
            AppLanguage.EN -> if (one) enOne else enMany
            AppLanguage.ES -> if (one) esOne else esMany
        }
    }
}

/** Texto de arquivo exportado no idioma do projeto. */
fun inProjectLanguage(projectLanguage: String, pt: String, en: String, es: String): String =
    when (AppLanguage.ofProject(projectLanguage)) {
        AppLanguage.PT -> pt
        AppLanguage.EN -> en
        AppLanguage.ES -> es
    }
