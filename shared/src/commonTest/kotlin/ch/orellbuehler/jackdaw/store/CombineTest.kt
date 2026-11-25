package ch.orellbuehler.jackdaw.store

import ch.orellbuehler.jackdaw.FakeClock
import ch.orellbuehler.jackdaw.JackdawSdk
import ch.orellbuehler.jackdaw.format.ImportErrorCode
import ch.orellbuehler.jackdaw.format.PackCombiner
import ch.orellbuehler.jackdaw.study.Grade
import ch.orellbuehler.jackdaw.study.SessionConfig
import ch.orellbuehler.jackdaw.study.SessionState
import ch.orellbuehler.jackdaw.sync.newTestDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val A = "pack.a"
private const val B = "pack.b"
private const val SVG = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 4 4'><circle r='1'/></svg>"
private val ID_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")

private fun card(
    id: String,
    front: String,
    back: String,
    extra: String = "",
): String = """{"id":"$id","type":"card","front":"$front","back":"$back"$extra}"""

private fun pack(
    id: String,
    vararg items: String,
    sections: String? = null,
    title: String = "Pack $id",
): String =
    buildString {
        append("""{"format":"jackdaw-pack","version":1,"id":"$id","title":"$title",""")
        if (sections != null) append(""""sections":$sections,""")
        append(""""study":{"exercises":{"card":["flip"]}},"items":[${items.joinToString(",")}]}""")
    }

private fun obj(text: String) = Json.parseToJsonElement(text) as JsonObject

class CombineTest {
    private val clock = FakeClock()
    private val sdk = JackdawSdk(newTestDriver(), clock, { TimeZone.UTC }, Random(1), Dispatchers.Unconfined)

    private suspend fun install(source: String) = assertIs<ImportResult.Success>(sdk.importPack(source))

    private suspend fun ids(packId: String) = sdk.items(packId).map { it.id }

    private suspend fun preview(
        into: String = A,
        from: String = B,
    ) = assertIs<CombinePreview.Valid>(sdk.previewCombine(into, from))

    private suspend fun learnFirst(packId: String) {
        val session = sdk.startSession(packId, SessionConfig.learn())
        while (true) {
            when (session.state.value) {
                is SessionState.Question -> session.reveal()
                is SessionState.Feedback -> session.selfGrade(Grade.GOOD)
                else -> return
            }
        }
    }

    @Test
    fun contentIgnoresIdSectionAndTagsAndPropertyOrder() {
        val base = obj("""{"id":"a","type":"card","front":"x","back":"y","section":"s","tags":["t"]}""")
        assertTrue(PackCombiner.sameContent(base, obj("""{"back":"y","front":"x","type":"card","id":"zzz"}""")))
        assertFalse(PackCombiner.sameContent(base, obj("""{"id":"a","type":"card","front":"x","back":"y","hint":"h"}""")))
        assertFalse(PackCombiner.sameContent(base, obj("""{"id":"a","type":"card","front":"x","back":"Y"}""")))
        assertFalse(PackCombiner.sameContent(base, obj("""{"id":"a","type":"card","front":"x ","back":"y"}""")))
        assertFalse(PackCombiner.sameContent(base, obj("""{"id":"a","type":"text","front":"x","back":"y"}""")))
    }

    @Test
    fun derivedIdsAreDeterministicValidAndShort() {
        val first = PackCombiner.derivedId("pack.b", "item", emptySet())
        assertEquals(first, PackCombiner.derivedId("pack.b", "item", emptySet()))
        assertNotEquals(first, PackCombiner.derivedId("pack.c", "item", emptySet()))
        assertTrue(first.startsWith("item-"))
        val long = PackCombiner.derivedId("p".repeat(128), "x".repeat(64), emptySet())
        assertEquals(64, long.length)
        assertTrue(ID_PATTERN.matches(long))
        assertNotEquals(first, PackCombiner.derivedId("pack.b", "item", setOf(first)))
    }

    @Test
    fun addsNewItemsAfterTheOwnOnesInTheOrderOfTheOtherPack() =
        runTest {
            install(pack(A, card("a1", "one", "1"), card("a2", "two", "2")))
            install(pack(B, card("b1", "three", "3"), card("b2", "four", "4"), card("b3", "five", "5")))
            val shown = preview()
            assertEquals(3, shown.addedCount)
            assertEquals(0, shown.duplicateCount)
            assertEquals("Pack pack.b", shown.otherTitle)
            val done = assertIs<ImportResult.Success>(sdk.combinePacks(A, B))
            assertEquals(5, done.itemCount)
            assertEquals(3, done.counts.addedItems)
            assertEquals(2, done.counts.keptItems)
            assertEquals(listOf("a1", "a2", "b1", "b2", "b3"), ids(A))
            assertEquals(listOf("b1", "b2", "b3"), ids(B))
            assertTrue(
                sdk
                    .observePacks()
                    .first()
                    .single { it.id == A }
                    .edited,
            )
            assertFalse(
                sdk
                    .observePacks()
                    .first()
                    .single { it.id == B }
                    .edited,
            )
        }

    @Test
    fun skipsExactDuplicatesOnlyIncludingOnesInsideTheOtherPack() =
        runTest {
            install(pack(A, card("a1", "one", "1", ""","tags":["x"]"""), card("a2", "two", "2")))
            install(
                pack(
                    B,
                    card("b1", "one", "1"),
                    card("b2", "One", "1"),
                    card("b3", "two", "2", ""","hint":"h""""),
                    card("b4", "new", "n"),
                    card("b5", "new", "n"),
                    """{"id":"b6","type":"text","question":"one","answers":["1"]}""",
                ),
            )
            val shown = preview()
            assertEquals(4, shown.addedCount)
            assertEquals(2, shown.duplicateCount)
            assertIs<ImportResult.Success>(sdk.combinePacks(A, B))
            assertEquals(listOf("a1", "a2", "b2", "b3", "b4", "b6"), ids(A))
        }

    @Test
    fun anItemIdThatExistsGetsAStableNewIdAndTwoDevicesAgree() =
        runTest {
            val a = pack(A, card("x", "one", "1"), card("y", "two", "2"))
            val b = pack(B, card("x", "other", "o"), card("z", "zed", "z"))
            install(a)
            install(b)
            assertIs<ImportResult.Success>(sdk.combinePacks(A, B))
            val combined = ids(A)
            assertEquals(4, combined.size)
            assertEquals(listOf("x", "y"), combined.take(2))
            assertNotEquals("x", combined[2])
            assertEquals("z", combined[3])
            assertTrue(combined.all { ID_PATTERN.matches(it) })
            assertEquals(combined[2], PackCombiner.derivedId(B, "x", setOf("x", "y")))

            val other = JackdawSdk(newTestDriver(), FakeClock(5), { TimeZone.UTC }, Random(99), Dispatchers.Unconfined)
            assertIs<ImportResult.Success>(other.importPack(a))
            assertIs<ImportResult.Success>(other.importPack(b))
            assertIs<ImportResult.Success>(other.combinePacks(A, B))
            assertEquals(sdk.exportPack(A), other.exportPack(A))
        }

    @Test
    fun combiningTwiceAddsNothingTheSecondTime() =
        runTest {
            install(pack(A, card("x", "one", "1")))
            install(pack(B, card("x", "other", "o"), card("z", "zed", "z")))
            assertIs<ImportResult.Success>(sdk.combinePacks(A, B))
            val once = sdk.exportPack(A)
            val shown = preview()
            assertEquals(0, shown.addedCount)
            assertEquals(2, shown.duplicateCount)
            val again = assertIs<ImportResult.Success>(sdk.combinePacks(A, B))
            assertEquals(0, again.counts.addedItems)
            assertEquals(3, again.itemCount)
            assertEquals(once, sdk.exportPack(A))
        }

    @Test
    fun aPackCombinedWithItselfHasOnlyDuplicates() =
        runTest {
            install(pack(A, card("x", "one", "1"), card("y", "two", "2")))
            val shown = preview(A, A)
            assertEquals(0, shown.addedCount)
            assertEquals(2, shown.duplicateCount)
        }

    @Test
    fun learningProgressOfTheOwnItemsIsKept() =
        runTest {
            install(pack(A, card("a1", "one", "1"), card("a2", "two", "2")))
            install(pack(B, card("b1", "three", "3")))
            learnFirst(A)
            val before = sdk.items(A)
            assertTrue(before.all { it.status != ItemStatus.NEW })
            assertIs<ImportResult.Success>(sdk.combinePacks(A, B))
            val after = sdk.items(A)
            assertEquals(before, after.take(2))
            assertEquals(ItemStatus.NEW, after.last().status)
            assertEquals(3, after.size)
            assertEquals(
                3,
                sdk
                    .observePacks()
                    .first()
                    .single { it.id == A }
                    .facetCount,
            )
        }

    @Test
    fun theOtherPackIsNotChangedAndKeepsItsProgress() =
        runTest {
            install(pack(A, card("a1", "one", "1")))
            install(pack(B, card("b1", "two", "2")))
            learnFirst(B)
            val source = sdk.exportPack(B)
            val items = sdk.items(B)
            assertIs<ImportResult.Success>(sdk.combinePacks(A, B))
            assertEquals(source, sdk.exportPack(B))
            assertEquals(items, sdk.items(B))
            assertEquals(ItemStatus.NEW, sdk.items(A).last().status)
        }

    @Test
    fun sectionsComeAlongAndAClashingIdGetsANewOneUnlessTheTitleMatches() =
        runTest {
            install(
                pack(
                    A,
                    card("a1", "one", "1", ""","section":"s1""""),
                    sections = """[{"id":"s1","title":"Basics"},{"id":"s2","title":"Verbs"}]""",
                ),
            )
            install(
                pack(
                    B,
                    card("b1", "two", "2", ""","section":"s1""""),
                    card("b2", "three", "3", ""","section":"s2""""),
                    card("b3", "four", "4", ""","section":"s3""""),
                    card("b4", "five", "5", ""","section":"s4""""),
                    card("b5", "six", "6"),
                    card("b6", "one", "1", ""","section":"s5""""),
                    sections =
                        """[{"id":"s1","title":"Basics"},{"id":"s2","title":"Other verbs","description":"d"},
                        {"id":"s3","title":"Nouns"},{"id":"s5","title":"Unused"}]""",
                ),
            )
            val shown = preview()
            assertEquals(5, shown.addedCount)
            assertEquals(1, shown.duplicateCount)
            assertEquals(2, shown.sectionCount)
            assertIs<ImportResult.Success>(sdk.combinePacks(A, B))
            val sections = sdk.observePack(A).first()!!.sections
            assertEquals(5, sections.size)
            assertEquals(listOf("s1", "s2"), sections.take(2).map { it.id })
            assertEquals(listOf("Basics", "Verbs", "Other verbs", "Nouns"), sections.filter { !it.isUngrouped }.map { it.title })
            val renamed = sections[2]
            assertEquals(PackCombiner.derivedId(B, "s2", setOf("s1", "s2")), renamed.id)
            assertEquals("d", renamed.descriptionText)
            assertEquals("s3", sections[3].id)
            val bySection = sdk.items(A).associate { it.id to it.sectionId }
            assertEquals("s1", bySection["a1"])
            assertEquals("s1", bySection["b1"])
            assertEquals(renamed.id, bySection["b2"])
            assertEquals("s3", bySection["b3"])
            assertEquals("", bySection["b4"])
            assertEquals("", bySection["b5"])
            assertFalse(bySection.containsKey("b6"))
        }

    @Test
    fun aPackWithoutSectionsReceivesTheOnesOfTheOtherPack() =
        runTest {
            install(pack(A, card("a1", "one", "1")))
            install(pack(B, card("b1", "two", "2", ""","section":"s1""""), sections = """[{"id":"s1","title":"Basics"}]"""))
            assertIs<ImportResult.Success>(sdk.combinePacks(A, B))
            assertEquals(
                listOf("s1", ""),
                sdk
                    .observePack(A)
                    .first()!!
                    .sections
                    .map { it.id },
            )
            assertEquals("s1", sdk.items(A).last().sectionId)
        }

    @Test
    fun figuresFromTheOtherPackMakeTheResultVersionTwo() =
        runTest {
            val figure = ""","figure":{"alt":"dot","svg":"$SVG"}"""
            install(pack(A, card("a1", "one", "1")))
            install(pack(B, card("b1", "two", "2", figure), card("b2", "three", "3")))
            assertIs<ImportResult.Success>(sdk.combinePacks(A, B))
            val exported = assertNotNull(sdk.exportPack(A))
            assertTrue(Regex(""""version":\s*2""").containsMatchIn(exported))
            assertEquals(3, assertIs<ImportPreview.Valid>(sdk.previewImport(exported)).itemCount)
        }

    @Test
    fun withoutFiguresTheVersionStaysOne() =
        runTest {
            install(pack(A, card("a1", "one", "1")))
            install(pack(B, card("b1", "two", "2")))
            assertIs<ImportResult.Success>(sdk.combinePacks(A, B))
            assertTrue(Regex(""""version":\s*1""").containsMatchIn(assertNotNull(sdk.exportPack(A))))
        }

    @Test
    fun theCombinedPackExportsAsAValidPackAndKeepsUnknownProperties() =
        runTest {
            install(pack(A, card("a1", "one", "1", ""","x-note":"keep""""), title = "Mine"))
            install(pack(B, card("b1", "two", "2", ""","x-note":"b","explanation":"why","tags":["t"]""")))
            assertIs<ImportResult.Success>(sdk.combinePacks(A, B))
            val exported = assertNotNull(sdk.exportPack(A))
            val preview = assertIs<ImportPreview.Valid>(sdk.previewImport(exported))
            assertEquals(2, preview.itemCount)
            assertEquals(0, preview.skippedCount)
            assertEquals("Mine", preview.title)
            assertTrue(exported.contains("\"x-note\": \"b\""))
            assertEquals("why", sdk.itemSource(A, "b1")?.let { Regex(""""explanation": "(.*?)"""").find(it)?.groupValues?.get(1) })
        }

    @Test
    fun aLaterUpdateFromTheOriginalFileDropsTheAddedItemsAndTheyComeBackWithTheirProgress() =
        runTest {
            val original = pack(A, card("a1", "one", "1"))
            install(original)
            install(pack(B, card("b1", "two", "2")))
            assertIs<ImportResult.Success>(sdk.combinePacks(A, B))
            val update = assertIs<ImportPreview.Valid>(sdk.previewImport(original))
            assertTrue(update.overwritesEdits)
            assertEquals(1, update.counts.removedItems)
            assertIs<ImportResult.Success>(sdk.importPack(original))
            assertEquals(listOf("a1"), ids(A))
            assertIs<ImportResult.Success>(sdk.combinePacks(A, B))
            assertEquals(listOf("a1", "b1"), ids(A))
        }

    @Test
    fun missingPacksAreReported() =
        runTest {
            install(pack(A, card("a1", "one", "1")))
            val error = assertIs<CombinePreview.Invalid>(sdk.previewCombine(A, "nope")).error
            assertEquals(ImportErrorCode.PACK_MISSING, error.code)
            assertEquals(ImportErrorCode.PACK_MISSING, assertIs<CombinePreview.Invalid>(sdk.previewCombine("nope", A)).error.code)
            assertEquals(ImportErrorCode.PACK_MISSING, assertIs<ImportResult.Failure>(sdk.combinePacks(A, "nope")).error.code)
        }
}
