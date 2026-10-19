package com.oikos.fieldbook.services

import android.Manifest
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O Nearby recusa sem localizacao tambem no Android novo: 8034 sem a aproximada
 * e 8036 sem a precisa (visto no Android 16 com as permissoes de dispositivos
 * proximos dadas). O envio direto tem de pedir as duas em qualquer versao.
 */
@RunWith(AndroidJUnit4::class)
class NearbyPermissionsTest {

    @Test
    fun envioDiretoPedeLocalizacaoPrecisaEAproximadaEmQualquerVersao() {
        val required = NearbyTransfer.requiredPermissions().toList()
        assertTrue(required.toString(), Manifest.permission.ACCESS_FINE_LOCATION in required)
        assertTrue(required.toString(), Manifest.permission.ACCESS_COARSE_LOCATION in required)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assertTrue(required.toString(), Manifest.permission.BLUETOOTH_SCAN in required)
            assertTrue(required.toString(), Manifest.permission.BLUETOOTH_ADVERTISE in required)
            assertTrue(required.toString(), Manifest.permission.BLUETOOTH_CONNECT in required)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            assertTrue(required.toString(), Manifest.permission.NEARBY_WIFI_DEVICES in required)
        }
    }

    @Test
    fun faltaDeLocalizacaoPrecisaViraMensagemDePermissao() {
        assertTrue(ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_FINE_LOCATION in NearbyTransfer.MISSING_PERMISSION_CODES)
        assertTrue(ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_COARSE_LOCATION in NearbyTransfer.MISSING_PERMISSION_CODES)
        assertTrue(ConnectionsStatusCodes.MISSING_PERMISSION_NEARBY_WIFI_DEVICES in NearbyTransfer.MISSING_PERMISSION_CODES)
    }
}
