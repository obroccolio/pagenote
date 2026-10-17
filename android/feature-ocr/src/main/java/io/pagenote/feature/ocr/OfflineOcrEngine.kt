package io.pagenote.feature.ocr

import android.content.Context
import android.graphics.Bitmap
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.model.OCRError
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class OfflineOcrException(message: String, cause: Throwable? = null) : Exception(message, cause)

class OfflineOcrEngine private constructor(
  private val pipeline: PaddleOCR,
) {
  private val mutex = Mutex()
  private var released = false

  suspend fun recognize(bitmap: Bitmap): OcrResult = mutex.withLock {
    if (released) return@withLock failure("OCR engine has been closed.")
    if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
      return@withLock failure("OCR input image is empty or has been recycled.")
    }

    try {
      val output = pipeline.recognize(bitmap)
      OcrResultMapper.map(
        imageWidth = bitmap.width,
        imageHeight = bitmap.height,
        recognized = output.results.map { result ->
          RecognizedTextBlock(
            text = result.text,
            confidence = result.confidence.toDouble(),
            points = result.box.points.map { OcrPoint(it.x, it.y) },
          )
        },
      )
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Throwable) {
      failure("Offline OCR inference failed: ${error.message ?: error.javaClass.simpleName}")
    }
  }

  suspend fun close() = mutex.withLock {
    if (!released) {
      pipeline.release()
      released = true
    }
  }

  private fun failure(message: String) = OcrResult(
    engine = OcrResultMapper.ENGINE,
    status = "failed",
    warnings = listOf(message),
  )

  companion object {
    private const val DETECTION_MODEL = "models/det/inference.onnx"
    private const val RECOGNITION_MODEL = "models/rec/inference.onnx"
    private const val RECOGNITION_CONFIG = "models/rec/inference.yml"
    private val openCvLock = Any()
    private var openCvInitialized = false

    suspend fun create(context: Context): OfflineOcrEngine = withContext(Dispatchers.IO) {
      val appContext = context.applicationContext
      val isOpenCvReady = synchronized(openCvLock) {
        if (!openCvInitialized) {
          openCvInitialized = OpenCVUtils.init(appContext)
        }
        openCvInitialized
      }
      if (!isOpenCvReady) {
        throw OfflineOcrException("OpenCV could not be loaded; offline OCR is unavailable.")
      }

      try {
        val pipeline = PaddleOCR.create(
          context = appContext,
          config = PaddleOCRConfig(recBatchSize = 1),
          engineConfig = EngineConfig(numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)),
          detModelAssetPath = DETECTION_MODEL,
          recModelAssetPath = RECOGNITION_MODEL,
          recConfigAssetPath = RECOGNITION_CONFIG,
        )
        OfflineOcrEngine(pipeline)
      } catch (error: OCRError.ModelNotFound) {
        throw OfflineOcrException("A bundled OCR model is missing: ${error.message}", error)
      } catch (error: OCRError.ConfigParseFailed) {
        throw OfflineOcrException("The bundled OCR recognition dictionary is invalid: ${error.message}", error)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Throwable) {
        throw OfflineOcrException("Offline OCR models could not be initialized: ${error.message}", error)
      }
    }
  }
}
