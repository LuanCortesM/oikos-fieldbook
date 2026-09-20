package com.oikos.fieldbook

import android.app.Application
import com.oikos.fieldbook.data.OikosDatabase
import com.oikos.fieldbook.data.OikosRepository

class OikosApplication : Application() {
    val database: OikosDatabase by lazy { OikosDatabase.create(this) }
    val repository: OikosRepository by lazy { OikosRepository(database) }
}
