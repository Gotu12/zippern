package com.zipper.datingapp.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.zipper.datingapp.ui.DatingUiState
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Aligned with live preview smile gate in [processFaceFrame]. */
private const val SMILE_PROBABILITY_THRESHOLD = 0.6f

@Composable
fun FaceVerificationScreen(
    uiState: DatingUiState,
    /** Called after capture; must invoke [onFinished] when upload/processing ends (success or failure). */
    onVerify: (Uri, onFinished: () -> Unit) -> Unit,
    onCancel: () -> Unit
) {
    BackHandler(onBack = onCancel)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    /** Gate from camera analyzer thread (avoid stale Compose state + unsafe thread writes). */
    val captureInProgress = remember { AtomicBoolean(false) }
    val lastNoFacePreviewToastMs = remember { AtomicLong(0L) }
    val analysisHolder = remember { object { @Volatile var analysis: ImageAnalysis? = null } }

    var isCapturing by remember { mutableStateOf(false) }
    var smileHint by remember { mutableStateOf("Smile to verify") }

    val detector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF09090F))
            .padding(24.dp)
    ) {
        IconButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Verified,
                contentDescription = null,
                tint = Color(0xFF4FC3F7),
                modifier = Modifier.size(64.dp)
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Face Verification",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Align your face and smile. Verification triggers automatically when your smile is detected.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Spacer(Modifier.height(40.dp))

            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(2.dp, Color(0xFF4FC3F7), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                smileHint,
                color = if (isCapturing) Color(0xFF4ADE80) else Color.White,
                fontWeight = FontWeight.Medium
            )
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            color = Color.White.copy(alpha = 0.05f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4ADE80), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reference: ${uiState.currentUser?.name}'s profile photos", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            }
        }
    }

    LaunchedEffect(Unit) {
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { useCase ->
                analysisHolder.analysis = useCase
                useCase.setAnalyzer(cameraExecutor) { imageProxy ->
                    processFaceFrame(
                        imageProxy = imageProxy,
                        detector = detector,
                        context = context,
                        mainExecutor = mainExecutor,
                        lastNoFacePreviewToastMs = lastNoFacePreviewToastMs,
                        onSmileDetected = {
                            if (captureInProgress.compareAndSet(false, true)) {
                                mainExecutor.execute {
                                    isCapturing = true
                                    smileHint = "Smile detected! Capturing..."
                                    runCatching {
                                        captureImageWithMediaImageValidation(
                                            context = context,
                                            imageCapture = imageCapture,
                                            cameraExecutor = cameraExecutor,
                                            detector = detector,
                                            mainExecutor = mainExecutor,
                                            onReadyForUpload = { uri, _ ->
                                                try {
                                                    onVerify(uri) {
                                                        mainExecutor.execute {
                                                            captureInProgress.set(false)
                                                            isCapturing = false
                                                            smileHint = "Smile to verify"
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("FaceVerification", "onVerify failed", e)
                                                    mainExecutor.execute {
                                                        captureInProgress.set(false)
                                                        isCapturing = false
                                                        smileHint = "Verification failed. Smile to try again"
                                                    }
                                                }
                                            },
                                            onInvalidFace = {
                                                mainExecutor.execute {
                                                    captureInProgress.set(false)
                                                    isCapturing = false
                                                    smileHint = "Smile to verify"
                                                }
                                            },
                                            onError = {
                                                mainExecutor.execute {
                                                    captureInProgress.set(false)
                                                    isCapturing = false
                                                    smileHint = "Could not capture. Smile to verify"
                                                }
                                            }
                                        )
                                    }.onFailure { e ->
                                        Log.e("FaceVerification", "captureImage", e)
                                        mainExecutor.execute {
                                            captureInProgress.set(false)
                                            isCapturing = false
                                            smileHint = "Could not capture. Smile to verify"
                                        }
                                    }
                                }
                            }
                        },
                        onNoSmile = {
                            mainExecutor.execute {
                                if (!captureInProgress.get()) smileHint = "Smile to verify"
                            }
                        }
                    )
                }
            }

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_FRONT_CAMERA,
            preview,
            imageCapture,
            analysis
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { analysisHolder.analysis?.clearAnalyzer() }
            analysisHolder.analysis = null
            runCatching { cameraProviderFuture.get().unbindAll() }
            runCatching { detector.close() }
            runCatching { cameraExecutor.shutdown() }
        }
    }
}

private fun processFaceFrame(
    imageProxy: ImageProxy,
    detector: com.google.mlkit.vision.face.FaceDetector,
    context: Context,
    mainExecutor: Executor,
    lastNoFacePreviewToastMs: AtomicLong,
    onSmileDetected: () -> Unit,
    onNoSmile: () -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    detector.process(inputImage)
        .addOnSuccessListener { faces: List<Face> ->
            when {
                faces.isEmpty() -> {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastNoFacePreviewToastMs.get() > 2500L) {
                        lastNoFacePreviewToastMs.set(now)
                        mainExecutor.execute {
                            Toast.makeText(
                                context,
                                "No face detected, try again",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    onNoSmile()
                }
                faces.any { (it.smilingProbability ?: 0f) > SMILE_PROBABILITY_THRESHOLD } -> onSmileDetected()
                else -> onNoSmile()
            }
        }
        .addOnFailureListener { onNoSmile() }
        .addOnCompleteListener { runCatching { imageProxy.close() } }
}

/**
 * Decode JPEG from disk, apply orientation by **physically** rotating the bitmap.
 * Prefer EXIF when present; if EXIF reports 0 (e.g. raw buffer saved without tags), use [fallbackRotationDegrees].
 */
private fun bitmapFromJpegFileForMlKit(file: File, fallbackRotationDegrees: Int): Bitmap {
    val exif = ExifInterface(file)
    val orientation = exif.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )
    val exifRotation = exif.rotationDegrees
    val rotationDegrees = if (exifRotation != 0) exifRotation else fallbackRotationDegrees
    val decoded = BitmapFactory.decodeFile(file.absolutePath)
        ?: throw IllegalStateException("Could not decode captured image")
    Log.d(
        "FaceVerification",
        "JPEG decode: EXIF orientation=$orientation exifRotation=$exifRotation fallback=$fallbackRotationDegrees applied=$rotationDegrees file=${file.name}"
    )
    if (rotationDegrees == 0) return decoded
    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    val rotated = Bitmap.createBitmap(
        decoded,
        0,
        0,
        decoded.width,
        decoded.height,
        matrix,
        true
    )
    if (rotated != decoded) decoded.recycle()
    return rotated
}

/** CameraX file capture usually embeds EXIF; fallback 0. */
private fun inputImageFromCapturedFile(file: File): InputImage {
    val bitmap = bitmapFromJpegFileForMlKit(file, fallbackRotationDegrees = 0)
    return InputImage.fromBitmap(bitmap, 0)
}

/** Average luminance 0–255 of center crop; used to explain false negatives. */
private fun averageLuminance(bitmap: Bitmap): Float {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return 128f
    val cw = (w * 0.25f).toInt().coerceAtLeast(1)
    val ch = (h * 0.25f).toInt().coerceAtLeast(1)
    val x0 = (w - cw) / 2
    val y0 = (h - ch) / 2
    var sum = 0L
    var n = 0
    for (y in y0 until (y0 + ch)) {
        for (x in x0 until (x0 + cw)) {
            val p = bitmap.getPixel(x, y)
            val r = (p shr 16) and 0xff
            val g = (p shr 8) and 0xff
            val b = p and 0xff
            sum += (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            n++
        }
    }
    return if (n == 0) 128f else sum / n.toFloat()
}

private fun logAndToastNoFace(context: Context, bitmapForMetrics: Bitmap?, tagReason: String) {
    val lum = bitmapForMetrics?.let { averageLuminance(it) }
    val message = when {
        bitmapForMetrics == null -> "Could not read the photo — try again"
        (lum ?: 128f) < 42f -> "Image too dark — try better lighting"
        else -> "No clear face — move closer and face the camera"
    }
    Log.w(
        "FaceVerification",
        "facesEmpty reason=$tagReason luminance=$lum (threshold dark < 42)"
    )
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

/**
 * Disk capture only: CameraX writes [photoFile] with EXIF; we read orientation via [ExifInterface],
 * apply [Matrix] in [bitmapFromJpegFileForMlKit], then [InputImage.fromBitmap] — only [onReadyForUpload]
 * when ML Kit reports [faces.isNotEmpty].
 */
private fun captureImageWithMediaImageValidation(
    context: Context,
    imageCapture: ImageCapture,
    cameraExecutor: Executor,
    detector: com.google.mlkit.vision.face.FaceDetector,
    mainExecutor: Executor,
    onReadyForUpload: (Uri, File) -> Unit,
    onInvalidFace: () -> Unit,
    onError: () -> Unit
) {
    val photoFile = File(context.cacheDir, "face_verify_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    Log.d("FaceVerification", "capture -> photoFile=${photoFile.name} (EXIF + Matrix before ML Kit)")
    imageCapture.takePicture(
        outputOptions,
        cameraExecutor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val uri = outputFileResults.savedUri ?: FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    photoFile
                )
                try {
                    val inputImage = inputImageFromCapturedFile(photoFile)
                    detector.process(inputImage)
                        .addOnSuccessListener { faces: List<Face> ->
                            val hasSmile = faces.any {
                                (it.smilingProbability ?: 0f) > SMILE_PROBABILITY_THRESHOLD
                            }
                            when {
                                faces.isEmpty() -> {
                                    Log.w("FaceVerification", "ML Kit: faces.isEmpty() after EXIF+Matrix upright decode")
                                    val bmp = runCatching {
                                        bitmapFromJpegFileForMlKit(photoFile, 0)
                                    }.getOrNull()
                                    mainExecutor.execute {
                                        logAndToastNoFace(context, bmp, "disk_capture_empty")
                                        onInvalidFace()
                                    }
                                }
                                !hasSmile -> {
                                    Log.w("FaceVerification", "Instant verify: face(s) present but smile below threshold")
                                    mainExecutor.execute {
                                        Toast.makeText(
                                            context,
                                            "Smile clearly for the camera, then try again.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        onInvalidFace()
                                    }
                                }
                                else -> {
                                    Log.d(
                                        "FaceVerification",
                                        "Instant verify: faces=${faces.size} smile OK — upload + Firestore approval"
                                    )
                                    mainExecutor.execute { onReadyForUpload(uri, photoFile) }
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("FaceVerification", "ML Kit process failed on disk capture", e)
                            mainExecutor.execute {
                                Toast.makeText(
                                    context,
                                    "Face detection failed. Try again.",
                                    Toast.LENGTH_LONG
                                ).show()
                                onInvalidFace()
                            }
                        }
                } catch (e: Exception) {
                    Log.e("FaceVerification", "decode/InputImage from photoFile", e)
                    mainExecutor.execute {
                        Toast.makeText(
                            context,
                            "Could not read capture. Try again.",
                            Toast.LENGTH_LONG
                        ).show()
                        onInvalidFace()
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("FaceVerification", "takePicture(file) failed", exception)
                mainExecutor.execute {
                    Toast.makeText(
                        context,
                        "Could not capture photo. Try again.",
                        Toast.LENGTH_LONG
                    ).show()
                    onError()
                }
            }
        }
    )
}
