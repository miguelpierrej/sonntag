package com.example.sonntag.net

/** Porta e grupo multicast do anuncio. Detalhe de implementacao da rede. */
internal const val DISCOVERY_PORT = 45654
internal const val DISCOVERY_GROUP = "239.7.7.7"

/** Prefixo do anuncio, para ignorar qualquer outro trafego que caia na porta. */
internal const val ANNOUNCE_PREFIX = "SONNTAG/1"
