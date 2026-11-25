package ch.orellbuehler.jackdaw.format

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object PackEditor {
    const val NEW_ITEM = "{\n    \"id\": \"new-item\",\n    \"type\": \"card\",\n    \"front\": \"\",\n    \"back\": \"\"\n}"
    private val pretty = Json { prettyPrint = true }
    private val figureKeys = listOf("figure", "answerFigure")
    private val mapKeys = listOf("map", "answerMap")

    fun item(
        source: String,
        itemId: String,
    ): String? {
        val items = parse(source)?.get("items") as? JsonArray ?: return null
        val item = items.firstOrNull { idOf(it) == itemId } ?: return null
        return pretty.encodeToString(JsonElement.serializer(), item)
    }

    fun idOf(itemJson: String): String? = idOf(parse(itemJson))

    class Placed(
        val source: String,
        val index: Int,
    )

    fun withItem(
        source: String,
        itemId: String?,
        itemJson: String,
    ): Placed? {
        val item = parse(itemJson) ?: return null
        var index = -1
        val result =
            edit(source) { items ->
                index = if (itemId == null) items.size else items.indexOfFirst { idOf(it) == itemId }
                when {
                    itemId == null -> items + item
                    index < 0 -> null
                    else -> items.toMutableList().also { it[index] = item }
                }
            } ?: return null
        return Placed(result, index)
    }

    fun withoutItem(
        source: String,
        itemId: String,
    ): String? =
        edit(source) { items ->
            val at = items.indexOfFirst { idOf(it) == itemId }
            if (at < 0) null else items.filterIndexed { index, _ -> index != at }
        }

    fun withInfo(
        source: String,
        title: String,
        description: String,
    ): String? {
        val root = parse(source) ?: return null
        val fields = LinkedHashMap<String, JsonElement>(root)
        fields["title"] = JsonPrimitive(title.trim())
        if (description.isBlank()) fields.remove("description") else fields["description"] = JsonPrimitive(description.trim())
        return pretty.encodeToString(JsonElement.serializer(), JsonObject(fields))
    }

    private fun edit(
        source: String,
        change: (List<JsonElement>) -> List<JsonElement>?,
    ): String? {
        val root = parse(source) ?: return null
        val items = change(root["items"] as? JsonArray ?: return null) ?: return null
        val fields = LinkedHashMap<String, JsonElement>(root)
        fields["items"] = JsonArray(items)
        val declared = (root["version"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1
        val needsFigures = items.any { item -> item is JsonObject && figureKeys.any { it in item } }
        val needsMaps = items.any { item -> item is JsonObject && mapKeys.any { it in item } }
        val needed =
            if (needsMaps) {
                3
            } else if (needsFigures) {
                2
            } else {
                1
            }
        if (declared < needed) fields["version"] = JsonPrimitive(needed)
        return pretty.encodeToString(JsonElement.serializer(), JsonObject(fields))
    }

    private fun parse(text: String): JsonObject? =
        runCatching {
            Json.parseToJsonElement(text.removePrefix("\uFEFF"))
        }.getOrNull() as? JsonObject

    private fun idOf(item: JsonElement?): String? = ((item as? JsonObject)?.get("id") as? JsonPrimitive)?.takeIf { it.isString }?.content
}
