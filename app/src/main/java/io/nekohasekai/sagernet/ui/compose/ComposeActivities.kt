package io.nekohasekai.sagernet.ui.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.ui.compose.screens.AssetsScreen
import io.nekohasekai.sagernet.ui.compose.screens.AssetItem
import io.nekohasekai.sagernet.ui.compose.screens.AppListScreen
import io.nekohasekai.sagernet.ui.compose.screens.AppItem
import io.nekohasekai.sagernet.ui.compose.screens.ConfigEditScreen
import io.nekohasekai.sagernet.ui.compose.screens.GroupSettingsScreen
import io.nekohasekai.sagernet.ui.compose.screens.ProbeCertScreen
import io.nekohasekai.sagernet.ui.compose.screens.ProfileSelectScreen
import io.nekohasekai.sagernet.ui.compose.screens.QuickSwitchScreen
import io.nekohasekai.sagernet.ui.compose.screens.RouteSettingsScreen
import io.nekohasekai.sagernet.ui.compose.screens.ScannerScreen
import io.nekohasekai.sagernet.ui.compose.screens.StunScreen
import io.nekohasekai.sagernet.ui.compose.screens.UniversalProfileSettingsScreen

class ComposeAssetsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OwenclaveTheme {
                AssetsScreen(
                    assets = emptyList(),
                    onBack = { finish() },
                    onAdd = {},
                    onEdit = {},
                    onDelete = {},
                )
            }
        }
    }
}

class ComposeAppListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OwenclaveTheme {
                AppListScreen(
                    apps = emptyList(),
                    loading = false,
                    onBack = { finish() },
                    onToggle = {},
                    onInvert = {},
                    onClear = {},
                    onCopy = {},
                )
            }
        }
    }
}

class ComposeScannerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OwenclaveTheme {
                ScannerScreen(
                    onBack = { finish() },
                    onImportFile = {},
                    onToggleFlash = {},
                    onSwitchCamera = {},
                )
            }
        }
    }
}

class ComposeGroupSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OwenclaveTheme {
                GroupSettingsScreen(
                    groupName = "",
                    groupType = 0,
                    onBack = { finish() },
                    onSave = { _, _ -> finish() },
                )
            }
        }
    }
}

class ComposeRouteSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OwenclaveTheme {
                RouteSettingsScreen(
                    onBack = { finish() },
                    onSave = { finish() },
                )
            }
        }
    }
}

class ComposeConfigEditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = intent.getStringExtra("content") ?: ""
        setContent {
            OwenclaveTheme {
                ConfigEditScreen(
                    initialContent = content,
                    onBack = { finish() },
                    onSave = { finish() },
                )
            }
        }
    }
}

class ComposeStunActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OwenclaveTheme {
                StunScreen(onBack = { finish() })
            }
        }
    }
}

class ComposeProbeCertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OwenclaveTheme {
                ProbeCertScreen(onBack = { finish() })
            }
        }
    }
}

class ComposeProfileSelectActivity : ComponentActivity() {
    companion object {
        const val EXTRA_SELECTED = "selected"
        const val EXTRA_PROFILE_ID = "profileId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val selectedId = intent.getLongExtra(EXTRA_SELECTED, 0L)
        setContent {
            OwenclaveTheme {
                ProfileSelectScreen(
                    profiles = emptyList(),
                    selectedId = selectedId,
                    onSelect = { profile ->
                        setResult(RESULT_OK, intent.putExtra(EXTRA_PROFILE_ID, profile.id))
                        finish()
                    },
                    onBack = { finish() },
                )
            }
        }
    }
}

class ComposeQuickToggleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OwenclaveTheme {
                QuickSwitchScreen(
                    profiles = emptyList(),
                    currentProfileId = DataStore.selectedProxy,
                    onSelect = { profile ->
                        DataStore.selectedProxy = profile.id
                        io.nekohasekai.sagernet.SagerNet.reloadService()
                        finish()
                    },
                )
            }
        }
    }
}

class ComposeProfileSettingsActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PROFILE_ID = "profileId"
        const val EXTRA_PROFILE_TYPE = "profileType"
        const val EXTRA_IS_SUBSCRIPTION = "isSubscription"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profileType = intent.getIntExtra(EXTRA_PROFILE_TYPE, ProxyEntity.TYPE_SOCKS)
        setContent {
            OwenclaveTheme {
                UniversalProfileSettingsScreen(
                    profileType = profileType,
                    profileName = "",
                    serverAddress = "",
                    serverPort = "",
                    onBack = { finish() },
                    onSave = { finish() },
                )
            }
        }
    }
}
