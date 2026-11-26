package ch.orellbuehler.jackdaw

import ch.orellbuehler.jackdaw.samples.DemoLibrary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SdkDemoLibraryTest {
    private val fixture = SdkFixture()
    private val sdk get() = fixture.sdk

    @Test
    fun seedsAStudiedLibraryOnce() =
        runTest {
            assertTrue(sdk.seedDemoLibrary())
            val packs = sdk.observePacks().first()
            assertEquals(DemoLibrary.PACKS.map { DemoLibrary.PREFIX + it.sample }.toSet(), packs.map { it.id }.toSet())
            val stats = sdk.observeStats().first()
            assertTrue(stats.currentStreak >= 12, "streak ${stats.currentStreak}")
            assertTrue(stats.reviewsToday > 0)
            assertTrue(stats.dueToday > 0)
            assertTrue(stats.facetsMastered > 0)
            assertTrue(stats.accuracy > 0.8)
            assertTrue(sdk.settings().onboardingSeen)
            assertTrue(packs.any { it.newCount == it.itemCount })

            assertFalse(sdk.seedDemoLibrary())
            assertEquals(stats.totalReviews, sdk.observeStats().first().totalReviews)
        }
}
