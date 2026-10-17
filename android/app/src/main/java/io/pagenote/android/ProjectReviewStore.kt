package io.pagenote.android

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import io.pagenote.feature.annotations.ReviewCreate
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ProjectReviewStore(
  private val resolver: ContentResolver,
  private val sourceDirectoryUri: Uri,
  reviewsUri: Uri?,
) {
  private val json = Json { ignoreUnknownKeys = true }
  private var reviewsUri: Uri? = reviewsUri

  fun list(page: Int? = null): JsonArray {
    val uri = reviewsUri ?: return JsonArray(emptyList())
    val records = readRecords(uri)
    if (page == null) return records
    return JsonArray(records.filter {
      (it as? JsonObject)?.get("page")?.jsonPrimitive?.content?.toIntOrNull() == page
    })
  }

  fun create(side: String, kind: String, page: Int, instruction: String, revision: String?): JsonObject {
    return create(ReviewCreate(
      side = side,
      kind = kind,
      page = page,
      instruction = instruction,
      revision = revision,
    ))
  }

  fun create(input: ReviewCreate): JsonObject {
    validate(input)
    val uri = ensureFile()
    val records = readRecords(uri).toMutableList()
    val number = (records.mapNotNull {
      (it as? JsonObject)?.get("number")?.jsonPrimitive?.content?.toIntOrNull()
    }.maxOrNull() ?: 0) + 1
    val record = buildRecord(input, number)
    records.add(record)
    writeRecords(uri, JsonArray(records))
    return record
  }

  fun find(id: String): JsonObject? {
    val uri = reviewsUri ?: return null
    return readRecords(uri).firstOrNull {
      (it as? JsonObject)?.get("id")?.jsonPrimitive?.content == id
    } as? JsonObject
  }

  fun transition(
    id: String,
    to: String,
    authorizeApplied: (JsonObject) -> Boolean = { false },
  ): JsonObject {
    val uri = reviewsUri ?: throw NoSuchElementException("review not found")
    val records = readRecords(uri).toMutableList()
    val index = records.indexOfFirst { (it as? JsonObject)?.get("id")?.jsonPrimitive?.content == id }
    if (index < 0) throw NoSuchElementException("review not found")
    val current = records[index] as? JsonObject ?: throw NoSuchElementException("review not found")
    if (to == "applied") {
      require(authorizeApplied(current)) { "applied requires a changed note write matching the review page and revision" }
    }
    if (!ReviewSafBoundary.canTransition(current, to)) {
      throw IllegalStateException("review cannot transition from open")
    }
    val updated = JsonObject(current + mapOf(
      "status" to JsonPrimitive(to),
      "updated" to JsonPrimitive(java.time.Instant.now().toString()),
    ))
    records[index] = updated
    writeRecords(uri, JsonArray(records))
    return updated
  }

  fun delete(id: String): Boolean {
    val uri = reviewsUri ?: return false
    val records = readRecords(uri)
    val kept = records.filterNot { (it as? JsonObject)?.get("id")?.jsonPrimitive?.content == id }
    if (kept.size == records.size) return false
    writeRecords(uri, JsonArray(kept))
    return true
  }

  private fun validate(input: ReviewCreate) {
    require(input.side == "document" || input.side == "note") { "bad side" }
    require(input.kind == "overall" || input.kind == "selection" || input.kind == "region") { "bad kind" }
    require(input.page >= 1) { "bad page" }
    val trimmed = input.instruction.trim()
    require(trimmed.isNotEmpty() && trimmed.length <= 20000) { "bad instruction" }
    require((input.quote ?: "").length <= 100000) { "quote too large" }
    require((input.context ?: "").length <= 1000000) { "context too large" }
    input.anchor?.let {
      require(Json.encodeToString(JsonElement.serializer(), it).length <= 500000) { "anchor too large" }
    }
  }

  private fun buildRecord(input: ReviewCreate, number: Int): JsonObject {
    val trimmed = input.instruction.trim()
    return buildJsonObjectHelper(mapOf(
      "id" to JsonPrimitive(java.util.UUID.randomUUID().toString()),
      "number" to JsonPrimitive(number),
      "side" to JsonPrimitive(input.side),
      "kind" to JsonPrimitive(input.kind),
      "page" to JsonPrimitive(input.page),
      "scope" to JsonPrimitive(if (input.scope == "document") "document" else "page"),
      "instruction" to JsonPrimitive(trimmed),
      "quote" to JsonPrimitive(input.quote ?: ""),
      "context" to JsonPrimitive(input.context ?: ""),
      "anchor" to (input.anchor ?: JsonNull),
      "status" to JsonPrimitive("open"),
      "created" to JsonPrimitive(java.time.Instant.now().toString()),
    ), input.revision)
  }

  private fun buildJsonObjectHelper(fields: Map<String, kotlinx.serialization.json.JsonElement>, revision: String?): JsonObject {
    val map = fields.toMutableMap()
    if (revision != null) map["revision"] = JsonPrimitive(revision)
    else map["revision"] = kotlinx.serialization.json.JsonNull
    return JsonObject(map)
  }

  private fun ensureFile(): Uri {
    reviewsUri?.let { return it }
    val created = DocumentsContract.createDocument(
      resolver, sourceDirectoryUri, "application/json", "ai-reviews.json",
    ) ?: throw IOException("无法创建项目 JSON 文件")
    reviewsUri = created
    resolver.openOutputStream(created, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
      it.write("[]")
    } ?: throw IOException("无法写入项目 JSON 文件")
    return created
  }

  private fun readRecords(uri: Uri): JsonArray {
    val text = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
      ?: throw IOException("无法读取项目 JSON 文件")
    try {
      val element = json.parseToJsonElement(text)
      return element as? JsonArray ?: throw IOException("项目 JSON 文件格式无效")
    } catch (error: IllegalArgumentException) {
      throw IOException("项目 JSON 文件格式无效", error)
    }
  }

  private fun writeRecords(uri: Uri, records: JsonArray) {
    resolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
      it.write(Json.encodeToString(JsonArray.serializer(), records))
    } ?: throw IOException("无法写入项目 JSON 文件")
  }
}
