package androidx.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DesktopLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.nekohasekai.sagernet.SagerNet
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import javax.swing.SwingUtilities
import kotlinx.coroutines.delay

open class ComponentActivity : Activity(), LifecycleOwner {
    private val desktopLifecycle = DesktopLifecycle(this)
    override val lifecycle: Lifecycle
        get() = desktopLifecycle

    var composeContent by mutableStateOf<(@Composable () -> Unit)?>(null)

    init {
        attachBaseContext(SagerNet.application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        desktopLifecycle.dispatch(Lifecycle.Event.ON_CREATE)
    }

    override fun onStart() {
        super.onStart()
        desktopLifecycle.dispatch(Lifecycle.Event.ON_START)
    }

    override fun onResume() {
        super.onResume()
        desktopLifecycle.dispatch(Lifecycle.Event.ON_RESUME)
    }

    override fun onPause() {
        desktopLifecycle.dispatch(Lifecycle.Event.ON_PAUSE)
        super.onPause()
    }

    override fun onStop() {
        desktopLifecycle.dispatch(Lifecycle.Event.ON_STOP)
        super.onStop()
    }

    override fun onDestroy() {
        desktopLifecycle.dispatch(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun finish() {
        DesktopActivityStack.pop(this)
        super.finish()
    }
}

fun ComponentActivity.enableEdgeToEdge() {}

object DesktopActivityStack {
    class BackCallbackEntry(
        var enabled: Boolean,
        var onBack: () -> Unit,
    )

    val stack = mutableStateListOf<ComponentActivity>()
    val backCallbacks = mutableStateListOf<BackCallbackEntry>()

    init {
        Context.activityStarter = { _, intent ->
            startActivityFromIntent(intent)
        }
    }

    fun dispatchBack(): Boolean {
        val handler = backCallbacks.lastOrNull { it.enabled }
        if (handler != null) {
            handler.onBack()
            return true
        }
        if (stack.size > 1) {
            stack.lastOrNull()?.finish()
            return true
        }
        return false
    }

    fun startActivityFromIntent(intent: Intent) {
        val className = intent.component?.className
        if (className == null) {
            handleImplicitIntent(intent)
            return
        }
        runCatching {
            val clazz = Class.forName(className)
            if (ComponentActivity::class.java.isAssignableFrom(clazz)) {
                val existing = stack.find { it.javaClass.name == className }
                if (existing != null && className.endsWith("ComposeMainActivity")) {
                    while (stack.size > 1 && stack.last() !== existing) {
                        val top = stack.removeLast()
                        top.onStop()
                        top.onDestroy()
                    }
                    existing.onNewIntent(intent)
                    existing.onResume()
                    return
                }
                val activity = clazz.getDeclaredConstructor().newInstance() as ComponentActivity
                activity.intent = intent
                push(activity)
            }
        }
    }

    private fun handleImplicitIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val streamUri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                val srcFile = streamUri?.toFileOrNull()
                if (srcFile != null && srcFile.isFile) {
                    if (!GraphicsEnvironment.isHeadless()) {
                        Thread({
                            runCatching {
                                val dialog = FileDialog(null as Frame?, "Save File", FileDialog.SAVE)
                                dialog.file = srcFile.name
                                dialog.isVisible = true
                                val dir = dialog.directory
                                val file = dialog.file
                                if (dir != null && file != null) {
                                    val dest = File(dir, file)
                                    srcFile.copyTo(dest, overwrite = true)
                                    Toast.makeText(SagerNet.application, "Saved to ${dest.absolutePath}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }, "owenclave-save-share").apply {
                            isDaemon = true
                            start()
                        }
                    }
                    return
                }
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrEmpty()) {
                    runCatching {
                        val selection = StringSelection(text)
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                        Toast.makeText(SagerNet.application, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                val raw = uri.toString().trim()
                if (raw.isEmpty()) return
                val scheme = uri.scheme?.lowercase().orEmpty()
                if (scheme == "http" || scheme == "https" || scheme == "mailto") {
                    openExternalUrl(raw)
                } else {
                    val root = stack.firstOrNull() ?: return
                    while (stack.size > 1) {
                        val top = stack.removeLast()
                        top.onStop()
                        top.onDestroy()
                    }
                    root.onNewIntent(intent)
                    root.onResume()
                }
            }
        }
    }

    private fun openExternalUrl(url: String) {
        Thread({
            val openedByDesktop = runCatching {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(URI(url))
                    true
                } else {
                    false
                }
            }.getOrDefault(false)
            if (!openedByDesktop) {
                val os = System.getProperty("os.name")?.lowercase().orEmpty()
                runCatching {
                    when {
                        os.contains("linux") -> ProcessBuilder("xdg-open", url).start()
                        os.contains("mac") || os.contains("darwin") -> ProcessBuilder("open", url).start()
                        os.contains("win") -> ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start()
                    }
                }
            }
        }, "owenclave-open-url").apply {
            isDaemon = true
            start()
        }
    }

    fun push(activity: ComponentActivity) {
        stack.lastOrNull()?.onPause()
        stack.add(activity)
        activity.onCreate(null)
        activity.onStart()
        activity.onResume()
    }

    fun pop(activity: ComponentActivity) {
        if (stack.size <= 1) return
        if (stack.remove(activity)) {
            activity.onPause()
            activity.onStop()
            activity.onDestroy()
            stack.lastOrNull()?.onResume()
        }
    }

    @Composable
    fun Host(root: ComponentActivity) {
        if (stack.isEmpty()) {
            push(root)
        }
        val active = stack.lastOrNull() ?: root
        var toastMessage by remember { mutableStateOf<String?>(null) }
        var toastSeq by remember { mutableIntStateOf(0) }

        DisposableEffect(Unit) {
            val prevSink = Toast.toastSink
            Toast.toastSink = { msg ->
                if (msg.isNotBlank()) {
                    if (SwingUtilities.isEventDispatchThread()) {
                        toastMessage = msg
                        toastSeq++
                    } else {
                        SwingUtilities.invokeLater {
                            toastMessage = msg
                            toastSeq++
                        }
                    }
                }
            }
            onDispose {
                Toast.toastSink = prevSink
            }
        }

        LaunchedEffect(toastSeq) {
            if (toastMessage != null) {
                delay(3200)
                toastMessage = null
            }
        }

        CompositionLocalProvider(
            LocalContext provides active,
            LocalLifecycleOwner provides active,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusable()
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                            dispatchBack()
                        } else {
                            false
                        }
                    },
            ) {
                active.composeContent?.invoke()

                AnimatedVisibility(
                    visible = toastMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 20.dp, start = 24.dp, end = 24.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp,
                    ) {
                        Text(
                            text = toastMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}
