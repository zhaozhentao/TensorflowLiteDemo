package tao.machine

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.tensorflow.lite.Interpreter
import tao.machine.FindMax.chars
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class OcrModel(private val context: Context) {

    private var interpreter: Interpreter? = null
    var isInitialized = false

    private val executorService: ExecutorService = Executors.newCachedThreadPool()
    private var inputImageWidth: Int = 0 // will be inferred from TF Lite model
    private var inputImageHeight: Int = 0 // will be inferred from TF Lite model
    private var channel: Int = 3 //
    private var modelInputSize: Int = 0 // will be inferred from TF Lite model

    fun initialize(): Task<Void?> {
        val task = TaskCompletionSource<Void?>()
        executorService.execute {
            try {
                initializeInterpreter()
                task.setResult(null)
            } catch (e: IOException) {
                task.setException(e)
            }
        }
        return task.task
    }

    @Throws(IOException::class)
    private fun initializeInterpreter() {
        // Load the TF Lite model
        val assetManager = context.assets
        val model = loadModelFile(assetManager)

        // Initialize TF Lite Interpreter with NNAPI enabled
        val options = Interpreter.Options()
        val interpreter = Interpreter(model, options)

        // Read input shape from model file
        val inputShape = interpreter.getInputTensor(0).shape()
        inputImageWidth = inputShape[2]
        inputImageHeight = inputShape[1]
        channel = inputShape[3]
        modelInputSize =
            FLOAT_TYPE_SIZE * inputImageWidth * inputImageHeight * channel * PIXEL_SIZE

        // Finish interpreter initialization
        this.interpreter = interpreter
        isInitialized = true
        Log.d(TAG, "Initialized TFLite interpreter.")
    }

    @Throws(IOException::class)
    private fun loadModelFile(assetManager: AssetManager): ByteBuffer {
        val fileDescriptor = assetManager.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun classify(resizedImage: Bitmap): String {
        if (!isInitialized) throw IllegalStateException("TF Lite Interpreter is not initialized yet.")

        val byteBuffer = convertBitmapToByteBuffer(resizedImage)

        val result = Array(14) {
            Array(1) {
                FloatArray(87)
            }
        }

        interpreter?.run(byteBuffer, result)

        val sb = StringBuilder()
        var lastIndex = -1;

        for (element in result) {
            val index = FindMax.findMax(element[0])

            if ((index < chars.size) && (lastIndex != index)) sb.append(chars[index]);

            lastIndex = index;
        }

        return sb.toString()
    }

    fun classifyAsync(bitmap: Bitmap): Task<String> {
        val task = TaskCompletionSource<String>()
        executorService.execute { task.setResult(classify(bitmap)) }
        return task.task
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(modelInputSize)
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputImageWidth * inputImageHeight)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (pixelValue in pixels) {
            val r = (pixelValue shr 16 and 0xFF)
            val g = (pixelValue shr 8 and 0xFF)
            val b = (pixelValue and 0xFF)

            // Convert RGB to grayscale and normalize pixel value to [0..1]
            byteBuffer.putFloat(b / 255.0f)
            byteBuffer.putFloat(g / 255.0f)
            byteBuffer.putFloat(r / 255.0f)
        }

        return byteBuffer
    }

    companion object {
        private const val TAG = "OCR"

        private const val MODEL_FILE = "ocr.tflite"

        private const val FLOAT_TYPE_SIZE = 4
        private const val PIXEL_SIZE = 1
    }
}