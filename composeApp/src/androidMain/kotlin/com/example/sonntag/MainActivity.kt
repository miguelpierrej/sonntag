package com.example.sonntag

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.example.sonntag.platform.AndroidApp
import com.example.sonntag.platform.FilePicker

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
    }

    override fun onDestroy() {
        if (AndroidApp.activity === this) AndroidApp.activity = null
        super.onDestroy()
    }
}
