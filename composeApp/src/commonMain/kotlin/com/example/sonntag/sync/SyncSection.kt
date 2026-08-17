package com.example.sonntag.sync

/**
 * Blocos que o usuario escolhe ao exportar. A ordem importa na importacao: quem e
 * referenciado entra antes de quem referencia (membros antes das designacoes).
 */
enum class SyncSection(val id: String, val label: String, val tables: List<String>) {
    CONGREGACAO("congregacao", "Congregação e dias de reunião", listOf("settings", "meeting_days")),
    MEMBROS("membros", "Membros", listOf("members")),
    REUNIOES("reunioes", "Reuniões", listOf("meetings")),
    FIM_DE_SEMANA("fim_de_semana", "Programas de fim de semana", listOf("weekend_programs")),
    MEIO_DE_SEMANA("meio_de_semana", "Programas de meio de semana", listOf("midweek_programs")),
    AUDIO_VIDEO("audio_video", "Áudio/vídeo e acomodadores", listOf("av_assignments")),
    LIMPEZA("limpeza", "Limpeza", listOf("cleaning_groups", "cleaning_assignments")),
    PREGACAO(
        "pregacao",
        "Pregação",
        listOf(
            "preaching_spots", "preaching_groups",
            "preaching_templates", "preaching_shifts", "preaching_notes",
        ),
    );

    companion object {
        fun fromId(id: String): SyncSection? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Blocos que dependem de outros para fazer sentido. Exportar as designacoes sem os
 * membros produz um arquivo que importa "nomes" que nao existem do outro lado.
 */
val SyncSection.requires: List<SyncSection>
    get() = when (this) {
        SyncSection.FIM_DE_SEMANA, SyncSection.MEIO_DE_SEMANA, SyncSection.AUDIO_VIDEO ->
            listOf(SyncSection.MEMBROS, SyncSection.REUNIOES)
        SyncSection.LIMPEZA -> listOf(SyncSection.REUNIOES)
        SyncSection.PREGACAO -> listOf(SyncSection.MEMBROS)
        else -> emptyList()
    }
