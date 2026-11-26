package ch.orellbuehler.jackdaw.format

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PackEditorTest {
    private val pack =
        """{"format":"jackdaw-pack","version":1,"id":"p","title":"t","items":[{"id":"a","type":"card","front":"f","back":"b"}]}"""

    private fun versionAfter(item: String): Int {
        val edited = assertNotNull(PackEditor.withItem(pack, null, item)).source
        return Json
            .parseToJsonElement(edited)
            .jsonObject
            .getValue("version")
            .jsonPrimitive.content
            .toInt()
    }

    @Test
    fun theVersionIsRaisedToWhatTheItemsNeed() {
        assertEquals(1, versionAfter("""{"id":"b","type":"card","front":"f","back":"b"}"""))
        assertEquals(2, versionAfter("""{"id":"b","type":"card","front":"f","back":"b","figure":{"svg":"<svg viewBox='0 0 1 1'/>"}}"""))
        assertEquals(3, versionAfter("""{"id":"b","type":"card","front":"f","back":"b","answerMap":{"pins":[{"lat":1,"lng":2}]}}"""))
        assertEquals(
            3,
            versionAfter("""{"id":"b","type":"card","front":"f","back":"b","map":{"pins":[{"lat":1,"lng":2}]},"answerFigure":{}}"""),
        )
    }

    @Test
    fun aCodeBlockInAnyRichTextPropertyNeedsVersionFour() {
        val fence = "```"
        assertEquals(4, versionAfter("""{"id":"b","type":"card","front":"${fence}py\nx\n$fence","back":"b"}"""))
        assertEquals(4, versionAfter("""{"id":"b","type":"card","front":"f","back":"b","hint":"a\r\n$fence\r\n$fence"}"""))
        assertEquals(4, versionAfter("""{"id":"b","type":"card","front":"f","back":"b","distractors":["x","$fence\nx\n$fence"]}"""))
        assertEquals(
            4,
            versionAfter(
                """{"id":"b","type":"choice","question":"q","options":[{"text":"a","explanation":"$fence\nx\n$fence"},{"text":"b"}]}""",
            ),
        )
        assertEquals(4, versionAfter("""{"id":"b","type":"cloze","text":"$fence\n{{x}}\n$fence"}"""))
        assertEquals(4, versionAfter("""{"id":"b","type":"open","question":"q","keyPoints":["$fence\nx\n$fence"]}"""))
        assertEquals(4, versionAfter("""{"id":"b","type":"order","prompt":"p","sequence":["a","$fence\nx\n$fence"]}"""))
        assertEquals(4, versionAfter("""{"id":"b","type":"truefalse","statement":"$fence\nx\n$fence","answer":true}"""))
        assertEquals(
            4,
            versionAfter("""{"id":"b","type":"card","front":"f","back":"b","map":{"pins":[{"lat":1,"lng":2}]},"hint":"$fence\n$fence"}"""),
        )
    }

    @Test
    fun fencesThatAreNoBlockAndPlainPropertiesKeepTheVersion() {
        val fence = "```"
        assertEquals(1, versionAfter("""{"id":"b","type":"card","front":"${fence}py\nx","back":"b"}"""))
        assertEquals(1, versionAfter("""{"id":"b","type":"card","front":"text $fence\nx\n$fence","back":"b"}"""))
        assertEquals(1, versionAfter("""{"id":"b","type":"text","question":"q","answers":["$fence\nx\n$fence"]}"""))
        assertEquals(1, versionAfter("""{"id":"b","type":"card","front":"f","back":"b","alternatives":["$fence\nx\n$fence"]}"""))
        assertEquals(1, versionAfter("""{"id":"b","type":"card","front":"f","back":"b","tags":["$fence\nx\n$fence"]}"""))
    }
}
