package com.example.sonntag.platform

import android.app.Activity
import android.content.Context

/**
 * Ponte para o contexto do Android. As funcoes `actual` do projeto sao top-level e
 * nao recebem parametros, entao o contexto precisa estar acessivel de forma global.
 */
object AndroidApp {
    lateinit var context: Context

    /** Necessario para dialogos de arquivo; nulo quando nao ha tela em primeiro plano. */
    var activity: Activity? = null
}
