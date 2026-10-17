package io.pagenote.feature.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineOcrEngineTest {
  @Test
  fun bundledModelsRecognizeChineseAndEnglishWithoutChangingInputImage() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val bitmap = Bitmap.createBitmap(1200, 180, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    canvas.drawText(
      "离线 OCR Test 123",
      24f,
      125f,
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 76f
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
      },
    )
    val originalPixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(originalPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    assertTrue(originalPixels.any { it != Color.WHITE })

    val engine = OfflineOcrEngine.create(context)
    try {
      val result = engine.recognize(bitmap)
      val recognized = OcrResultPolicy.textOf(result)
      assertFalse(result.warnings.joinToString(" "), result.status == "failed")
      assertTrue("Expected Chinese in: $recognized", recognized.contains("离线"))
      assertTrue("Expected English in: $recognized", recognized.lowercase().contains("test"))

      val afterPixels = IntArray(bitmap.width * bitmap.height)
      bitmap.getPixels(afterPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
      assertTrue(originalPixels.contentEquals(afterPixels))
    } finally {
      engine.close()
      bitmap.recycle()
    }
  }

  @Test
  fun recycledBitmapReturnsAnExplicitFailure() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val engine = OfflineOcrEngine.create(context)
    val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { recycle() }
    try {
      val result = engine.recognize(bitmap)
      assertTrue(OcrResultPolicy.needsVisualReview(result))
      assertTrue(result.warnings.single().contains("recycled"))
    } finally {
      engine.close()
    }
  }
}
