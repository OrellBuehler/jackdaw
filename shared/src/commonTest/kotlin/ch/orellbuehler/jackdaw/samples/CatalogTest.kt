package ch.orellbuehler.jackdaw.samples

import ch.orellbuehler.jackdaw.store.SamplePack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CatalogTest {
    private val bundled =
        listOf(
            SamplePack("a", "A", "about a", "🅰️", "#112233", 3, revision = 2, downloadUrl = null),
            SamplePack("b", "B", "about b", null, null, 5, revision = 1, downloadUrl = null),
        )

    private fun catalog(
        packs: String,
        format: String = "jackdaw-catalog",
        version: String = "1",
    ) = """{"format": "$format", "version": $version, "packs": [$packs]}"""

    private fun entry(
        id: String,
        revision: Int,
        file: String = "$id.jackdaw",
    ) =
        """{"id": "$id", "file": "$file", "title": "Online $id", "description": "d", "icon": "🌍", "color": "#000000", "items": 7, "revision": $revision}"""

    @Test
    fun bundledCopyWinsUnlessTheCatalogIsNewer() {
        val result = Catalog.read(catalog(entry("a", 2) + "," + entry("b", 4) + "," + entry("c", 1)), bundled)!!
        assertEquals(listOf("a", "b", "c"), result.map { it.id })
        assertEquals(bundled[0], result[0])
        assertEquals("Online b", result[1].title)
        assertEquals(4, result[1].revision)
        assertEquals(7, result[1].itemCount)
        assertEquals(Catalog.BASE_URL + "b.jackdaw", result[1].downloadUrl)
        assertEquals(Catalog.BASE_URL + "c.jackdaw", result[2].downloadUrl)
    }

    @Test
    fun bundledPacksMissingFromTheCatalogAreKept() {
        val result = Catalog.read(catalog(entry("c", 1)), bundled)!!
        assertEquals(listOf("c", "a", "b"), result.map { it.id })
    }

    @Test
    fun entriesWithUnsafeFileNamesOrMissingFieldsAreSkipped() {
        val packs =
            listOf(
                entry("c", 1, file = "../c.jackdaw"),
                entry("d", 1, file = "https://example.com/d.jackdaw"),
                entry("e", 1, file = "e.json"),
                """{"id": "f", "file": "f.jackdaw", "items": 3}""",
                """{"id": "g", "file": "g.jackdaw", "title": "G", "items": 0}""",
                "42",
                entry("h", 1),
                entry("h", 2),
            ).joinToString(",")
        val result = Catalog.read(catalog(packs), emptyList())!!
        assertEquals(listOf("h"), result.map { it.id })
        assertEquals(1, result.single().revision)
    }

    @Test
    fun anythingThatIsNotACatalogIsRejected() {
        assertNull(Catalog.read("<html>", bundled))
        assertNull(Catalog.read("[]", bundled))
        assertNull(Catalog.read(catalog("", format = "jackdaw-pack"), bundled))
        assertNull(Catalog.read(catalog("", version = "2"), bundled))
        assertNull(Catalog.read(catalog("", version = "\"1\""), bundled))
        assertNull(Catalog.read("""{"format": "jackdaw-catalog", "version": 1}""", bundled))
    }

    @Test
    fun aCatalogOfTheBundledPacksNeedsNoDownload() {
        val samples =
            GENERATED_SAMPLE_PACKS.indices.map { index ->
                SamplePack("id$index", "T", "", null, null, 1, revision = 1, downloadUrl = null)
            }
        val text = catalog(samples.joinToString(",") { entry(it.id, 1) })
        assertEquals(samples, Catalog.read("\uFEFF" + text, samples))
    }
}
