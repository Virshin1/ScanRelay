package com.scanrelay.app.ui.scanner

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.hardware.camera2.CameraCharacteristics
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

private fun Context.findActivity(): ComponentActivity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun CameraPreview(
    isTorchOn: Boolean,
    useUltraWide: Boolean = false,
    onBarcodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = activity ?: LocalLifecycleOwner.current

    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { BarcodeScanning.getClient() }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                cameraProviderRef?.unbindAll()
            } catch (_: Exception) {}
            cameraExecutor.shutdown()
            barcodeScanner.close()
        }
    }

    LaunchedEffect(isTorchOn, camera) {
        camera?.cameraControl?.enableTorch(isTorchOn)
    }

    // Bind or re-bind camera when useUltraWide or provider/previewView changes
    LaunchedEffect(useUltraWide, cameraProviderRef, previewViewRef) {
        val provider = cameraProviderRef ?: return@LaunchedEffect
        val pv = previewViewRef ?: return@LaunchedEffect

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(pv.surfaceProvider)
        }

        var lastAnalyzedTimestamp = 0L

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val currentTime = System.currentTimeMillis()
                    // Throttle ML Kit processing to ~5 fps (every 200ms) to avoid CPU overheating
                    if (currentTime - lastAnalyzedTimestamp < 200L) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    lastAnalyzedTimestamp = currentTime

                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        barcodeScanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                for (barcode in barcodes) {
                                    if (barcode.valueType == Barcode.TYPE_TEXT ||
                                        barcode.valueType == Barcode.TYPE_URL ||
                                        barcode.valueType == Barcode.TYPE_UNKNOWN
                                    ) {
                                        barcode.rawValue?.let { raw ->
                                            onBarcodeScanned(raw)
                                        }
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
            }

        val (cameraSelector, targetZoom) = selectCameraSelector(provider, useUltraWide)

        try {
            provider.unbindAll()
            val boundCamera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
            camera = boundCamera

            if (useUltraWide) {
                val minZoom = boundCamera.cameraInfo.zoomState.value?.minZoomRatio ?: 1.0f
                val zoomToSet = if (targetZoom != null) targetZoom.coerceAtLeast(minZoom) else minZoom
                if (zoomToSet < 1.0f || minZoom < 1.0f) {
                    boundCamera.cameraControl.setZoomRatio(minZoom)
                }
            } else {
                boundCamera.cameraControl.setZoomRatio(1.0f)
            }
        } catch (_: Exception) {}
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                previewViewRef = previewView

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    cameraProviderRef = cameraProviderFuture.get()
                }, ContextCompat.getMainExecutor(ctx))

                // Tap to focus
                previewView.setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        val factory = previewView.meteringPointFactory
                        val point = factory.createPoint(event.x, event.y)
                        val action = FocusMeteringAction.Builder(point).build()
                        camera?.cameraControl?.startFocusAndMetering(action)
                    }
                    true
                }

                previewView
            }
        )

        // Clear scanning target frame overlay
        ScanningOverlay()
    }
}

/**
 * Discovers and selects the Ultra-Wide camera lens if requested, or falls back gracefully:
 * 1. Physical back camera with intrinsicZoomRatio < 1.0f (CameraX 1.3+ native detection)
 * 2. Physical back camera with focal length < 3.2mm via Camera2 characteristics (Ultra-Wide hardware sensor)
 * 3. Logical camera with minimum zoom ratio (0.5x / 0.6x)
 */
@OptIn(ExperimentalCamera2Interop::class)
private fun selectCameraSelector(
    cameraProvider: ProcessCameraProvider,
    preferUltraWide: Boolean
): Pair<CameraSelector, Float?> {
    if (!preferUltraWide) {
        return Pair(CameraSelector.DEFAULT_BACK_CAMERA, 1.0f)
    }

    val backCameras = cameraProvider.availableCameraInfos.filter {
        it.lensFacing == CameraSelector.LENS_FACING_BACK
    }

    // Method 1: Check for physical back camera with intrinsicZoomRatio < 1.0
    val ultraWideByIntrinsic = backCameras.firstOrNull {
        try {
            it.intrinsicZoomRatio < 1.0f
        } catch (_: Exception) {
            false
        }
    }
    if (ultraWideByIntrinsic != null) {
        return Pair(ultraWideByIntrinsic.cameraSelector, null)
    }

    // Method 2: Check Camera2 focal lengths (ultra-wide lenses typically <= 3.2mm)
    var ultraWideByFocal: CameraInfo? = null
    var minFocalLength = Float.MAX_VALUE

    for (info in backCameras) {
        try {
            val c2Info = Camera2CameraInfo.from(info)
            val focals = c2Info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            if (focals != null && focals.isNotEmpty()) {
                val minFocal = focals.minOrNull() ?: Float.MAX_VALUE
                if (minFocal < minFocalLength) {
                    minFocalLength = minFocal
                    ultraWideByFocal = info
                }
            }
        } catch (_: Exception) {}
    }

    if (ultraWideByFocal != null && minFocalLength < 3.2f && backCameras.size > 1) {
        return Pair(ultraWideByFocal.cameraSelector, null)
    }

    // Method 3: Logical multi-camera fallback: use DEFAULT_BACK_CAMERA and target 0.5x zoom
    return Pair(CameraSelector.DEFAULT_BACK_CAMERA, 0.5f)
}

@Composable
fun ScanningOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Scan box size (square, approx 70% of screen width)
        val boxSize = canvasWidth * 0.70f
        val left = (canvasWidth - boxSize) / 2f
        val top = (canvasHeight - boxSize) / 2.2f

        // Draw translucent dark background with clear window
        drawRect(
            color = Color.Black.copy(alpha = 0.55f),
            size = size
        )

        // Clear scanning cutout window
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(boxSize, boxSize),
            cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
            blendMode = BlendMode.Clear
        )

        // Draw bright border around scanning window
        drawRoundRect(
            color = Color(0xFF4CAF50),
            topLeft = Offset(left, top),
            size = Size(boxSize, boxSize),
            cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
            style = Stroke(width = 3.dp.toPx())
        )

        // Corner accents (top-left, top-right, bottom-left, bottom-right)
        val cornerLen = 28.dp.toPx()
        val strokeWidth = 5.dp.toPx()
        val cornerColor = Color(0xFF81C784)

        // Top-Left corner
        drawLine(cornerColor, Offset(left - 2, top + cornerLen), Offset(left - 2, top), strokeWidth)
        drawLine(cornerColor, Offset(left - 2, top - 2), Offset(left + cornerLen, top - 2), strokeWidth)

        // Top-Right corner
        drawLine(cornerColor, Offset(left + boxSize + 2, top + cornerLen), Offset(left + boxSize + 2, top), strokeWidth)
        drawLine(cornerColor, Offset(left + boxSize + 2, top - 2), Offset(left + boxSize - cornerLen, top - 2), strokeWidth)

        // Bottom-Left corner
        drawLine(cornerColor, Offset(left - 2, top + boxSize - cornerLen), Offset(left - 2, top + boxSize), strokeWidth)
        drawLine(cornerColor, Offset(left - 2, top + boxSize + 2), Offset(left + cornerLen, top + boxSize + 2), strokeWidth)

        // Bottom-Right corner
        drawLine(cornerColor, Offset(left + boxSize + 2, top + boxSize - cornerLen), Offset(left + boxSize + 2, top + boxSize), strokeWidth)
        drawLine(cornerColor, Offset(left + boxSize + 2, top + boxSize + 2), Offset(left + boxSize - cornerLen, top + boxSize + 2), strokeWidth)
    }
}
