package io.pagenote.feature.pen

import org.junit.Test

class PenStrokeTest {
  private fun stroke() = PenStroke("pen", "#ff0000", 2.0, listOf(PenPoint(1f, 2f, 0.5f)))

  @Test
  fun validStrokePasses() {
    PenValidation.validate(stroke(), 1)
  }

  @Test(expected = IllegalArgumentException::class)
  fun badColorRejected() {
    PenValidation.validate(stroke().copy(color = "red"), 1)
  }
}
