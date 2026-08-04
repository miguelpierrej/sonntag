package com.example.sonntag.imports

import android.database.sqlite.SQLiteDatabase
import com.example.sonntag.domain.models.TalkOutline
import com.example.sonntag.platform.AndroidApp
import com.example.sonntag.platform.FilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** "1. Titulo do discurso" — o numero ja vem embutido no titulo dentro do .jwpub. */
private val NUMBERED_TITLE = Regex("""^\s*(\d{1,3})[.)]\s*(.+)$""")

/**
 * Mesma leitura do desktop (ZIP -> contents -> SQLite), trocando o JDBC pelo
 * SQLiteDatabase do Android. O .jwpub precisa ser copiado para o armazenamento do
 * app porque o SQLite nao abre banco atraves de um content:// URI.
 */
class S34ImportServiceAndroid : S34ImportService {

    override suspend fun pickTalkOutlines(dialogTitle: String, filterLabel: String): List<TalkOutline>? {
        val uri = FilePicker.open(arrayOf("*/*")) ?: return null
        return withContext(Dispatchers.IO) {
            val workDir = File(AndroidApp.context.cacheDir, "s34-import").apply {
                deleteRecursively()
                mkdirs()
            }
            try {
                val jwpub = File(workDir, "publicacao.jwpub")
                AndroidApp.context.contentResolver.openInputStream(uri)?.use { input ->
                    jwpub.outputStream().use { input.copyTo(it) }
                } ?: return@withContext emptyList()

                val contents = File(workDir, "contents")
                if (!extractEntry(jwpub, contents) { it.name == "contents" }) return@withContext emptyList()

                val db = File(workDir, "publication.db")
                if (!extractEntry(contents, db) { it.name.endsWith(".db") }) return@withContext emptyList()

                queryOutlines(db)
            } finally {
                workDir.deleteRecursively()
            }
        }
    }

    private fun extractEntry(zipFile: File, target: File, predicate: (ZipEntry) -> Boolean): Boolean =
        runCatching {
            ZipFile(zipFile).use { zip ->
                val entry = zip.entries().asSequence().firstOrNull(predicate) ?: return false
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                true
            }
        }.getOrDefault(false)

    private fun queryOutlines(db: File): List<TalkOutline> {
        val titles = runCatching {
            SQLiteDatabase.openDatabase(db.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
                database.rawQuery("SELECT Title FROM Document ORDER BY DocumentId", null).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) cursor.getString(0)?.let { add(it) }
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

/** Importacao da apostila: o seletor devolve o PDF e a extracao e a mesma do desktop. */
class MwbImportServiceAndroid : MwbImportService {
    override suspend fun pickPdfText(dialogTitle: String, pdfFilterLabel: String): String? {
        val uri = FilePicker.open(arrayOf("application/pdf")) ?: return null
        return withContext(Dispatchers.IO) {
            val bytes = AndroidApp.context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            MwbTextExtractor.extract(bytes)
        }
    }
}

actual fun createMwbImportService(): MwbImportService = MwbImportServiceAndroid()

actual fun createS34ImportService(): S34ImportService = S34ImportServiceAndroid()
