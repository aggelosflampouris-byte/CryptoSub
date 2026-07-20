package com.privatemessenger.platform

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.*
import platform.AVFoundation.*
import platform.CoreImage.*
import platform.Foundation.*
import platform.UIKit.*

/**
 * iOS actual for [QrCodeScannerView].
 * Hosts a native AVCaptureSession via UIKitView (Compose Multiplatform).
 * Barcode detection is done by the AVMetadataObjectTypeQRCode capture output.
 */
@Composable
actual fun QrCodeScannerView(onScanned: (String) -> Unit) {
    val onScannedRef = rememberUpdatedState(onScanned)
    var didScan by remember { mutableStateOf(false) }

    UIKitView(
        modifier = Modifier,
        factory = {
            val session = AVCaptureSession()
            val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
                ?: return@UIKitView UIView()

            val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null) ?: return@UIKitView UIView()
            if (session.canAddInput(input)) session.addInput(input)

            val metadataOutput = AVCaptureMetadataOutput()
            if (session.canAddOutput(metadataOutput)) session.addOutput(metadataOutput)

            metadataOutput.setMetadataObjectTypes(listOf(AVMetadataObjectTypeQRCode))
            metadataOutput.setMetadataObjectsDelegate(
                delegate = object : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
                    override fun captureOutput(
                        output: AVCaptureOutput,
                        didOutputMetadataObjects: List<*>,
                        fromConnection: AVCaptureConnection,
                    ) {
                        if (didScan) return
                        val obj = didOutputMetadataObjects.firstOrNull() as? AVMetadataMachineReadableCodeObject ?: return
                        val raw = obj.stringValue ?: return
                        didScan = true
                        onScannedRef.value(raw)
                    }
                },
                queue = dispatch_get_main_queue(),
            )

            val previewLayer = AVCaptureVideoPreviewLayer(session = session)
            previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill

            val container = UIView()
            container.layer.addSublayer(previewLayer)

            // Start session on background thread
            dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
                session.startRunning()
            }

            // Keep previewLayer in sync with container size via a layout callback
            container.setNeedsLayout()
            container
        },
        update = { view ->
            view.layer.sublayers
                ?.filterIsInstance<AVCaptureVideoPreviewLayer>()
                ?.firstOrNull()
                ?.setFrame(view.bounds)
        },
    )
}

/**
 * iOS actual for [QrCodeImageView].
 * Generates a QR code bitmap using CoreImage's CIQRCodeGenerator filter.
 */
@Composable
actual fun QrCodeImageView(content: String, modifier: Modifier) {
    val uiImage = remember(content) {
        val filter = CIFilter.filterWithName("CIQRCodeGenerator") ?: return@remember null
        val data = content.encodeToByteArray().toNSData()
        filter.setValue(data, forKey = "inputMessage")
        filter.setValue("H", forKey = "inputCorrectionLevel") // High error correction

        val outputImage = filter.outputImage ?: return@remember null
        // Scale up for display quality
        val scaleTransform = CGAffineTransformMakeScale(10.0, 10.0)
        val scaledImage = outputImage.imageByApplyingTransform(scaleTransform)

        val context = CIContext.contextWithOptions(null)
        val cgImage = context.createCGImage(scaledImage, fromRect = scaledImage.extent)
            ?: return@remember null
        UIImage.imageWithCGImage(cgImage)
    }

    if (uiImage != null) {
        UIKitView(
            modifier = modifier,
            factory = { UIImageView(image = uiImage) },
        )
    }
}

// ── Extension ─────────────────────────────────────────────────────────────────

private fun ByteArray.toNSData(): NSData = memScoped {
    val ptr = allocArray<ByteVar>(size)
    for (i in indices) ptr[i] = this@toNSData[i]
    NSData.create(bytes = ptr, length = size.toULong())
}
