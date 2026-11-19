package androidx.activity.result.contract

import android.net.Uri
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.SwingUtilities

abstract class ActivityResultContract<I, O> {
    abstract fun executeDesktop(input: I, onResult: (O) -> Unit)
}

object ActivityResultContracts {
    class GetContent : ActivityResultContract<String, Uri?>() {
        override fun executeDesktop(input: String, onResult: (Uri?) -> Unit) {
            Thread {
                val dialog = FileDialog(null as Frame?, "Open File", FileDialog.LOAD)
                dialog.isVisible = true
                val dir = dialog.directory
                val file = dialog.file
                val uri = if (dir != null && file != null) {
                    Uri.fromFile(File(dir, file))
                } else {
                    null
                }
                SwingUtilities.invokeLater { onResult(uri) }
            }.start()
        }
    }

    class CreateDocument(private val mimeType: String = "*/*") : ActivityResultContract<String, Uri?>() {
        override fun executeDesktop(input: String, onResult: (Uri?) -> Unit) {
            Thread {
                val dialog = FileDialog(null as Frame?, "Save File", FileDialog.SAVE)
                dialog.file = input
                dialog.isVisible = true
                val dir = dialog.directory
                val file = dialog.file
                val uri = if (dir != null && file != null) {
                    Uri.fromFile(File(dir, file))
                } else {
                    null
                }
                SwingUtilities.invokeLater { onResult(uri) }
            }.start()
        }
    }
}

