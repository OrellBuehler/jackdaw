package ch.orellbuehler.jackdaw.format

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompatibilityTest {
    private val manifest =
        Json
            .parseToJsonElement(GENERATED_COMPAT_MANIFEST)
            .jsonObject
            .getValue("packs")
            .jsonArray
            .map { it.jsonObject }

    @Test
    fun everyFrozenPackIsListed() {
        assertEquals(GENERATED_COMPAT_PACKS.keys, manifest.map { it.getValue("file").jsonPrimitive.content }.toSet())
        assertTrue(manifest.any { it.getValue("revision").jsonPrimitive.content == "1.0" })
        assertTrue(manifest.any { it.getValue("revision").jsonPrimitive.content == "2.0" })
        assertTrue(manifest.any { it.getValue("revision").jsonPrimitive.content == "3.0" })
    }

    @Test
    fun frozenPacksImportCompletely() {
        for (entry in manifest) {
            val file = entry.getValue("file").jsonPrimitive.content
            val result = assertIs<ReadResult.Accepted>(PackReader.read(GENERATED_COMPAT_PACKS.getValue(file)), file)
            assertEquals(emptyList(), result.warnings, "warnings in $file")
            assertEquals(0, result.skippedItems, "skipped items in $file")
            assertEquals(entry.getValue("items").jsonPrimitive.int, result.pack.items.size, "items in $file")
            result.payloads.forEachIndexed { index, payload ->
                val stored =
                    PackReader.readStoredItem(
                        payload,
                        result.pack.study,
                        result.pack.sections
                            .map { it.id }
                            .toSet(),
                    )
                assertEquals(result.pack.items[index], stored, "stored item $index of $file")
            }
        }
    }

    @Test
    fun frozenMapsAreRead() {
        val result = assertIs<ReadResult.Accepted>(PackReader.read(GENERATED_COMPAT_PACKS.getValue("3.0-maps.jackdaw")))
        val items = result.pack.items.associateBy { it.id }
        assertEquals(7, items.size)
        assertEquals(
            "Paris",
            items
                .getValue("card")
                .map
                ?.pins
                ?.single()
                ?.label,
        )
        assertEquals(true, items.getValue("card").answerMap?.line)
        assertEquals(
            2,
            items
                .getValue("choice")
                .answerMap
                ?.pins
                ?.size,
        )
        assertEquals(null, items.getValue("choice").map)
        assertEquals(
            3,
            items
                .getValue("text")
                .map
                ?.pins
                ?.size,
        )
        assertEquals(
            20,
            items
                .getValue("cloze")
                .map
                ?.pins
                ?.size,
        )
        assertNotNull(items.getValue("truefalse").figure)
        assertNotNull(items.getValue("truefalse").answerMap)
        assertNotNull(items.getValue("order").map)
        assertNotNull(items.getValue("order").answerFigure)
        assertEquals("A river from its source to its mouth.", items.getValue("open").answerVisual?.alt)
        for (item in result.pack.items) {
            val shown = item.questionVisual ?: item.answerVisual
            assertNotNull(shown, item.id)
            assertTrue(shown.svg.length < FigureRules.MAX_LENGTH, item.id)
        }
    }

    @Test
    fun frozenFiguresAreRead() {
        val result = assertIs<ReadResult.Accepted>(PackReader.read(GENERATED_COMPAT_PACKS.getValue("2.0-figures.jackdaw")))
        val items = result.pack.items.associateBy { it.id }
        assertEquals(7, items.size)
        val card = items.getValue("card")
        assertEquals(2.0, assertNotNull(card.figure).aspectRatio)
        assertEquals("A dot moving left and right.", card.figure?.alt)
        val answer = assertNotNull(card.answerFigure)
        assertEquals(1.0, answer.aspectRatio)
        assertTrue(answer.svg.startsWith("<!-- frozen fixture -->"))
        assertNotNull(items.getValue("text").answerFigure)
        assertEquals(null, items.getValue("text").figure)
        for (id in listOf("choice", "cloze", "truefalse", "order", "open")) assertNotNull(items.getValue(id).figure, id)
    }
}
