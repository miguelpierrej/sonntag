package com.example.sonntag

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import com.example.sonntag.platform.AndroidApp
import com.example.sonntag.platform.FilePicker
import com.example.sonntag.sync.IncomingPackage

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidApp.activity = this

        // Precisam ser registrados aqui: o contrato exige registro antes de a
        // Activity chegar em STARTED.
        FilePicker.openLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let {
                    // Sem isso a permissao morre junto com a Activity.
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            it,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
                FilePicker.deliver(uri)
            }
        FilePicker.createLauncher =
            registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
                FilePicker.deliver(uri)
            }

        setContent { App() }

        // Arquivo aberto por fora do app (toque num .sonntag ou "compartilhar com").
        recebePacote(intent)
    }

    /** Com o app ja aberto, o arquivo chega por aqui em vez do onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recebePacote(intent)
    }

    /**
     * Le o pacote que veio no Intent e o entrega a tela de Dados.
     *
     * O arquivo e lido aqui, e nao quando a tela aparece: a permissao de leitura vale
     * para este Intent e pode nao sobreviver a navegacao.
     */
    private fun recebePacote(intent: Intent?) {
        val uri: Uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return

        val bytes = runCatching {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return

        IncomingPackage.oferecer(bytes, nomeDoArquivo(uri))
    }

    private fun nomeDoArquivo(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment

    override fun onDestroy() {
        if (AndroidApp.activity === this) AndroidApp.activity = null
        super.onDestroy()
    }
}
