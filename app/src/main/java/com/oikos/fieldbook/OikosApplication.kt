package com.oikos.fieldbook

import android.app.Application
import com.oikos.fieldbook.backup.BackupService
import com.oikos.fieldbook.backup.RestoreOutcome
import com.oikos.fieldbook.data.ChecklistDatabase
import com.oikos.fieldbook.data.OikosDatabase
import com.oikos.fieldbook.data.OikosRepository

class OikosApplication : Application() {
    val database: OikosDatabase by lazy { OikosDatabase.create(this) }
    val checklistDatabase: ChecklistDatabase by lazy { ChecklistDatabase.create(this) }
    val repository: OikosRepository by lazy { OikosRepository(database) }

    /** Restauracao de backup concluida (ou que falhou) ao abrir este processo. */
    var restoreOutcome: RestoreOutcome = RestoreOutcome.None
        private set

    override fun onCreate() {
        super.onCreate()
        // Antes de qualquer acesso ao banco (que e lazy): conclui uma
        // restauracao preparada antes do reinicio do app.
        restoreOutcome = BackupService.applyPendingRestore(this)
        // Modelo do geoide (2 MB) carregado ja, fora da tela: a primeira captura
        // de GPS nao espera por ele.
        Thread { com.oikos.fieldbook.geo.Geoid.get(this) }.apply { isDaemon = true; start() }
    }
}
