package io.pagenote.feature.ocr

data class NormalizedOcrRegion(
  val left: Int,
  val top: Int,
  val right: Int,
  val bottom: Int,
) {
  fun toPixelRect(imageWidth: Int, imageHeight: Int): OcrPixelRect {
    require(imageWidth > 0 && imageHeight > 0) { "OCR image dimensions must be positive" }
    require(left in 0..1000 && top in 0..1000 && right in 0..1000 && bottom in 0..1000) {
      "OCR region must be within normalized page bounds 0..1000"
    }
    require(right > left && bottom > top) { "OCR region must have positive area" }

    val pixelLeft = (left.toLong() * imageWidth / NORMALIZED_SCALE).toInt().coerceIn(0, imageWidth)
    val pixelTop = (top.toLong() * imageHeight / NORMALIZED_SCALE).toInt().coerceIn(0, imageHeight)
    val pixelRight = ceilPixel(right, imageWidth)
    val pixelBottom = ceilPixel(bottom, imageHeight)
    require(pixelRight > pixelLeft && pixelBottom > pixelTop) {
      "OCR region does not intersect the image"
    }

    return OcrPixelRect(pixelLeft, pixelTop, pixelRight, pixelBottom)
  }

  private fun ceilPixel(normalized: Int, pixels: Int): Int =
    ((normalized.toLong() * pixels + NORMALIZED_SCALE - 1) / NORMALIZED_SCALE)
      .toInt()
      .coerceIn(0, pixels)

  private companion object {
    const val NORMALIZED_SCALE = 1000L
  }
}

data class OcrPixelRect(
  val left: Int,
  val top: Int,
  val right: Int,
  val bottom: Int,
) {
  init {
    require(left >= 0 && top >= 0) { "OCR crop origin must be non-negative" }
    require(right > left && bottom > top) { "OCR crop must have positive area" }
  }

  val width: Int get() = right - left
  val height: Int get() = bottom - top
}
