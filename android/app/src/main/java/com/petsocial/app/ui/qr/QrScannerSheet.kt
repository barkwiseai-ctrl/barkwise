package com.petsocial.app.ui.qr

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.petsocial.app.ui.rememberPhoneSizeClass
import com.petsocial.app.ui.scannerPreviewHeightDp
import java.util.concurrent.Executors

@SuppressLint("UnsafeOptInUsageError")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerSheet(
    onDetected: (String) -> Unit,
    onDismiss: () -> Unit,
    sheetTitle: String = "Scan Invite QR",
    permissionDescription: String = "Camera permission is required to scan invite QR codes.",
    hintDescription: String = "Point the camera at a BarkWise invite or install QR.",
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val phoneSizeClass = rememberPhoneSizeClass()
    val previewHeight = scannerPreviewHeightDp(
        sizeClass = phoneSizeClass,
        screenHeightDp = configuration.screenHeightDp,
    )
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scannerOptions = remember {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    }
    val scanner = remember { BarcodeScanning.getClient(scannerOptions) }
    val previewView = remember { PreviewView(context) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasDetected by remember { mutableStateOf(false) }
    var scannerError by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(sheetTitle, style = MaterialTheme.typography.titleMedium)
            if (!hasCameraPermission) {
                Text(
                    permissionDescription,
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Allow camera")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            } else {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(previewHeight),
                    factory = {
                        previewView.apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    },
                )
                Text(
                    hintDescription,
                    style = MaterialTheme.typography.bodySmall,
                )
                scannerError?.let { errorText ->
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    DisposableEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            onDispose { }
        } else {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val listener = Runnable {
                runCatching {
                    val cameraProvider = cameraProviderFuture.get()
                    val backSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    val frontSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                    val selector = when {
                        cameraProvider.hasCamera(backSelector) -> backSelector
                        cameraProvider.hasCamera(frontSelector) -> frontSelector
                        else -> null
                    }
                    if (selector == null) {
                        scannerError = "No camera available on this device."
                        return@Runnable
                    }
                    scannerError = null
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    val analyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analyzer.setAnalyzer(executor) { proxy ->
                        if (hasDetected) {
                            proxy.close()
                            return@setAnalyzer
                        }
                        val mediaImage = proxy.image
                        if (mediaImage == null) {
                            proxy.close()
                            return@setAnalyzer
                        }
                        val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                if (hasDetected) return@addOnSuccessListener
                                val value = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                                if (!value.isNullOrBlank()) {
                                    hasDetected = true
                                    onDetected(value)
                                }
                            }
                            .addOnCompleteListener {
                                proxy.close()
                            }
                    }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analyzer)
                }.onFailure {
                    scannerError = "Camera setup failed on this phone. Please retry."
                }
            }
            cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))
            onDispose {
                runCatching {
                    val provider = cameraProviderFuture.get()
                    provider.unbindAll()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { scanner.close() }
            executor.shutdown()
        }
    }
}
