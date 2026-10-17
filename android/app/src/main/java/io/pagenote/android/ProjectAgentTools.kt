package io.pagenote.android

import android.content.ContentResolver
import io.pagenote.agent.ToolRequest
import io.pagenote.agent.ToolResult
import io.pagenote.core.project.PageNotes
import io.pagenote.core.reader.PdfTextExtractionStatus
import io.pagenote.feature.notes.NotePage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

object ProjectAgentTools {
  val READ_TOOLS = listOf("read_page", "read_review", "read_handoff")

  fun parsePage(args: Map<String, String>, currentPage: Int): Int {
    val raw = args["page"] ?: return currentPage.also { require(it >= 1) { "bad page" } }
    val page = raw.toIntOrNull() ?: throw IllegalArgumentException("bad page: $raw")
    require(page >= 1) { "bad page: $raw" }
    return page
  }

  fun validReviewStatus(status: String?): Boolean =
    status == null || status == "open" || status == "applied" || status == "dismissed"

  fun handlers(
    resolver: ContentResolver,
    project: OpenedProject,
    renderer: PdfDocumentSession?,
    currentPage: Int,
  ): Map<String, suspend (ToolRequest) -> ToolResult> = mapOf(
    "read_page" to { request -> readPage(resolver, project, renderer, currentPage, request) },
    "read_review" to { request -> readReview(resolver, project, request) },
    "read_handoff" to { request -> readHandoff(resolver, project, renderer, request) },
  )

  private suspend fun readPage(
    resolver: ContentResolver,
    project: OpenedProject,
    renderer: PdfDocumentSession?,
    currentPage: Int,
    request: ToolRequest,
  ): ToolResult {
    val page = try {
      parsePage(request.args, currentPage)
    } catch (error: IllegalArgumentException) {
      return ToolResult(ok = false, detail = error.message ?: "bad page")
    }
    return try {
      val notes = ProjectNoteStore(resolver, project.notesUri).read()
      val text = NotePage.pageAt(notes, page)
      val revision = PageNotes.revision(text)
      val native = renderer?.let {
        runCatching { it.extractText(page - 1) }.getOrNull()
      }
      val nativeText = if (native != null && native.status == PdfTextExtractionStatus.AVAILABLE) {
        native.fullText
      } else {
        "unavailable"
      }
      ToolResult(
        ok = true,
        detail = "page=$page revision=$revision nativeChars=${nativeText.length} noteChars=${text.length}",
        touchedPage = page,
      )
    } catch (error: Exception) {
      ToolResult(ok = false, detail = error.message ?: "read_page failed")
    }
  }

  private suspend fun readReview(
    resolver: ContentResolver,
    project: OpenedProject,
    request: ToolRequest,
  ): ToolResult {
    val status = request.args["status"]
    if (!validReviewStatus(status)) {
      return ToolResult(ok = false, detail = "bad status: $status")
    }
    return try {
      val store = ProjectReviewStore(resolver, project.sourceDirectoryUri, project.reviewsUri)
      val records = store.list(null)
      val filtered = records.filter { element ->
        val obj = element as? JsonObject ?: return@filter false
        if (status == null) return@filter true
        obj["status"]?.jsonPrimitive?.content == status
      }
      val summary = filtered.take(20).mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val number = obj["number"]?.jsonPrimitive?.content ?: "?"
        val itemStatus = obj["status"]?.jsonPrimitive?.content ?: "?"
        val instruction = obj["instruction"]?.jsonPrimitive?.content.orEmpty().take(120)
        "#$number[$itemStatus] $instruction"
      }.joinToString("\n")
      ToolResult(ok = true, detail = "reviews=${filtered.size}\n$summary")
    } catch (error: Exception) {
      ToolResult(ok = false, detail = error.message ?: "read_review failed")
    }
  }

  private suspend fun readHandoff(
    resolver: ContentResolver,
    project: OpenedProject,
    renderer: PdfDocumentSession?,
    request: ToolRequest,
  ): ToolResult {
    val raw = request.args["page"] ?: return ToolResult(ok = false, detail = "missing page")
    val page = raw.toIntOrNull()?.takeIf { it >= 1 }
      ?: return ToolResult(ok = false, detail = "bad page: $raw")
    return try {
      val notes = ProjectNoteStore(resolver, project.notesUri).read()
      val noteRev = PageNotes.revision(NotePage.pageAt(notes, page))
      val srcRev = renderer?.sourceRevision.orEmpty()
      val store = ProjectHandoffStore(resolver, project.sourceDirectoryUri, project.handoffsUri)
      val view = store.view(page, noteRev, srcRev)
      val record = view.record ?: return ToolResult(ok = true, detail = "handoff=null")
      ToolResult(
        ok = true,
        detail = "stale=${view.stale} summary=${record.summary.take(500)}",
        touchedPage = page,
      )
    } catch (error: Exception) {
      ToolResult(ok = false, detail = error.message ?: "read_handoff failed")
    }
  }
}
