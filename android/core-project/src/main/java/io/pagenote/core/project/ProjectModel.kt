package io.pagenote.core.project

data class ProjectManifest(
  val format: String,
  val version: Int,
  val id: String,
  val name: String,
  val created: String? = null,
  val modified: String? = null,
  val left: String,
  val right: String,
  val leftName: String? = null,
  val preview: String? = null,
)

object ProjectValidation {
  private val ID = Regex("^[a-f0-9]{64}$")
  private val LINK = Regex("^src/.+")

  fun validate(manifest: ProjectManifest) {
    require(manifest.format == "pagenote-project" || manifest.format == "page-notes-project") { "not a pagenote project" }
    require(ID.matches(manifest.id)) { "bad project id" }
    require(manifest.name.isNotBlank()) { "bad project name" }
    require(LINK.matches(manifest.left)) { "left link must stay under src/" }
    require(LINK.matches(manifest.right)) { "right link must stay under src/" }
    manifest.preview?.let { require(LINK.matches(it)) { "preview link must stay under src/" } }
  }

  fun kindOf(left: String): String {
    val ext = left.substringAfterLast('.', "").lowercase()
    return if (ext == "md" || ext == "markdown") "markdown" else "pdf"
  }
}
