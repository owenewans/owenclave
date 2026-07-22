package io.nekohasekai.sagernet.ui.compose.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.ui.compose.components.DividerItem
import io.nekohasekai.sagernet.ui.compose.components.OwenclaveTopAppBar
import io.nekohasekai.sagernet.ui.compose.components.PreferenceHeader
import io.nekohasekai.sagernet.ui.compose.components.PreferenceItem
import io.nekohasekai.sagernet.ui.compose.components.SectionCard
import io.nekohasekai.sagernet.ui.compose.components.SwitchPreferenceItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalProfileSettingsScreen(
    profileType: Int,
    profileName: String,
    serverAddress: String,
    serverPort: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    var name by remember { mutableStateOf(profileName) }
    var address by remember { mutableStateOf(serverAddress) }
    var port by remember { mutableStateOf(serverPort) }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("") }
    var sni by remember { mutableStateOf("") }
    var alpn by remember { mutableStateOf("") }
    var uuid by remember { mutableStateOf("") }
    var flow by remember { mutableStateOf("") }
    var encryption by remember { mutableStateOf("") }
    var network by remember { mutableStateOf("tcp") }
    var security by remember { mutableStateOf("none") }
    var headerType by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var allowInsecure by remember { mutableStateOf(false) }
    var utlsFingerprint by remember { mutableStateOf("") }
    var realityPublicKey by remember { mutableStateOf("") }
    var realityShortId by remember { mutableStateOf("") }
    var echEnabled by remember { mutableStateOf(false) }
    var echConfig by remember { mutableStateOf("") }
    var mtlsCertificate by remember { mutableStateOf("") }
    var mtlsPrivateKey by remember { mutableStateOf("") }
    var singUot by remember { mutableStateOf(false) }
    var singMux by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            OwenclaveTopAppBar(
                title = "$protocolName Settings",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
                actions = {
                    Button(onClick = onSave) { Text("Save") }
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
                // ── Basic ──
                PreferenceHeader("Basic")
                SectionCard {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Profile Name") },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        singleLine = true,
                    )
                    DividerItem()
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Server Address") },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        singleLine = true,
                    )
                    DividerItem()
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { it.isDigit() } },
                        label = { Text("Server Port") },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        singleLine = true,
                    )
                }

                // ── Authentication (protocol-dependent) ──
                val needsPassword = profileType in listOf(
                    ProxyEntity.TYPE_SOCKS, ProxyEntity.TYPE_HTTP,
                    ProxyEntity.TYPE_SS, ProxyEntity.TYPE_SSR,
                    ProxyEntity.TYPE_TROJAN, ProxyEntity.TYPE_NAIVE,
                    ProxyEntity.TYPE_SNELL, ProxyEntity.TYPE_SHADOWQUIC,
                )
                val needsUsername = profileType in listOf(
                    ProxyEntity.TYPE_SOCKS, ProxyEntity.TYPE_HTTP,
                    ProxyEntity.TYPE_NAIVE, ProxyEntity.TYPE_SNELL,
                )
                val needsMethod = profileType in listOf(
                    ProxyEntity.TYPE_SS, ProxyEntity.TYPE_SSR,
                )
                val needsUUID = profileType in listOf(
                    ProxyEntity.TYPE_VMESS, ProxyEntity.TYPE_VLESS,
                )
                val needsFlow = profileType == ProxyEntity.TYPE_VLESS
                val needsEncryption = profileType == ProxyEntity.TYPE_VLESS

                if (needsPassword || needsUsername || needsMethod || needsUUID || needsFlow || needsEncryption) {
                    PreferenceHeader("Authentication")
                    SectionCard {
                        if (needsUsername) {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("Username") },
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                singleLine = true,
                            )
                            DividerItem()
                        }
                        if (needsPassword) {
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password") },
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                            )
                            DividerItem()
                        }
                        if (needsMethod) {
                            OutlinedTextField(
                                value = method,
                                onValueChange = { method = it },
                                label = { Text("Encryption Method") },
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                singleLine = true,
                            )
                            DividerItem()
                        }
                        if (needsUUID) {
                            OutlinedTextField(
                                value = uuid,
                                onValueChange = { uuid = it },
                                label = { Text("UUID") },
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                singleLine = true,
                            )
                            DividerItem()
                        }
                        if (needsFlow) {
                            PreferenceItem(
                                title = "Flow",
                                subtitle = flow.ifEmpty { "None" },
                                onClick = { /* select flow */ },
                            )
                            DividerItem()
                        }
                        if (needsEncryption) {
                            PreferenceItem(
                                title = "Encryption",
                                subtitle = encryption.ifEmpty { "none" },
                                onClick = { /* select encryption */ },
                            )
                        }
                    }
                }

                // ── TLS / Transport ──
                val needsTLS = profileType in listOf(
                    ProxyEntity.TYPE_VMESS, ProxyEntity.TYPE_VLESS,
                    ProxyEntity.TYPE_TROJAN, ProxyEntity.TYPE_HYSTERIA2,
                    ProxyEntity.TYPE_TUIC5, ProxyEntity.TYPE_JUICITY,
                    ProxyEntity.TYPE_HTTP3, ProxyEntity.TYPE_ANYTLS,
                    ProxyEntity.TYPE_SHADOWQUIC, ProxyEntity.TYPE_TRUSTTUNNEL,
                    ProxyEntity.TYPE_SNELL, ProxyEntity.TYPE_NAIVE,
                )
                val needsTransport = profileType in listOf(
                    ProxyEntity.TYPE_VMESS, ProxyEntity.TYPE_VLESS,
                    ProxyEntity.TYPE_TROJAN,
                )

                if (needsTLS || needsTransport) {
                    PreferenceHeader("TLS & Transport")
                    SectionCard {
                        if (needsTransport) {
                            PreferenceItem(
                                title = "Network",
                                subtitle = network,
                                onClick = { /* select network: tcp/ws/grpc/quic/splithttp/httpupgrade/mekya/meek */ },
                            )
                            DividerItem()
                            PreferenceItem(
                                title = "Security",
                                subtitle = security,
                                onClick = { /* select security: none/tls/reality */ },
                            )
                            DividerItem()
                            if (network == "ws" || network == "splithttp" || network == "httpupgrade") {
                                OutlinedTextField(
                                    value = path,
                                    onValueChange = { path = it },
                                    label = { Text("Path") },
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    singleLine = true,
                                )
                                DividerItem()
                                OutlinedTextField(
                                    value = host,
                                    onValueChange = { host = it },
                                    label = { Text("Host") },
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    singleLine = true,
                                )
                                DividerItem()
                            }
                        }
                        if (needsTLS) {
                            OutlinedTextField(
                                value = sni,
                                onValueChange = { sni = it },
                                label = { Text("SNI / Server Name") },
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                singleLine = true,
                            )
                            DividerItem()
                            OutlinedTextField(
                                value = alpn,
                                onValueChange = { alpn = it },
                                label = { Text("ALPN") },
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                singleLine = true,
                            )
                            DividerItem()
                            SwitchPreferenceItem(
                                title = "Allow Insecure",
                                checked = allowInsecure,
                                onCheckedChange = { allowInsecure = it },
                            )
                            DividerItem()
                            OutlinedTextField(
                                value = utlsFingerprint,
                                onValueChange = { utlsFingerprint = it },
                                label = { Text("uTLS Fingerprint") },
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                singleLine = true,
                            )
                            if (security == "reality") {
                                DividerItem()
                                OutlinedTextField(
                                    value = realityPublicKey,
                                    onValueChange = { realityPublicKey = it },
                                    label = { Text("Reality Public Key") },
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    singleLine = true,
                                )
                                DividerItem()
                                OutlinedTextField(
                                    value = realityShortId,
                                    onValueChange = { realityShortId = it },
                                    label = { Text("Reality Short ID") },
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    singleLine = true,
                                )
                            }
                            DividerItem()
                            SwitchPreferenceItem(
                                title = "ECH Enabled",
                                checked = echEnabled,
                                onCheckedChange = { echEnabled = it },
                            )
                            if (echEnabled) {
                                DividerItem()
                                OutlinedTextField(
                                    value = echConfig,
                                    onValueChange = { echConfig = it },
                                    label = { Text("ECH Config") },
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                )
                            }
                        }
                    }
                }

                // ── Advanced ──
                PreferenceHeader("Advanced")
                SectionCard {
                    SwitchPreferenceItem(
                        title = "UDP over TCP",
                        subtitle = "Route UDP traffic through TCP",
                        checked = singUot,
                        onCheckedChange = { singUot = it },
                    )
                    DividerItem()
                    SwitchPreferenceItem(
                        title = "Mux",
                        subtitle = "Multiplex connections",
                        checked = singMux,
                        onCheckedChange = { singMux = it },
                    )
                    if (needsTLS) {
                        DividerItem()
                        PreferenceItem(
                            title = "Client Certificate (mTLS)",
                            subtitle = mtlsCertificate.ifEmpty { "None" },
                            onClick = { /* select cert */ },
                        )
                    }
                }
            }
        }
    }
}
