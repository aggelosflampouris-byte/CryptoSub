package com.privatemessenger.ui.screens.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.privatemessenger.PrivateMessengerApp
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    app: PrivateMessengerApp,
    onContactScanned: (address: String) -> Unit,
    onBack: () -> Unit
) {
    var hasCameraPermission by remember { mutableStateOf(false) }
    var isShowingMyCode by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Contact") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = { isShowingMyCode = false }) {
                        Text("Scan QR Code", fontWeight = if (!isShowingMyCode) FontWeight.Bold else FontWeight.Normal)
                    }
                    TextButton(onClick = { isShowingMyCode = true }) {
                        Text("My QR Code", fontWeight = if (isShowingMyCode) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (isShowingMyCode) {
                MyQrCodeView(app)
            } else {
                if (hasCameraPermission) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CameraPreviewView(
                            onBarcodeScanned = { barcodeValue, resetScanner ->
                                try {
                                    val uri = Uri.parse(barcodeValue)
                                    if (uri.scheme == "ethereum") {
                                        val address = uri.path ?: uri.schemeSpecificPart
                                        if (address.startsWith("0x")) {
                                            onContactScanned(address)
                                            return@CameraPreviewView
                                        }
                                    } else if (barcodeValue.startsWith("0x")) {
                                        onContactScanned(barcodeValue)
                                        return@CameraPreviewView
                                    }
                                    
                                    // If we reach here, it's not a recognized address
                                    Toast.makeText(context, "Unrecognized QR code format. Expected Ethereum address.", Toast.LENGTH_LONG).show()
                                    resetScanner() // Reset scanner so user can try again
                                } catch (e: Exception) {
                                    Log.e("ScannerScreen", "Invalid QR code format: $barcodeValue")
                                    Toast.makeText(context, "Failed to parse QR code", Toast.LENGTH_SHORT).show()
                                    resetScanner()
                                }
                            }
                        )
                        
                        PasteAddressView(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                            onAddressPasted = { address ->
                                val trimmed = address.trim()
                                if (trimmed.startsWith("0x") && trimmed.length == 42) {
                                    onContactScanned(trimmed)
                                } else {
                                    Toast.makeText(context, "Invalid Ethereum Address", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                } else {
                    Text("Camera permission is required to scan QR codes.")
                }
            }
        }
    }
}

@Composable
fun MyQrCodeView(app: PrivateMessengerApp) {
    val publicAddress = app.xmtpClient?.publicIdentity?.identifier ?: "Not registered"
    
    val uri = "ethereum:$publicAddress"
    
    val qrBitmap = remember(uri) { generateQrCodeBitmap(uri, 800) }
    val context = LocalContext.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "My QR Code",
                modifier = Modifier
                    .size(250.dp)
                    .background(androidx.compose.ui.graphics.Color.White)
                    .padding(8.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Your Public Address:",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        ) {
            Text(
                text = publicAddress.take(12) + "..." + publicAddress.takeLast(12),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Public Address", publicAddress)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Address copied to clipboard", Toast.LENGTH_SHORT).show()
            }) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy Address",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Text(
            text = "Have a friend scan this code to add you as a contact securely.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun generateQrCodeBitmap(text: String, size: Int): Bitmap? {
    if (text.isEmpty()) return null
    return try {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

@Composable
fun CameraPreviewView(onBarcodeScanned: (String, () -> Unit) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isScanned by remember { mutableStateOf(false) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val barcodeScanner = BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                )

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val executor = Executors.newSingleThreadExecutor()
                imageAnalysis.setAnalyzer(executor) { imageProxy: ImageProxy ->
                    if (isScanned) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        barcodeScanner.process(inputImage)
                            .addOnSuccessListener { barcodes ->
                                for (barcode in barcodes) {
                                    val rawValue = barcode.rawValue
                                    if (rawValue != null) {
                                        isScanned = true
                                        onBarcodeScanned(rawValue) {
                                            isScanned = false
                                        }
                                        break
                                    }
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreviewView", "Use case binding failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasteAddressView(
    modifier: Modifier = Modifier,
    onAddressPasted: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        tonalElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Or add manually by Public Address",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Paste Ethereum Address (0x...)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (text.isNotBlank()) {
                        IconButton(onClick = {
                            onAddressPasted(text)
                            text = ""
                        }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Add Contact", modifier = Modifier.background(MaterialTheme.colorScheme.primary, shape = androidx.compose.foundation.shape.CircleShape).padding(4.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            )
        }
    }
}
