package io.pagenote.feature.notes

import io.pagenote.core.project.PageNotes

data class PageNoteEditResult(
  val markdown: String,
  val pageRevision: String,
)

class PageNoteConflictException : IllegalStateException("笔记页冲突：内容已被其他修改更新，请重新载入后再保存")

object PageNoteEditor {
  fun replacePage(
    latestMarkdown: String,
    page: Int,
    replacement: String,
    expectedRevision: String,
  ): PageNoteEditResult {
    require(page >= 1) { "页码必须从 1 开始" }
    val pages = PageNotes.split(latestMarkdown).toMutableList()
    while (pages.size < page) pages += ""

    val index = page - 1
    val currentRevision = PageNotes.revision(pages[index])
    if (currentRevision != expectedRevision) throw PageNoteConflictException()

    pages[index] = replacement
    return PageNoteEditResult(
      markdown = PageNotes.join(pages),
      pageRevision = PageNotes.revision(replacement),
    )
  }
}
