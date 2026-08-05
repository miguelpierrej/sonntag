package com.example.sonntag.net

/**
 * Registro da troca pela rede. Sai no console do desktop e no logcat do Android
 * (`adb logcat | grep LanSync`), que e a unica janela para o que acontece entre os
 * dois aparelhos.
 */
internal object LanLog {
    fun i(msg: String) = println("[LanSync] $msg")

    fun e(msg: String, t: Throwable? = null) {
        println("[LanSync] ERRO $msg${t?.let { ": ${it::class.simpleName}: ${it.message}" } ?: ""}")
        t?.printStackTrace()
    }
}
