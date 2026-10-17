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
  val WRITE_TOOLS = listOf("write_note", "patch_note", "write_review_status", "write_handoff")

  data class ParsedPatch(val start: Int, val end: Int, val replacement: String)

  fun parsePatch(args: Map<String, String>, pageLength: Int): ParsedPatch {
    val replacement = args["replacement"] ?: throw IllegalArgumentException("missing replacement")
    val start = args["start"]?.toIntOrNull() ?: throw IllegalArgumentException("bad start")
    require(start in 0..pageLength) { "bad start: $start" }
    val end = args["end"]?.toIntOrNull() ?: minOf(pageLength, start + replacement.length)
    require(end in start..pageLength) { "bad end: $end" }
    return ParsedPatch(start, end, replacement)
  }

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
    "write_note" to { request -> writeNote(resolver, project, request) },
    "patch_note" to { request -> patchNote(resolver, project, request) },
    "write_review_status" to { request -> writeReviewStatus(resolver, project, request) },
    "write_handoff" to { request -> writeHandoff(resolver, project, renderer, request) },
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

private suspend fun writeNote(
  resolver: ContentResolver,
  project: OpenedProject,
  request: ToolRequest,
): ToolResult {
  val page = try {
    ProjectAgentTools.parsePage(request.args, -1)
  } catch (error: IllegalArgumentException) {
    return ToolResult(ok = false, detail = error.message ?: "bad page")
  }
  val markdown = request.args["markdown"] ?: return ToolResult(ok = false, detail = "missing markdown")
  val expectedRevision = request.args["expectedRevision"]
    ?: return ToolResult(ok = false, detail = "missing expectedRevision")
  return try {
    val store = ProjectNoteStore(resolver, project.notesUri)
    val latest = store.read()
    val edited = io.pagenote.feature.notes.PageNoteEditor.replacePage(
      latestMarkdown = latest, page = page, replacement = markdown, expectedRevision = expectedRevision,
    )
    store.write(edited.markdown, expectedOriginal = latest)
    ToolResult(ok = true, detail = "page=$page revision=${edited.pageRevision}", touchedPage = page, updatedPage = page)
  } catch (error: Exception) {
    ToolResult(ok = false, detail = error.message ?: "write_note failed")
  }
}

private suspend fun patchNote(
  resolver: ContentResolver,
  project: OpenedProject,
  request: ToolRequest,
): ToolResult {
  val page = try {
    ProjectAgentTools.parsePage(request.args, -1)
  } catch (error: IllegalArgumentException) {
    return ToolResult(ok = false, detail = error.message ?: "bad page")
  }
  val expectedRevision = request.args["expectedRevision"]
    ?: return ToolResult(ok = false, detail = "missing expectedRevision")
  return try {
    val store = ProjectNoteStore(resolver, project.notesUri)
    val latest = store.read()
    val current = io.pagenote.feature.notes.NotePage.pageAt(latest, page)
    val parsed = try {
      ProjectAgentTools.parsePatch(request.args, current.length)
    } catch (error: IllegalArgumentException) {
      return ToolResult(ok = false, detail = error.message ?: "bad patch")
    }
    val patched = io.pagenote.feature.notes.NoteRange.patch(current, parsed.start, parsed.replacement, parsed.end)
    val edited = io.pagenote.feature.notes.PageNoteEditor.replacePage(
      latestMarkdown = latest, page = page, replacement = patched, expectedRevision = expectedRevision,
    )
    store.write(edited.markdown, expectedOriginal = latest)
    ToolResult(ok = true, detail = "page=$page revision=${edited.pageRevision}", touchedPage = page, updatedPage = page)
  } catch (error: Exception) {
    ToolResult(ok = false, detail = error.message ?: "patch_note failed")
  }
}

private suspend fun writeReviewStatus(
  resolver: ContentResolver,
  project: OpenedProject,
  request: ToolRequest,
): ToolResult {
  val id = request.args["id"] ?: return ToolResult(ok = false, detail = "missing id")
  val status = request.args["status"] ?: return ToolResult(ok = false, detail = "missing status")
  val noteRewritten = request.args["noteRewritten"]?.toBooleanStrictOrNull() ?: false
  return try {
    val store = ProjectReviewStore(resolver, project.sourceDirectoryUri, project.reviewsUri)
    store.transition(id, status, noteRewritten = noteRewritten)
    ToolResult(ok = true, detail = "review=$id status=$status")
  } catch (error: Exception) {
    ToolResult(ok = false, detail = error.message ?: "write_review_status failed")
  }
}

private suspend fun writeHandoff(
  resolver: ContentResolver,
  project: OpenedProject,
  renderer: PdfDocumentSession?,
  request: ToolRequest,
): ToolResult {
  val page = try {
    ProjectAgentTools.parsePage(request.args, -1)
  } catch (error: IllegalArgumentException) {
    return ToolResult(ok = false, detail = error.message ?: "bad page")
  }
  val summary = request.args["summary"] ?: return ToolResult(ok = false, detail = "missing summary")
  val expectedRevision = request.args["expectedRevision"]
    ?: return ToolResult(ok = false, detail = "missing expectedRevision")
  val sourceRevision = request.args["sourceRevision"]
    ?: return ToolResult(ok = false, detail = "missing sourceRevision")
  return try {
    val notes = ProjectNoteStore(resolver, project.notesUri).read()
    val current = io.pagenote.feature.notes.NotePage.pageAt(notes, page)
    val currentRev = io.pagenote.core.project.PageNotes.revision(current)
    val store = ProjectHandoffStore(resolver, project.sourceDirectoryUri, project.handoffsUri)
    store.write(page, summary, expectedRevision, sourceRevision, currentRev)
    ToolResult(ok = true, detail = "handoff page=$page", touchedPage = page, updatedPage = page)
  } catch (error: Exception) {
    ToolResult(ok = false, detail = error.message ?: "write_handoff failed")
  }
}
