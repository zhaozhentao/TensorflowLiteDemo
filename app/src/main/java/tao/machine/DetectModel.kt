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
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DetectModel(private val context: Context) {
    private var interpreter: Interpreter? = null
    var isInitialized = false
        private set

    /** Executor to run inference task in the background */
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
        options.useNNAPI = true
        val interpreter = Interpreter(model, options)

        // Read input shape from model file
        val inputShape = interpreter.getInputTensor(0).shape()
        inputImageWidth = inputShape[1]
        inputImageHeight = inputShape[2]
        channel = inputShape[3]
        modelInputSize = FLOAT_TYPE_SIZE * inputImageWidth * inputImageHeight * channel * PIXEL_SIZE

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

    private fun classify(bitmap: Bitmap): Bitmap {
        if (!isInitialized) {
            throw IllegalStateException("TF Lite Interpreter is not initialized yet.")
        }

        // Preprocessing: resize the input
        var startTime: Long = System.nanoTime()
        val resizedImage =
            Bitmap.createScaledBitmap(bitmap, inputImageWidth, inputImageHeight, true)
        val byteBuffer = convertBitmapToByteBuffer(resizedImage)
        var elapsedTime: Long = (System.nanoTime() - startTime) / 1000000
        Log.d(TAG, "Preprocessing time = " + elapsedTime + "ms")

        startTime = System.nanoTime()
        val result = Array(1) { FloatArray(OUTPUT_CLASSES_COUNT) }
        interpreter?.run(byteBuffer, result)
        elapsedTime = (System.nanoTime() - startTime) / 1000000
        Log.d(TAG, "Inference time = " + elapsedTime + "ms")

        val x1 = result[0][0] * bitmap.width
        val y1 = result[0][1] * bitmap.height

        val x2 = result[0][2] * bitmap.width
        val y2 = result[0][3] * bitmap.height

        val x3 = result[0][4] * bitmap.width
        val y3 = result[0][5] * bitmap.height

        val x4 = result[0][6] * bitmap.width
        val y4 = result[0][7] * bitmap.height

        val point0 = Point(x1.toDouble(), y1.toDouble())
        val point1 = Point(x2.toDouble(), y2.toDouble())
        val point2 = Point(x3.toDouble(), y3.toDouble())
        val point3 = Point(x4.toDouble(), y4.toDouble())

        val mat1 = MatOfPoint2f(point0, point3, point1, point2)
        val mat2 =
            MatOfPoint2f(Point(0.0, 0.0), Point(144.0, 0.0), Point(0.0, 50.0), Point(144.0, 50.0))
        val transform = Imgproc.getPerspectiveTransform(mat1, mat2)
        val plateImage = Mat()

        val rawImage = Mat()
        Utils.bitmapToMat(bitmap, rawImage)
        Imgproc.warpPerspective(rawImage, plateImage, transform, Size(144.0, 50.0))

        val plateBitmap =
            Bitmap.createBitmap(plateImage.cols(), plateImage.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(plateImage, plateBitmap);

        return plateBitmap
    }

    fun classifyAsync(bitmap: Bitmap): Task<Bitmap> {
        val task = TaskCompletionSource<Bitmap>()
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
            byteBuffer.putFloat(r / 255.0f)
            byteBuffer.putFloat(g / 255.0f)
            byteBuffer.putFloat(b / 255.0f)
        }

        return byteBuffer
    }

    companion object {
        private const val TAG = "DigitClassifier"

        private const val MODEL_FILE = "detect.tflite"

        private const val FLOAT_TYPE_SIZE = 4
        private const val PIXEL_SIZE = 1

        private const val OUTPUT_CLASSES_COUNT = 8
    }
}
