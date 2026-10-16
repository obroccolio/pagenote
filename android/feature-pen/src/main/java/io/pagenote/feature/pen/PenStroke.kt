package io.pagenote.feature.pen

data class PenPoint(val x: Float, val y: Float, val pressure: Float)

data class PenStroke(
  val type: String,
  val color: String,
  val width: Double,
  val points: List<PenPoint>,
)

object PenValidation {
  private val TYPES = setOf("pen", "highlight", "line", "arrow", "rect", "ellipse")
  private val COLOR = Regex("^#[a-fA-F0-9]{6}$")

  fun validate(stroke: PenStroke, page: Int) {
    require(page >= 1) { "bad page" }
    require(TYPES.contains(stroke.type)) { "bad stroke type" }
    require(COLOR.matches(stroke.color)) { "bad color" }
    require(stroke.width.isFinite() && stroke.width >= 0.1) { "bad width" }
    require(stroke.points.isNotEmpty() && stroke.points.size <= 10000) { "bad points" }
  }
}
