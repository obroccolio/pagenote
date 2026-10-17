package io.pagenote.feature.ocr

data class OcrPoint(val x: Float, val y: Float)

data class RecognizedTextBlock(
  val text: String,
  val confidence: Double,
  val points: List<OcrPoint>,
)

object OcrResultMapper {
  const val ENGINE = "pp-ocrv6-tiny-onnx"

  fun map(
    imageWidth: Int,
    imageHeight: Int,
    recognized: List<RecognizedTextBlock>,
  ): OcrResult {
    require(imageWidth > 0 && imageHeight > 0) { "OCR image dimensions must be positive" }

    val warnings = mutableListOf<String>()
    if (recognized.isEmpty()) {
      warnings += "No text was detected; verify the original image."
    }
    val blocks = recognized.mapNotNull { item ->
      require(item.points.size == 4) { "OCR quadrilateral must contain four points" }
      require(item.confidence in 0.0..1.0) { "OCR confidence must be between 0 and 1" }
      require(item.points.all { it.x.isFinite() && it.y.isFinite() }) { "OCR box coordinates must be finite" }

      val left = item.points.minOf { it.x }.coerceIn(0f, imageWidth.toFloat())
      val top = item.points.minOf { it.y }.coerceIn(0f, imageHeight.toFloat())
      val right = item.points.maxOf { it.x }.coerceIn(0f, imageWidth.toFloat())
      val bottom = item.points.maxOf { it.y }.coerceIn(0f, imageHeight.toFloat())

      if (item.text.isBlank() || right <= left || bottom <= top) {
        warnings += "An OCR region had no usable text or area and was omitted."
        null
      } else {
        OcrBlock(
          text = item.text,
          confidence = item.confidence,
          x = left * 1000.0 / imageWidth,
          y = top * 1000.0 / imageHeight,
          width = (right - left) * 1000.0 / imageWidth,
          height = (bottom - top) * 1000.0 / imageHeight,
        )
      }
    }

    return OcrResult(
      engine = ENGINE,
      status = if (warnings.isEmpty()) "ok" else "partial",
      blocks = blocks,
      warnings = warnings,
    ).also(OcrResultPolicy::validate)
  }
}
