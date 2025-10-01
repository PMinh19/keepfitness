//package com.example.keepyfitness
//
//import android.content.res.AssetFileDescriptor
//import android.content.res.AssetManager
//import android.graphics.Bitmap
//import android.util.Log
//import org.tensorflow.lite.Interpreter
//import java.io.FileInputStream
//import java.nio.ByteBuffer
//import java.nio.ByteOrder
//import java.nio.channels.FileChannel
//
//class Classifier(
//    private val assetManager: AssetManager,
//    private val modelPath: String,
//    private val labelPath: String,
//    private val inputSize: Int = 224
//) {
//    private var interpreter: Interpreter
//    private var labels: List<String>
//
//    init {
//        interpreter = Interpreter(loadModelFile(assetManager, modelPath))
//        labels = loadLabels(assetManager, labelPath)
//    }
//
//    private fun loadModelFile(assetManager: AssetManager, modelPath: String): ByteBuffer {
//        val fileDescriptor: AssetFileDescriptor = assetManager.openFd(modelPath)
//        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
//        val fileChannel: FileChannel = inputStream.channel
//        val startOffset: Long = fileDescriptor.startOffset
//        val declaredLength: Long = fileDescriptor.declaredLength
//        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
//    }
//
//    private fun loadLabels(assetManager: AssetManager, labelPath: String): List<String> {
//        return assetManager.open(labelPath).bufferedReader().useLines { it.toList() }
//    }
//
//    fun recognizeImage(bitmap: Bitmap): Pair<String, Float> {
//        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
//        val byteBuffer = convertBitmapToByteBuffer(resizedBitmap)
//
//        val result = Array(1) { FloatArray(labels.size) }
//        interpreter.run(byteBuffer, result)
//
//        val probabilities = result[0]
//        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
//
//        // Log tất cả xác suất để debug
//        Log.d("Classifier", "----- Inference Raw -----")
//        for (i in labels.indices) {
//            Log.d("Classifier", "${labels[i]} = ${probabilities[i]}")
//        }
//
//        return labels[maxIndex] to probabilities[maxIndex]
//    }
//
//    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
//        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
//        byteBuffer.order(ByteOrder.nativeOrder())
//
//        val intValues = IntArray(inputSize * inputSize)
//        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
//
//        var pixel = 0
//        for (i in 0 until inputSize) {
//            for (j in 0 until inputSize) {
//                val value = intValues[pixel++]
//
//                // chuẩn hóa 0–1 vì model Food11 được train như vậy
//                val r = ((value shr 16 and 0xFF) / 255.0f)
//                val g = ((value shr 8 and 0xFF) / 255.0f)
//                val b = ((value and 0xFF) / 255.0f)
//
//                byteBuffer.putFloat(r)
//                byteBuffer.putFloat(g)
//                byteBuffer.putFloat(b)
//            }
//        }
//        return byteBuffer
//    }
//}
