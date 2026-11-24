package ch.orellbuehler.jackdaw.store

import ch.orellbuehler.jackdaw.FakeClock
import ch.orellbuehler.jackdaw.JackdawSdk
import ch.orellbuehler.jackdaw.card
import ch.orellbuehler.jackdaw.format.ImportErrorCode
import ch.orellbuehler.jackdaw.packJson
import ch.orellbuehler.jackdaw.study.Grade
import ch.orellbuehler.jackdaw.study.SessionConfig
import ch.orellbuehler.jackdaw.study.SessionState
import ch.orellbuehler.jackdaw.sync.newTestDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PACK = "test.pack"
private const val URL = "https://example.org/pack.jackdaw"

class LibraryTest {
    private val clock = FakeClock()
    private val sdk = JackdawSdk(newTestDriver(), clock, { TimeZone.UTC }, Random(1), Dispatchers.Unconfined)
    private val source = packJson(card("a", "front a", "back a"), card("b", "front b", "back b"))

    private suspend fun pack() = sdk.observePacks().first().single { it.id == PACK }

    private suspend fun failure(result: ImportResult) = assertIs<ImportResult.Failure>(result).error.code

    @Test
    fun archivedPacksStayInstalledButLeaveTheDueCount() =
        runTest {
            assertIs<ImportResult.Success>(sdk.importPack(source))
            val session = sdk.startSession(PACK, SessionConfig.learn())
            while (true) {
                when (session.state.value) {
                    is SessionState.Question -> session.reveal()
                    is SessionState.Feedback -> session.selfGrade(Grade.AGAIN)
                    else -> break
                }
                if (sdk.observeStats().first().dueToday > 0) break
            }
            assertTrue(sdk.observeStats().first().dueToday > 0)
            sdk.setPackArchived(PACK, true)
            assertTrue(pack().archived)
            assertEquals(0, sdk.observeStats().first().dueToday)
            sdk.setPackArchived(PACK, false)
            assertFalse(pack().archived)
            assertTrue(sdk.observeStats().first().dueToday > 0)
        }

    @Test
    fun aPackRemembersItsUrlAcrossUpdates() =
        runTest {
            assertIs<ImportResult.Success>(sdk.importPackFromUrl(source, URL, false))
            assertEquals(URL, pack().sourceUrl)
            assertIs<ImportResult.Success>(sdk.importPack(source))
            assertEquals(URL, pack().sourceUrl)
            sdk.removePackUrl(PACK)
            assertNull(pack().sourceUrl)
        }

    @Test
    fun editingAnItemMarksThePackAndAnUpdateWarnsAndClearsIt() =
        runTest {
            assertIs<ImportResult.Success>(sdk.importPackFromUrl(source, URL, false))
            assertFalse(assertIs<ImportPreview.Valid>(sdk.previewImport(source)).overwritesEdits)
            val item = assertNotNull(sdk.itemSource(PACK, "a"))
            assertIs<ImportResult.Success>(sdk.saveItem(PACK, "a", item.replace("front a", "changed")))
            assertTrue(pack().edited)
            assertEquals("changed", sdk.items(PACK).first { it.id == "a" }.title)
            assertTrue(assertNotNull(sdk.exportPack(PACK)).contains("changed"))
            assertTrue(assertIs<ImportPreview.Valid>(sdk.previewImport(source)).overwritesEdits)
            assertIs<ImportResult.Success>(sdk.importPackFromUrl(source, URL, false))
            assertFalse(pack().edited)
            assertEquals("front a", sdk.items(PACK).first { it.id == "a" }.title)
        }

    @Test
    fun itemsCanBeAddedAndDeleted() =
        runTest {
            assertIs<ImportResult.Success>(sdk.importPack(source))
            assertIs<ImportResult.Success>(sdk.saveItem(PACK, null, card("c", "front c", "back c")))
            assertEquals(listOf("a", "b", "c"), sdk.items(PACK).map { it.id })
            assertIs<ImportResult.Success>(sdk.deleteItem(PACK, "b"))
            assertEquals(listOf("a", "c"), sdk.items(PACK).map { it.id })
            assertEquals(2, pack().itemCount)
            assertIs<ImportResult.Success>(sdk.deleteItem(PACK, "a"))
            assertEquals(ImportErrorCode.NO_VALID_ITEMS, failure(sdk.deleteItem(PACK, "c")))
            assertEquals(listOf("c"), sdk.items(PACK).map { it.id })
        }

    @Test
    fun aBrokenItemIsNotSaved() =
        runTest {
            assertIs<ImportResult.Success>(sdk.importPack(source))
            assertEquals(ImportErrorCode.ITEM_INVALID, failure(sdk.saveItem(PACK, "a", "{ not json")))
            assertEquals(ImportErrorCode.ITEM_INVALID, failure(sdk.saveItem(PACK, "a", """{"id":"a","type":"card","front":"x"}""")))
            assertEquals(ImportErrorCode.ITEM_INVALID, failure(sdk.saveItem(PACK, "a", """{"id":"a","type":"riddle"}""")))
            assertEquals(ImportErrorCode.ITEM_INVALID, failure(sdk.saveItem(PACK, null, card("b", "again", "b"))))
            assertEquals(ImportErrorCode.ITEM_INVALID, failure(sdk.saveItem(PACK, "zz", card("zz", "x", "y"))))
            assertEquals(ImportErrorCode.ITEM_INVALID, failure(sdk.saveItem(PACK, "a", card("a", "x", "y", ""","figure":{"svg":"<p>"}"""))))
            assertEquals(ImportErrorCode.PACK_MISSING, failure(sdk.saveItem("nope", null, card("c", "x", "y"))))
            assertFalse(pack().edited)
            assertEquals("front a", sdk.items(PACK).first { it.id == "a" }.title)
        }

    @Test
    fun anItemWithAFigureMakesThePackVersionTwo() =
        runTest {
            assertIs<ImportResult.Success>(sdk.importPack(source))
            val svg = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 4 4'><circle r='1'/></svg>"
            val figure = ""","figure":{"alt":"dot","svg":"$svg"}"""
            assertIs<ImportResult.Success>(sdk.saveItem(PACK, "a", card("a", "front a", "back a", figure)))
            assertTrue(Regex(""""version":\s*2""").containsMatchIn(assertNotNull(sdk.exportPack(PACK))))
        }

    @Test
    fun titleAndDescriptionCanBeChanged() =
        runTest {
            assertIs<ImportResult.Success>(sdk.importPack(source))
            assertIs<ImportResult.Success>(sdk.updatePackInfo(PACK, " New title ", "About it"))
            assertEquals("New title", pack().title)
            assertEquals("About it", pack().descriptionText)
            assertIs<ImportResult.Success>(sdk.updatePackInfo(PACK, "New title", " "))
            assertNull(pack().descriptionText)
            assertEquals(ImportErrorCode.TITLE_INVALID, failure(sdk.updatePackInfo(PACK, "  ", "")))
        }

    @Test
    fun groupsHoldPacksAndForgetDeletedOnes() =
        runTest {
            assertIs<ImportResult.Success>(sdk.importPack(source))
            assertNull(sdk.createGroup("  "))
            val favourites = assertNotNull(sdk.createGroup(" Favourites "))
            val languages = assertNotNull(sdk.createGroup("Languages"))
            sdk.setPackInGroup(PACK, favourites, true)
            sdk.setPackInGroup(PACK, languages, true)
            sdk.setPackInGroup(PACK, "unknown", true)
            sdk.setPackInGroup("unknown", favourites, true)
            assertEquals(listOf("Favourites", "Languages"), sdk.observeGroups().first().map { it.name })
            assertEquals(listOf(listOf(PACK), listOf(PACK)), sdk.observeGroups().first().map { it.packIds })
            assertEquals(setOf(favourites, languages), pack().groupIds.toSet())
            sdk.renameGroup(favourites, "Best")
            sdk.setPackInGroup(PACK, languages, false)
            sdk.deleteGroup(languages)
            assertEquals(listOf("Best"), sdk.observeGroups().first().map { it.name })
            assertEquals(listOf(favourites), pack().groupIds)
            sdk.deletePack(PACK)
            assertEquals(
                emptyList(),
                sdk
                    .observeGroups()
                    .first()
                    .single()
                    .packIds,
            )
            assertIs<ImportResult.Success>(sdk.importPack(source))
            assertEquals(emptyList(), pack().groupIds)
            assertFalse(pack().archived)
        }
}
