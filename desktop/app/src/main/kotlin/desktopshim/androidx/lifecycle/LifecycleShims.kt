package androidx.lifecycle

import android.app.Application

class DesktopLifecycle(owner: LifecycleOwner) : Lifecycle() {
    private val delegate = LifecycleRegistry.createUnsafe(owner).apply {
        currentState = State.RESUMED
    }

    override val currentState: State
        get() = delegate.currentState

    override val currentStateFlow
        get() = delegate.currentStateFlow

    override fun addObserver(observer: LifecycleObserver) {
        delegate.addObserver(observer)
    }

    override fun removeObserver(observer: LifecycleObserver) {
        delegate.removeObserver(observer)
    }

    fun dispatch(event: Event) {
        if (event != Event.ON_ANY) {
            delegate.handleLifecycleEvent(event)
        }
    }
}

open class AndroidViewModel(private val app: Application) : ViewModel() {
    @Suppress("UNCHECKED_CAST")
    fun <T : Application> getApplication(): T = app as T
}

open class MutableLiveData<T>(private var _value: T? = null) {
    open var value: T?
        get() = _value
        set(v) {
            _value = v
        }

    open fun postValue(value: T?) {
        _value = value
    }
}

