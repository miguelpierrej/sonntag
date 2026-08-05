package com.example.sonntag.net

import android.content.Context
import android.net.wifi.WifiManager
import com.example.sonntag.platform.AndroidApp

/**
 * O Android descarta pacotes multicast por padrao, para poupar bateria. Sem segurar
 * este bloqueio, o anuncio dos outros aparelhos simplesmente nao chega.
 */
internal object MulticastLock {
    private var lock: WifiManager.MulticastLock? = null

    fun acquire() {
        if (lock != null) return
        val wifi = AndroidApp.context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        lock = wifi.createMulticastLock("sonntag-sync").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    fun release() {
        runCatching { lock?.release() }
        lock = null
    }
}
