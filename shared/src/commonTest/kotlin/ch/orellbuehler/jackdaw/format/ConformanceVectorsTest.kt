package ch.orellbuehler.jackdaw.format

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConformanceVectorsTest {
    private val vectors = Json.parseToJsonElement(GENERATED_TEST_VECTORS).jsonObject

    private fun section(name: String): List<JsonObject> = vectors.getValue(name).jsonArray.map { it.jsonObject }

    private fun flag(
        vector: JsonObject,
        name: String,
    ): Boolean = vector[name]?.jsonPrimitive?.booleanOrNull ?: false

    @Test
    fun vectorsFileIsLoaded() {
        assertTrue(section("normalize").size > 30)
        assertTrue(section("match").size > 10)
        assertTrue(section("markup").size > 20)
        assertTrue(section("cloze").size > 15)
        assertTrue(section("code").size > 30)
        assertTrue(section("clozeCode").size > 8)
    }

    @Test
    fun codeVectors() {
        section("code").forEachIndexed { index, v ->
            val input = v.getValue("input").jsonPrimitive.content
            val actual =
                splitCodeBlocks(normalizeNewlines(input)).map { block ->
                    when (block) {
                        is RawBlock.Text -> JsonArray(listOf(JsonPrimitive("t"), JsonPrimitive(block.raw)))
                        is RawBlock.Code ->
                            JsonArray(
                                listOf(
                                    JsonPrimitive("c"),
                                    block.language?.let { JsonPrimitive(it) } ?: JsonNull,
                                    JsonPrimitive(block.code),
                                ),
                            )
                    }
                }
            val expected: List<JsonElement> = v.getValue("blocks").jsonArray.toList()
            assertEquals(expected, actual, "code[$index] $input")
            assertEquals(v.getValue("plain").jsonPrimitive.content, RichText.parse(input).plain, "code plain[$index] $input")
            assertEquals(
                expected.any { it.jsonArray[0].jsonPrimitive.content == "c" },
                containsCodeBlock(input),
                "code contains[$index] $input",
            )
        }
    }

    @Test
    fun clozeCodeVectors() {
        section("clozeCode").forEachIndexed { index, v ->
            val input = v.getValue("input").jsonPrimitive.content

            fun pieces(parts: List<ClozePart>): JsonArray =
                JsonArray(
                    parts.map { part ->
                        when (part) {
                            is ClozePart.Text -> JsonArray(listOf(JsonPrimitive("l"), JsonPrimitive(part.raw)))
                            is ClozePart.Gap ->
                                JsonArray(
                                    listOf(
                                        JsonPrimitive("g"),
                                        JsonObject(
                                            mapOf(
                                                "answers" to JsonArray(part.answers.map { JsonPrimitive(it) }),
                                                "hint" to (part.hint?.let { JsonPrimitive(it) } ?: JsonNull),
                                            ),
                                        ),
                                    ),
                                )
                        }
                    },
                )
            val actual =
                parseClozeBlocks(input).map { block ->
                    when (block) {
                        is ClozeBlock.Text -> JsonArray(listOf(JsonPrimitive("t"), pieces(block.parts)))
                        is ClozeBlock.Code ->
                            JsonArray(
                                listOf(JsonPrimitive("c"), block.language?.let { JsonPrimitive(it) } ?: JsonNull, pieces(block.parts)),
                            )
                    }
                }
            val expected: List<JsonElement> = v.getValue("blocks").jsonArray.toList()
            assertEquals(expected, actual, "clozeCode[$index] $input")
        }
    }

    @Test
    fun figureVectors() {
        assertTrue(section("figure").size > 30)
        section("figure").forEachIndexed { index, v ->
            val svg = v.getValue("svg").jsonPrimitive.content
            val checked = FigureRules.check(svg)
            assertEquals(v.getValue("valid").jsonPrimitive.boolean, checked != null, "figure[$index] $svg")
            if (checked != null) {
                val expected =
                    v
                        .getValue("aspectRatio")
                        .jsonPrimitive.content
                        .toDouble()
                assertEquals(expected, checked.aspectRatio, 1e-9, "figure[$index] $svg")
            }
        }
    }

    @Test
    fun mapVectors() {
        assertTrue(section("map").size > 25)
        section("map").forEachIndexed { index, v ->
            val input = v.getValue("input")
            val read = MapRules.read(input).map
            val expected = v.getValue("expected")
            val label = "map[$index] $input"
            if (expected is JsonNull) {
                assertEquals(null, read, label)
                return@forEachIndexed
            }
            val map = read ?: error("expected a map, got none: $label")
            val pins =
                expected.jsonObject
                    .getValue("pins")
                    .jsonArray
                    .map { it.jsonArray }
            assertEquals(pins.size, map.pins.size, label)
            pins.forEachIndexed { i, pin ->
                assertEquals(pin[0].jsonPrimitive.content.toDouble(), map.pins[i].lat, 1e-12, label)
                assertEquals(pin[1].jsonPrimitive.content.toDouble(), map.pins[i].lng, 1e-12, label)
                assertEquals(pin[2].jsonPrimitive.contentOrNull, map.pins[i].label, label)
            }
            assertEquals(
                expected.jsonObject
                    .getValue("line")
                    .jsonPrimitive.boolean,
                map.line,
                label,
            )
            assertEquals(
                expected.jsonObject
                    .getValue("alt")
                    .jsonPrimitive.contentOrNull,
                map.alt,
                label,
            )
        }
    }

    @Test
    fun normalizeVectors() {
        section("normalize").forEachIndexed { index, v ->
            val input = v.getValue("input").jsonPrimitive.content
            val expected = v.getValue("expected").jsonPrimitive.content
            assertEquals(
                expected,
                normalize(input, flag(v, "caseSensitive"), flag(v, "ignoreDiacritics")),
                "normalize[$index] $input",
            )
        }
    }

    @Test
    fun matchVectors() {
        section("match").forEachIndexed { index, v ->
            val typed = v.getValue("typed").jsonPrimitive.content
            val expected = v.getValue("expected").jsonPrimitive.content
            val result = v.getValue("result").jsonPrimitive.boolean
            assertEquals(
                result,
                matchesAnswer(typed, listOf(expected), flag(v, "caseSensitive"), flag(v, "ignoreDiacritics")),
                "match[$index] typed=$typed expected=$expected",
            )
        }
    }

    @Test
    fun markupVectors() {
        section("markup").forEachIndexed { index, v ->
            val input = v.getValue("input").jsonPrimitive.content
            val spans = parseMarkup(input).map { listOf(styleOf(it), it.text) }
            val expected = v.getValue("spans").jsonArray.map { span -> span.jsonArray.map { it.jsonPrimitive.content } }
            assertEquals(expected, spans, "markup[$index] $input")
            v["plain"]?.let { assertEquals(it.jsonPrimitive.content, RichText.parse(input).plain, "markup plain[$index] $input") }
        }
    }

    @Test
    fun clozeVectors() {
        section("cloze").forEachIndexed { index, v ->
            val input = v.getValue("input").jsonPrimitive.content
            val actual =
                parseCloze(input).map { part ->
                    when (part) {
                        is ClozePart.Text -> JsonArray(listOf(JsonPrimitive("t"), JsonPrimitive(part.raw)))
                        is ClozePart.Gap ->
                            JsonArray(
                                listOf(
                                    JsonPrimitive("g"),
                                    JsonObject(
                                        mapOf(
                                            "answers" to JsonArray(part.answers.map { JsonPrimitive(it) }),
                                            "hint" to (part.hint?.let { JsonPrimitive(it) } ?: JsonNull),
                                        ),
                                    ),
                                ),
                            )
                    }
                }
            val expected: List<JsonElement> = v.getValue("segments").jsonArray.toList()
            assertEquals(expected, actual, "cloze[$index] $input")
            assertTrue(v.getValue("segments").jsonArray.all { it.jsonArray[0].jsonPrimitive.contentOrNull in listOf("t", "g") })
        }
    }

    private fun styleOf(span: TextSpan): String =
        when {
            span.bold -> "b"
            span.italic -> "i"
            span.code -> "c"
            else -> ""
        }
}
