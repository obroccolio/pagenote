package io.pagenote.feature.annotations

data class ReviewRecord(
  val id: String,
  val number: Int,
  val side: String,
  val kind: String,
  val page: Int,
  val scope: String,
  val instruction: String,
  val status: String,
  val created: String,
)

object ReviewValidation {
  fun validate(side: String, kind: String, page: Int, instruction: String) {
    require(side == "document" || side == "note") { "bad side" }
    require(kind == "overall" || kind == "selection" || kind == "region") { "bad kind" }
    require(page >= 1) { "bad page" }
    require(instruction.isNotBlank() && instruction.length <= 20000) { "bad instruction" }
  }

  fun canApply(status: String): Boolean = status == "open"
}
