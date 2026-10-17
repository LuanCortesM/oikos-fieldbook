package com.oikos.fieldbook.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.oikos.fieldbook.data.MediaEntity
import org.json.JSONArray
import org.json.JSONObject
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Estado de um formulario que sobrevive a rotacao da tela e a morte do
 * processo em segundo plano (o Android fecha o app enquanto a camera esta
 * aberta, por exemplo).
 *
 * Ate a v0.2.0 cada campo vivia em `remember`: girar o aparelho no meio de uma
 * ocorrencia apagava tudo o que tinha sido digitado. Aqui os valores sao texto
 * num mapa observavel, salvo no Bundle da Activity como JSON.
 *
 * [initialized] marca que o formulario ja foi preenchido a partir do registro
 * do banco; depois de restaurado, ele nao e sobrescrito de novo.
 */
class FormState internal constructor(
    values: Map<String, String> = emptyMap(),
    pending: List<MediaEntity> = emptyList(),
    initialized: Boolean = false,
    dirty: Boolean = false
) {
    val values = mutableStateMapOf<String, String>().apply { putAll(values) }

    /** Midias capturadas para um registro ainda nao salvo. */
    var pendingMedia by mutableStateOf(pending)

    var initialized by mutableStateOf(initialized)

    /** Houve edicao desde que o formulario foi carregado. */
    var dirty by mutableStateOf(dirty)

    operator fun get(key: String): String = values[key].orEmpty()

    operator fun set(key: String, value: String) {
        if (values[key].orEmpty() != value) {
            values[key] = value
            if (initialized) dirty = true
        }
    }

    fun bool(key: String, default: Boolean): Boolean = values[key]?.toBooleanStrictOrNull() ?: default
    fun setBool(key: String, value: Boolean) = set(key, value.toString())

    /** Carrega valores iniciais sem marcar o formulario como editado. */
    fun load(initial: Map<String, String>) {
        values.clear()
        values.putAll(initial.filterValues { it.isNotEmpty() })
        initialized = true
        dirty = false
    }

    /** Delegacao: `var nome by form.field("nome")`. */
    fun field(key: String): ReadWriteProperty<Any?, String> = object : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): String = get(key)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) = set(key, value)
    }

    companion object {
        val Saver = listSaver<FormState, String>(
            save = { state ->
                listOf(
                    JSONObject(state.values.toMap()).toString(),
                    mediaToJson(state.pendingMedia),
                    state.initialized.toString(),
                    state.dirty.toString()
                )
            },
            restore = { saved ->
                FormState(
                    values = jsonToMap(saved.getOrElse(0) { "{}" }),
                    pending = jsonToMedia(saved.getOrElse(1) { "[]" }),
                    initialized = saved.getOrElse(2) { "false" }.toBoolean(),
                    dirty = saved.getOrElse(3) { "false" }.toBoolean()
                )
            }
        )

        private fun jsonToMap(json: String): Map<String, String> = runCatching {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.optString(it) }
        }.getOrDefault(emptyMap())

        private fun mediaToJson(media: List<MediaEntity>): String = JSONArray(media.map { item ->
            JSONObject()
                .put("projectId", item.projectId)
                .put("sessionId", item.sessionId)
                .put("mediaType", item.mediaType)
                .put("filePath", item.filePath)
                .put("fileName", item.fileName)
                .put("mimeType", item.mimeType)
                .put("createdAt", item.createdAt)
                .put("latitude", item.latitude ?: JSONObject.NULL)
                .put("longitude", item.longitude ?: JSONObject.NULL)
                .put("caption", item.caption)
                .put("mediaSubject", item.mediaSubject)
                .put("notes", item.notes)
        }).toString()

        private fun jsonToMedia(json: String): List<MediaEntity> = runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                MediaEntity(
                    projectId = obj.getLong("projectId"),
                    sessionId = obj.getLong("sessionId"),
                    mediaType = obj.getString("mediaType"),
                    filePath = obj.getString("filePath"),
                    fileName = obj.getString("fileName"),
                    mimeType = obj.getString("mimeType"),
                    createdAt = obj.getLong("createdAt"),
                    latitude = if (obj.isNull("latitude")) null else obj.getDouble("latitude"),
                    longitude = if (obj.isNull("longitude")) null else obj.getDouble("longitude"),
                    caption = obj.optString("caption"),
                    mediaSubject = obj.optString("mediaSubject"),
                    notes = obj.optString("notes")
                )
            }
        }.getOrDefault(emptyList())
    }
}

/** Formulario salvavel; [key] distingue, por exemplo, "nova ocorrencia" de "editar #12". */
@Composable
fun rememberFormState(key: String): FormState = rememberSaveable(key, saver = FormState.Saver) { FormState() }
