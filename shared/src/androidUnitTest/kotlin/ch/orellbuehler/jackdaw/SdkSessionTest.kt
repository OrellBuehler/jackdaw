package ch.orellbuehler.jackdaw

import ch.orellbuehler.jackdaw.samples.GENERATED_SAMPLE_PACKS
import ch.orellbuehler.jackdaw.store.ItemStatus
import ch.orellbuehler.jackdaw.study.ChoiceExercise
import ch.orellbuehler.jackdaw.study.ClozeExercise
import ch.orellbuehler.jackdaw.study.Facet
import ch.orellbuehler.jackdaw.study.FlipExercise
import ch.orellbuehler.jackdaw.study.Grade
import ch.orellbuehler.jackdaw.study.OpenExercise
import ch.orellbuehler.jackdaw.study.OrderExercise
import ch.orellbuehler.jackdaw.study.PracticeFilter
import ch.orellbuehler.jackdaw.study.SessionConfig
import ch.orellbuehler.jackdaw.study.SessionState
import ch.orellbuehler.jackdaw.study.TrueFalseExercise
import ch.orellbuehler.jackdaw.study.TypeExercise
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SdkSessionTest {
    private val fixture = SdkFixture()
    private val sdk get() = fixture.sdk
    private val prefix = "com.github.orellbuehler.jackdaw.examples."

    private fun sample(name: String) = GENERATED_SAMPLE_PACKS.first { it.first == "$name.jackdaw" }.second

    @Test
    fun aSessionWithEveryExerciseKindPersistsProgress() =
        runTest {
            fixture.importOk(sample("format-tour"))
            val id = prefix + "format-tour"
            val learner = ScriptedLearner()
            val session = sdk.startSession(id, SessionConfig.learn().withNewLimit(13).withSize(13))
            val summary = learner.run(session)
            assertEquals(13, summary.total)
            assertEquals(13, summary.answered)
            assertTrue(summary.wrong > 0 && summary.retries > 0)
            assertTrue(summary.newFacets == 13)
            val kinds = learner.asked.map { it::class }.toSet()
            assertEquals(
                setOf(
                    FlipExercise::class,
                    ChoiceExercise::class,
                    TypeExercise::class,
                    ClozeExercise::class,
                    TrueFalseExercise::class,
                    OrderExercise::class,
                    OpenExercise::class,
                ),
                kinds,
            )
            val items = sdk.items(id)
            assertTrue(items.none { it.status == ItemStatus.NEW })
            assertTrue(items.any { it.status == ItemStatus.LEARNING })
            assertEquals(summary.wrong, items.count { it.facets.size == 1 && it.status == ItemStatus.LEARNING })
            assertEquals(13, sdk.observeStats().first().reviewsToday)
        }

    @Test
    fun abandonedSessionsKeepEveryAnswerThatWasGiven() =
        runTest {
            fixture.importOk(flipPack(cards = 5))
            val session = sdk.startSession("test.flip", SessionConfig.learn())
            repeat(2) {
                session.reveal()
                session.selfGrade(Grade.GOOD)
            }
            val pack = sdk.observePacks().first().single()
            assertEquals(3, pack.newCount)
            assertEquals(2, pack.reviewCount)
            assertEquals(2, sdk.observeStats().first().reviewsToday)
            assertEquals(3, fixture.studyFlipCards("test.flip").total)
        }

    @Test
    fun learnSessionsServeDueItemsBeforeNewOnes() =
        runTest {
            fixture.importOk(flipPack(cards = 6))
            fixture.studyFlipCards("test.flip", Grade.GOOD, SessionConfig.learn().withNewLimit(2))
            fixture.clock.advanceDays(1)
            val preview = sdk.previewSession("test.flip", SessionConfig.learn().withNewLimit(2))
            assertEquals(2, preview.dueCount)
            assertEquals(2, preview.newCount)
            assertEquals(4, preview.totalCount)
            val learner = ScriptedLearner()
            learner.run(sdk.startSession("test.flip", SessionConfig.learn().withNewLimit(2)))
            assertEquals(listOf("c1", "c2", "c3", "c4"), learner.asked.map { it.itemId })
            val items = sdk.items("test.flip")
            assertEquals(2, items[0].facets.single().intervalDays)
            assertEquals(1, items[2].facets.single().intervalDays)
        }

    @Test
    fun missedCardsReturnQuicklyAndLapsedCardsRelearn() =
        runTest {
            fixture.importOk(flipPack(cards = 1))
            fixture.studyFlipCards("test.flip")
            fixture.clock.advanceDays(1)
            fixture.studyFlipCards("test.flip", Grade.AGAIN)
            val facet =
                sdk
                    .items("test.flip")
                    .single()
                    .facets
                    .single()
            assertEquals(ItemStatus.LEARNING, facet.status)
            assertEquals(1, facet.lapses)
            assertEquals(2, facet.reps)
            assertEquals(fixture.clock.millis + 10 * 60_000L, facet.dueAt)
            assertEquals(1, sdk.previewSession("test.flip", SessionConfig.learn()).dueCount)
            fixture.clock.advanceMinutes(11)
            fixture.studyFlipCards("test.flip")
            assertEquals(ItemStatus.REVIEW, sdk.items("test.flip").single().status)
        }

    @Test
    fun aMissingPackGivesAnEmptySession() =
        runTest {
            val session = sdk.startSession("nope", SessionConfig.learn())
            assertIs<SessionState.Finished>(session.state.value)
            assertEquals(0, sdk.previewSession("nope", SessionConfig.practice()).totalCount)
        }

    @Test
    fun practiceFiltersAndSectionsRestrictTheSession() =
        runTest {
            fixture.importOk(sample("world-history-20th-century"))
            val id = prefix + "world-history-20th-century"

            suspend fun total(config: SessionConfig) = sdk.previewSession(id, config.withSize(100)).totalCount
            assertEquals(31, total(SessionConfig.practice()))
            assertEquals(19, total(SessionConfig.practice().withFilter(PracticeFilter.QUIZ)))
            assertEquals(12, total(SessionConfig.practice().withFilter(PracticeFilter.TYPING)))
            assertEquals(0, total(SessionConfig.practice().withFilter(PracticeFilter.FLASHCARDS)))
            val inSection = sdk.items(id).count { it.sectionId == "ww1" }
            assertTrue(inSection in 1..30)
            assertEquals(inSection, total(SessionConfig.practice().withSection("ww1")))
            assertEquals(5, sdk.previewSession(id, SessionConfig.practice().withSize(5)).totalCount)
        }

    @Test
    fun reverseFacetsAreIntroducedOnceTheForwardFacetIsLearned() =
        runTest {
            fixture.importOk(sample("spanish-basics").replace("[\"flip\", \"choice\", \"type\"]", "[\"flip\"]"))
            val id = prefix + "spanish-basics"
            val day1 = ScriptedLearner()
            day1.run(sdk.startSession(id, SessionConfig.learn()))
            assertEquals(
                8,
                day1.asked
                    .map { it.itemId }
                    .toSet()
                    .size,
            )
            assertTrue(day1.asked.all { it.facet == Facet.FORWARD })

            fixture.clock.advanceDays(1)
            val day2 = ScriptedLearner()
            day2.run(sdk.startSession(id, SessionConfig.learn()))
            assertEquals(
                16,
                day2.asked
                    .map { it.itemId }
                    .toSet()
                    .size,
            )
            assertTrue(day2.asked.all { it.facet == Facet.FORWARD })

            fixture.clock.advanceDays(1)
            val day3 = ScriptedLearner()
            day3.run(sdk.startSession(id, SessionConfig.learn()))
            val reverse =
                day3.asked
                    .filter { it.facet == Facet.REVERSE }
                    .map { it.itemId }
                    .toSet()
            assertEquals(day1.asked.map { it.itemId }.toSet(), reverse)
            val pack = sdk.observePacks().first().single()
            assertEquals(82 - 16 - 8, pack.newCount)
        }
}
