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
import androidx.compose.material3.RadioButton
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
fun StunScreen(
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var stunServer by remember { mutableStateOf("stun.l.google.com:19302") }
    var testMode by remember { mutableStateOf(0) }
    var mappingResult by remember { mutableStateOf("") }
    var filteringResult by remember { mutableStateOf("") }
    var natTypeResult by remember { mutableStateOf("") }
    var externalAddr by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            OwenclaveTopAppBar(
                title = "STUN Test",
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
                            value = stunServer,
                            onValueChange = { stunServer = it },
                            label = { Text("STUN Server") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = testMode == 0, onClick = { testMode = 0 })
                            Text("STUN Test (RFC 5780)", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(16.dp))
                            RadioButton(selected = testMode == 1, onClick = { testMode = 1 })
                            Text("Legacy (RFC 3489)", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                running = true
                                error = ""
                                mappingResult = ""
                                filteringResult = ""
                                natTypeResult = ""
                                externalAddr = ""
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val stunClient = Libexclavecore.newStunClient()
                                        if (SagerNet.started && DataStore.startedProfile > 0) {
                                            stunClient.useUDS(
                                                SagerNet.deviceStorage.noBackupFilesDir.toString() + "/ipc.sock"
                                            )
                                            stunClient.useDNSUDS(
                                                SagerNet.deviceStorage.noBackupFilesDir.toString() + "/ipc_dns.sock"
                                            )
                                        }
                                        if (testMode == 0) {
                                            val result = stunClient.stunTest(stunServer)
                                            withContext(Dispatchers.Main) {
                                                if (result.error.isNotEmpty()) {
                                                    error = result.error
                                                } else {
                                                    mappingResult = result.natMapping
                                                    filteringResult = result.natFiltering
                                                }
                                                running = false
                                            }
                                        } else {
                                            val result = stunClient.stunLegacyTest(stunServer)
                                            withContext(Dispatchers.Main) {
                                                if (result.error.isNotEmpty()) {
                                                    error = result.error
                                                } else {
                                                    natTypeResult = result.natType
                                                    externalAddr = result.host
                                                }
                                                running = false
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            error = e.message ?: e.toString()
                                            running = false
                                        }
                                    }
                                }
                            },
                            enabled = !running,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (running) "Running..." else "Start") }
                    }
                }

                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }

                if (mappingResult.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Mapping Behaviour", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            Text(mappingResult, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                        }
                    }
                }
                if (filteringResult.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Filtering Behaviour", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            Text(filteringResult, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                        }
                    }
                }
                if (natTypeResult.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("NAT Type", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            Text(natTypeResult, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                        }
                    }
                }
                if (externalAddr.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("External Address", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            Text(externalAddr, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                        }
                    }
                }
            }
        }
    }
}
