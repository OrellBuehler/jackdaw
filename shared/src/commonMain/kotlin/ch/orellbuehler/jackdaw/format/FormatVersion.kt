package ch.orellbuehler.jackdaw.format

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object FormatVersion {
    private val figureKeys = listOf("figure", "answerFigure")
    private val mapKeys = listOf("map", "answerMap")

    private val richTextKeys =
        mapOf(
            "card" to listOf("front", "back", "distractors", "frontDistractors"),
            "choice" to listOf("question"),
            "text" to listOf("question"),
            "cloze" to listOf("text"),
            "truefalse" to listOf("statement"),
            "order" to listOf("prompt", "sequence"),
            "open" to listOf("question", "answer", "keyPoints"),
        )
    private val commonRichTextKeys = listOf("hint", "explanation")

    fun needed(items: Iterable<JsonElement>): Int {
        val objects = items.filterIsInstance<JsonObject>()
        return when {
            objects.any { hasCodeBlock(it) } -> 4
            objects.any { item -> mapKeys.any { it in item } } -> 3
            objects.any { item -> figureKeys.any { it in item } } -> 2
            else -> 1
        }
    }

    private fun hasCodeBlock(item: JsonObject): Boolean {
        val type = (item["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val keys = commonRichTextKeys + type?.let { richTextKeys[it] }.orEmpty()
        if (keys.any { containsBlock(item[it]) }) return true
        if (type != "choice") return false
        val options = item["options"] as? JsonArray ?: return false
        return options.any { option ->
            option is JsonObject && (containsBlock(option["text"]) || containsBlock(option["explanation"]))
        }
    }

    private fun containsBlock(value: JsonElement?): Boolean =
        when (value) {
            is JsonPrimitive -> value.isString && value.content.contains("```") && containsCodeBlock(value.content)
            is JsonArray -> value.any { containsBlock(it) }
            else -> false
        }

    fun declared(root: JsonObject): Int = (root["version"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1

    fun raised(
        root: JsonObject,
        items: Iterable<JsonElement>,
    ): JsonElement? = needed(items).takeIf { it > declared(root) }?.let { JsonPrimitive(it) }
}
