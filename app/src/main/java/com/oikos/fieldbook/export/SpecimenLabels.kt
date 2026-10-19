package com.oikos.fieldbook.export

import com.oikos.fieldbook.data.OccurrenceEntity
import com.oikos.fieldbook.data.ProjectEntity
import com.oikos.fieldbook.data.SessionEntity
import com.oikos.fieldbook.util.AppLanguage
import com.oikos.fieldbook.util.Axis
import com.oikos.fieldbook.util.CoordinateUtils
import com.oikos.fieldbook.util.parseFlatJsonMap
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Texto de uma etiqueta, ja na ordem em que e impressa. */
data class LabelContent(
    val header: String,
    val family: String,
    /** Parte do nome em italico (genero e epiteto). */
    val nameItalic: String,
    val authorship: String,
    val lines: List<String>,
    /** Codigo no QR e no rodape: o occurrenceID liga a exsicata ao registro digital. */
    val code: String,
    /** dwc:identificationQualifier (cf., aff., sp....), impresso sem italico. */
    val qualifier: String = "",
    /**
     * Linha que pode ser encurtada se a etiqueta nao couber (a descricao):
     * coletor, numero, data e determinador nunca sao cortados. -1 = nenhuma.
     */
    val shrinkableLine: Int = -1
)

/**
 * Etiquetas de herbario e colecao. Seguem o conteudo usual de uma etiqueta de
 * exsicata: instituicao, familia, nome com autor, localidade do pais ao ponto,
 * coordenada com datum, altitude, habitat, descricao, coletor com numero e
 * data, determinador. O QR leva o occurrenceID, o mesmo do Darwin Core.
 * Sem dependencia de Android: o desenho fica em SpecimenLabelPdf.
 */
object SpecimenLabels {

    /** Naturezas de registro que viram material de colecao. */
    val specimenBases = setOf("PreservedSpecimen", "MaterialSample", "LivingSpecimen", "FossilSpecimen")

    fun content(project: ProjectEntity, session: SessionEntity?, occurrence: OccurrenceEntity): LabelContent {
        val dynamic = parseFlatJsonMap(occurrence.dynamicFieldsJson)
        // Etiqueta no idioma do projeto.
        val language = AppLanguage.ofProject(project.language)
        fun say(pt: String, en: String, es: String) = when (language) {
            AppLanguage.PT -> pt
            AppLanguage.EN -> en
            AppLanguage.ES -> es
        }
        fun formatDate(value: String) = formatDate(value, language)
        val authorship = dynamic["scientificNameAuthorship"].orEmpty()
        val name = occurrence.scientificName.trim()
        val italic = if (authorship.isNotBlank() && name.endsWith(authorship)) name.removeSuffix(authorship).trim() else name
        // Pais em branco fica em branco: antes virava "BRASIL" mesmo para
        // material coletado fora do pais.
        val place = listOf(
            session?.country.orEmpty().uppercase(),
            session?.stateProvince.orEmpty(),
            session?.municipality.orEmpty()
        ).filter { it.isNotBlank() }.joinToString(": ").let { if (it.isBlank()) "" else "$it." }
        val locality = listOf(session?.locality.orEmpty(), dynamic["locality"].orEmpty()).filter { it.isNotBlank() }.distinct().joinToString(", ")
        // Mesma regra da exportacao: sem coordenada propria, o ponto da
        // expedicao com a incerteza e o datum dela, nunca campo a campo.
        val own = occurrence.latitude != null && occurrence.longitude != null
        val inherited = !own && session?.latitude != null && session.longitude != null
        val latitude = if (own) occurrence.latitude else if (inherited) session?.latitude else null
        val longitude = if (own) occurrence.longitude else if (inherited) session?.longitude else null
        val uncertaintyValue = if (own) occurrence.coordinateUncertaintyInMeters else if (inherited) session?.coordinateUncertaintyInMeters else null
        val datum = (if (inherited) session?.geodeticDatum.orEmpty() else occurrence.geodeticDatum).ifBlank { project.defaultDatum }
        val coordinates = if (latitude != null && longitude != null) {
            val uncertainty = uncertaintyValue?.let { " ±${Math.round(it)} m" }.orEmpty()
            "${CoordinateUtils.toDms(latitude, Axis.Latitude)} ${CoordinateUtils.toDms(longitude, Axis.Longitude)}$uncertainty ($datum)"
        } else ""
        val altitude = occurrence.altitude ?: if (inherited) session?.altitude else null
        val elevation = altitude?.let { "Alt. ${Math.round(it)} m" }.orEmpty()
        val habitat = dynamic["habitat"].orEmpty().ifBlank { session?.habitat.orEmpty() }
        val collector = occurrence.recordedBy.ifBlank { session?.recordedBy.orEmpty() }
        val date = formatDate(dynamic["eventDate"].orEmpty().ifBlank { session?.eventDate.orEmpty() })
        val collection = listOf(
            collector,
            occurrence.recordNumber.takeIf { it.isNotBlank() }?.let { say("nº $it", "No. $it", "n.º $it") }.orEmpty()
        ).filter { it.isNotBlank() }.joinToString(" ")
        val determiner = occurrence.identifiedBy.takeIf { it.isNotBlank() }?.let { by ->
            "Det. $by" + formatDate(occurrence.dateIdentified).takeIf { it.isNotBlank() }?.let { ", $it" }.orEmpty()
        }.orEmpty()
        val remarks = occurrence.occurrenceRemarks.trim()
        val lines = listOf(
            listOf(place, locality).filter { it.isNotBlank() }.joinToString(" "),
            listOf(coordinates, elevation).filter { it.isNotBlank() }.joinToString(" · "),
            habitat.takeIf { it.isNotBlank() }?.let { say("Hábitat: $it", "Habitat: $it", "Hábitat: $it") }.orEmpty(),
            remarks,
            occurrence.vernacularName.takeIf { it.isNotBlank() }?.let { say("Nome popular: $it", "Common name: $it", "Nombre común: $it") }.orEmpty(),
            listOf(collection, date).filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotBlank() }?.let { say("Col. $it", "Coll. $it", "Col. $it") }.orEmpty(),
            determiner
        ).filter { it.isNotBlank() }
        val header = listOf(project.institution, project.name).filter { it.isNotBlank() }.distinct().joinToString(" · ")
        return LabelContent(
            header = header,
            family = dynamic["family"].orEmpty().uppercase(),
            nameItalic = italic,
            authorship = authorship,
            lines = lines,
            code = occurrence.occurrenceId,
            // "cf." e "aff." mudam o sentido da determinacao; antes a etiqueta
            // imprimia o nome como se a identificacao fosse firme.
            qualifier = dynamic["identificationQualifier"].orEmpty().trim(),
            shrinkableLine = if (remarks.isBlank()) -1 else lines.indexOf(remarks)
        )
    }

    /**
     * "2026-10-17" -> "17/10/2026" (portugues e espanhol) ou "17 Oct 2026" (ingles,
     * sem a ambiguidade dia/mes); outro formato fica como esta.
     */
    fun formatDate(value: String, language: AppLanguage = AppLanguage.PT): String = runCatching {
        val date = LocalDate.parse(value.trim().take(10))
        if (language == AppLanguage.EN) date.format(DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH))
        else date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }.getOrDefault(value.trim())
}
