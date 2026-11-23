package ch.orellbuehler.jackdaw

import app.cash.turbine.test
import ch.orellbuehler.jackdaw.store.ThemePreference
import ch.orellbuehler.jackdaw.study.Grade
import ch.orellbuehler.jackdaw.study.SessionConfig
import ch.orellbuehler.jackdaw.study.SessionMode
import ch.orellbuehler.jackdaw.study.SessionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SdkStatsTest {
    private val fixture = SdkFixture()
    private val sdk get() = fixture.sdk

    private suspend fun studyDay(
        cards: Int = 2,
        packId: String = "test.flip",
    ) {
        val config = SessionConfig.practice().withSize(cards)
        fixture.studyFlipCards(packId, Grade.GOOD, config)
    }

    @Test
    fun emptyDatabaseHasEmptyStats() =
        runTest {
            val stats = sdk.observeStats().first()
            assertEquals(0, stats.currentStreak)
            assertEquals(0, stats.longestStreak)
            assertEquals(0, stats.reviewsToday)
            assertEquals(0, stats.totalReviews)
            assertEquals(0.0, stats.accuracy)
            assertEquals(0, stats.dueToday)
            assertEquals(0, stats.facetsLearned)
            assertFalse(stats.goalReached)
            assertEquals(91, stats.activity.size)
            assertTrue(stats.activity.all { it.reviews == 0 })
            assertEquals("2026-11-20", stats.activity.last().date)
            assertEquals("2026-08-22", stats.activity.first().date)
        }

    @Test
    fun activitySeriesIsDenseAndEndsToday() =
        runTest {
            fixture.importOk(flipPack(cards = 4))
            fixture.studyFlipCards("test.flip", Grade.GOOD, SessionConfig.learn().withNewLimit(3))
            fixture.clock.advanceDays(2)
            fixture.studyFlipCards("test.flip", Grade.AGAIN, SessionConfig.learn().withNewLimit(1))
            val stats = sdk.observeStats(7).first()
            assertEquals(7, stats.activity.size)
            assertEquals(
                listOf(
                    "2026-11-16",
                    "2026-11-17",
                    "2026-11-18",
                    "2026-11-19",
                    "2026-11-20",
                    "2026-11-21",
                    "2026-11-22",
                ),
                stats.activity.map {
                    it.date
                },
            )
            assertEquals(listOf(0, 0, 0, 0, 3, 0, 4), stats.activity.map { it.reviews })
            assertEquals(listOf(0, 0, 0, 0, 3, 0, 0), stats.activity.map { it.correct })
            assertEquals(7, stats.totalReviews)
            assertEquals(3.0 / 7.0, stats.accuracy)
            assertEquals(1, observeDays(1).activity.size)
            assertEquals(730, observeDays(100_000).activity.size)
            assertEquals(1, observeDays(0).activity.size)
        }

    private suspend fun observeDays(days: Int) = sdk.observeStats(days).first()

    @Test
    fun streakCountsConsecutiveDaysEndingTodayOrYesterday() =
        runTest {
            fixture.importOk(flipPack(cards = 2))
            studyDay()
            assertEquals(1, sdk.observeStats().first().currentStreak)
            fixture.clock.advanceDays(1)
            studyDay()
            fixture.clock.advanceDays(1)
            studyDay()
            var stats = sdk.observeStats().first()
            assertEquals(3, stats.currentStreak)
            assertEquals(3, stats.longestStreak)
            fixture.clock.advanceDays(1)
            stats = sdk.observeStats().first()
            assertEquals(3, stats.currentStreak, "yesterday's activity keeps the streak alive")
            assertEquals(0, stats.reviewsToday)
            fixture.clock.advanceDays(1)
            stats = sdk.observeStats().first()
            assertEquals(0, stats.currentStreak, "a missed day breaks the streak")
            assertEquals(3, stats.longestStreak)
            studyDay()
            stats = sdk.observeStats().first()
            assertEquals(1, stats.currentStreak)
            assertEquals(3, stats.longestStreak)
        }

    @Test
    fun longestStreakKeepsTheBestRun() =
        runTest {
            fixture.importOk(flipPack(cards = 2))
            for (gap in listOf(0, 1, 5, 1, 1, 1, 3, 1)) {
                fixture.clock.advanceDays(gap.toLong())
                studyDay()
            }
            val stats = sdk.observeStats().first()
            assertEquals(2, stats.currentStreak)
            assertEquals(4, stats.longestStreak)
        }

    @Test
    fun daysFollowTheDeviceTimeZone() =
        runTest {
            val zurich = SdkFixture(TimeZone.of("Europe/Zurich"), start = "2026-11-20T22:30:00Z")
            zurich.importOk(flipPack(cards = 2))
            zurich.studyFlipCards("test.flip", Grade.GOOD, SessionConfig.practice())
            zurich.clock.advanceMinutes(60)
            zurich.studyFlipCards("test.flip", Grade.GOOD, SessionConfig.practice())
            val zurichStats = zurich.sdk.observeStats(3).first()
            assertEquals(listOf("2026-11-20", "2026-11-21"), zurichStats.activity.takeLast(2).map { it.date })
            assertEquals(listOf(2, 2), zurichStats.activity.takeLast(2).map { it.reviews })
            assertEquals(2, zurichStats.currentStreak)
            assertEquals(2, zurichStats.reviewsToday)

            val utc = SdkFixture(TimeZone.UTC, start = "2026-11-20T22:30:00Z")
            utc.importOk(flipPack(cards = 2))
            utc.studyFlipCards("test.flip", Grade.GOOD, SessionConfig.practice())
            utc.clock.advanceMinutes(60)
            utc.studyFlipCards("test.flip", Grade.GOOD, SessionConfig.practice())
            val utcStats = utc.sdk.observeStats(3).first()
            assertEquals(1, utcStats.currentStreak)
            assertEquals(4, utcStats.reviewsToday)
        }

    @Test
    fun dueTodayCountsFacetsAcrossPacksUntilTheEndOfTheDay() =
        runTest {
            fixture.importOk(flipPack(id = "a", title = "A", cards = 3))
            fixture.importOk(flipPack(id = "b", title = "B", cards = 2))
            fixture.studyFlipCards("a")
            fixture.studyFlipCards("b")
            assertEquals(0, sdk.observeStats().first().dueToday)
            assertEquals(5, sdk.observeStats().first().facetsLearned)
            fixture.clock.advanceDays(1)
            sdk.refresh()
            val stats = sdk.observeStats().first()
            assertEquals(5, stats.dueToday)
            assertEquals(0, stats.facetsMastered)
            assertEquals(listOf(3, 2), sdk.observePacks().first().map { it.dueCount })
        }

    @Test
    fun reviewsAreDueFromTheStartOfTheirDay() =
        runTest {
            fixture.importOk(flipPack(cards = 1))
            fixture.studyFlipCards("test.flip")
            fixture.clock.advanceDays(1)
            fixture.clock.millis -= 13 * 3_600_000L
            assertEquals(
                0,
                sdk
                    .observePacks()
                    .first()
                    .single()
                    .dueCount,
            )
            fixture.clock.advanceMinutes(60)
            assertEquals(
                1,
                sdk
                    .observePacks()
                    .first()
                    .single()
                    .dueCount,
                "due as soon as the calendar day of the due date starts",
            )
            val session = sdk.startSession("test.flip", SessionConfig.learn())
            assertIs<SessionState.Question>(session.state.value)
        }

    @Test
    fun dailyGoalIsReportedInStats() =
        runTest {
            fixture.importOk(flipPack(cards = 4))
            sdk.setDailyGoal(3)
            fixture.studyFlipCards("test.flip", Grade.GOOD, SessionConfig.learn().withNewLimit(2))
            var stats = sdk.observeStats().first()
            assertEquals(3, stats.dailyGoal)
            assertFalse(stats.goalReached)
            fixture.studyFlipCards("test.flip", Grade.GOOD, SessionConfig.learn().withNewLimit(1))
            stats = sdk.observeStats().first()
            assertTrue(stats.goalReached)
            assertEquals(3, stats.reviewsToday)
            assertEquals(3, stats.correctToday)
        }

    @Test
    fun practiceSessionsCountForStatsButNotForScheduling() =
        runTest {
            fixture.importOk(flipPack(cards = 3))
            val summary = fixture.studyFlipCards("test.flip", Grade.GOOD, SessionConfig.practice())
            assertEquals(SessionMode.PRACTICE, summary.mode)
            assertEquals(3, summary.answered)
            val pack = sdk.observePacks().first().single()
            assertEquals(3, pack.newCount)
            assertEquals(0.0, pack.progress)
            assertTrue(pack.lastStudiedAt > 0)
            val stats = sdk.observeStats().first()
            assertEquals(3, stats.reviewsToday)
            assertEquals(1, stats.currentStreak)
            assertEquals(0, stats.facetsLearned)
        }

    @Test
    fun practiceCanRescheduleOnRequest() =
        runTest {
            fixture.importOk(flipPack(cards = 3))
            fixture.studyFlipCards("test.flip", Grade.GOOD, SessionConfig.practice().withReschedule(true))
            assertEquals(
                0,
                sdk
                    .observePacks()
                    .first()
                    .single()
                    .newCount,
            )
            assertEquals(3, sdk.observeStats().first().facetsLearned)
        }

    @Test
    fun accuracyUsesFirstAttemptsOnly() =
        runTest {
            fixture.importOk(flipPack(cards = 2))
            fixture.studyFlipCards("test.flip", Grade.AGAIN)
            val stats = sdk.observeStats().first()
            assertEquals(2, stats.totalReviews)
            assertEquals(0.0, stats.accuracy)
        }

    @Test
    fun statsFlowUpdatesWhileStudying() =
        runTest {
            fixture.importOk(flipPack(cards = 2))
            sdk.observeStats().test {
                assertEquals(0, awaitItem().reviewsToday)
                val session = sdk.startSession("test.flip", SessionConfig.learn())
                session.reveal()
                session.selfGrade(Grade.GOOD)
                assertEquals(1, expectMostRecentItem().reviewsToday)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun settingsHaveDefaultsAndPersist() =
        runTest {
            val defaults = sdk.settings()
            assertEquals(20, defaults.dailyGoal)
            assertEquals(20, defaults.defaultSessionSize)
            assertEquals(ThemePreference.SYSTEM, defaults.theme)
            assertFalse(defaults.onboardingSeen)
            assertTrue(defaults.typoTolerance)

            sdk.observeSettings().test {
                assertEquals(defaults, awaitItem())
                sdk.setTypoTolerance(false)
                assertFalse(awaitItem().typoTolerance)
                sdk.setTheme(ThemePreference.DARK)
                assertEquals(ThemePreference.DARK, awaitItem().theme)
                sdk.setOnboardingSeen(true)
                assertTrue(awaitItem().onboardingSeen)
                sdk.setDailyGoal(50)
                assertEquals(50, awaitItem().dailyGoal)
                sdk.setDefaultSessionSize(12)
                assertEquals(12, awaitItem().defaultSessionSize)
                cancelAndIgnoreRemainingEvents()
            }
            val restarted = fixture.restart().settings()
            assertEquals(50, restarted.dailyGoal)
            assertEquals(12, restarted.defaultSessionSize)
            assertEquals(ThemePreference.DARK, restarted.theme)
            assertTrue(restarted.onboardingSeen)
        }

    @Test
    fun settingsAreClamped() =
        runTest {
            sdk.setDailyGoal(0)
            sdk.setDefaultSessionSize(100_000)
            assertEquals(1, sdk.settings().dailyGoal)
            assertEquals(500, sdk.settings().defaultSessionSize)
            sdk.setDailyGoal(-5)
            assertEquals(1, sdk.settings().dailyGoal)
            sdk.setDailyGoal(5000)
            assertEquals(1000, sdk.settings().dailyGoal)
        }

    @Test
    fun defaultSessionSizeLimitsLearningSessions() =
        runTest {
            fixture.importOk(flipPack(cards = 30))
            sdk.setDefaultSessionSize(7)
            assertEquals(7, sdk.previewSession("test.flip", SessionConfig.learn()).totalCount)
            assertEquals(7, fixture.studyFlipCards("test.flip").total)
            assertEquals(12, sdk.previewSession("test.flip", SessionConfig.learn().withSize(12)).totalCount)
        }
}
