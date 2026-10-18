package com.oikos.fieldbook.checklist

import com.oikos.fieldbook.data.ChecklistEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Versao nova disponivel" so quando a fonte publicou algo mais novo que o instalado. */
class RemoteVersionTest {

    private fun installed(version: String, pubDate: String) = ChecklistEntity(
        sourceKey = "flora_brasil", title = "Flora", version = version, pubDate = pubDate,
        license = "", citation = "", sourceUrl = "app", importedAt = 0, taxonCount = 1
    )

    @Test
    fun `versao numerica maior e nova, igual ou menor nao`() {
        assertTrue(RemoteVersion("393.433", null).isNewerThan(installed("393.432", "2026-10-01")))
        assertTrue(RemoteVersion("393.1000", null).isNewerThan(installed("393.999", "")))
        assertFalse(RemoteVersion("393.432", null).isNewerThan(installed("393.432", "2026-10-01")))
        assertFalse(RemoteVersion("393.430", null).isNewerThan(installed("393.432", "2026-10-01")))
    }

    @Test
    fun `data do ChecklistBank mais antiga que a copia do app nao e novidade`() {
        assertFalse(RemoteVersion(null, "2026-09-15").isNewerThan(installed("393.432", "2026-10-01")))
        assertTrue(RemoteVersion(null, "2026-11-02").isNewerThan(installed("393.432", "2026-10-01")))
    }
}
