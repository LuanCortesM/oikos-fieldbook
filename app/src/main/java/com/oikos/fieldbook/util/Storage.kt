package com.oikos.fieldbook.util

import android.content.Context
import android.os.storage.StorageManager
import java.io.File

/**
 * Espaco que o app consegue usar em [dir], contando o cache que o Android pode
 * liberar sozinho. Em celular quase cheio, usableSpace dizia "sem espaco" mesmo
 * quando o sistema liberaria o necessario.
 */
fun allocatableBytes(context: Context, dir: File): Long = runCatching {
    val storage = context.getSystemService(StorageManager::class.java)
    storage.getAllocatableBytes(storage.getUuidForPath(dir))
}.getOrDefault(dir.usableSpace)
