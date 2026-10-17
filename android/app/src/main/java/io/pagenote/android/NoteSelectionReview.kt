package io.pagenote.android

import io.pagenote.core.project.PageNotes
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class NoteSelectionReviewDraft private constructor(
  val quote: String,
  val context: String,
  val page: Int,
  val revision: String,
  val anchor: JsonObject,
) {
  fun requireCurrentPage(currentPageText: String) {
    require(PageNotes.revision(currentPageText) == revision) {
      "note page changed since selection capture"
    }
  }

  companion object {
    private const val MAX_QUOTE_LENGTH = 100_000
    private const val MAX_CONTEXT_LENGTH = 1_000_000

    fun capture(page: Int, context: String, start: Int, end: Int): NoteSelectionReviewDraft {
      require(page >= 1) { "page must be one-based" }
      require(context.length <= MAX_CONTEXT_LENGTH) { "note page context is too large" }

      val selectionStart = minOf(start, end)
      val selectionEnd = maxOf(start, end)
      require(selectionStart >= 0 && selectionEnd <= context.length) { "selection is outside note page" }
      require(selectionStart < selectionEnd) { "selection must not be empty" }

      val quote = context.substring(selectionStart, selectionEnd)
      require(!quote.isBlank()) { "selection must contain non-whitespace text" }
      require(quote.length <= MAX_QUOTE_LENGTH) { "selection is too large" }

      val anchor = buildJsonObject {
        put("coordinateSystem", JsonPrimitive("markdown-source-utf16"))
        put("start", JsonPrimitive(selectionStart))
        put("end", JsonPrimitive(selectionEnd))
      }
      return NoteSelectionReviewDraft(
        quote = quote,
        context = context,
        page = page,
        revision = PageNotes.revision(context),
        anchor = anchor,
      )
    }
  }
}
