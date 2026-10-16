package io.pagenote.feature.annotations

import java.io.File
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Mirror of desktop ai-reviews.json semantics (server.mjs POST/DELETE/PATCH).
 * Records are kept as JsonObjects so unknown fields (quote/context/revision/anchor/...) survive round-trips.
 */
object ReviewStore {
  private val json = Json { ignoreUnknownKeys = true }
  private val SIDES = setOf("document", "note")
  private val KINDS = setOf("overall", "selection", "region")
  private val STATUSES = setOf("open", "applied", "dismissed")

  fun read(file: File): JsonArray {
    if (!file.exists()) return JsonArray(emptyList())
    val element = json.parseToJsonElement(file.readText(Charsets.UTF_8))
    return element as? JsonArray ?: throw IllegalArgumentException("bad ai-reviews file")
  }

  fun create(
    srcDir: File,
    side: String,
    kind: String,
    page: Int,
    instruction: String,
    scope: String? = null,
  ): JsonObject {
    require(SIDES.contains(side)) { "bad side" }
    require(KINDS.contains(kind)) { "bad kind" }
    require(page >= 1) { "bad page" }
    val trimmed = instruction.trim()
    require(trimmed.isNotEmpty() && trimmed.length <= 20000) { "bad instruction" }
    val file = File(srcDir, "ai-reviews.json")
    val records = read(file).toMutableList()
    val number = (records.mapNotNull { (it as? JsonObject)?.get("number")?.jsonPrimitive?.content?.toIntOrNull() }.maxOrNull() ?: 0) + 1
    val record = buildJsonObject {
      put("id", JsonPrimitive(UUID.randomUUID().toString()))
      put("number", JsonPrimitive(number))
      put("side", JsonPrimitive(side))
      put("kind", JsonPrimitive(kind))
      put("page", JsonPrimitive(page))
      put("scope", JsonPrimitive(if (scope == "document") "document" else "page"))
      put("instruction", JsonPrimitive(trimmed))
      put("status", JsonPrimitive("open"))
      put("created", JsonPrimitive(java.time.Instant.now().toString()))
    }
    records.add(record)
    atomicWriteWithHistory(file, JsonArray(records))
    return record
  }

  /** Deletes only the record; note text is never touched. Returns true when a record was removed. */
  fun delete(srcDir: File, id: String): Boolean {
    val file = File(srcDir, "ai-reviews.json")
    val records = read(file)
    val kept = records.filterNot { (it as? JsonObject)?.get("id")?.jsonPrimitive?.content == id }
    if (kept.size == records.size) return false
    atomicWriteWithHistory(file, JsonArray(kept))
    return true
  }

  /**
   * Status transition with the Android task guard: `applied` requires a successful
   * note rewrite first ([noteRewritten] must be true). Desktop PATCH field limits apply:
   * only instruction/status/boardPosition may change here.
   */
  fun transition(srcDir: File, id: String, to: String, noteRewritten: Boolean = false): JsonObject {
    require(STATUSES.contains(to)) { "bad status" }
    if (to == "applied") require(noteRewritten) { "mark applied only after a successful rewrite" }
    val file = File(srcDir, "ai-reviews.json")
    val records = read(file).toMutableList()
    val index = records.indexOfFirst { (it as? JsonObject)?.get("id")?.jsonPrimitive?.content == id }
    if (index < 0) throw NoSuchElementException("review not found")
    val current = records[index].jsonObject
    val updated = buildJsonObject {
      current.forEach { (key, value) -> put(key, value) }
      put("status", JsonPrimitive(to))
      put("updated", JsonPrimitive(java.time.Instant.now().toString()))
    }
    records[index] = updated
    atomicWriteWithHistory(file, JsonArray(records))
    return updated
  }

  /** Desktop PATCH instruction field limits. */
  fun updateInstruction(srcDir: File, id: String, instruction: String): JsonObject {
    val trimmed = instruction.trim()
    require(trimmed.isNotEmpty() && trimmed.length <= 20000) { "bad instruction" }
    return patchField(srcDir, id, "instruction", JsonPrimitive(trimmed))
  }

  /** Desktop PATCH boardPosition field limits. */
  fun updateBoardPosition(srcDir: File, id: String, x: Double, y: Double): JsonObject {
    require(x.isFinite() && y.isFinite() && x >= 0 && y >= 0 && x <= 100000 && y <= 100000) {
      "bad board position"
    }
    val position = buildJsonObject {
      put("x", JsonPrimitive(x))
      put("y", JsonPrimitive(y))
    }
    return patchField(srcDir, id, "boardPosition", position)
  }

  private fun patchField(srcDir: File, id: String, field: String, value: kotlinx.serialization.json.JsonElement): JsonObject {
    val file = File(srcDir, "ai-reviews.json")
    val records = read(file).toMutableList()
    val index = records.indexOfFirst { (it as? JsonObject)?.get("id")?.jsonPrimitive?.content == id }
    if (index < 0) throw NoSuchElementException("review not found")
    val current = records[index].jsonObject
    val updated = buildJsonObject {
      current.forEach { (key, old) -> put(key, old) }
      put(field, value)
      put("updated", JsonPrimitive(java.time.Instant.now().toString()))
    }
    records[index] = updated
    atomicWriteWithHistory(file, JsonArray(records))
    return updated
  }

  private fun atomicWriteWithHistory(file: File, records: JsonArray) {
    if (file.exists()) {
      val history = File(file.parentFile, "review-history")
      history.mkdirs()
      val backup = File(history, "${System.currentTimeMillis()}-${UUID.randomUUID()}.json")
      val backupTmp = File(history, backup.name + ".tmp")
      backupTmp.writeText(file.readText(Charsets.UTF_8), Charsets.UTF_8)
      if (!backupTmp.renameTo(backup)) {
        backup.writeText(file.readText(Charsets.UTF_8), Charsets.UTF_8)
        backupTmp.delete()
      }
    } else {
      File(file.parentFile, "review-history").mkdirs()
    }
    val text = Json.encodeToString(JsonArray.serializer(), records)
    file.parentFile?.mkdirs()
    val tmp = File(file.parentFile, file.name + "." + UUID.randomUUID() + ".tmp")
    tmp.writeText(text, Charsets.UTF_8)
    if (!tmp.renameTo(file)) {
      file.writeText(text, Charsets.UTF_8)
      tmp.delete()
    }
  }
}
