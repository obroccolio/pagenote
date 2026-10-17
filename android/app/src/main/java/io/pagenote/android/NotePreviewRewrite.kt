package io.pagenote.android

object NotePreviewRewrite {
  const val DEFAULT_ASSET_SCHEME = "pagenote-asset"
  private val ATTACHMENT_SRC = Regex("/attachments/([a-f0-9-]+\\.[a-z0-9]+)", RegexOption.IGNORE_CASE)
  private val FENCE = Regex("(?m)^ {0,3}(`{3,}|~{3,})\\s*([^\\s]*)\\s*$")

  fun forPreview(markdown: String, assetBase: String = DEFAULT_ASSET_SCHEME): String {
    if (markdown.isEmpty()) return markdown
    var text = rewriteFences(markdown)
    text = ATTACHMENT_SRC.replace(text) { "$assetBase://${it.groupValues[1]}" }
    return text
  }

  private fun rewriteFences(markdown: String): String {
    val out = StringBuilder()
    var cursor = 0
    while (true) {
      val open = FENCE.find(markdown, cursor) ?: break
      val mark = open.groupValues[1][0]
      val info = open.groupValues[2].trim().lowercase()
      if (info != "mermaid" && info != "math" && info != "latex" && info != "tex") {
        cursor = open.range.last + 1
        continue
      }
      val closePattern = Regex("(?m)^ {0,3}(${mark}{3,})\\s*$")
      val close = closePattern.find(markdown, open.range.last + 1)
      val end = close?.range?.last?.plus(1) ?: markdown.length
      val innerStart = markdown.indexOf('\n', open.range.last + 1).let {
        if (it < 0 || (close != null && it > close.range.first)) open.range.last + 1 else it + 1
      }
      val innerEnd = close?.range?.first ?: markdown.length
      val source = if (innerStart <= innerEnd) markdown.substring(innerStart, innerEnd).trim('\n') else ""
      out.append(markdown, cursor, open.range.first)
      if (info == "mermaid") {
        val state = if (source.isBlank()) "EMPTY" else "READY"
        out.append("> Diagram source ($state)\n>\n")
        source.lines().forEach { out.append("> ").append(it).append('\n') }
      } else {
        out.append("> Formula\n>\n")
        source.lines().forEach { out.append("> ").append(it).append('\n') }
      }
      if (close == null) break
      cursor = end
    }
    out.append(markdown, cursor, markdown.length)
    return out.toString()
  }
}
