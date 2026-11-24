package ch.orellbuehler.jackdaw.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.orellbuehler.jackdaw.JackdawSdk
import ch.orellbuehler.jackdaw.store.PackGroup
import ch.orellbuehler.jackdaw.store.PackSummary
import ch.orellbuehler.jackdaw.store.SamplePack
import ch.orellbuehler.jackdaw.store.Stats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

sealed interface LibraryFilter {
    data object All : LibraryFilter

    data object Archived : LibraryFilter

    data class Group(
        val id: String,
    ) : LibraryFilter
}

data class LibraryState(
    val loaded: Boolean = false,
    val packs: List<PackSummary> = emptyList(),
    val stats: Stats? = null,
    val samples: List<SamplePack> = emptyList(),
    val groups: List<PackGroup> = emptyList(),
    val selected: LibraryFilter = LibraryFilter.All,
) {
    val active: List<PackSummary> get() = packs.filter { !it.archived }
    val archived: List<PackSummary> get() = packs.filter { it.archived }
    val filter: LibraryFilter
        get() =
            when (selected) {
                is LibraryFilter.Group -> if (groups.any { it.id == selected.id }) selected else LibraryFilter.All
                LibraryFilter.Archived -> if (archived.isEmpty()) LibraryFilter.All else selected
                LibraryFilter.All -> if (active.isEmpty() && archived.isNotEmpty()) LibraryFilter.Archived else selected
            }
    val visible: List<PackSummary>
        get() =
            when (val current = filter) {
                LibraryFilter.All -> active
                LibraryFilter.Archived -> archived
                is LibraryFilter.Group -> active.filter { current.id in it.groupIds }
            }
    val continuePack: PackSummary? get() = pickContinuePack(active)
    val totalDue: Int get() = active.sumOf { it.dueCount }
}

fun pickContinuePack(packs: List<PackSummary>): PackSummary? {
    val recent = compareByDescending<PackSummary> { it.lastStudiedAt }
    val due = packs.filter { it.dueCount > 0 }.sortedWith(compareByDescending<PackSummary> { it.dueCount }.then(recent))
    if (due.isNotEmpty()) return due.first()
    return packs.filter { it.newCount > 0 }.sortedWith(recent).firstOrNull()
}

class LibraryViewModel(
    private val sdk: JackdawSdk,
) : ViewModel() {
    private val samples by lazy { sdk.samplePacks() }

    private val selected = MutableStateFlow<LibraryFilter>(LibraryFilter.All)

    val state: StateFlow<LibraryState> =
        combine(sdk.observePacks(), sdk.observeStats(7), sdk.observeGroups(), selected) { packs, stats, groups, filter ->
            LibraryState(loaded = true, packs = packs, stats = stats, samples = samples, groups = groups, selected = filter)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    fun select(filter: LibraryFilter) {
        selected.value = filter
    }
}
