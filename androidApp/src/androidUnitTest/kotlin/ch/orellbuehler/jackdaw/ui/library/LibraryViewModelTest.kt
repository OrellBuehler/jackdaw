package ch.orellbuehler.jackdaw.ui.library

import app.cash.turbine.test
import ch.orellbuehler.jackdaw.JackdawSdk
import ch.orellbuehler.jackdaw.MainDispatcherRule
import ch.orellbuehler.jackdaw.packSummary
import ch.orellbuehler.jackdaw.statsOf
import ch.orellbuehler.jackdaw.store.PackGroup
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun picksTheBusiestPackWithDueItems() {
        val packs =
            listOf(
                packSummary("a", due = 2, lastStudiedAt = 5),
                packSummary("b", due = 9, lastStudiedAt = 1),
                packSummary("c", due = 0, new = 30),
            )
        assertEquals("b", pickContinuePack(packs)?.id)
    }

    @Test
    fun breaksTiesByRecentStudy() {
        val packs = listOf(packSummary("a", due = 4, lastStudiedAt = 5), packSummary("b", due = 4, lastStudiedAt = 9))
        assertEquals("b", pickContinuePack(packs)?.id)
    }

    @Test
    fun fallsBackToPacksWithNewItems() {
        val packs = listOf(packSummary("a", new = 3, lastStudiedAt = 1), packSummary("b", new = 8, lastStudiedAt = 4))
        assertEquals("b", pickContinuePack(packs)?.id)
    }

    @Test
    fun hasNoTargetWhenEverythingIsDone() {
        assertNull(pickContinuePack(listOf(packSummary("a"))))
        assertNull(pickContinuePack(emptyList()))
    }

    @Test
    fun combinesPacksAndStats() =
        runTest {
            val sdk = mockk<JackdawSdk>()
            val packs = MutableStateFlow(listOf(packSummary("a", due = 3)))
            every { sdk.observePacks() } returns packs
            every { sdk.observeStats(7) } returns flowOf(statsOf(streak = 4, reviewsToday = 5))
            every { sdk.samplePacks() } returns emptyList()
            every { sdk.observeGroups() } returns flowOf(emptyList())
            val viewModel = LibraryViewModel(sdk)
            viewModel.state.test {
                var state = awaitItem()
                while (!state.loaded) state = awaitItem()
                assertEquals(4, state.stats?.currentStreak)
                assertEquals(3, state.totalDue)
                assertEquals("a", state.continuePack?.id)
                assertFalse(state.packs.isEmpty())
                packs.value = emptyList()
                state = awaitItem()
                assertTrue(state.packs.isEmpty())
                assertNull(state.continuePack)
            }
        }

    @Test
    fun filtersByGroupAndKeepsArchivedPacksApart() {
        val packs =
            listOf(
                packSummary("a", due = 2, groupIds = listOf("g1")),
                packSummary("b", due = 5),
                packSummary("c", due = 9, archived = true, groupIds = listOf("g1")),
            )
        val groups = listOf(PackGroup("g1", "Favourites", listOf("a", "c")))
        val state = LibraryState(loaded = true, packs = packs, groups = groups)
        assertEquals(listOf("a", "b"), state.visible.map { it.id })
        assertEquals(7, state.totalDue)
        assertEquals("b", state.continuePack?.id)
        assertEquals(listOf("a"), state.copy(selected = LibraryFilter.Group("g1")).visible.map { it.id })
        assertEquals(listOf("c"), state.copy(selected = LibraryFilter.Archived).visible.map { it.id })
        assertEquals(LibraryFilter.All, state.copy(selected = LibraryFilter.Group("gone")).filter)
        assertEquals(LibraryFilter.All, state.copy(packs = packs.take(2), selected = LibraryFilter.Archived).filter)
        assertEquals(LibraryFilter.Archived, state.copy(packs = packs.drop(2)).filter)
    }
}
