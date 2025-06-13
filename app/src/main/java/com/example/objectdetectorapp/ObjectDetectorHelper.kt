package com.example.objectdetectorapp

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection

class ObjectDetectorHelperWithScore(context: Context, scoreThreshold: Float) {
    val objectDetector: ObjectDetector

    init {
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(3)
            .setScoreThreshold(scoreThreshold)
            .build()

        objectDetector = ObjectDetector.createFromFileAndOptions(
            context,
            "common_detect.tflite",
            options
        )
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        val tensorImage = TensorImage.fromBitmap(bitmap)
        return objectDetector.detect(tensorImage)
    }
}
