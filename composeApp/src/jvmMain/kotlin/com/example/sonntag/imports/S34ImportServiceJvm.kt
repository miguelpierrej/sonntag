package com.example.sonntag.imports

import com.example.sonntag.domain.models.TalkOutline
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import java.util.zip.ZipFile

/** "1. Titulo do discurso" — o numero ja vem embutido no titulo dentro do .jwpub. */
private val NUMBERED_TITLE = Regex("""^\s*(\d{1,3})[.)]\s*(.+)$""")

/**
 * Um .jwpub e um ZIP com `manifest.json` e `contents`; `contents` e outro ZIP que
 * guarda as imagens e o banco SQLite da publicacao. Os titulos dos bosquejos ficam
 * em texto puro na tabela `Document` desse banco (so o corpo do texto e cifrado).
 */
class S34ImportServiceJvm : S34ImportService {

    override suspend fun pickTalkOutlines(dialogTitle: String, filterLabel: String): List<TalkOutline>? {
        val path = chooseOpenPath(dialogTitle, filterLabel, "jwpub") ?: return null
        return readOutlines(File(path))
    }

    internal fun readOutlines(jwpub: File): List<TalkOutline> {
        val workDir = Files.createTempDirectory("s34-import").toFile()
        try {
            val contents = File(workDir, "contents")
            if (!extractEntry(jwpub, contents) { it.name == "contents" }) return emptyList()

            val publicationDb = File(workDir, "publication.db")
            if (!extractEntry(contents, publicationDb) { it.name.endsWith(".db") }) return emptyList()

            return queryOutlines(publicationDb)
        } finally {
            workDir.deleteRecursively()
        }
    }

    /** Copia para [target] a primeira entrada de [zipFile] que casar com [predicate]. */
    private fun extractEntry(
        zipFile: File,
        target: File,
        predicate: (java.util.zip.ZipEntry) -> Boolean,
    ): Boolean {
        return runCatching {
            ZipFile(zipFile).use { zip ->
                val entry = zip.entries().asSequence().firstOrNull(predicate) ?: return false
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                true
            }
        }.getOrDefault(false)
    }

    private fun queryOutlines(db: File): List<TalkOutline> {
        val titles = runCatching {
            DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT Title FROM Document ORDER BY DocumentId").use { rs ->
                        buildList {
                            while (rs.next()) rs.getString(1)?.let { add(it) }
                        }
                    }
                }
            }
        }.getOrDefault(emptyList())

        return titles.mapNotNull { raw ->
            val match = NUMBERED_TITLE.find(raw.trim()) ?: return@mapNotNull null
            val numero = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            TalkOutline(numero, match.groupValues[2].trim())
        }
    }
}

actual fun createS34ImportService(): S34ImportService = S34ImportServiceJvm()
