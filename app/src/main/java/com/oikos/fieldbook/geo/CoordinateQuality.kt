package com.oikos.fieldbook.geo

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Conferencia de coordenada no momento do registro, quando ainda da para
 * corrigir. Os casos seguem os alertas que o GBIF aplica depois da publicacao
 * (coordenada zero, sinal trocado, latitude e longitude invertidas, valor
 * arredondado, pais ou estado divergente); aqui eles aparecem em campo.
 * Sem dependencia de Android.
 */
object CoordinateQuality {

    enum class Level { INFO, WARNING, ERROR }

    data class Issue(val level: Level, val message: String)

    data class Report(val place: PlaceCheck?, val issues: List<Issue>) {
        val worst: Level? get() = issues.maxByOrNull { it.level.ordinal }?.level
    }

    private val ufNames = mapOf(
        "acre" to "AC", "alagoas" to "AL", "amapa" to "AP", "amazonas" to "AM", "bahia" to "BA", "ceara" to "CE",
        "distrito federal" to "DF", "espirito santo" to "ES", "goias" to "GO", "maranhao" to "MA", "mato grosso" to "MT",
        "mato grosso do sul" to "MS", "minas gerais" to "MG", "para" to "PA", "paraiba" to "PB", "parana" to "PR",
        "pernambuco" to "PE", "piaui" to "PI", "rio de janeiro" to "RJ", "rio grande do norte" to "RN",
        "rio grande do sul" to "RS", "rondonia" to "RO", "roraima" to "RR", "santa catarina" to "SC",
        "sao paulo" to "SP", "sergipe" to "SE", "tocantins" to "TO"
    )

    /** Nome por extenso, como o Darwin Core recomenda para stateProvince. */
    val ufFullName = mapOf(
        "AC" to "Acre", "AL" to "Alagoas", "AP" to "Amapá", "AM" to "Amazonas", "BA" to "Bahia", "CE" to "Ceará",
        "DF" to "Distrito Federal", "ES" to "Espírito Santo", "GO" to "Goiás", "MA" to "Maranhão", "MT" to "Mato Grosso",
        "MS" to "Mato Grosso do Sul", "MG" to "Minas Gerais", "PA" to "Pará", "PB" to "Paraíba", "PR" to "Paraná",
        "PE" to "Pernambuco", "PI" to "Piauí", "RJ" to "Rio de Janeiro", "RN" to "Rio Grande do Norte",
        "RS" to "Rio Grande do Sul", "RO" to "Rondônia", "RR" to "Roraima", "SC" to "Santa Catarina",
        "SP" to "São Paulo", "SE" to "Sergipe", "TO" to "Tocantins"
    )

    /** "Rio de Janeiro", "RJ" ou "rj" -> "RJ"; nulo se nao for uma UF brasileira. */
    fun ufCode(value: String): String? {
        val key = normalize(value)
        if (key.isEmpty()) return null
        if (key.length == 2) return key.uppercase(Locale.ROOT).takeIf { it in ufNames.values }
        return ufNames[key]
    }

    fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase(Locale.ROOT)
            .replace("[^a-z0-9 ]+".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    /**
     * [place] e o resultado da malha para o ponto; [locate] acha o municipio de
     * outro ponto (para testar sinal trocado e inversao). [declaredUf] e
     * [declaredMunicipality] sao o que a expedicao diz.
     */
    fun check(
        latitude: Double,
        longitude: Double,
        place: PlaceCheck?,
        locate: (Double, Double) -> Municipality?,
        declaredUf: String = "",
        declaredMunicipality: String = ""
    ): Report {
        val issues = mutableListOf<Issue>()
        if (latitude == 0.0 && longitude == 0.0) {
            issues += Issue(Level.ERROR, "Coordenada 0, 0 (no mar, no Golfo da Guiné): quase sempre é campo vazio ou erro de digitação.")
            return Report(place, issues)
        }
        if (latitude == latitude.roundToInt().toDouble() || longitude == longitude.roundToInt().toDouble()) {
            issues += Issue(Level.WARNING, "Coordenada sem casas decimais: parece arredondada. Um grau tem cerca de 111 km.")
        }
        if (place == null) return Report(null, issues)
        val inside = place.municipality
        if (inside == null) {
            val alternatives = listOf(
                "com a latitude de sinal trocado (N/S)" to (-latitude to longitude),
                "com a longitude de sinal trocado (L/O)" to (latitude to -longitude),
                "com os dois sinais trocados" to (-latitude to -longitude),
                "com latitude e longitude invertidas" to (longitude to latitude)
            )
            val fix = alternatives.firstNotNullOfOrNull { (description, point) ->
                if (abs(point.first) <= 90 && abs(point.second) <= 180) locate(point.first, point.second)?.let { description to it } else null
            }
            when {
                place.nearest != null -> issues += Issue(
                    Level.WARNING,
                    "Fora da malha municipal, a ${distanceText(place.nearestDistanceMeters)} de ${place.nearest.label}: mar, ilha pequena ou outro país? Confira."
                )
                fix != null -> issues += Issue(Level.ERROR, "Fora do Brasil. ${fix.first.replaceFirstChar { it.uppercase() }} cairia em ${fix.second.label}: confira.")
                else -> issues += Issue(Level.INFO, "Fora do Brasil: o app só confere municípios brasileiros.")
            }
            return Report(place, issues)
        }
        if (place.nearest != null) {
            issues += Issue(
                Level.WARNING,
                "A ${distanceText(place.nearestDistanceMeters)} da divisa com ${place.nearest.label}: confira o município."
            )
        }
        val declared = ufCode(declaredUf)
        if (declared != null && declared != inside.uf) {
            issues += Issue(Level.WARNING, "A expedição está em $declaredUf, mas o ponto cai em ${inside.label}.")
        } else if (declaredMunicipality.isNotBlank() && normalize(declaredMunicipality) != normalize(inside.name)) {
            issues += Issue(Level.WARNING, "A expedição está em $declaredMunicipality, mas o ponto cai em ${inside.label}.")
        }
        return Report(place, issues)
    }

    private fun distanceText(meters: Double?): String = when {
        meters == null -> "?"
        meters < 1000 -> "${(meters / 10).roundToInt() * 10} m"
        else -> String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", meters / 1000)
    }
}
