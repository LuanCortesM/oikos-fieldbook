package com.oikos.fieldbook.ui

/**
 * E-mail de relato (problema, ideia ou duvida). Vai pelo app de e-mail do
 * celular: sem servidor, e sem sinal a mensagem espera na caixa de saida.
 * O rodape leva so o necessario para reproduzir um erro: versao do app e do
 * Android, modelo do aparelho e idioma. Nada de localizacao, nome de projeto
 * ou dado de registro.
 */
internal object FeedbackEmail {
    /** Contato publico do app (Play e politica de privacidade). */
    const val ADDRESS = "herpetomantiqueira@gmail.com"

    fun subject(appVersion: String, kind: String): String = "Oikos FieldBook $appVersion · $kind"

    /**
     * Abre o app de e-mail ja preenchido. ACTION_SEND leva assunto e texto
     * como extras, que todo app de e-mail le; o seletor mailto: deixa so os
     * apps de e-mail na escolha. Antes o texto ia dentro do mailto: e um "&"
     * no relato cortava o resto da mensagem.
     */
    fun intent(subject: String, body: String): android.content.Intent =
        android.content.Intent(android.content.Intent.ACTION_SEND)
            .putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(ADDRESS))
            .putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
            .putExtra(android.content.Intent.EXTRA_TEXT, body)
            .apply { selector = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:")) }

    fun body(
        text: String,
        appVersion: String,
        appBuild: Int,
        androidRelease: String,
        androidSdk: Int,
        device: String,
        deviceLabel: String,
        languageLabel: String,
        languageCode: String
    ): String = buildString {
        append(text.trim())
        append("\n\n—\n")
        append("Oikos FieldBook $appVersion ($appBuild)\n")
        append("Android $androidRelease (API $androidSdk)\n")
        append("$deviceLabel: ${device.trim()}\n")
        append("$languageLabel: $languageCode")
    }
}
