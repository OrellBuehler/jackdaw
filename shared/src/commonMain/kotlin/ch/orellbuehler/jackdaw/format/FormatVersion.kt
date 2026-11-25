package ch.orellbuehler.jackdaw.format

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object FormatVersion {
    private val figureKeys = listOf("figure", "answerFigure")
    private val mapKeys = listOf("map", "answerMap")

    fun needed(items: Iterable<JsonElement>): Int {
        val objects = items.filterIsInstance<JsonObject>()
        return when {
            objects.any { item -> mapKeys.any { it in item } } -> 3
            objects.any { item -> figureKeys.any { it in item } } -> 2
            else -> 1
        }
    }

    fun declared(root: JsonObject): Int = (root["version"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1

    fun raised(
        root: JsonObject,
        items: Iterable<JsonElement>,
    ): JsonElement? = needed(items).takeIf { it > declared(root) }?.let { JsonPrimitive(it) }
}
