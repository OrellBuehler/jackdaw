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
