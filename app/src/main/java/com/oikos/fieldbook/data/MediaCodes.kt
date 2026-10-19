package com.oikos.fieldbook.data

import androidx.sqlite.db.SupportSQLiteDatabase
import java.text.Normalizer

/**
 * Numero fixo de cada midia: iniciais de quem coletou e um numero sequencial
 * deste aparelho, como "LSCM-0001". Ele viaja com a midia quando o projeto
 * passa de um celular para outro, para que a mesma foto tenha o mesmo numero
 * nas planilhas de toda a equipe (mediaID na exportacao e no nome do arquivo).
 *
 * As midias que ja existiam antes do numero fixo (banco v8) ficaram com o
 * numero que a exportacao ja usava, MED-000123 (id da linha).
 */
object MediaCodes {
    /** Prefixo quando ninguem foi anotado como coletor. */
    const val NO_COLLECTOR = "MED"

    private const val MAX_INITIALS = 6

    /** Partes de nome que nao viram inicial ("Luan da Silva" -> "LS"). */
    private val PARTICLES = setOf(
        "d", "da", "das", "de", "del", "della", "der", "di", "do", "dos", "du",
        "e", "la", "le", "van", "von", "y"
    )

    /**
     * Iniciais do primeiro coletor de um recordedBy. Coletores separados por
     * "|" ou ";" (separador do Darwin Core); "Sobrenome, Nome" tambem vale:
     * "Luan da Silva Cortes Maciel" e "Maciel, L. S. C." dao "LSCM".
     */
    fun initials(recordedBy: String): String {
        val first = recordedBy.split('|', ';').map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return ""
        val parts = first.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val ordered = if (parts.size == 2) "${parts[1]} ${parts[0]}" else parts.joinToString(" ")
        val plain = Normalizer.normalize(ordered, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return plain.split(Regex("[^A-Za-z]+"))
            .filter { it.isNotEmpty() && it.lowercase() !in PARTICLES }
            .joinToString("") { it.first().uppercase() }
            .take(MAX_INITIALS)
    }

    fun prefix(recordedBy: String): String = initials(recordedBy).ifEmpty { NO_COLLECTOR }

    fun format(prefix: String, number: Int): String = "$prefix-${number.toString().padStart(4, '0')}"

    /** Numero das midias anteriores ao numero fixo: o id da linha, como a exportacao fazia. */
    fun legacy(id: Long): String = "MED-${id.toString().padStart(6, '0')}"

    /** Forma usada no nome do arquivo exportado: sem hifen (MED000123, LSCM0001). */
    fun fileToken(code: String): String = code.replace("-", "")

    fun isTaken(db: SupportSQLiteDatabase, code: String): Boolean =
        db.query("SELECT 1 FROM media WHERE mediaCode = ? LIMIT 1", arrayOf<Any>(code)).use { it.moveToFirst() }

    /**
     * O numero, se estiver livre neste aparelho; se outra midia ja o usa (duas
     * pessoas com as mesmas iniciais), ganha uma letra: LSCM-0001b, LSCM-0001c...
     */
    fun free(db: SupportSQLiteDatabase, code: String): String {
        if (!isTaken(db, code)) return code
        for (letter in 'b'..'z') {
            val candidate = "$code$letter"
            if (!isTaken(db, candidate)) return candidate
        }
        var index = 2
        while (isTaken(db, "$code-$index")) index++
        return "$code-$index"
    }

    /**
     * Proximo numero para uma midia nova, pelas iniciais de quem coletou: o
     * coletor da ocorrencia ou, sem ele, o da expedicao. O contador so cresce,
     * entao um numero de midia apagada nunca volta a ser usado. Chamar dentro
     * da transacao que grava a midia.
     */
    fun next(db: SupportSQLiteDatabase, sessionId: Long, occurrenceId: Long?): String {
        val recordedBy = occurrenceId?.let { text(db, "SELECT recordedBy FROM occurrences WHERE id = ?", it) }.orEmpty()
            .ifBlank { text(db, "SELECT recordedBy FROM sessions WHERE id = ?", sessionId).orEmpty() }
        val prefix = prefix(recordedBy)
        val last = db.query("SELECT lastNumber FROM media_code_counters WHERE prefix = ?", arrayOf<Any>(prefix)).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        var number = last + 1
        while (isTaken(db, format(prefix, number))) number++
        db.execSQL("INSERT OR REPLACE INTO media_code_counters (prefix, lastNumber) VALUES (?, ?)", arrayOf<Any>(prefix, number))
        return format(prefix, number)
    }

    private fun text(db: SupportSQLiteDatabase, sql: String, id: Long): String? =
        db.query(sql, arrayOf<Any>(id)).use { if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null }
}
