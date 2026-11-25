package ch.orellbuehler.jackdaw.ui.pack

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.orellbuehler.jackdaw.JackdawSdk
import ch.orellbuehler.jackdaw.store.CombinePreview
import ch.orellbuehler.jackdaw.store.ImportResult
import ch.orellbuehler.jackdaw.store.PackDetail
import ch.orellbuehler.jackdaw.store.PackSummary
import ch.orellbuehler.jackdaw.study.SessionConfig
import ch.orellbuehler.jackdaw.study.SessionPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PackDetailState {
    data object Loading : PackDetailState

    data object Missing : PackDetailState

    data class Ready(
        val detail: PackDetail,
        val learn: SessionPreview,
    ) : PackDetailState
}

class PackDetailViewModel(
    private val sdk: JackdawSdk,
    val packId: String,
) : ViewModel() {
    val state: StateFlow<PackDetailState> =
        sdk
            .observePack(packId)
            .mapLatest { detail ->
                if (detail == null) {
                    PackDetailState.Missing
                } else {
                    PackDetailState.Ready(detail, sdk.previewSession(packId, SessionConfig.learn()))
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PackDetailState.Loading)

    val otherPacks: StateFlow<List<PackSummary>> =
        sdk
            .observePacks()
            .map { packs -> packs.filter { it.id != packId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun previewCombine(otherPackId: String): CombinePreview = sdk.previewCombine(packId, otherPackId)

    suspend fun combine(otherPackId: String): ImportResult = sdk.combinePacks(packId, otherPackId)

    fun resetProgress() {
        viewModelScope.launch { sdk.resetProgress(packId) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            sdk.deletePack(packId)
            onDeleted()
        }
    }

    suspend fun exportJson(): String? = sdk.exportPack(packId)

    fun setArchived(archived: Boolean) {
        viewModelScope.launch { sdk.setPackArchived(packId, archived) }
    }

    fun removeLink() {
        viewModelScope.launch { sdk.removePackUrl(packId) }
    }

    suspend fun updateInfo(
        title: String,
        description: String,
    ): ImportResult = sdk.updatePackInfo(packId, title, description)
}
