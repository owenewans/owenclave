package io.nekohasekai.sagernet.ui.compose.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ui.compose.components.OwenclaveTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import libexclavecore.Libexclavecore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProbeCertScreen(
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf("example.com") }
    var port by remember { mutableStateOf("443") }
    var sni by remember { mutableStateOf("example.com") }
    var alpn by remember { mutableStateOf("h2,http/1.1") }
    var protocol by remember { mutableStateOf(0) }
    var certResult by remember { mutableStateOf("") }
    var verifyError by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var certHash by remember { mutableStateOf("") }
    var hashType by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            OwenclaveTopAppBar(
                title = "Probe Certificate",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Column {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        OutlinedTextField(
                            value = server,
                            onValueChange = { server = it; if (sni.isEmpty()) sni = it },
                            label = { Text("Server") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it.filter { c -> c.isDigit() } },
                            label = { Text("Port") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = sni,
                            onValueChange = { sni = it },
                            label = { Text("SNI / Server Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = alpn,
                            onValueChange = { alpn = it },
                            label = { Text("ALPN") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = protocol == 0,
                                onClick = { protocol = 0; alpn = "h2,http/1.1" },
                            )
                            Text("TLS", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(16.dp))
                            androidx.compose.material3.RadioButton(
                                selected = protocol == 1,
                                onClick = { protocol = 1; alpn = "h3" },
                            )
                            Text("QUIC", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                running = true
                                error = ""
                                certResult = ""
                                verifyError = ""
                                certHash = ""
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val portNum = port.toInt()
                                        val certProber = Libexclavecore.newCertProber()
                                        if (SagerNet.started && DataStore.startedProfile > 0) {
                                            certProber.useUDS(
                                                SagerNet.deviceStorage.noBackupFilesDir.toString() + "/ipc.sock"
                                            )
                                        }
                                        val result = if (protocol == 0) {
                                            certProber.probeTLS(server, portNum, sni, alpn)
                                        } else {
                                            certProber.probeQUIC(server, portNum, sni, alpn)
                                        }
                                        withContext(Dispatchers.Main) {
                                            if (result.error.isNotEmpty()) {
                                                error = result.error
                                            } else {
                                                certResult = result.cert
                                                if (result.verifyError.isNotEmpty()) {
                                                    verifyError = result.verifyError
                                                }
                                            }
                                            running = false
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            error = e.message ?: e.toString()
                                            running = false
                                        }
                                    }
                                }
                            },
                            enabled = !running && server.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (running) "Probing..." else "Probe") }
                    }
                }

                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }

                if (verifyError.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Verify Error", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            Text(verifyError, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (certResult.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Certificate (PEM)", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = certResult,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Certificate Hash", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.RadioButton(selected = hashType == 0, onClick = { hashType = 0 })
                                Text("Cert SHA256", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.width(8.dp))
                                androidx.compose.material3.RadioButton(selected = hashType == 1, onClick = { hashType = 1 })
                                Text("PubKey SHA256", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.width(8.dp))
                                androidx.compose.material3.RadioButton(selected = hashType == 2, onClick = { hashType = 2 })
                                Text("Chain SHA256", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(8.dp))
                            val hash = remember(certResult, hashType) {
                                try {
                                    when (hashType) {
                                        0 -> Libexclavecore.calculatePEMCertSHA256Hash(certResult)
                                        1 -> Libexclavecore.calculatePEMCertPublicKeySHA256Hash(certResult)
                                        2 -> Libexclavecore.calculatePEMCertChainSHA256Hash(certResult)
                                        else -> ""
                                    }
                                } catch (_: Exception) { "" }
                            }
                            if (hash.isNotEmpty()) {
                                Text(
                                    text = hash,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
