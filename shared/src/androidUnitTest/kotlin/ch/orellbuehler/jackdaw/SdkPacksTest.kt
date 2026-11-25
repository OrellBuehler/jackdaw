package ch.orellbuehler.jackdaw

import app.cash.turbine.test
import ch.orellbuehler.jackdaw.format.ImportErrorCode
import ch.orellbuehler.jackdaw.format.ItemType
import ch.orellbuehler.jackdaw.samples.GENERATED_SAMPLE_PACKS
import ch.orellbuehler.jackdaw.store.ImportPreview
import ch.orellbuehler.jackdaw.store.ImportResult
import ch.orellbuehler.jackdaw.store.ItemStatus
import ch.orellbuehler.jackdaw.study.Facet
import ch.orellbuehler.jackdaw.study.Grade
import ch.orellbuehler.jackdaw.study.PracticeFilter
import ch.orellbuehler.jackdaw.study.SessionConfig
import ch.orellbuehler.jackdaw.study.SessionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SdkPacksTest {
    private val fixture = SdkFixture()
    private val sdk get() = fixture.sdk
    private val day = 86_400_000L

    private fun sample(name: String) = GENERATED_SAMPLE_PACKS.first { it.first == name }.second

    @Test
    fun authoringPromptDescribesTheFormat() {
        val prompt = sdk.authoringPrompt()
        assertTrue(prompt.startsWith("You are creating a learning pack for the app Jackdaw."))
        assertTrue("\"format\": \"jackdaw-pack\"" in prompt)
        assertTrue(prompt.endsWith("Then output only the JSON code block."))
    }

    @Test
    fun importedPackAppearsInTheLibrary() =
        runTest {
            assertEquals(emptyList(), sdk.observePacks().first())
            val result = fixture.importOk(sample("spanish-basics.jackdaw"))
            assertEquals(41, result.itemCount)
            assertFalse(result.isUpdate)
            assertEquals(emptyList(), result.warnings)
            assertEquals(0, result.skippedCount)
            assertEquals(41, result.counts.addedItems)
            val pack = sdk.observePacks().first().single()
            assertEquals("Spanish Basics", pack.title)
            assertTrue(pack.descriptionText.orEmpty().startsWith("About 40 essential Spanish words"))
            assertEquals("🇪🇸", pack.icon)
            assertEquals("#C60B1E", pack.color)
            assertEquals("es", pack.language)
            assertEquals(41, pack.itemCount)
            assertEquals(82, pack.facetCount)
            assertEquals(82, pack.newCount)
            assertEquals(0, pack.learningCount + pack.dueCount + pack.masteredCount + pack.reviewCount)
            assertEquals(0.0, pack.progress)
            assertEquals(0L, pack.lastStudiedAt)
            assertEquals(fixture.clock.millis, pack.importedAt)
        }

    @Test
    fun everySamplePackImports() =
        runTest {
            val samples = sdk.samplePacks()
            assertEquals(GENERATED_SAMPLE_PACKS.size, samples.size)
            assertTrue(samples.all { it.title.isNotBlank() && it.itemCount > 0 && it.descriptionText.isNotBlank() })
            for (sample in samples) assertIs<ImportResult.Success>(sdk.importSample(sample.id), sample.id)
            assertEquals(
                samples.map { it.id }.toSet(),
                sdk
                    .observePacks()
                    .first()
                    .map { it.id }
                    .toSet(),
            )
            assertEquals(samples.sumOf { it.itemCount }, sdk.observePacks().first().sumOf { it.itemCount })
            val again = assertIs<ImportResult.Success>(sdk.importSample(samples.first().id))
            assertTrue(again.isUpdate)
        }

    @Test
    fun unknownSampleFails() =
        runTest {
            val failure = assertIs<ImportResult.Failure>(sdk.importSample("nope"))
            assertEquals(ImportErrorCode.UNKNOWN_SAMPLE, failure.error.code)
        }

    @Test
    fun invalidFilesAreRejectedWithoutTouchingTheDatabase() =
        runTest {
            fixture.importOk(flipPack())
            val failure =
                assertIs<ImportResult.Failure>(sdk.importPack("""{"format":"jackdaw-pack","version":4,"id":"x","title":"t","items":[]}"""))
            assertEquals(ImportErrorCode.VERSION_TOO_NEW, failure.error.code)
            assertTrue(failure.error.message.isNotBlank())
            assertEquals(ImportErrorCode.NOT_JSON, assertIs<ImportResult.Failure>(sdk.importPack("garbage")).error.code)
            assertEquals(1, sdk.observePacks().first().size)
        }

    @Test
    fun importReportsWarningsForSkippedItems() =
        runTest {
            val json = flipPack(extraItems = listOf("""{"id":"bad","type":"card"}""", """{"id":"m","type":"matching"}"""))
            val result = fixture.importOk(json)
            assertEquals(3, result.itemCount)
            assertEquals(2, result.skippedCount)
            assertEquals(2, result.warnings.size)
        }

    @Test
    fun previewDescribesTheImportWithoutInstallingIt() =
        runTest {
            val preview = assertIs<ImportPreview.Valid>(sdk.previewImport(sample("format-tour.jackdaw")))
            assertEquals("Jackdaw Format Tour", preview.title)
            assertEquals(13, preview.itemCount)
            assertEquals(2, preview.sectionCount)
            assertEquals(4, preview.revision)
            assertFalse(preview.isUpdate)
            assertEquals(0, preview.skippedCount)
            assertEquals(2, preview.typeCounts.first { it.type == ItemType.CARD }.count)
            assertEquals(13, preview.typeCounts.sumOf { it.count })
            assertEquals(13, preview.counts.addedItems)
            assertEquals(emptyList(), sdk.observePacks().first())
            val invalid = assertIs<ImportPreview.Invalid>(sdk.previewImport("{}"))
            assertEquals(ImportErrorCode.NOT_A_PACK, invalid.error.code)
        }

    @Test
    fun previewOfAnInstalledPackShowsTheUpdateAndDowngrade() =
        runTest {
            fixture.importOk(flipPack(cards = 3, revision = 5, title = "Old title"))
            val update = assertIs<ImportPreview.Valid>(sdk.previewImport(flipPack(cards = 4, revision = 6)))
            assertTrue(update.isUpdate)
            assertFalse(update.isDowngrade)
            assertEquals("Old title", update.installedTitle)
            assertEquals(5, update.installedRevision)
            assertEquals(1, update.counts.addedItems)
            assertEquals(3, update.counts.keptItems)
            val downgrade = assertIs<ImportPreview.Valid>(sdk.previewImport(flipPack(cards = 3, revision = 4)))
            assertTrue(downgrade.isDowngrade)
            val same = assertIs<ImportPreview.Valid>(sdk.previewImport(flipPack(cards = 3, revision = 5)))
            assertFalse(same.isDowngrade)
            val unversioned = assertIs<ImportPreview.Valid>(sdk.previewImport(flipPack(cards = 3)))
            assertFalse(unversioned.isDowngrade)
        }

    @Test
    fun downgradeNeedsConfirmation() =
        runTest {
            fixture.importOk(flipPack(revision = 5, title = "Five"))
            val refused = assertIs<ImportResult.Failure>(sdk.importPack(flipPack(revision = 4, title = "Four")))
            assertEquals(ImportErrorCode.REVISION_DOWNGRADE, refused.error.code)
            assertEquals(
                "Five",
                sdk
                    .observePacks()
                    .first()
                    .single()
                    .title,
            )
            assertIs<ImportResult.Success>(sdk.importPackAllowingDowngrade(flipPack(revision = 4, title = "Four")))
            assertEquals(
                "Four",
                sdk
                    .observePacks()
                    .first()
                    .single()
                    .title,
            )
            assertIs<ImportResult.Success>(sdk.importPack(flipPack(revision = 4, title = "Four again")))
            assertIs<ImportResult.Success>(sdk.importPack(flipPack(title = "No revision")))
        }

    @Test
    fun updateKeepsProgressPerItemIdAndResetsChangedTypes() =
        runTest {
            fixture.importOk(
                flipPack(
                    cards = 3,
                    revision = 1,
                    extraItems = listOf("""{"id":"x","type":"truefalse","statement":"s","answer":true}"""),
                ),
            )
            fixture.studyFlipCards("test.flip", Grade.GOOD, SessionConfig.learn().withNewLimit(2))
            val before = sdk.items("test.flip").associateBy { it.id }
            assertEquals(ItemStatus.REVIEW, before.getValue("c1").status)
            assertEquals(ItemStatus.NEW, before.getValue("c3").status)

            val updated =
                flipPack(
                    cards = 2,
                    revision = 2,
                    title = "Renamed",
                    extraItems =
                        listOf(
                            """{"id":"x","type":"text","question":"changed type","answers":["a"]}""",
                            """{"id":"fresh","type":"card","front":"new","back":"new"}""",
                        ),
                ).replace("front 1", "front one reworded")
            val result = assertIs<ImportResult.Success>(sdk.importPack(updated))
            assertTrue(result.isUpdate)
            assertEquals(1, result.counts.addedItems)
            assertEquals(1, result.counts.resetItems)
            assertEquals(1, result.counts.removedItems)
            assertEquals(2, result.counts.keptItems)

            val after = sdk.items("test.flip").associateBy { it.id }
            assertEquals(setOf("c1", "c2", "x", "fresh"), after.keys)
            assertEquals(ItemStatus.REVIEW, after.getValue("c1").status)
            assertEquals("front one reworded", after.getValue("c1").title)
            assertEquals(ItemStatus.REVIEW, after.getValue("c2").status)
            assertEquals(ItemStatus.NEW, after.getValue("x").status)
            assertEquals(ItemType.TEXT, after.getValue("x").type)
            assertEquals(ItemStatus.NEW, after.getValue("fresh").status)
            assertEquals(listOf("c1", "c2", "x", "fresh"), sdk.items("test.flip").map { it.id })
            assertEquals(
                "Renamed",
                sdk
                    .observePacks()
                    .first()
                    .single()
                    .title,
            )
        }

    @Test
    fun updateKeepsTheImportDateAndReplacesTheMetadata() =
        runTest {
            fixture.importOk(flipPack(title = "Before"))
            val importedAt = fixture.clock.millis
            fixture.clock.advanceDays(3)
            fixture.importOk(flipPack(title = "After"))
            val pack = sdk.observePacks().first().single()
            assertEquals("After", pack.title)
            assertEquals(importedAt, pack.importedAt)
            assertEquals(fixture.clock.millis, pack.updatedAt)
        }

    @Test
    fun togglingReversibleAddsAndRemovesFacetsButKeepsForwardProgress() =
        runTest {
            fixture.importOk(flipPack(cards = 2, revision = 1))
            fixture.studyFlipCards("test.flip")
            assertEquals(
                2,
                sdk
                    .observePacks()
                    .first()
                    .single()
                    .facetCount,
            )
            fixture.importOk(flipPack(cards = 2, revision = 2, reversible = true))
            val reversible = sdk.observePacks().first().single()
            assertEquals(4, reversible.facetCount)
            assertEquals(2, reversible.newCount)
            val items = sdk.items("test.flip")
            assertEquals(listOf(Facet.FORWARD, Facet.REVERSE), items.first().facets.map { it.facet })
            assertEquals(
                ItemStatus.REVIEW,
                items
                    .first()
                    .facets
                    .first()
                    .status,
            )
            assertEquals(
                ItemStatus.NEW,
                items
                    .first()
                    .facets
                    .last()
                    .status,
            )
            fixture.importOk(flipPack(cards = 2, revision = 3, reversible = false))
            assertEquals(
                2,
                sdk
                    .observePacks()
                    .first()
                    .single()
                    .facetCount,
            )
            assertEquals(
                ItemStatus.REVIEW,
                sdk
                    .items("test.flip")
                    .first()
                    .facets
                    .single()
                    .status,
            )
        }

    @Test
    fun deleteRemovesThePackButKeepsTheActivityHistory() =
        runTest {
            fixture.importOk(flipPack())
            fixture.importOk(flipPack(id = "other", title = "Other"))
            fixture.studyFlipCards("test.flip")
            sdk.deletePack("test.flip")
            assertEquals(listOf("other"), sdk.observePacks().first().map { it.id })
            assertEquals(emptyList(), sdk.items("test.flip"))
            assertNull(sdk.exportPack("test.flip"))
            assertNull(sdk.observePack("test.flip").first())
            assertEquals(3, sdk.observeStats().first().reviewsToday)
            sdk.deletePack("does.not.exist")
            val fresh = fixture.importOk(flipPack())
            assertFalse(fresh.isUpdate)
            assertEquals(
                3,
                sdk
                    .observePacks()
                    .first()
                    .first { it.id == "test.flip" }
                    .newCount,
            )
        }

    @Test
    fun resetProgressStartsOverButKeepsStats() =
        runTest {
            fixture.importOk(flipPack())
            fixture.studyFlipCards("test.flip")
            assertEquals(
                0,
                sdk
                    .observePacks()
                    .first()
                    .single()
                    .newCount,
            )
            sdk.resetProgress("test.flip")
            val pack = sdk.observePacks().first().single()
            assertEquals(3, pack.newCount)
            assertEquals(0.0, pack.progress)
            assertEquals(3, sdk.items("test.flip").count { it.status == ItemStatus.NEW })
            assertEquals(3, sdk.observeStats().first().reviewsToday)
            assertEquals(3, fixture.studyFlipCards("test.flip").total)
        }

    @Test
    fun exportReturnsTheOriginalText() =
        runTest {
            val text = sample("swiss-cantons.jackdaw")
            sdk.importPack("\uFEFF$text")
            assertEquals(text, sdk.exportPack("com.github.orellbuehler.jackdaw.examples.swiss-cantons"))
            assertNull(sdk.exportPack("missing"))
            val updated = text.replace("\"revision\": 1", "\"revision\": 2")
            sdk.importPack(updated)
            assertEquals(updated, sdk.exportPack("com.github.orellbuehler.jackdaw.examples.swiss-cantons"))
        }

    @Test
    fun exportedPackCanBeReimportedElsewhere() =
        runTest {
            fixture.importOk(sample("world-history-20th-century.jackdaw"))
            val exported = assertNotNull(sdk.exportPack("com.github.orellbuehler.jackdaw.examples.world-history-20th-century"))
            val other = SdkFixture()
            val result = other.importOk(exported)
            assertEquals(31, result.itemCount)
            assertEquals(emptyList(), result.warnings)
        }

    @Test
    fun dataSurvivesRestartingTheSdkOnTheSameDatabase() =
        runTest {
            fixture.importOk(flipPack())
            fixture.studyFlipCards("test.flip")
            val restarted = fixture.restart()
            val pack = restarted.observePacks().first().single()
            assertEquals(3, pack.learningCount + pack.reviewCount)
            assertEquals(3, restarted.observeStats().first().reviewsToday)
        }

    @Test
    fun itemListShowsTitlesSubtitlesAndStates() =
        runTest {
            fixture.importOk(sample("format-tour.jackdaw"))
            val items = sdk.items("com.github.orellbuehler.jackdaw.examples.format-tour").associateBy { it.id }
            assertEquals(13, items.size)
            val card = items.getValue("card-extension")
            assertEquals(ItemType.CARD, card.type)
            assertEquals("What is the file extension of a Jackdaw pack?", card.title)
            assertEquals(".jackdaw", card.subtitle)
            assertEquals("basics", card.sectionId)
            assertEquals(0, card.position)
            assertEquals(ItemStatus.NEW, card.status)
            assertFalse(card.isDue)
            assertEquals(listOf(Facet.FORWARD, Facet.REVERSE), items.getValue("card-format-value").facets.map { it.facet })
            assertEquals("open", items.getValue("choice-single").subtitle)
            assertEquals("\u2717", items.getValue("tf-progress").subtitle)
            assertTrue(items.getValue("cloze-syntax").title.contains("\u2026"))
            assertTrue(items.getValue("cloze-syntax").subtitle.contains("two"))
            assertEquals(
                "Property names starting with which prefix are reserved for vendor extensions?",
                items.getValue("text-extension-prefix").title,
            )
            assertEquals("x-", items.getValue("text-extension-prefix").subtitle)
            assertTrue(items.getValue("order-update").subtitle.contains(" > "))
        }

    @Test
    fun itemStatusFollowsTheLearningState() =
        runTest {
            fixture.importOk(flipPack(cards = 3))
            fixture.studyFlipCards("test.flip", Grade.GOOD, SessionConfig.learn().withNewLimit(1))
            fixture.studyFlipCards("test.flip", Grade.AGAIN, SessionConfig.learn().withNewLimit(1).withSection(null))
            val items = sdk.items("test.flip")
            assertEquals(ItemStatus.REVIEW, items[0].status)
            assertEquals(1, items[0].facets.single().intervalDays)
            assertEquals(1, items[0].facets.single().reps)
            assertEquals(ItemStatus.LEARNING, items[1].status)
            assertEquals(1, items[1].facets.single().reps)
            assertTrue(items[1].isDue)
            assertEquals(ItemStatus.NEW, items[2].status)
            assertFalse(items[0].isDue)
            fixture.clock.advanceDays(1)
            assertTrue(sdk.items("test.flip")[0].isDue)
        }

    @Test
    fun packDetailListsSectionsTypesAndFilters() =
        runTest {
            fixture.importOk(sample("swiss-cantons.jackdaw"))
            val id = "com.github.orellbuehler.jackdaw.examples.swiss-cantons"
            val detail = assertNotNull(sdk.observePack(id).first())
            assertEquals(36, detail.summary.itemCount)
            assertEquals(
                listOf("central", "eastern", "zurich-northwest", "mittelland", "lake-geneva-ticino", "facts"),
                detail.sections.map { it.id },
            )
            assertEquals(36, detail.sections.sumOf { it.itemCount })
            assertEquals(detail.summary.newCount, detail.sections.sumOf { it.newCount })
            assertEquals(26, detail.typeCounts.first { it.type == ItemType.CARD }.count)
            assertEquals(6, detail.newPerSession)
            assertFalse(detail.randomOrder)
            assertEquals("CC0-1.0", detail.license)
            assertTrue(detail.tags.isNotEmpty())
            assertTrue(PracticeFilter.ALL in detail.practiceFilters)
            assertTrue(PracticeFilter.QUIZ in detail.practiceFilters)
            assertTrue(PracticeFilter.TYPING in detail.practiceFilters)
            assertFalse(PracticeFilter.FLASHCARDS in detail.practiceFilters)
            assertTrue(detail.sections.none { it.isUngrouped })
        }

    @Test
    fun packDetailShowsAnUngroupedSectionForItemsWithoutOne() =
        runTest {
            val json =
                """{"format":"jackdaw-pack","version":1,"id":"p","title":"P","sections":[{"id":"a","title":"A"}],"items":[
                {"id":"i1","type":"card","front":"f","back":"b","section":"a"},
                {"id":"i2","type":"card","front":"f","back":"b"},
                {"id":"i3","type":"card","front":"f","back":"b","section":"unknown"}]}"""
            val result = fixture.importOk(json)
            assertEquals(1, result.warnings.size)
            val detail = assertNotNull(sdk.observePack("p").first())
            assertEquals(listOf("a", ""), detail.sections.map { it.id })
            assertEquals(listOf(1, 2), detail.sections.map { it.itemCount })
            assertTrue(detail.sections.last().isUngrouped)
            assertEquals(listOf("i2", "i3"), sdk.items("p").filter { it.sectionId == "" }.map { it.id })
        }

    @Test
    fun libraryFlowEmitsWhenProgressChanges() =
        runTest {
            fixture.importOk(flipPack())
            sdk.observePacks().test {
                assertEquals(3, awaitItem().single().newCount)
                fixture.studyFlipCards("test.flip", Grade.GOOD, SessionConfig.learn().withNewLimit(1))
                val updated = expectMostRecentItem().single()
                assertEquals(2, updated.newCount)
                assertEquals(1, updated.reviewCount)
                assertTrue(updated.lastStudiedAt > 0)
                assertTrue(updated.progress > 0.0)
                fixture.importOk(flipPack(id = "second", title = "Second"))
                assertEquals(2, expectMostRecentItem().size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun refreshRecomputesTimeDependentCounts() =
        runTest {
            fixture.importOk(flipPack(cards = 1))
            fixture.studyFlipCards("test.flip")
            sdk.observePacks().test {
                assertEquals(0, awaitItem().single().dueCount)
                fixture.clock.advanceDays(2)
                sdk.refresh()
                assertEquals(1, awaitItem().single().dueCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun masteredAndProgressFollowTheInterval() =
        runTest {
            fixture.importOk(flipPack(cards = 1))
            repeat(8) {
                fixture.studyFlipCards("test.flip")
                fixture.clock.advanceDays(200)
            }
            val pack = sdk.observePacks().first().single()
            assertEquals(1, pack.masteredCount)
            assertEquals(1.0, pack.progress)
            assertEquals(1, sdk.observeStats().first().facetsMastered)
            assertEquals(ItemStatus.MASTERED, sdk.items("test.flip").single().status)
        }

    @Test
    fun aPackAtTheRecommendedLimitStaysUsable() =
        runTest {
            val items =
                (1..5000).joinToString(",") {
                    """{"id":"i$it","type":"card","front":"front $it","back":"back $it","section":"s${it % 10}"}"""
                }
            val sections = (0..9).joinToString(",") { """{"id":"s$it","title":"Section $it"}""" }
            val json = """{"format":"jackdaw-pack","version":1,"id":"big","title":"Big","sections":[$sections],"items":[$items]}"""
            val started = System.nanoTime()
            val result = fixture.importOk(json)
            val importMs = (System.nanoTime() - started) / 1_000_000
            assertEquals(5000, result.itemCount)
            val session = sdk.startSession("big", SessionConfig.practice().withFilter(PracticeFilter.QUIZ).withSize(20))
            assertEquals(20, assertIs<SessionState.Question>(session.state.value).total)
            val updateStarted = System.nanoTime()
            fixture.importOk(json.replace("front 1\"", "front one\""))
            val updateMs = (System.nanoTime() - updateStarted) / 1_000_000
            assertEquals(
                5000,
                sdk
                    .observePack("big")
                    .first()!!
                    .summary.itemCount,
            )
            assertTrue(importMs < 20_000 && updateMs < 20_000, "import ${importMs}ms, update ${updateMs}ms")
        }
}
