package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import io.nekohasekai.sagernet.DesktopResourceLoader

@Composable
@ReadOnlyComposable
fun stringResource(id: Int): String = DesktopResourceLoader.getString(id)

@Composable
@ReadOnlyComposable
fun stringResource(id: Int, vararg formatArgs: Any): String =
    DesktopResourceLoader.getString(id, *formatArgs)

@Composable
@ReadOnlyComposable
fun stringArrayResource(id: Int): Array<String> = DesktopResourceLoader.getStringArray(id)

