package com.privatemessenger.platform

import androidx.compose.runtime.Composable

/**
 * Platform-specific composable that renders a live QR code scanner camera preview.
 *
 * Android actual: CameraX + ML Kit Barcode Scanner
 * iOS actual:     AVCaptureSession + Vision framework (via UIViewControllerRepresentable)
 */
@Composable
expect fun QrCodeScannerView(onScanned: (String) -> Unit)

/**
 * Generates a QR code bitmap from the given text and renders it as a Compose Image.
 *
 * Android actual: ZXing QRCodeWriter
 * iOS actual:     CoreImage CIQRCodeGenerator
 */
@Composable
expect fun QrCodeImageView(content: String, modifier: androidx.compose.ui.Modifier)
