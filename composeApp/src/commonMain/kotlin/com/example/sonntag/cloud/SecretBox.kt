package com.example.sonntag.cloud

/**
 * Guarda segredos (senha do banco, chave da congregacao) cifrados com uma chave que
 * nao mora no banco do app. Quem copia so o arquivo do banco — um backup, por
 * exemplo — nao leva as credenciais da nuvem junto.
 */
interface SecretBox {
    fun seal(plain: String): String

    /** Null se a chave deste aparelho nao abre (outro aparelho, chave apagada). */
    fun open(sealed: String): String?
}

expect fun createSecretBox(): SecretBox
