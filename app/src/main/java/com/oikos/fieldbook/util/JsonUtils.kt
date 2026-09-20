package com.oikos.fieldbook.util

import org.json.JSONObject

fun parseFlatJsonMap(json: String): Map<String, String> {
    if (json.isBlank()) return emptyMap()
    return runCatching {
        val objectJson = JSONObject(json)
        objectJson.keys().asSequence().associateWith { objectJson.optString(it) }
    }.getOrElse { emptyMap() }
}

fun flatJson(values: Map<String, String>): String {
    val objectJson = JSONObject()
    values.forEach { (key, value) ->
        if (value.isNotBlank()) objectJson.put(key, value)
    }
    return objectJson.toString()
}
