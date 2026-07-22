package io.nekohasekai.sagernet.ui.compose.screens

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.nekohasekai.sagernet.ui.compose.components.OwenclaveTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onImportFile: () -> Unit,
    onToggleFlash: () -> Unit,
    onSwitchCamera: () -> Unit,
) {
    Scaffold(
        topBar = {
            OwenclaveTopAppBar(
                title = "Scan QR Code",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
                actions = {
                    IconButton(onClick = onImportFile) {
                        Icon(Icons.Filled.UploadFile, contentDescription = "Import file")
                    }
                    IconButton(onClick = onToggleFlash) {
                        Icon(Icons.Filled.FlashOn, contentDescription = "Flash")
                    }
                    IconButton(onClick = onSwitchCamera) {
                        Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch camera")
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { context ->
                    PreviewView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            CircularProgressIndicator(
                modifier = Modifier.padding(bottom = 48.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
