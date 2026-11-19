package androidx.compose.ui.platform

import android.content.Context
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.LifecycleOwner
import io.nekohasekai.sagernet.SagerNet

val LocalContext = staticCompositionLocalOf<Context> { SagerNet.application }

val LocalLifecycleOwner: ProvidableCompositionLocal<LifecycleOwner>
    get() = androidx.lifecycle.compose.LocalLifecycleOwner

