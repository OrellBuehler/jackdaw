package ch.orellbuehler.jackdaw.format

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal data class MapPin(
    val lat: Double,
    val lng: Double,
    val label: String?,
)

internal data class MapSpec(
    val pins: List<MapPin>,
    val line: Boolean,
    val alt: String?,
) {
    val figure: Figure by lazy { MapFigure.render(this) }
}

internal object MapRules {
    const val MAX_PINS = 20
    private val numberPattern = Regex("^-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?$")

    class Read(
        val map: MapSpec?,
        val ignored: List<String>,
        val droppedPins: Int,
    )

    fun read(element: JsonElement): Read {
        val obj = element as? JsonObject ?: return Read(null, emptyList(), 0)
        val rawPins = obj["pins"] as? JsonArray ?: return Read(null, emptyList(), 0)
        val ignored = ArrayList<String>()
        val pins = ArrayList<MapPin>()
        var dropped = 0
        rawPins.forEachIndexed { index, entry ->
            val pin = entry as? JsonObject
            val lat = number(pin?.get("lat"))
            val lng = number(pin?.get("lng"))
            if (pin == null ||
                lat == null ||
                lng == null ||
                lat < -90.0 ||
                lat > 90.0 ||
                lng < -180.0 ||
                lng > 180.0 ||
                pins.size >= MAX_PINS
            ) {
                dropped++
                return@forEachIndexed
            }
            var label: String? = null
            pin["label"]?.let { labelElement ->
                val text = labelElement.asString()
                if (text == null) ignored += "pins[$index].label" else label = text.takeUnless { it.isBlankSpec() }
            }
            pins += MapPin(lat, lng, label)
        }
        if (pins.isEmpty()) return Read(null, emptyList(), 0)
        var line = false
        obj["line"]?.let { lineElement ->
            val value = lineElement.asBoolean()
            if (value == null) ignored += "line" else line = value
        }
        var alt: String? = null
        obj["alt"]?.let { altElement ->
            val text = altElement.asString()
            if (text == null) ignored += "alt" else alt = text.takeUnless { it.isBlankSpec() }
        }
        return Read(MapSpec(pins, line, alt), ignored, dropped)
    }

    private fun number(element: JsonElement?): Double? {
        val primitive = element as? JsonPrimitive ?: return null
        if (primitive.isString || !numberPattern.matches(primitive.content)) return null
        return primitive.content.toDoubleOrNull()?.takeIf { it.isFinite() }
    }
}
