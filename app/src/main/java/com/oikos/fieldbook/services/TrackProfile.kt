package com.oikos.fieldbook.services

/**
 * Quanto a trilha GPS pede ao receptor. O GPS ligado direto e o que mais gasta
 * bateria em campo; espaçar os pontos e permitir entrega em lote (o chip guarda
 * as posicoes e acorda o processador de vez em quando) estica um dia de trilha
 * em celular simples. A precisao de cada ponto nao muda: so a densidade.
 */
enum class TrackProfile(
    val code: String,
    val intervalMillis: Long,
    val minIntervalMillis: Long,
    val minDistanceMeters: Float,
    /** Atraso maximo para entregar posicoes em lote. */
    val maxDelayMillis: Long
) {
    PRECISE("precisa", 3_000L, 1_500L, 1.5f, 5_000L),
    BALANCED("equilibrada", 10_000L, 5_000L, 5f, 30_000L),
    SAVER("economia", 30_000L, 15_000L, 15f, 120_000L);

    companion object {
        const val PREFERENCE_KEY = "track_profile"
        fun parse(code: String?): TrackProfile = entries.firstOrNull { it.code == code } ?: PRECISE
    }
}
