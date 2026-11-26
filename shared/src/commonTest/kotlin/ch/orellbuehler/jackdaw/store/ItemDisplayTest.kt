package ch.orellbuehler.jackdaw.store

import ch.orellbuehler.jackdaw.packJson
import ch.orellbuehler.jackdaw.readPack
import kotlin.test.Test
import kotlin.test.assertEquals

class ItemDisplayTest {
    private val fence = "```"

    private fun item(json: String) = readPack(packJson(json)).items.single()

    @Test
    fun codeBlocksAreSummarisedAsTheirCodeOnOneLine() {
        val card =
            item("""{"id":"c","type":"card","front":"What prints?\n${fence}python\nif x:\n    print(1)\n$fence\nAfter","back":"1"}""")
        assertEquals("What prints? if x: print(1) After", displayTitle(card))
        assertEquals("1", displaySubtitle(card))
    }

    @Test
    fun clozeGapsInABlockAreEllipsesOrAnswers() {
        val cloze = item("""{"id":"z","type":"cloze","text":"Fill:\n${fence}js\nlet x = {{1}};\n$fence\nDone {{yes}}."}""")
        assertEquals("Fill: let x = \u2026; Done \u2026.", displayTitle(cloze))
        assertEquals("Fill: let x = 1; Done yes.", displaySubtitle(cloze))
    }

    @Test
    fun anUnclosedFenceStaysText() {
        val card = item("""{"id":"c","type":"card","front":"${fence}python\nx","back":"b"}""")
        assertEquals("${fence}python x", displayTitle(card))
    }
}
