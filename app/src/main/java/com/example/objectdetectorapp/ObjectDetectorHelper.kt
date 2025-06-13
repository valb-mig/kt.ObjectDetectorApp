// ObjectDetectorHelper.kt
package com.example.objectdetectorapp

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection

class ObjectDetectorHelper(context: Context) {
    private val objectDetector: ObjectDetector

    init {
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(3)
            .setScoreThreshold(0.6f)
            .build()

        objectDetector = ObjectDetector.createFromFileAndOptions(
            context,
            "common_detect.tflite", // seu modelo
            options
        )
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        val tensorImage = TensorImage.fromBitmap(bitmap)
        return objectDetector.detect(tensorImage)
    }
}
