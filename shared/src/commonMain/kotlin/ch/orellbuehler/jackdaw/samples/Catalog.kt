package ch.orellbuehler.jackdaw.samples

import ch.orellbuehler.jackdaw.store.SamplePack
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object Catalog {
    const val BASE_URL = "https://raw.githubusercontent.com/OrellBuehler/jackdaw/main/format/examples/"
    const val INDEX_URL = BASE_URL + "index.json"
    const val FORMAT = "jackdaw-catalog"
    const val VERSION = 1

    private val fileName = Regex("[a-z0-9][a-z0-9-]*\\.jackdaw")

    fun read(
        text: String,
        bundled: List<SamplePack>,
    ): List<SamplePack>? {
        val root = runCatching { Json.parseToJsonElement(text.removePrefix("\uFEFF")) }.getOrNull() as? JsonObject ?: return null
        if (root.string("format") != FORMAT) return null
        val version = root.int("version") ?: return null
        if (version < 1 || version > VERSION) return null
        val entries = root["packs"] as? JsonArray ?: return null
        val local = bundled.associateBy { it.id }
        val seen = HashSet<String>()
        val online =
            entries.mapNotNull { element ->
                val entry = element as? JsonObject ?: return@mapNotNull null
                val id = entry.string("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val title = entry.string("title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val file = entry.string("file")?.takeIf { fileName.matches(it) } ?: return@mapNotNull null
                val items = entry.int("items")?.takeIf { it > 0 } ?: return@mapNotNull null
                if (!seen.add(id)) return@mapNotNull null
                val revision = entry.int("revision") ?: 0
                val shipped = local[id]
                if (shipped != null && shipped.revision >= revision) return@mapNotNull shipped
                SamplePack(
                    id = id,
                    title = title,
                    descriptionText = entry.string("description") ?: "",
                    icon = entry.string("icon"),
                    color = entry.string("color"),
                    itemCount = items,
                    revision = revision,
                    downloadUrl = BASE_URL + file,
                )
            }
        return online + bundled.filter { it.id !in seen }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()
}
