package com.example.sonntag.imports

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.awt.EventQueue
import java.awt.KeyboardFocusManager
import java.io.File
import java.util.concurrent.FutureTask
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

class MwbImportServiceJvm : MwbImportService {

    override fun pickPdfText(): String? {
        val path = chooseOpenPath() ?: return null
        return Loader.loadPDF(File(path)).use { doc ->
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            stripper.getText(doc)
        }
    }

    // JFileChooser (Swing) navega pastas de forma confiavel em todas as plataformas,
    // ao contrario do java.awt.FileDialog, que no Linux/GTK costuma travar a navegacao.
    private fun chooseOpenPath(): String? = runOnEdt {
        val parent = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
        val chooser = JFileChooser().apply {
            dialogTitle = "Selecionar apostila (PDF)"
            fileSelectionMode = JFileChooser.FILES_ONLY
            isMultiSelectionEnabled = false
            fileFilter = FileNameExtensionFilter("Documentos PDF (*.pdf)", "pdf")
        }
        if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.absolutePath
        } else {
            null
        }
    }

    private fun <T> runOnEdt(block: () -> T): T {
        if (EventQueue.isDispatchThread()) return block()
        val task = FutureTask { block() }
        EventQueue.invokeAndWait(task)
        return task.get()
    }
}

actual fun createMwbImportService(): MwbImportService = MwbImportServiceJvm()
