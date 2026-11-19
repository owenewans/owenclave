package android.content

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.DesktopEnv
import io.nekohasekai.sagernet.DesktopResourceLoader
import io.nekohasekai.sagernet.SagerNet
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList

open class Context {
    companion object {
        const val MODE_PRIVATE = 0
        const val BIND_AUTO_CREATE = 1
        const val RECEIVER_EXPORTED = 2
        const val RECEIVER_NOT_EXPORTED = 4
        const val CLIPBOARD_SERVICE = "clipboard"
        const val CONNECTIVITY_SERVICE = "connectivity"
        const val NOTIFICATION_SERVICE = "notification"
        const val ACTIVITY_SERVICE = "activity"
        const val UI_MODE_SERVICE = "uimode"
        const val POWER_SERVICE = "power"
        const val WIFI_SERVICE = "wifi"
        const val LOCATION_SERVICE = "location"
        const val LOCALE_SERVICE = "locale"
        const val INPUT_METHOD_SERVICE = "input_method"

        private data class RegisteredReceiver(
            val receiver: BroadcastReceiver,
            val filter: IntentFilter,
        )

        private val receivers = CopyOnWriteArrayList<RegisteredReceiver>()

        @Volatile
        var activityStarter: ((Context, Intent) -> Unit)? = null

        @Volatile
        var serviceStarter: ((Context, Intent) -> Unit)? = null

        @Volatile
        var serviceBinder: ((Context, Intent, ServiceConnection, Int) -> Boolean)? = null

        @JvmStatic
        fun dispatchBroadcast(context: Context, intent: Intent) {
            val action = intent.action
            for (reg in receivers) {
                if (action == null || reg.filter.hasAction(action)) {
                    runCatching { reg.receiver.onReceive(context, intent) }
                }
            }
        }
    }

    open val filesDir: File
        get() = DesktopEnv.dataDir.apply { mkdirs() }

    open val noBackupFilesDir: File
        get() = File(DesktopEnv.dataDir, "no_backup").apply { mkdirs() }

    open val cacheDir: File
        get() {
            val defaultDir = File(DesktopEnv.dataDir, "cache")
            val osName = System.getProperty("os.name", "").lowercase()
            val isMac = osName.contains("mac") || osName.contains("darwin")
            val isWin = osName.contains("win")
            // `ipc.sock` and `ipc_dns.sock` live in `cacheDir`; macOS XNU limits `sockaddr_un.sun_path`
            // to 104 bytes (108 on Linux/Windows), which `/var/folders/...` or long usernames exceed.
            val dir = when {
                !isWin && (isMac || defaultDir.absolutePath.length > 65) -> {
                    val hash = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(DesktopEnv.dataDir.absolutePath.toByteArray(Charsets.UTF_8))
                        .take(6)
                        .joinToString("") { "%02x".format(it) }
                    File("/tmp", "owc-$hash")
                }
                isWin && defaultDir.absolutePath.length > 75 -> {
                    val hash = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(DesktopEnv.dataDir.absolutePath.toByteArray(Charsets.UTF_8))
                        .take(6)
                        .joinToString("") { "%02x".format(it) }
                    File(System.getProperty("java.io.tmpdir", "C:\\Temp"), "owc-$hash")
                }
                else -> defaultDir
            }
            dir.mkdirs()
            return dir
        }

    open val externalCacheDir: File?
        get() = File(DesktopEnv.dataDir, "external_cache").apply { mkdirs() }

    open fun getExternalFilesDir(type: String?): File? =
        File(DesktopEnv.dataDir, if (type == null) "external" else "external/$type").apply { mkdirs() }

    open fun getDir(name: String, mode: Int): File =
        File(DesktopEnv.dataDir, "app_$name").apply { mkdirs() }

    open fun getDatabasePath(name: String): File =
        File(DesktopEnv.dataDir, "databases/$name").apply { parentFile?.mkdirs() }

    open val packageName: String
        get() = BuildConfig.APPLICATION_ID

    open fun getApplicationContext(): Context = SagerNet.application

    open val contentResolver: ContentResolver = ContentResolver()

    open val packageManager: PackageManager = PackageManager()

    open val resources: Resources = Resources()

    open val assets: AssetManager = AssetManager()

    open val applicationInfo: ApplicationInfo = ApplicationInfo().apply {
        packageName = BuildConfig.APPLICATION_ID
        nativeLibraryDir = DesktopEnv.dataDir.absolutePath
    }

    open val theme: Resources.Theme = Resources.Theme()

    open fun setTheme(resid: Int) {}

    open fun getString(resId: Int): String = DesktopResourceLoader.getString(resId)

    open fun getString(resId: Int, vararg formatArgs: Any?): String =
        DesktopResourceLoader.getString(resId, *formatArgs)

    open fun getText(resId: Int): CharSequence = getString(resId)

    open fun checkSelfPermission(permission: String): Int = PackageManager.PERMISSION_GRANTED

    open fun createDeviceProtectedStorageContext(): Context = this

    open fun moveDatabaseFrom(sourceContext: Context, name: String): Boolean = true

    open fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter): Intent? {
        if (receiver != null) receivers.add(RegisteredReceiver(receiver, filter))
        return null
    }

    open fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter, flags: Int): Intent? =
        registerReceiver(receiver, filter)

    open fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter,
        broadcastPermission: String?,
        scheduler: Handler?,
    ): Intent? = registerReceiver(receiver, filter)

    open fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter,
        broadcastPermission: String?,
        scheduler: Handler?,
        flags: Int,
    ): Intent? = registerReceiver(receiver, filter)

    open fun unregisterReceiver(receiver: BroadcastReceiver?) {
        if (receiver != null) receivers.removeIf { it.receiver === receiver }
    }

    open fun sendBroadcast(intent: Intent) {
        dispatchBroadcast(this, intent)
    }

    open fun startActivity(intent: Intent) {
        activityStarter?.invoke(this, intent)
    }

    open fun startService(service: Intent): ComponentName? {
        serviceStarter?.invoke(this, service)
        return service.component
    }

    open fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
        return serviceBinder?.invoke(this, service, conn, flags) ?: true
    }

    open fun unbindService(conn: ServiceConnection) {}

    open fun getSystemService(name: String): Any? = null

    open fun <T> getSystemService(serviceClass: Class<T>): T? {
        return runCatching { serviceClass.getDeclaredConstructor().newInstance() }.getOrNull()
    }
}

open class ContextWrapper(open var baseContext: Context = Context()) : Context() {
    open fun attachBaseContext(base: Context) {
        baseContext = base
    }
}

class ComponentName(val packageName: String = "", val className: String = "") {
    constructor(context: Context, cls: Class<*>) : this(context.packageName, cls.name)
}

open class Intent {
    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val ACTION_SHUTDOWN = "android.intent.action.ACTION_SHUTDOWN"
        const val ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
        const val ACTION_MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED"
        const val ACTION_PACKAGE_ADDED = "android.intent.action.PACKAGE_ADDED"
        const val ACTION_PACKAGE_REMOVED = "android.intent.action.PACKAGE_REMOVED"
        const val EXTRA_TEXT = "android.intent.extra.TEXT"
        const val EXTRA_STREAM = "android.intent.extra.STREAM"
        const val FLAG_ACTIVITY_NEW_TASK = 0x10000000
        const val FLAG_ACTIVITY_REORDER_TO_FRONT = 0x00020000
        const val FLAG_GRANT_READ_URI_PERMISSION = 0x00000001

        @JvmStatic
        fun createChooser(target: Intent?, title: CharSequence?): Intent = target ?: Intent()
    }

    var action: String? = null
    var data: Uri? = null
    var type: String? = null
    var flags: Int = 0
    var component: ComponentName? = null
    var targetClass: Class<*>? = null
    private val extras = Bundle()

    constructor()
    constructor(action: String?) {
        this.action = action
    }
    constructor(action: String?, uri: Uri?) {
        this.action = action
        this.data = uri
    }
    constructor(packageContext: Context?, cls: Class<*>) {
        this.targetClass = cls
        this.component = ComponentName(packageContext?.packageName ?: "", cls.name)
    }

    fun setAction(action: String?): Intent {
        this.action = action
        return this
    }

    fun setData(data: Uri?): Intent {
        this.data = data
        return this
    }

    fun setType(type: String?): Intent {
        this.type = type
        return this
    }

    fun setDataAndType(data: Uri?, type: String?): Intent {
        this.data = data
        this.type = type
        return this
    }

    fun setFlags(flags: Int): Intent {
        this.flags = flags
        return this
    }

    fun addFlags(flags: Int): Intent {
        this.flags = this.flags or flags
        return this
    }

    fun setPackage(packageName: String?): Intent = this

    fun putExtra(name: String, value: String?): Intent {
        extras.putString(name, value)
        return this
    }

    fun putExtra(name: String, value: Int): Intent {
        extras.putInt(name, value)
        return this
    }

    fun putExtra(name: String, value: Long): Intent {
        extras.putLong(name, value)
        return this
    }

    fun putExtra(name: String, value: Boolean): Intent {
        extras.putBoolean(name, value)
        return this
    }

    fun putExtra(name: String, value: Any?): Intent {
        extras.map[name] = value
        return this
    }

    fun getStringExtra(name: String): String? = extras.getString(name)
    fun getIntExtra(name: String, defaultValue: Int): Int = extras.getInt(name, defaultValue)
    fun getLongExtra(name: String, defaultValue: Long): Long = extras.getLong(name, defaultValue)
    fun getBooleanExtra(name: String, defaultValue: Boolean): Boolean = extras.getBoolean(name, defaultValue)
    @Suppress("UNCHECKED_CAST")
    fun <T> getParcelableExtra(name: String): T? = extras.get(name) as? T
    fun hasExtra(name: String): Boolean = extras.containsKey(name)
}

class IntentFilter(vararg actions: String) {
    private val actionSet = LinkedHashSet<String>(actions.toList())
    fun addAction(action: String) {
        actionSet.add(action)
    }
    fun addDataScheme(scheme: String) {}
    fun hasAction(action: String): Boolean = actionSet.contains(action)
}

abstract class BroadcastReceiver {
    abstract fun onReceive(context: Context, intent: Intent)
}

interface ServiceConnection {
    fun onServiceConnected(name: ComponentName?, service: IBinder)
    fun onServiceDisconnected(name: ComponentName?)
}

open class ActivityNotFoundException(message: String? = null) : RuntimeException(message)

class ClipData private constructor(private val items: List<Item>) {
    class Item(val text: CharSequence?) {
        fun coerceToText(context: Context?): CharSequence? = text
    }
    val itemCount: Int get() = items.size
    fun getItemAt(index: Int): Item = items[index]

    companion object {
        @JvmStatic
        fun newPlainText(label: CharSequence?, text: CharSequence?): ClipData =
            ClipData(listOf(Item(text)))
    }
}

class ClipboardManager {
    private var fallbackText: String = ""

    val primaryClip: ClipData?
        get() {
            val text = runCatching {
                val cb = Toolkit.getDefaultToolkit().systemClipboard
                cb.getData(DataFlavor.stringFlavor) as? String
            }.getOrNull() ?: fallbackText
            return ClipData.newPlainText(null, text)
        }

    fun setPrimaryClip(clip: ClipData) {
        val text = if (clip.itemCount > 0) clip.getItemAt(0).text?.toString().orEmpty() else ""
        fallbackText = text
        runCatching {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        }
    }
}

