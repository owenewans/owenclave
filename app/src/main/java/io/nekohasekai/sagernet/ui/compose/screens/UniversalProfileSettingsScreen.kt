package io.nekohasekai.sagernet.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.ui.compose.components.DividerItem
import io.nekohasekai.sagernet.ui.compose.components.OwenclaveTopAppBar
import io.nekohasekai.sagernet.ui.compose.components.PreferenceHeader
import io.nekohasekai.sagernet.ui.compose.components.SectionCard

data class ProfileFieldState(
    val name: String = "",
    val serverAddress: String = "",
    val serverPort: String = "",
    val iconIndex: Int = -1,
    val username: String = "",
    val password: String = "",
    val uuid: String = "",
    val method: String = "",
    val encryption: String = "",
    val flow: String = "",
    val network: String = "tcp",
    val security: String = "none",
    val sni: String = "",
    val alpn: String = "",
    val host: String = "",
    val path: String = "",
    val allowInsecure: Boolean = false,
    val utlsFingerprint: String = "",
    val realityPublicKey: String = "",
    val realityShortId: String = "",
    val echEnabled: Boolean = false,
    val echConfig: String = "",
    val certificates: String = "",
    val congestionControl: String = "bbr",
    val uploadSpeed: String = "",
    val downloadSpeed: String = "",
    val serverPorts: String = "",
    val obfsType: String = "",
    val obfsPassword: String = "",
    // OLCRTC
    val authProvider: String = "jitsi",
    val transport: String = "datachannel",
    val roomId: String = "",
    val encryptionKey: String = "",
    val dnsServer: String = "8.8.8.8:53",
    val socksHost: String = "127.0.0.1",
    val socksPort: String = "8808",
    // SSH
    val authType: Int = 0,
    val privateKey: String = "",
    val publicKey: String = "",
    // WireGuard
    val localAddress: String = "",
    val privateKeyWg: String = "",
    val peerPublicKey: String = "",
    val peerPreSharedKey: String = "",
    val mtu: String = "1420",
    val reserved: String = "",
    // Snell
    val psk: String = "",
    val snellVersion: Int = 4,
    // SSR
    val ssrProtocol: String = "origin",
    val protocolParam: String = "",
    val obfs: String = "plain",
    val obfsParam: String = "",
    // Config
    val configContent: String = "{}",
    val configType: String = "v2ray",
    // Misc
    val singUot: Boolean = false,
    val singMux: Boolean = false,
    val mux: Boolean = false,
    val disableSNI: Boolean = false,
    val zeroRTT: Boolean = false,
    val noPostQuantum: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalProfileSettingsScreen(
    profileType: Int,
    initialState: ProfileFieldState,
    onBack: () -> Unit,
    onSave: (ProfileFieldState) -> Unit,
) {
    var s by remember { mutableStateOf(initialState) }

    val protocolName = when (profileType) {
        ProxyEntity.TYPE_SOCKS -> "SOCKS"
        ProxyEntity.TYPE_HTTP -> "HTTP"
        ProxyEntity.TYPE_SS -> "Shadowsocks"
        ProxyEntity.TYPE_SSR -> "ShadowsocksR"
        ProxyEntity.TYPE_VMESS -> "VMess"
        ProxyEntity.TYPE_VLESS -> "VLESS"
        ProxyEntity.TYPE_TROJAN -> "Trojan"
        ProxyEntity.TYPE_NAIVE -> "NaiveProxy"
        ProxyEntity.TYPE_HYSTERIA2 -> "Hysteria 2"
        ProxyEntity.TYPE_SSH -> "SSH"
        ProxyEntity.TYPE_WG -> "WireGuard"
        ProxyEntity.TYPE_MIERU -> "Mieru"
        ProxyEntity.TYPE_TUIC5 -> "TUIC"
        ProxyEntity.TYPE_JUICITY -> "Juicity"
        ProxyEntity.TYPE_HTTP3 -> "HTTP/3"
        ProxyEntity.TYPE_ANYTLS -> "AnyTLS"
        ProxyEntity.TYPE_SHADOWQUIC -> "ShadowQUIC"
        ProxyEntity.TYPE_TRUSTTUNNEL -> "TrustTunnel"
        ProxyEntity.TYPE_SNELL -> "Snell"
        ProxyEntity.TYPE_OLCRTC -> "OLCRTC"
        ProxyEntity.TYPE_CHAIN -> "Chain"
        ProxyEntity.TYPE_CONFIG -> "Custom Config"
        ProxyEntity.TYPE_BALANCER -> "Balancer"
        else -> "Unknown"
    }

    val showServerAddress = profileType !in listOf(
        ProxyEntity.TYPE_OLCRTC, ProxyEntity.TYPE_CONFIG,
        ProxyEntity.TYPE_CHAIN, ProxyEntity.TYPE_BALANCER,
    )

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            OwenclaveTopAppBar(
                title = "$protocolName Settings",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    Button(onClick = { onSave(s) }) { Text("Save") }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                SectionCard {
                    ProfileTextField(
                        label = "Profile Name",
                        value = s.name,
                        onValueChange = { s = s.copy(name = it) },
                    )
                    DividerItem()
                    ProfileIconPicker(
                        selected = s.iconIndex,
                        onSelect = { s = s.copy(iconIndex = it) },
                    )
                    if (showServerAddress) {
                        DividerItem()
                        ProfileTextField(
                            label = "Server Address",
                            value = s.serverAddress,
                            onValueChange = { s = s.copy(serverAddress = it) },
                        )
                        DividerItem()
                        if (profileType == ProxyEntity.TYPE_HYSTERIA2) {
                            ProfileTextField(
                                label = "Server Ports (e.g. 1080 or 1000-2000)",
                                value = s.serverPorts,
                                onValueChange = { s = s.copy(serverPorts = it) },
                            )
                        } else {
                            ProfileTextField(
                                label = "Server Port",
                                value = s.serverPort,
                                onValueChange = { s = s.copy(serverPort = it.filter { c -> c.isDigit() }) },
                                keyboardType = KeyboardType.Number,
                            )
                        }
                    }
                }

                when (profileType) {
                    ProxyEntity.TYPE_OLCRTC -> OlcrtcFields(s) { s = it }
                    ProxyEntity.TYPE_SOCKS -> SocksFields(s) { s = it }
                    ProxyEntity.TYPE_HTTP -> HttpFields(s) { s = it }
                    ProxyEntity.TYPE_SS -> ShadowsocksFields(s) { s = it }
                    ProxyEntity.TYPE_SSR -> ShadowsocksRFields(s) { s = it }
                    ProxyEntity.TYPE_VMESS -> VmessFields(s) { s = it }
                    ProxyEntity.TYPE_VLESS -> VlessFields(s) { s = it }
                    ProxyEntity.TYPE_TROJAN -> TrojanFields(s) { s = it }
                    ProxyEntity.TYPE_NAIVE -> NaiveFields(s) { s = it }
                    ProxyEntity.TYPE_HYSTERIA2 -> Hysteria2Fields(s) { s = it }
                    ProxyEntity.TYPE_SSH -> SshFields(s) { s = it }
                    ProxyEntity.TYPE_WG -> WireGuardFields(s) { s = it }
                    ProxyEntity.TYPE_MIERU -> MieruFields(s) { s = it }
                    ProxyEntity.TYPE_TUIC5 -> Tuic5Fields(s) { s = it }
                    ProxyEntity.TYPE_JUICITY -> JuicityFields(s) { s = it }
                    ProxyEntity.TYPE_HTTP3 -> Http3Fields(s) { s = it }
                    ProxyEntity.TYPE_ANYTLS -> AnyTlsFields(s) { s = it }
                    ProxyEntity.TYPE_SHADOWQUIC -> ShadowQuicFields(s) { s = it }
                    ProxyEntity.TYPE_TRUSTTUNNEL -> TrustTunnelFields(s) { s = it }
                    ProxyEntity.TYPE_SNELL -> SnellFields(s) { s = it }
                    ProxyEntity.TYPE_CONFIG -> ConfigFields(s) { s = it }
                }

                if (profileType in listOf(
                        ProxyEntity.TYPE_VMESS, ProxyEntity.TYPE_VLESS,
                        ProxyEntity.TYPE_TROJAN, ProxyEntity.TYPE_SS,
                    )
                ) {
                    PreferenceHeader("Advanced")
                    SectionCard {
                        ProfileSwitchItem(
                            title = "Mux",
                            subtitle = "Multiplex connections",
                            checked = s.mux,
                            onCheckedChange = { s = s.copy(mux = it) },
                        )
                        DividerItem()
                        ProfileSwitchItem(
                            title = "UDP over TCP",
                            checked = s.singUot,
                            onCheckedChange = { s = s.copy(singUot = it) },
                        )
                        DividerItem()
                        ProfileSwitchItem(
                            title = "Sing Mux",
                            checked = s.singMux,
                            onCheckedChange = { s = s.copy(singMux = it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit = {},
) {
    io.nekohasekai.sagernet.ui.compose.components.ExpressiveTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = singleLine,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
    )
}

@Composable
private fun ProfileSwitchItem(
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun OlcrtcFields(s: ProfileFieldState, update: (ProfileFieldState) -> Unit) {
    PreferenceHeader("OLCRTC Settings")
    SectionCard {
        ProfileTextField("Auth Provider", s.authProvider) { update(s.copy(authProvider = it)) }
        DividerItem()
        ProfileTextField("Transport", s.transport) { update(s.copy(transport = it)) }
        DividerItem()
        ProfileTextField("Room ID", s.roomId) { update(s.copy(roomId = it)) }
        DividerItem()
        ProfileTextField("Encryption Key", s.encryptionKey, password = true) { update(s.copy(encryptionKey = it)) }
        DividerItem()
        ProfileTextField("DNS Server", s.dnsServer) { update(s.copy(dnsServer = it)) }
        DividerItem()
        ProfileTextField("SOCKS Host", s.socksHost) { update(s.copy(socksHost = it)) }
        DividerItem()
        ProfileTextField("SOCKS Port", s.socksPort, keyboardType = KeyboardType.Number) { update(s.copy(socksPort = it.filter { c -> c.isDigit() })) }
    }
}

@Composable
private fun SocksFields(s: ProfileFieldState, update: (ProfileFieldState) -> Unit) {
    PreferenceHeader("Authentication")
    SectionCard {
        ProfileTextField("Username", s.username) { update(s.copy(username = it)) }
        DividerItem()
        ProfileTextField("Password", s.password, password = true) { update(s.copy(password = it)) }
    }
    TlsTransportFields(s, update)
}

@Composable
private fun HttpFields(s: ProfileFieldState, update: (ProfileFieldState) -> Unit) {
    PreferenceHeader("Authentication")
    SectionCard {
        ProfileTextField("Username", s.username) { update(s.copy(username = it)) }
        DividerItem()
        ProfileTextField("Password", s.password, password = true) { update(s.copy(password = it)) }
    }
    TlsTransportFields(s, update)
}

@Composable
private fun ShadowsocksFields(s: ProfileFieldState, update: (ProfileFieldState) -> Unit) {
    PreferenceHeader("Encryption")
    SectionCard {
        ProfileTextField("Encryption Method", s.method) { update(s.copy(method = it)) }
        DividerItem()
        ProfileTextField("Password", s.password, password = true) { update(s.copy(password = it)) }
    }
    TlsTransportFields(s, update)
}

@Composable
private fun ShadowsocksRFields(s: ProfileFieldState, update: (ProfileFieldState) -> Unit) {
    PreferenceHeader("ShadowsocksR Settings")
    SectionCard {
        ProfileTextField("Password", s.password, password = true) { update(s.copy(password = it)) }
        DividerItem()
        ProfileTextField("Method", s.method) { update(s.copy(method = it)) }
        DividerItem()
        ProfileTextField("Protocol", s.ssrProtocol) { update(s.copy(ssrProtocol = it)) }
        DividerItem()
        ProfileTextField("Protocol Param", s.protocolParam) { update(s.copy(protocolParam = it)) }
        DividerItem()
        ProfileTextField("Obfs", s.obfs) { update(s.copy(obfs = it)) }
        DividerItem()
        ProfileTextField("Obfs Param", s.obfsParam) { update(s.copy(obfsParam = it)) }
    }
}

@Composable
private fun VmessFields(s: ProfileFieldState, update: (ProfileFieldState) -> Unit) {
    PreferenceHeader("Authentication")
    SectionCard {
        ProfileTextField("UUID", s.uuid, password = true) { update(s.copy(uuid = it)) }
        DividerItem()
        ProfileTextField("Encryption", s.encryption) { update(s.copy(encryption = it)) }
        DividerItem()
        ProfileTextField("Alter ID", s.serverPort) { update(s.copy(serverPort = it)) }
    }
    TlsTransportFields(s, update)
}

@Composable
private fun VlessFields(s: ProfileFieldState, update: (ProfileFieldState) -> Unit) {
    PreferenceHeader("Authentication")
    SectionCard {
        ProfileTextField("UUID", s.uuid, password = true) { update(s.copy(uuid = it)) }
        DividerItem()
        ProfileTextField("Flow", s.flow) { update(s.copy(flow = it)) }
        DividerItem()
        ProfileTextField("Encryption", s.encryption) { update(s.copy(encryption = it)) }
    }
    TlsTransportFields(s, update)
}

@Composable
private fun TrojanFields(s: ProfileFieldState, update: (ProfileFieldState) -> Unit) {
    PreferenceHeader("Authentication")
    SectionCard {
        ProfileTextField("Password", s.password, password = true) { update(s.copy(password = it)) }
    }
    TlsTransportFields(s, update)
}

@Composable
private fun NaiveFields(s: ProfileFieldState, update: (ProfileFieldState) -> Unit) {
    PreferenceHeader("NaiveProxy Settings")
    SectionCard {
        ProfileTextField("Protocol (https/quic)", s.network) { update(s.copy(network = it)) }
        DividerItem()
        ProfileTextField("Username", s.username) { update(s.copy(username = it)) }
        DividerItem()
        ProfileTextField("Password", s.password, password = true) { update(s.copy(password = it)) }
        DividerItem()
        ProfileTextField("SNI", s.sni) { update(s.copy(sni = it)) }
        DividerItem()
        ProfileTextField("Certificate", s.certificates) { update(s.copy(certificates = it)) }
    }
}

@Composable
private fun Hysteria2Fields(s: ProfileFieldState, update: (ProfileFieldState) -> Unit) {
    PreferenceHeader("Hysteria 2 Settings")
    SectionCard {
        ProfileTextField("Password / Auth", s.password, password = true) { update(s.copy(password = it)) }
        DividerItem()
        ProfileTextField("SNI", s.sni) { update(s.copy(sni = it)) }
        DividerItem()
        ProfileTextField("Obfs Type (none/salamander/gecko)", s.obfsType) { update(s.copy(obfsType = it)) }
        if (s.obfsType.isNotEmpty()) {
            DividerItem()
            ProfileTextField("Obfs Password", s.obfsPassword, password = true) { update(s.copy(obfsPassword = it)) }
        }
        DividerItem()
        ProfileTextField("Upload Speed (Mbps)", s.uploadSpeed) { update(s.copy(uploadSpeed = it)) }
        DividerItem()
        ProfileTextField("Download Speed (Mbps)", s.downloadSpeed) { update(s.copy(downloadSpeed = it)) }
        DividerItem()
        ProfileTextField("Congestion Control", s.congestionControl) { update(s.copy(congestionControl = it)) }
        DividerItem()
        ProfileTextField("Certificates", s.certificates) { update(s.copy(certificates = it)) }
    }
    PreferenceHeader("TLS")
    SectionCard {
        ProfileSwitchItem("Allow Insecure", s.allowInsecure) { update(s.copy(allowInsecure = it)) }
        DividerItem()
        ProfileSwitchItem("ECH Enabled", s.echEnabled) { update(s.copy(echEnabled = it)) }
        if (s.echEnabled) {
            DividerItem()
            ProfileTextField("ECH Config", s.echConfig) { update(s.copy(echConfig = it)) }
        }
    }
}

@Composable
private fun SshFields(s: ProfileFieldState, update: (ProfileFieldState) -> Unit) {
    PreferenceHeader("SSH Settings")
    SectionCard {
        ProfileTextField("Username", s.username) { update(s.copy(username = it)) }
        DividerItem()
        ProfileTextField("Auth Type (0=none,1=password,2=publicKey)", s.authType.toString()) {
            update(s.copy(authType = it.toIntOrNull() ?: 0))
        }
        if (s.authType == 1) {
            DividerItem()
            ProfileTextField("Password", s.password, password = true) { update(s.copy(password = it)) }
        }
        if (s.authType == 2) {
            DividerItem()
            ProfileTextField("Private Key", s.privateKey, password = true) { update(s.copy(privateKey = it)) }
            DividerItem()
            ProfileTextField("Private Key Passphrase", s.password, password = true) { update(s.copy(password = it)) }
        }
        DividerItem()
        ProfileTextField("Public Key (host key pinning)", s.publicKey) { update(s.copy(publicKey = it)) }
    }
}

@Composable
private fun WireGuardFields(s: ProfileFieldState, update: (ProfileFieldState) -> Unit) {
    PreferenceHeader("WireGuard Settings")
    SectionCard {
        ProfileTextField("Local Address", s.localAddress) { update(s.copy(localAddress = it)) }
        DividerItem()
        ProfileTextField("Private Key", s.privateKeyWg, password = true) { update(s.copy(privateKeyWg = it)) }
        DividerItem()
        ProfileTextField("Peer Public Key", s.peerPublicKey) { update(s.copy(peerPublicKey = it)) }
        DividerItem()
        ProfileTextField("Peer Pre-Shared Key", s.peerPreSharedKey, password = true) { update(s.copy(peerPreSharedKey = it)) }
        DividerItem()
        ProfileTextField("MTU", s.mtu) { update(s.copy(mtu = it)) }
        DividerItem()
        ProfileTextField("Reserved", s.reserved) { update(s.copy(reserved = it)) }
    }
}

@Composable
private fun MieruFields(s: ProfileFieldState, update: (ProfileFieldState) -> Unit) {
    PreferenceHeader("Mieru Settings")
    SectionCard {
        ProfileTextField("Username", s.username) { update(s.copy(username = it)) }
        DividerItem()
        ProfileTextField("Password", s.password, password = true) { update(s.copy(password = it)) }
        DividerItem()
        ProfileTextField("Protocol (0=TCP,1=UDP)", s.network) { update(s.copy(network = it)) }
        DividerItem()
        ProfileTextField("Port Range (optional)", s.serverPorts) { update(s.copy(serverPorts = it)) }
    }
}

@Composable
private fun Tuic5Fields(s: ProfileFieldState, update: (ProfileFieldState) -> Unit) {
    PreferenceHeader("TUIC Settings")
    SectionCard {
        ProfileTextField("UUID", s.uuid, password = true) { update(s.copy(uuid = it)) }
        DividerItem()
        ProfileTextField("Password", s.password, password = true) { update(s.copy(password = it)) }
        DividerItem()
        ProfileTextField("SNI", s.sni) { update(s.copy(sni = it)) }
        DividerItem()
        ProfileTextField("ALPN", s.alpn) { update(s.copy(alpn = it)) }
        DividerItem()
        ProfileTextField("UDP Relay Mode (native/quic)", s.congestionControl) { update(s.copy(congestionControl = it)) }
        DividerItem()
        ProfileTextField("Congestion Control", s.congestionControl) { update(s.copy(congestionControl = it)) }
        DividerItem()
        ProfileTextField("Certificates", s.certificates) { update(s.copy(certificates = it)) }
    }
    PreferenceHeader("TLS")
    SectionCard {
        ProfileSwitchItem("Allow Insecure", s.allowInsecure) { update(s.copy(allowInsecure = it)) }
        DividerItem()
        ProfileSwitchItem("Disable SNI", s.disableSNI) { update(s.copy(disableSNI = it)) }
        DividerItem()
        ProfileSwitchItem("Zero RTT", s.zeroRTT) { update(s.copy(zeroRTT = it)) }
        DividerItem()
        ProfileSwitchItem("ECH Enabled", s.echEnabled) { update(s.copy(echEnabled = it)) }
        if (s.echEnabled) {
            DividerItem()
            ProfileTextField("ECH Config", s.echConfig) { update(s.copy(echConfig = it)) }
        }
    }
}

@Composable
private fun JuicityFields(s: ProfileFieldState, update: (ProfileFieldState) -> Unit) {
    PreferenceHeader("Juicity Settings")
    SectionCard {
        ProfileTextField("UUID", s.uuid, password = true) { update(s.copy(uuid = it)) }
        DividerItem()
        ProfileTextField("Password", s.password, password = true) { update(s.copy(password = it)) }
        DividerItem()
        ProfileTextField("SNI", s.sni) { update(s.copy(sni = it)) }
        DividerItem()
        ProfileTextField("Certificates", s.certificates) { update(s.copy(certificates = it)) }
    }
    PreferenceHeader("TLS")
    SectionCard {
        ProfileSwitchItem("Allow Insecure", s.allowInsecure) { update(s.copy(allowInsecure = it)) }
        DividerItem()
        ProfileSwitchItem("ECH Enabled", s.echEnabled) { update(s.copy(echEnabled = it)) }
        if (s.echEnabled) {
            DividerItem()
            ProfileTextField("ECH Config", s.echConfig) { update(s.copy(echConfig = it)) }
        }
    }
}

@Composable
private fun Http3Fields(s: ProfileFieldState, update: (ProfileFieldState) -> Unit) {
    PreferenceHeader("HTTP/3 Settings")
    SectionCard {
        ProfileTextField("Username", s.username) { update(s.copy(username = it)) }
        DividerItem()
        ProfileTextField("Password", s.password, password = true) { update(s.copy(password = it)) }
        DividerItem()
        ProfileTextField("SNI", s.sni) { update(s.copy(sni = it)) }
        DividerItem()
        ProfileTextField("Certificates", s.certificates) { update(s.copy(certificates = it)) }
    }
    PreferenceHeader("TLS")
    SectionCard {
        ProfileSwitchItem("Allow Insecure", s.allowInsecure) { update(s.copy(allowInsecure = it)) }
        DividerItem()
        ProfileSwitchItem("ECH Enabled", s.echEnabled) { update(s.copy(echEnabled = it)) }
        if (s.echEnabled) {
            DividerItem()
            ProfileTextField("ECH Config", s.echConfig) { update(s.copy(echConfig = it)) }
        }
    }
}

@Composable
private fun AnyTlsFields(s: ProfileFieldState, update: (ProfileFieldState) -> Unit) {
    PreferenceHeader("AnyTLS Settings")
    SectionCard {
        ProfileTextField("Password", s.password, password = true) { update(s.copy(password = it)) }
        DividerItem()
        ProfileTextField("Security (tls/reality)", s.security) { update(s.copy(security = it)) }
        DividerItem()
        ProfileTextField("SNI", s.sni) { update(s.copy(sni = it)) }
        DividerItem()
        ProfileTextField("ALPN", s.alpn) { update(s.copy(alpn = it)) }
        DividerItem()
        ProfileTextField("Certificates", s.certificates) { update(s.copy(certificates = it)) }
        DividerItem()
        ProfileTextField("uTLS Fingerprint", s.utlsFingerprint) { update(s.copy(utlsFingerprint = it)) }
    }
    if (s.security == "reality") {
        PreferenceHeader("Reality")
        SectionCard {
            ProfileTextField("Reality Public Key", s.realityPublicKey, password = true) { update(s.copy(realityPublicKey = it)) }
            DividerItem()
            ProfileTextField("Reality Short ID", s.realityShortId) { update(s.copy(realityShortId = it)) }
        }
    }
    PreferenceHeader("TLS")
    SectionCard {
        ProfileSwitchItem("Allow Insecure", s.allowInsecure) { update(s.copy(allowInsecure = it)) }
        DividerItem()
        ProfileSwitchItem("ECH Enabled", s.echEnabled) { update(s.copy(echEnabled = it)) }
        if (s.echEnabled) {
            DividerItem()
            ProfileTextField("ECH Config", s.echConfig) { update(s.copy(echConfig = it)) }
        }
    }
}

@Composable
private fun ShadowQuicFields(s: ProfileFieldState, update: (ProfileFieldState) -> Unit) {
    PreferenceHeader("ShadowQUIC Settings")
    SectionCard {
        ProfileTextField("Username", s.username) { update(s.copy(username = it)) }
        DividerItem()
        ProfileTextField("Password", s.password, password = true) { update(s.copy(password = it)) }
        DividerItem()
        ProfileTextField("SNI", s.sni) { update(s.copy(sni = it)) }
        DividerItem()
        ProfileTextField("ALPN", s.alpn) { update(s.copy(alpn = it)) }
        DividerItem()
        ProfileTextField("Congestion Control", s.congestionControl) { update(s.copy(congestionControl = it)) }
    }
    PreferenceHeader("Options")
    SectionCard {
        ProfileSwitchItem("Zero RTT", s.zeroRTT) { update(s.copy(zeroRTT = it)) }
        DividerItem()
        ProfileSwitchItem("Disable ALPN", s.disableSNI) { update(s.copy(disableSNI = it)) }
    }
}

@Composable
private fun TrustTunnelFields(s: ProfileFieldState, update: (ProfileFieldState) -> Unit) {
    PreferenceHeader("TrustTunnel Settings")
    SectionCard {
        ProfileTextField("Protocol (https/socks)", s.network) { update(s.copy(network = it)) }
        DividerItem()
        ProfileTextField("Username", s.username) { update(s.copy(username = it)) }
        DividerItem()
        ProfileTextField("Password", s.password, password = true) { update(s.copy(password = it)) }
        DividerItem()
        ProfileTextField("SNI", s.sni) { update(s.copy(sni = it)) }
        DividerItem()
        ProfileTextField("Certificate", s.certificates) { update(s.copy(certificates = it)) }
        DividerItem()
        ProfileTextField("uTLS Fingerprint", s.utlsFingerprint) { update(s.copy(utlsFingerprint = it)) }
    }
    PreferenceHeader("TLS")
    SectionCard {
        ProfileSwitchItem("Allow Insecure", s.allowInsecure) { update(s.copy(allowInsecure = it)) }
        DividerItem()
        ProfileSwitchItem("ECH Enabled", s.echEnabled) { update(s.copy(echEnabled = it)) }
        if (s.echEnabled) {
            DividerItem()
            ProfileTextField("ECH Config", s.echConfig) { update(s.copy(echConfig = it)) }
        }
    }
}

@Composable
private fun SnellFields(s: ProfileFieldState, update: (ProfileFieldState) -> Unit) {
    PreferenceHeader("Snell Settings")
    SectionCard {
        ProfileTextField("Version (4 or 6)", s.snellVersion.toString()) {
            update(s.copy(snellVersion = it.toIntOrNull() ?: 4))
        }
        DividerItem()
        ProfileTextField("PSK", s.psk, password = true) { update(s.copy(psk = it)) }
        DividerItem()
        ProfileTextField("Obfs Mode", s.obfsType) { update(s.copy(obfsType = it)) }
        DividerItem()
        ProfileTextField("Obfs Host", s.host) { update(s.copy(host = it)) }
    }
}

@Composable
private fun ConfigFields(s: ProfileFieldState, update: (ProfileFieldState) -> Unit) {
    PreferenceHeader("Custom Config")
    SectionCard {
        ProfileTextField("Config Type (v2ray/v2ray_outbound)", s.configType) { update(s.copy(configType = it)) }
        DividerItem()
        ProfileTextField(
            label = "Config Content (JSON)",
            value = s.configContent,
            onValueChange = { update(s.copy(configContent = it)) },
            singleLine = false,
        )
        if (s.configType == "v2ray_outbound") {
            DividerItem()
            ProfileTextField("Server Address", s.serverAddress) { update(s.copy(serverAddress = it)) }
        }
    }
}

@Composable
private fun TlsTransportFields(s: ProfileFieldState, update: (ProfileFieldState) -> Unit) {
    PreferenceHeader("Transport")
    SectionCard {
        ProfileTextField("Network (tcp/ws/grpc/quic/kcp/splithttp/httpupgrade)", s.network) { update(s.copy(network = it)) }
        DividerItem()
        ProfileTextField("Security (none/tls/reality)", s.security) { update(s.copy(security = it)) }
        if (s.network == "ws" || s.network == "splithttp" || s.network == "httpupgrade") {
            DividerItem()
            ProfileTextField("Path", s.path) { update(s.copy(path = it)) }
            DividerItem()
            ProfileTextField("Host", s.host) { update(s.copy(host = it)) }
        }
        if (s.network == "grpc") {
            DividerItem()
            ProfileTextField("gRPC Service Name", s.path) { update(s.copy(path = it)) }
        }
    }
    if (s.security == "tls" || s.security == "reality") {
        PreferenceHeader("TLS")
        SectionCard {
            ProfileTextField("SNI", s.sni) { update(s.copy(sni = it)) }
            DividerItem()
            ProfileTextField("ALPN", s.alpn) { update(s.copy(alpn = it)) }
            DividerItem()
            ProfileTextField("uTLS Fingerprint", s.utlsFingerprint) { update(s.copy(utlsFingerprint = it)) }
            DividerItem()
            ProfileTextField("Certificates", s.certificates) { update(s.copy(certificates = it)) }
            DividerItem()
            ProfileSwitchItem("Allow Insecure", s.allowInsecure) { update(s.copy(allowInsecure = it)) }
            DividerItem()
            ProfileSwitchItem("ECH Enabled", s.echEnabled) { update(s.copy(echEnabled = it)) }
            if (s.echEnabled) {
                DividerItem()
                ProfileTextField("ECH Config", s.echConfig) { update(s.copy(echConfig = it)) }
            }
        }
    }
    if (s.security == "reality") {
        PreferenceHeader("Reality")
        SectionCard {
            ProfileTextField("Reality Public Key", s.realityPublicKey, password = true) { update(s.copy(realityPublicKey = it)) }
            DividerItem()
            ProfileTextField("Reality Short ID", s.realityShortId) { update(s.copy(realityShortId = it)) }
        }
    }
}

@Composable
private fun ProfileIconPicker(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentIcon = if (selected in io.nekohasekai.sagernet.ui.compose.components.ProfileIconSet.indices)
        io.nekohasekai.sagernet.ui.compose.components.ProfileIconSet[selected]
    else
        io.nekohasekai.sagernet.ui.compose.components.ProfileIconSet[0]

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            io.nekohasekai.sagernet.ui.compose.components.ShapedIconStatic(
                icon = currentIcon,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                size = 40.dp,
            )
            Spacer(Modifier.padding(start = 16.dp))
            Text(
                text = "Icon",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.padding(12.dp).width(220.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                io.nekohasekai.sagernet.ui.compose.components.ProfileIconSet.forEachIndexed { idx, icon ->
                    val isSelected = idx == selected
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(
                                if (isSelected) androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                else androidx.compose.foundation.shape.CircleShape
                            )
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            .clickable { onSelect(idx); expanded = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}
