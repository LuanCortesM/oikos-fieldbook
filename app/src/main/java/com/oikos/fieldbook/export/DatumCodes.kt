package com.oikos.fieldbook.export

import java.util.Locale

/**
 * Datum como sai na exportacao. A pessoa escolhe e ve "SIRGAS 2000" no app,
 * mas o GBIF so reconhece o codigo EPSG ou os nomes da lista dele, que nao tem
 * SIRGAS: com o nome, todo registro saia com GEODETIC_DATUM_INVALID e o GBIF
 * assumia WGS84. O Darwin Core recomenda o codigo EPSG em geodeticDatum e
 * verbatimSRS. O banco, as telas e as etiquetas continuam com o nome.
 */
internal object DatumCodes {
    private val epsg = mapOf(
        "SIRGAS2000" to "EPSG:4674",
        "WGS84" to "EPSG:4326"
    )

    /** Codigo EPSG dos datums oferecidos no app; qualquer outro valor sai como esta. */
    fun forExport(datum: String): String =
        epsg[datum.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }] ?: datum.trim()

    /** "SIRGAS 2000 (EPSG:4674)", para textos lidos por pessoas, como o LEIA-ME. */
    fun withCode(datum: String): String {
        val name = datum.trim()
        val code = forExport(name)
        return if (code == name) name else "$name ($code)"
    }
}
