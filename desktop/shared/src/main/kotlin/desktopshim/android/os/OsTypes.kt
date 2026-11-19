package android.os

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

interface Parcelable {
    fun describeContents(): Int = 0
    fun writeToParcel(dest: Parcel, flags: Int) {
        when (this) {
            is io.nekohasekai.sagernet.database.RuleEntity ->
                io.nekohasekai.sagernet.database.ParcelizeBridge.writeRule(this, dest)
            is io.nekohasekai.sagernet.database.AssetEntity ->
                io.nekohasekai.sagernet.database.ParcelizeBridge.writeAsset(this, dest)
            is io.nekohasekai.sagernet.database.StatsEntity ->
                io.nekohasekai.sagernet.database.ParcelizeBridge.writeStats(this, dest)
            is io.nekohasekai.sagernet.aidl.AppStats ->
                io.nekohasekai.sagernet.database.ParcelizeBridge.writeAppStats(this, dest)
            is io.nekohasekai.sagernet.aidl.TrafficStats ->
                io.nekohasekai.sagernet.database.ParcelizeBridge.writeTrafficStats(this, dest)
            is io.nekohasekai.sagernet.aidl.AppStatsList ->
                io.nekohasekai.sagernet.database.ParcelizeBridge.writeAppStatsList(this, dest)
        }
    }

    interface Creator<T> {
        fun createFromParcel(source: Parcel): T
        fun newArray(size: Int): Array<T?>
    }
}

class Parcel private constructor() {
    private val out = ByteArrayOutputStream()
    private val dataOut = DataOutputStream(out)
    private var inBytes: ByteArray = ByteArray(0)
    private var dataIn: DataInputStream = DataInputStream(ByteArrayInputStream(inBytes))

    companion object {
        @JvmStatic
        fun obtain(): Parcel = Parcel()
    }

    fun recycle() {}

    fun marshall(): ByteArray {
        dataOut.flush()
        return out.toByteArray()
    }

    fun unmarshall(data: ByteArray, offset: Int, length: Int) {
        inBytes = data.copyOfRange(offset, offset + length)
        dataIn = DataInputStream(ByteArrayInputStream(inBytes))
    }

    fun setDataPosition(pos: Int) {
        dataIn = DataInputStream(ByteArrayInputStream(inBytes, pos, inBytes.size - pos))
    }

    fun writeByteArray(b: ByteArray?) {
        if (b == null) {
            dataOut.writeInt(-1)
        } else {
            dataOut.writeInt(b.size)
            dataOut.write(b)
        }
    }

    fun createByteArray(): ByteArray? {
        val len = runCatching { dataIn.readInt() }.getOrDefault(-1)
        if (len < 0) return null
        val b = ByteArray(len)
        dataIn.readFully(b)
        return b
    }

    fun writeString(s: String?) {
        if (s == null) {
            dataOut.writeInt(-1)
        } else {
            val bytes = s.toByteArray(Charsets.UTF_8)
            dataOut.writeInt(bytes.size)
            dataOut.write(bytes)
        }
    }

    fun readString(): String? {
        val len = runCatching { dataIn.readInt() }.getOrDefault(-1)
        if (len < 0) return null
        val b = ByteArray(len)
        dataIn.readFully(b)
        return String(b, Charsets.UTF_8)
    }

    fun writeInt(v: Int) = dataOut.writeInt(v)
    fun readInt(): Int = runCatching { dataIn.readInt() }.getOrDefault(0)
    fun writeLong(v: Long) = dataOut.writeLong(v)
    fun readLong(): Long = runCatching { dataIn.readLong() }.getOrDefault(0L)

    fun writeStringList(list: List<String>?) {
        if (list == null) {
            dataOut.writeInt(-1)
        } else {
            dataOut.writeInt(list.size)
            for (s in list) writeString(s)
        }
    }

    fun createStringArrayList(): ArrayList<String>? {
        val len = runCatching { dataIn.readInt() }.getOrDefault(-1)
        if (len < 0) return null
        val list = ArrayList<String>(len)
        repeat(len) {
            list.add(readString() ?: "")
        }
        return list
    }
}

class Bundle(val map: MutableMap<String, Any?> = HashMap()) {
    fun containsKey(key: String): Boolean = map.containsKey(key)
    fun get(key: String): Any? = map[key]
    fun getString(key: String, defaultValue: String? = null): String? = map[key] as? String ?: defaultValue
    fun putString(key: String, value: String?) { map[key] = value }
    fun getInt(key: String, defaultValue: Int = 0): Int = (map[key] as? Number)?.toInt() ?: defaultValue
    fun putInt(key: String, value: Int) { map[key] = value }
    fun getLong(key: String, defaultValue: Long = 0L): Long = (map[key] as? Number)?.toLong() ?: defaultValue
    fun putLong(key: String, value: Long) { map[key] = value }
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean = map[key] as? Boolean ?: defaultValue
    fun putBoolean(key: String, value: Boolean) { map[key] = value }
    fun putParcelable(key: String, value: Parcelable?) { map[key] = value }
    @Suppress("UNCHECKED_CAST")
    fun <T : Parcelable> getParcelable(key: String): T? = map[key] as? T
}

interface IBinder {
    interface DeathRecipient {
        fun binderDied()
    }
    fun linkToDeath(recipient: DeathRecipient, flags: Int) {}
    fun unlinkToDeath(recipient: DeathRecipient, flags: Int): Boolean = true
}

open class Binder : IBinder {
    companion object {
        @JvmStatic
        fun getCallingUserHandle(): UserHandle = UserHandle()
    }
}

class UserHandle {
    override fun hashCode(): Int = 0
    override fun equals(other: Any?): Boolean = other is UserHandle
}

interface IInterface {
    fun asBinder(): IBinder
}

open class RemoteException(message: String? = null) : Exception(message)

open class RemoteCallbackList<E : IInterface> {
    private val list = CopyOnWriteArrayList<E>()
    private var activeSnapshot: List<E> = emptyList()

    open fun onCallbackDied(callback: E?, cookie: Any?) {}

    fun register(callback: E): Boolean {
        if (!list.contains(callback)) list.add(callback)
        return true
    }

    fun unregister(callback: E): Boolean = list.remove(callback)

    fun beginBroadcast(): Int {
        activeSnapshot = list.toList()
        return activeSnapshot.size
    }

    fun getBroadcastItem(index: Int): E = activeSnapshot[index]

    fun finishBroadcast() {
        activeSnapshot = emptyList()
    }

    fun kill() {
        list.clear()
    }
}

class Looper private constructor(val thread: Thread) {
    companion object {
        private val mainLooper = Looper(Thread.currentThread())
        @JvmStatic
        fun getMainLooper(): Looper = mainLooper
    }
}

class Handler(val looper: Looper = Looper.getMainLooper()) {
    fun post(r: Runnable): Boolean {
        CoroutineScope(Dispatchers.Main.immediate).launch { r.run() }
        return true
    }

    fun postDelayed(r: Runnable, delayMillis: Long): Boolean {
        CoroutineScope(Dispatchers.Main.immediate).launch {
            delay(delayMillis)
            r.run()
        }
        return true
    }
}

object SystemClock {
    @JvmStatic
    fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000L
}

class CancellationSignal {
    @Volatile
    var isCanceled: Boolean = false
        private set

    fun cancel() {
        isCanceled = true
    }
}

class LocaleList(private val locales: List<Locale>) {
    constructor(vararg list: Locale) : this(list.toList())

    operator fun get(index: Int): Locale = locales.getOrElse(index) { Locale.getDefault() }
    fun size(): Int = locales.size
    fun isEmpty(): Boolean = locales.isEmpty()

    companion object {
        @JvmStatic
        fun getDefault(): LocaleList = LocaleList(Locale.getDefault())

        @JvmStatic
        fun getEmptyLocaleList(): LocaleList = LocaleList(emptyList())

        @JvmStatic
        fun forLanguageTags( list: String?): LocaleList {
            if (list.isNullOrBlank()) return getEmptyLocaleList()
            return LocaleList(list.split(',').map { Locale.forLanguageTag(it.trim()) })
        }
    }
}

class PowerManager {
    companion object {
        const val PARTIAL_WAKE_LOCK = 1
    }
    class WakeLock {
        fun acquire() {}
        fun release() {}
    }
    fun newWakeLock(levelAndFlags: Int, tag: String): WakeLock = WakeLock()
}

object StrictMode {
    class VmPolicy {
        class Builder {
            fun detectLeakedSqlLiteObjects() = this
            fun detectLeakedClosableObjects() = this
            fun detectLeakedRegistrationObjects() = this
            fun penaltyLog() = this
            fun build() = VmPolicy()
        }
    }
    @JvmStatic
    fun setVmPolicy(policy: VmPolicy) {}
}
