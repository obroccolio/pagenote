package io.pagenote.core.reader

data class PreparedManifest(
  val version: Int = PreparedCache.VERSION,
  val documentId: String,
  val page: Int,
  val sourceRevision: String,
  val kind: String,
  val region: PageRegion,
  val cacheKey: String,
  val width: Int,
  val height: Int,
  val scale: Double,
  val warnings: List<String> = emptyList(),
  val visualReviewRequired: Boolean = true,
)

object PreparedManifestStore {
  private val SOURCE_REVISION = Regex("^[a-f0-9]{64}$")

  fun validate(manifest: PreparedManifest) {
    require(manifest.version == PreparedCache.VERSION) { "bad manifest version" }
    require(manifest.page >= 1) { "bad page" }
    require(SOURCE_REVISION.matches(manifest.sourceRevision)) { "bad sourceRevision" }
    require(manifest.kind == "pdf" || manifest.kind == "markdown") { "bad kind" }
    PreparedCache.validateRegion(manifest.region)
    require(manifest.cacheKey.isNotBlank()) { "bad cacheKey" }
    require(manifest.width > 0 && manifest.height > 0) { "bad page size" }
    require(manifest.visualReviewRequired) { "visual review must stay required" }
  }

  fun isHit(cached: PreparedManifest, sourceRevision: String, cacheKey: String): Boolean {
    return cached.version == PreparedCache.VERSION &&
      cached.sourceRevision == sourceRevision &&
      cached.cacheKey == cacheKey
  }
}
