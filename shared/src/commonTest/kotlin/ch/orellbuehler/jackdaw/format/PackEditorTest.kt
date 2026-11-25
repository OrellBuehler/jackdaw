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
}
