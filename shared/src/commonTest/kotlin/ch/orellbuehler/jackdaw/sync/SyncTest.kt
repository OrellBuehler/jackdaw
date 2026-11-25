package ch.orellbuehler.jackdaw.sync

import ch.orellbuehler.jackdaw.FakeClock
import ch.orellbuehler.jackdaw.JackdawSdk
import ch.orellbuehler.jackdaw.store.CombinePreview
import ch.orellbuehler.jackdaw.store.ImportResult
import ch.orellbuehler.jackdaw.store.ItemStatus
import ch.orellbuehler.jackdaw.store.ItemSummary
import ch.orellbuehler.jackdaw.store.ThemePreference
import ch.orellbuehler.jackdaw.study.Grade
import ch.orellbuehler.jackdaw.study.SessionConfig
import ch.orellbuehler.jackdaw.study.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PACK = "sync.pack"

private fun cardPack(
    id: String = PACK,
    cards: Int = 3,
    title: String = "Sync Pack",
    typeOverride: Map<String, String> = emptyMap(),
): String {
    val items =
        (1..cards).map {
            val name = "c$it"
            if (typeOverride[name] == "text") {
                """{"id":"$name","type":"text","question":"question $it","answers":["answer $it"]}"""
            } else {
                """{"id":"$name","type":"card","front":"front $it","back":"back $it"}"""
            }
        }
    return """{"format":"jackdaw-pack","version":1,"id":"$id","title":"$title",
        "study":{"exercises":{"card":["flip"]},"reversible":false,"newPerSession":50},"items":[${items.joinToString(",")}]}"""
}

private class Device(
    val clock: FakeClock,
    seed: Int,
    tracking: Boolean = true,
) {
    val sdk = JackdawSdk(newTestDriver(), clock, { TimeZone.UTC }, Random(seed), Dispatchers.Unconfined)

    init {
        if (tracking) sdk.sync.enableTracking()
    }

    suspend fun import(json: String) = assertIs<ImportResult.Success>(sdk.importPack(json))

    suspend fun study(
        packId: String = PACK,
        grade: Grade = Grade.GOOD,
    ) {
        val session = sdk.startSession(packId, SessionConfig.learn())
        var retry = false
        while (true) {
            when (val state = session.state.value) {
                is SessionState.Question -> {
                    retry = state.isRetry
                    session.reveal()
                }
                is SessionState.Feedback -> session.selfGrade(if (retry) Grade.GOOD else grade)
                is SessionState.Finished -> return
            }
        }
    }

    suspend fun items(packId: String = PACK): List<ItemSummary> = sdk.items(packId)

    suspend fun totalReviews(): Int = sdk.observeStats().first().totalReviews
}

private suspend fun push(
    from: Device,
    to: Device,
) {
    val sync = from.sync()
    val names = sync.pendingRecords()
    val packs = ArrayList<SyncPackRecord>()
    val reviews = ArrayList<SyncReviewRecord>()
    val settings = ArrayList<SyncSettingRecord>()
    val versions = HashMap<String, Long>()
    for (name in names) {
        when (sync.kindOf(name)) {
            SyncNames.PACK ->
                sync.packRecord(name)?.let {
                    packs += it
                    versions[name] = it.version
                }
            SyncNames.REVIEW ->
                sync.reviewRecord(name)?.let {
                    reviews += it
                    versions[name] = it.version
                }
            SyncNames.SETTING ->
                sync.settingRecord(name)?.let {
                    settings += it
                    versions[name] = it.version
                }
        }
    }
    to.sdk.sync.apply(packs, reviews, settings)
    for ((name, version) in versions) sync.markSent(name, version, null)
}

private fun Device.sync() = sdk.sync

private suspend fun converge(
    a: Device,
    b: Device,
) {
    repeat(3) {
        push(a, b)
        push(b, a)
    }
}

class SyncTest {
    private val clock = FakeClock(1_800_000_000_000L)

    private fun device(seed: Int = 1) = Device(clock, seed)

    @Test
    fun twoDevicesStudyingOfflineKeepEveryReview() =
        runTest {
            val a = device(1)
            val b = device(2)
            a.import(cardPack())
            converge(a, b)
            clock.advanceMinutes(5)
            a.study(grade = Grade.GOOD)
            clock.advanceMinutes(5)
            b.study(grade = Grade.AGAIN)
            assertEquals(3, a.totalReviews())
            assertEquals(3, b.totalReviews())
            converge(a, b)
            assertEquals(6, a.totalReviews())
            assertEquals(6, b.totalReviews())
            assertEquals(a.items(), b.items())
            assertTrue(a.items().all { item -> item.facets.all { it.reps == 2 } })
        }

    @Test
    fun aFreshDeviceRebuildsTheSameScheduleFromTheLogAlone() =
        runTest {
            val a = device(1)
            a.import(cardPack(cards = 4))
            a.study(grade = Grade.GOOD)
            clock.advanceDays(2)
            a.study(grade = Grade.AGAIN)
            clock.advanceDays(1)
            a.study(grade = Grade.EASY)
            val b = device(2)
            converge(a, b)
            assertEquals(a.items(), b.items())
            assertTrue(a.items().any { it.facets.any { facet -> facet.reps >= 2 } })
            assertEquals(a.totalReviews(), b.totalReviews())
        }

    @Test
    fun applyingReviewsBeforeTheirPackStillGivesTheSameSchedule() =
        runTest {
            val a = device(1)
            a.import(cardPack())
            a.study()
            val b = device(2)
            val sync = a.sync()
            val names = sync.pendingRecords()
            val reviews = names.filter { sync.kindOf(it) == SyncNames.REVIEW }.mapNotNull { sync.reviewRecord(it) }
            val packs = names.filter { sync.kindOf(it) == SyncNames.PACK }.mapNotNull { sync.packRecord(it) }
            b.sdk.sync.apply(emptyList(), reviews, emptyList())
            assertTrue(
                b.sdk
                    .observePacks()
                    .first()
                    .isEmpty(),
            )
            b.sdk.sync.apply(packs, emptyList(), emptyList())
            assertEquals(a.items(), b.items())
        }

    @Test
    fun aCombinedPackReachesTheOtherDeviceWithItsProgress() =
        runTest {
            val a = device(1)
            val b = device(2)
            a.import(cardPack())
            a.import(cardPack(id = "sync.other", cards = 5, title = "Other"))
            converge(a, b)
            clock.advanceMinutes(5)
            b.study()
            clock.advanceMinutes(5)
            val shown = assertIs<CombinePreview.Valid>(a.sdk.previewCombine(PACK, "sync.other"))
            assertEquals(2, shown.addedCount)
            assertEquals(3, shown.duplicateCount)
            assertIs<ImportResult.Success>(a.sdk.combinePacks(PACK, "sync.other"))
            assertEquals(3 + 2, a.items().size)
            converge(a, b)
            assertEquals(a.items(), b.items())
            assertEquals(a.sdk.exportPack(PACK), b.sdk.exportPack(PACK))
            assertTrue(b.items().take(3).all { item -> item.facets.all { it.reps == 1 } })
            assertTrue(b.items().drop(3).all { it.status == ItemStatus.NEW })
        }

    @Test
    fun deletingAPackPropagatesAndIsNotResurrectedByAStaleDevice() =
        runTest {
            val a = device(1)
            val b = device(2)
            a.import(cardPack())
            converge(a, b)
            clock.advanceMinutes(10)
            a.sdk.deletePack(PACK)
            assertTrue(
                a.sdk
                    .observePacks()
                    .first()
                    .isEmpty(),
            )
            b.sdk.sync.queueEverything()
            converge(a, b)
            assertTrue(
                a.sdk
                    .observePacks()
                    .first()
                    .isEmpty(),
            )
            assertTrue(
                b.sdk
                    .observePacks()
                    .first()
                    .isEmpty(),
            )
            assertNull(b.sdk.exportPack(PACK))
        }

    @Test
    fun importingADeletedPackAgainStartsFreshOnEveryDevice() =
        runTest {
            val a = device(1)
            val b = device(2)
            a.import(cardPack())
            a.study()
            converge(a, b)
            assertTrue(b.items().all { it.status != ItemStatus.NEW })
            clock.advanceMinutes(10)
            a.sdk.deletePack(PACK)
            clock.advanceMinutes(10)
            a.import(cardPack())
            assertTrue(a.items().all { it.status == ItemStatus.NEW })
            converge(a, b)
            assertTrue(b.items().all { it.status == ItemStatus.NEW })
            assertEquals(3, b.totalReviews())
            clock.advanceMinutes(10)
            b.study()
            converge(a, b)
            assertEquals(a.items(), b.items())
            assertTrue(a.items().all { it.status != ItemStatus.NEW })
        }

    @Test
    fun resettingProgressPropagates() =
        runTest {
            val a = device(1)
            val b = device(2)
            a.import(cardPack())
            a.study()
            converge(a, b)
            clock.advanceMinutes(10)
            a.sdk.resetProgress(PACK)
            converge(a, b)
            assertTrue(b.items().all { it.status == ItemStatus.NEW })
            clock.advanceMinutes(10)
            b.study()
            converge(a, b)
            assertEquals(a.items(), b.items())
            assertTrue(a.items().all { it.facets.all { facet -> facet.reps == 1 } })
        }

    @Test
    fun firstSyncMergesTwoDevicesThatBothHaveLocalData() =
        runTest {
            val a = device(1)
            val b = device(2)
            a.import(cardPack())
            clock.advanceMinutes(1)
            b.import(cardPack())
            a.study(grade = Grade.GOOD)
            clock.advanceMinutes(30)
            b.study(grade = Grade.HARD)
            a.sdk.sync.queueEverything()
            b.sdk.sync.queueEverything()
            converge(a, b)
            assertEquals(6, a.totalReviews())
            assertEquals(6, b.totalReviews())
            assertEquals(a.items(), b.items())
            assertTrue(a.items().all { item -> item.facets.all { it.reps == 2 } })
        }

    @Test
    fun laterContentOfAPackWinsOnBothDevices() =
        runTest {
            val a = device(1)
            val b = device(2)
            a.import(cardPack(title = "Original"))
            converge(a, b)
            clock.advanceMinutes(5)
            a.import(cardPack(title = "From A"))
            clock.advanceMinutes(5)
            b.import(cardPack(title = "From B"))
            converge(a, b)
            assertEquals(
                listOf("From B"),
                a.sdk
                    .observePacks()
                    .first()
                    .map { it.title },
            )
            assertEquals(
                listOf("From B"),
                b.sdk
                    .observePacks()
                    .first()
                    .map { it.title },
            )
        }

    @Test
    fun anItemWhoseTypeChangedDoesNotInheritTheOldTypesReviews() =
        runTest {
            val a = device(1)
            a.import(cardPack())
            a.study()
            clock.advanceMinutes(5)
            a.import(cardPack(typeOverride = mapOf("c1" to "text")))
            assertEquals(ItemStatus.NEW, a.items().first { it.id == "c1" }.status)
            val b = device(2)
            converge(a, b)
            assertEquals(a.items(), b.items())
            assertEquals(ItemStatus.NEW, b.items().first { it.id == "c1" }.status)
            assertTrue(b.items().filter { it.id != "c1" }.all { it.status != ItemStatus.NEW })
        }

    @Test
    fun syncedSettingsFollowTheLatestWriteAndDeviceSettingsStayLocal() =
        runTest {
            val a = device(1)
            val b = device(2)
            a.sdk.setDailyGoal(30)
            a.sdk.setTheme(ThemePreference.DARK)
            a.sdk.setOnboardingSeen(true)
            clock.advanceMinutes(1)
            b.sdk.setDailyGoal(40)
            b.sdk.setDefaultSessionSize(15)
            converge(a, b)
            assertEquals(40, a.sdk.settings().dailyGoal)
            assertEquals(40, b.sdk.settings().dailyGoal)
            assertEquals(15, a.sdk.settings().defaultSessionSize)
            assertEquals(ThemePreference.DARK, a.sdk.settings().theme)
            assertEquals(ThemePreference.SYSTEM, b.sdk.settings().theme)
            assertEquals(false, b.sdk.settings().onboardingSeen)
        }

    @Test
    fun appliedRecordsAreNotSentBackAndChangesDuringASendStayPending() =
        runTest {
            val a = device(1)
            val b = device(2)
            a.import(cardPack())
            a.study()
            push(a, b)
            assertTrue(
                b.sdk.sync
                    .pendingRecords()
                    .isEmpty(),
            )
            assertTrue(
                a.sdk.sync
                    .pendingRecords()
                    .isEmpty(),
            )

            clock.advanceMinutes(1)
            a.sdk.resetProgress(PACK)
            val name = "pack.$PACK"
            assertEquals(listOf(name), a.sdk.sync.pendingRecords())
            val snapshot = assertNotNull(a.sdk.sync.packRecord(name))
            clock.advanceMinutes(1)
            a.sdk.resetProgress(PACK)
            a.sdk.sync.markSent(name, snapshot.version, "fields")
            assertEquals(listOf(name), a.sdk.sync.pendingRecords())
            assertEquals("fields", a.sdk.sync.systemFields(name))
            val newer = assertNotNull(a.sdk.sync.packRecord(name))
            a.sdk.sync.markSent(name, newer.version, null)
            assertTrue(
                a.sdk.sync
                    .pendingRecords()
                    .isEmpty(),
            )
            assertNull(a.sdk.sync.packRecord(name))
        }

    @Test
    fun aConflictingSavedRecordIsMergedAndOnlyDifferencesAreUploadedAgain() =
        runTest {
            val a = device(1)
            val b = device(2)
            a.import(cardPack())
            converge(a, b)
            clock.advanceMinutes(5)
            b.sdk.deletePack(PACK)
            val name = "pack.$PACK"
            val tombstone = assertNotNull(b.sdk.sync.packRecord(name))
            clock.advanceMinutes(5)
            a.import(cardPack(title = "Imported again"))
            val result = a.sdk.sync.apply(listOf(tombstone), emptyList(), emptyList())
            assertEquals(listOf(name), result.needsUpload)
            assertEquals(
                listOf("Imported again"),
                a.sdk
                    .observePacks()
                    .first()
                    .map { it.title },
            )
            val merged = assertNotNull(a.sdk.sync.packRecord(name))
            assertEquals(tombstone.deletedAt, merged.deletedAt)
            val back = b.sdk.sync.apply(listOf(merged), emptyList(), emptyList())
            assertTrue(back.needsUpload.isEmpty())
            assertEquals(
                listOf("Imported again"),
                b.sdk
                    .observePacks()
                    .first()
                    .map { it.title },
            )
            val again = a.sdk.sync.apply(listOf(merged), emptyList(), emptyList())
            assertTrue(again.needsUpload.isEmpty())
        }

    @Test
    fun aLivePackRecordWithoutContentIsSkippedInsteadOfDeletingThePack() =
        runTest {
            val a = device(1)
            a.import(cardPack())
            val name = "pack.$PACK"
            val broken = SyncPackRecord(name, PACK, null, 1L, clock.millis + 1000, 0L, 0L, 0L)
            val result = a.sdk.sync.apply(listOf(broken), emptyList(), emptyList())
            assertTrue(result.notes.isNotEmpty())
            assertEquals(
                1,
                a.sdk
                    .observePacks()
                    .first()
                    .size,
            )
        }

    @Test
    fun discardingARecordClearsItFromTheOutbox() =
        runTest {
            val a = device(1)
            a.import(cardPack())
            val name = "pack.$PACK"
            assertEquals(listOf(name), a.sdk.sync.pendingRecords())
            a.sdk.sync.discard(name)
            assertTrue(
                a.sdk.sync
                    .pendingRecords()
                    .isEmpty(),
            )
        }

    @Test
    fun nothingIsTrackedUntilTrackingIsEnabled() =
        runTest {
            val device = Device(clock, 3, tracking = false)
            device.import(cardPack())
            device.study()
            device.sdk.setDailyGoal(12)
            assertTrue(
                device.sdk.sync
                    .pendingRecords()
                    .isEmpty(),
            )
            device.sdk.sync.queueEverything()
            assertEquals(
                1 + 3 + 1,
                device.sdk.sync
                    .pendingRecords()
                    .size,
            )
        }

    @Test
    fun metaValuesPersistAndSystemFieldsAreKeptPerRecord() =
        runTest {
            val device = device()
            assertNull(device.sdk.sync.meta("engine"))
            device.sdk.sync.setMeta("engine", "state")
            assertEquals("state", device.sdk.sync.meta("engine"))
            device.sdk.sync.setMeta("engine", null)
            assertNull(device.sdk.sync.meta("engine"))
            device.sdk.sync.setSystemFields("pack.x", "abc")
            assertEquals("abc", device.sdk.sync.systemFields("pack.x"))
            assertTrue(
                device.sdk.sync
                    .pendingRecords()
                    .isEmpty(),
            )
        }
}
