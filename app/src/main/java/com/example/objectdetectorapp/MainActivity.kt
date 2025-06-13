package com.example.objectdetectorapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.objectdetectorapp.ui.theme.ObjectDetectorAppTheme
import org.tensorflow.lite.task.vision.detector.Detection
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun startCamera() {
        setContent {
            ObjectDetectorAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CameraPreviewWithDetector()
                }
            }
        }
    }

    @Composable
    fun DetectionOverlay(
        modifier: Modifier = Modifier,
        detections: List<Detection>,
        imageSize: Size,
        previewSize: Size
    ) {
        Canvas(modifier = modifier) {
            val scaleX = size.width / imageSize.width
            val scaleY = size.height / imageSize.height

            detections.forEach { detection ->
                val bbox = detection.boundingBox

                val left = bbox.left * scaleX
                val top = bbox.top * scaleY
                val right = bbox.right * scaleX
                val bottom = bbox.bottom * scaleY

                val rectColor = Color(0xFF4CAF50).copy(alpha = 0.7f)
                val label = detection.categories.firstOrNull()?.label ?: "?"
                val score = detection.categories.firstOrNull()?.score ?: 0f
                val labelText = "$label: ${"%.1f".format(score * 100)}%"

                // Caixa
                drawRoundRect(
                    color = rectColor,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    cornerRadius = CornerRadius(12f, 12f),
                    style = Stroke(width = 4f)
                )

                // Fundo para o texto
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = Offset(left, top - 40),
                    size = Size((right - left).coerceAtLeast(120f), 40f)
                )

                // Texto da label
                drawContext.canvas.nativeCanvas.apply {
                    drawText(
                        labelText,
                        left + 10,
                        top - 10,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 32f
                            isAntiAlias = true
                        }
                    )
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    @Composable
    fun CameraPreviewWithDetector() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        var scoreThreshold by remember { mutableStateOf(0.6f) }
        val detectorHelper = remember(scoreThreshold) { ObjectDetectorHelperWithScore(context, scoreThreshold) }
        val logMessages = remember { mutableStateListOf("Iniciando...") }
        val previewView = remember { PreviewView(context) }

        val detections = remember { mutableStateListOf<Detection>() }
        var imageSize by remember { mutableStateOf(Size(1f, 1f)) }

        var lastLogTime by remember { mutableStateOf(0L) }

        val configuration = LocalContext.current.resources.configuration
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        DisposableEffect(scoreThreshold) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

            val listener = Runnable {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                    val bitmap = imageProxy.toBitmap()
                    imageSize = Size(bitmap.width.toFloat(), bitmap.height.toFloat())

                    val results = detectorHelper.detect(bitmap)
                    detections.clear()
                    detections.addAll(results)

                    // Adiciona logs a cada 2 segundos
                    val now = System.currentTimeMillis()
                    if (now - lastLogTime > 2000) {
                        lastLogTime = now

                        val detectionText = results.joinToString("\n") {
                            val label = it.categories.firstOrNull()?.label ?: "?"
                            val score = it.categories.firstOrNull()?.score ?: 0f
                            "$label: ${"%.2f".format(score)}"
                        }

                        logMessages.add(0, detectionText)

                        if (logMessages.size > 50) {
                            logMessages.removeLast()
                        }
                    }

                    imageProxy.close()
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageAnalyzer
                )
            }

            cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

            onDispose {
                cameraProviderFuture.get().unbindAll()
            }
        }

        // Layout com preview, overlay, logs, e controle do score
        if (isLandscape) {
            Row(Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                    DetectionOverlay(
                        modifier = Modifier.fillMaxSize(),
                        detections = detections,
                        imageSize = imageSize,
                        previewSize = Size(previewView.width.toFloat(), previewView.height.toFloat())
                    )
                    // Mostrar score no canto inferior esquerdo
                    Text(
                        text = "Score: ${"%.2f".format(scoreThreshold)}",
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.BottomStart),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Divider(modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp))

                Column(
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight()
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Logs:",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Button(onClick = { logMessages.clear() }) {
                            Text("Limpar")
                        }
                    }

                    // Botões de aumentar e diminuir score (pequenos circulares)
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (scoreThreshold > 0.1f) {
                                    scoreThreshold -= 0.05f
                                }
                            },
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("-", style = MaterialTheme.typography.titleLarge)
                        }

                        Button(
                            onClick = {
                                if (scoreThreshold < 1f) {
                                    scoreThreshold += 0.05f
                                }
                            },
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("+", style = MaterialTheme.typography.titleLarge)
                        }
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(logMessages) { index, log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            if (index < logMessages.size - 1) Divider()
                        }
                    }
                }
            }
        } else {
            // Layout vertical (pra quando estiver na vertical)
            Column(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                    DetectionOverlay(
                        modifier = Modifier.fillMaxSize(),
                        detections = detections,
                        imageSize = imageSize,
                        previewSize = Size(previewView.width.toFloat(), previewView.height.toFloat())
                    )
                    Text(
                        text = "Score: ${"%.2f".format(scoreThreshold)}",
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.BottomStart),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Divider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Logs:",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Button(onClick = { logMessages.clear() }) {
                        Text("Limpar Logs")
                    }
                }

                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (scoreThreshold > 0.1f) {
                                scoreThreshold -= 0.05f
                            }
                        },
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("-", style = MaterialTheme.typography.titleLarge)
                    }

                    Button(
                        onClick = {
                            if (scoreThreshold < 1f) {
                                scoreThreshold += 0.05f
                            }
                        },
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    itemsIndexed(logMessages) { index, log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                        if (index < logMessages.size - 1) Divider()
                    }
                }
            }
        }
    }

    fun ImageProxy.toBitmap(): Bitmap {
        val yBuffer: ByteBuffer = planes[0].buffer
        val uBuffer: ByteBuffer = planes[1].buffer
        val vBuffer: ByteBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
        val imageBytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
}
