package com.example.sonntag.imports

import java.awt.EventQueue
import java.awt.KeyboardFocusManager
import java.util.concurrent.FutureTask
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Abre o seletor de arquivo e devolve o caminho escolhido, ou null se cancelado.
 *
 * JFileChooser (Swing) navega pastas de forma confiavel em todas as plataformas,
 * ao contrario do java.awt.FileDialog, que no Linux/GTK costuma travar a navegacao.
 */
internal fun chooseOpenPath(title: String, filterLabel: String, extension: String): String? = runOnEdt {
    val parent = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.FILES_ONLY
        isMultiSelectionEnabled = false
        fileFilter = FileNameExtensionFilter("$filterLabel (*.$extension)", extension)
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
