package io.pagenote.android

data class PdfViewportPointer(val id: Long, val x: Float, val y: Float)

data class PdfViewportGesture(
  val centroidX: Float,
  val centroidY: Float,
  val zoomChange: Float,
  val panX: Float,
  val panY: Float,
)

data class PdfViewportTransform(
  val scale: Float,
  val translationX: Float,
  val translationY: Float,
) {
  companion object {
    val DEFAULT = PdfViewportTransform(scale = 1f, translationX = 0f, translationY = 0f)
    const val MIN_SCALE = 1f
    const val MAX_SCALE = 6f

    fun calculateTwoPointerGesture(
      previous: List<PdfViewportPointer>,
      current: List<PdfViewportPointer>,
    ): PdfViewportGesture? {
      val previousById = previous.associateBy(PdfViewportPointer::id)
      val matching = current.mapNotNull { pointer ->
        previousById[pointer.id]?.let { it to pointer }
      }
      if (matching.size < 2 || matching.any { (before, after) ->
          listOf(before.x, before.y, after.x, after.y).any { !it.isFinite() }
        }
      ) {
        return null
      }

      val previousCentroidX = matching.sumOf { it.first.x.toDouble() }.toFloat() / matching.size
      val previousCentroidY = matching.sumOf { it.first.y.toDouble() }.toFloat() / matching.size
      val centroidX = matching.sumOf { it.second.x.toDouble() }.toFloat() / matching.size
      val centroidY = matching.sumOf { it.second.y.toDouble() }.toFloat() / matching.size
      val previousRadius = matching.sumOf { (before, _) ->
        distance(before.x, before.y, previousCentroidX, previousCentroidY).toDouble()
      }.toFloat() / matching.size
      val currentRadius = matching.sumOf { (_, after) ->
        distance(after.x, after.y, centroidX, centroidY).toDouble()
      }.toFloat() / matching.size

      return PdfViewportGesture(
        centroidX = centroidX,
        centroidY = centroidY,
        zoomChange = if (previousRadius > 0f) currentRadius / previousRadius else 1f,
        panX = centroidX - previousCentroidX,
        panY = centroidY - previousCentroidY,
      )
    }

    fun applyGesture(
      current: PdfViewportTransform,
      viewportWidthPx: Float,
      viewportHeightPx: Float,
      pageWidthPx: Float,
      pageHeightPx: Float,
      centroidX: Float,
      centroidY: Float,
      zoomChange: Float,
      panX: Float,
      panY: Float,
    ): PdfViewportTransform {
      val measurements = listOf(
        viewportWidthPx,
        viewportHeightPx,
        pageWidthPx,
        pageHeightPx,
        centroidX,
        centroidY,
        zoomChange,
        panX,
        panY,
        current.scale,
        current.translationX,
        current.translationY,
      )
      if (measurements.any { !it.isFinite() } ||
        viewportWidthPx <= 0f || viewportHeightPx <= 0f ||
        pageWidthPx <= 0f || pageHeightPx <= 0f ||
        zoomChange <= 0f || current.scale <= 0f
      ) {
        return current
      }

      val nextScale = (current.scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
      val scaleRatio = nextScale / current.scale
      val nextX = current.translationX * scaleRatio +
        (centroidX - viewportWidthPx / 2f) * (1f - scaleRatio) + panX
      val nextY = current.translationY * scaleRatio +
        (centroidY - viewportHeightPx / 2f) * (1f - scaleRatio) + panY
      val maxTranslationX = maxOf(0f, (pageWidthPx * nextScale - viewportWidthPx) / 2f)
      val maxTranslationY = maxOf(0f, (pageHeightPx * nextScale - viewportHeightPx) / 2f)
      val clampedX = nextX.coerceIn(-maxTranslationX, maxTranslationX)
      val clampedY = nextY.coerceIn(-maxTranslationY, maxTranslationY)
      return PdfViewportTransform(
        scale = nextScale,
        translationX = if (clampedX == 0f) 0f else clampedX,
        translationY = if (clampedY == 0f) 0f else clampedY,
      )
    }

    fun fitWidthScale(viewportWidthPx: Float, pageWidthPx: Float): Float {
      if (!viewportWidthPx.isFinite() || !pageWidthPx.isFinite() ||
        viewportWidthPx <= 0f || pageWidthPx <= 0f
      ) {
        return MIN_SCALE
      }
      return (viewportWidthPx / pageWidthPx).coerceIn(MIN_SCALE, MAX_SCALE)
    }

    fun fitWidth(viewportWidthPx: Float, pageWidthPx: Float): PdfViewportTransform =
      PdfViewportTransform(
        scale = fitWidthScale(viewportWidthPx, pageWidthPx),
        translationX = 0f,
        translationY = 0f,
      )

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
      kotlin.math.hypot(x1 - x2, y1 - y2)
  }
}
