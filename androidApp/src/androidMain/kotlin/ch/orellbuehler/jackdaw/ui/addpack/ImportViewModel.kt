package ch.orellbuehler.jackdaw.ui.addpack

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.orellbuehler.jackdaw.JackdawSdk
import ch.orellbuehler.jackdaw.data.PackTextReader
import ch.orellbuehler.jackdaw.data.ReadOutcome
import ch.orellbuehler.jackdaw.data.TextFetcher
import ch.orellbuehler.jackdaw.format.ImportError
import ch.orellbuehler.jackdaw.format.ItemType
import ch.orellbuehler.jackdaw.store.ImportPreview
import ch.orellbuehler.jackdaw.store.ImportResult
import ch.orellbuehler.jackdaw.store.SamplePack
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ImportProblem {
    data class Sdk(
        val error: ImportError,
    ) : ImportProblem

    data object ReadFailed : ImportProblem

    data object NotUtf8 : ImportProblem

    data object Empty : ImportProblem

    data object TooLarge : ImportProblem

    data object DownloadFailed : ImportProblem
}

enum class CatalogState {
    Idle,
    Loading,
    Loaded,
    Failed,
}

sealed interface ImportUiState {
    data object Idle : ImportUiState

    data object Reading : ImportUiState

    data class Preview(
        val text: String,
        val preview: ImportPreview.Valid,
        val busy: Boolean = false,
    ) : ImportUiState

    data class Failed(
        val problem: ImportProblem,
    ) : ImportUiState
}

sealed interface ImportEvent {
    data class Imported(
        val result: ImportResult.Success,
        val fromSample: Boolean,
    ) : ImportEvent

    data class SampleFailed(
        val problem: ImportProblem,
    ) : ImportEvent
}

class ImportViewModel(
    private val sdk: JackdawSdk,
    private val reader: PackTextReader,
    private val fetcher: TextFetcher,
) : ViewModel() {
    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state

    private val _events = Channel<ImportEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val samples = sdk.samplePacks()

    private val _library = MutableStateFlow(samples)
    val library: StateFlow<List<SamplePack>> = _library

    private val _catalog = MutableStateFlow(CatalogState.Idle)
    val catalog: StateFlow<CatalogState> = _catalog

    private val _adding = MutableStateFlow<Set<String>>(emptySet())
    val adding: StateFlow<Set<String>> = _adding

    val installedRevisions: StateFlow<Map<String, Int>> =
        sdk
            .observePacks()
            .map { packs -> packs.associate { it.id to it.revision } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _promptTypes = MutableStateFlow(ItemType.entries.toSet())
    val promptTypes: StateFlow<Set<ItemType>> = _promptTypes

    private val _promptFigures = MutableStateFlow(false)
    val promptFigures: StateFlow<Boolean> = _promptFigures

    fun togglePromptType(type: ItemType) {
        _promptTypes.update { types ->
            when {
                type !in types -> types + type
                types.size > 1 -> types - type
                else -> types
            }
        }
    }

    fun setPromptFigures(enabled: Boolean) {
        _promptFigures.value = enabled
    }

    fun authoringPrompt(
        types: Set<ItemType>,
        figures: Boolean,
    ): String = sdk.authoringPrompt(types.map { it.code }, figures)

    val installedIds: StateFlow<Set<String>> =
        sdk
            .observePacks()
            .map { packs -> packs.mapTo(HashSet()) { it.id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun openUri(uri: Uri) {
        _state.value = ImportUiState.Reading
        viewModelScope.launch {
            when (val outcome = reader.read(uri)) {
                is ReadOutcome.Text -> preview(outcome.text)
                is ReadOutcome.Failed -> _state.value = ImportUiState.Failed(outcome.problem)
            }
        }
    }

    fun openText(text: String) {
        _state.value = ImportUiState.Reading
        viewModelScope.launch { preview(text) }
    }

    private suspend fun preview(text: String) {
        if (text.isBlank()) {
            _state.value = ImportUiState.Failed(ImportProblem.Empty)
            return
        }
        _state.value =
            when (val preview = sdk.previewImport(text)) {
                is ImportPreview.Valid -> ImportUiState.Preview(text, preview)
                is ImportPreview.Invalid -> ImportUiState.Failed(ImportProblem.Sdk(preview.error))
            }
    }

    fun confirm() {
        val current = _state.value as? ImportUiState.Preview ?: return
        if (current.busy) return
        _state.value = current.copy(busy = true)
        viewModelScope.launch {
            val result =
                if (current.preview.isDowngrade) {
                    sdk.importPackAllowingDowngrade(current.text)
                } else {
                    sdk.importPack(current.text)
                }
            when (result) {
                is ImportResult.Success -> {
                    _state.value = ImportUiState.Idle
                    _events.send(ImportEvent.Imported(result, fromSample = false))
                }
                is ImportResult.Failure -> _state.value = ImportUiState.Failed(ImportProblem.Sdk(result.error))
            }
        }
    }

    fun dismiss() {
        _state.update { if (it is ImportUiState.Preview && it.busy) it else ImportUiState.Idle }
    }

    fun loadCatalog() {
        if (_catalog.value == CatalogState.Loading) return
        _catalog.value = CatalogState.Loading
        viewModelScope.launch {
            val packs = fetcher.fetch(sdk.catalogUrl())?.let { sdk.readCatalog(it) }
            if (packs == null) {
                _catalog.value = CatalogState.Failed
            } else {
                _library.value = packs
                _catalog.value = CatalogState.Loaded
            }
        }
    }

    fun addSample(sampleId: String) {
        if (sampleId in _adding.value) return
        val url = _library.value.firstOrNull { it.id == sampleId }?.downloadUrl
        _adding.update { it + sampleId }
        viewModelScope.launch {
            val result = if (url == null) sdk.importSample(sampleId) else fetcher.fetch(url)?.let { sdk.importPack(it) }
            _adding.update { it - sampleId }
            when (result) {
                null -> _events.send(ImportEvent.SampleFailed(ImportProblem.DownloadFailed))
                is ImportResult.Success -> _events.send(ImportEvent.Imported(result, fromSample = true))
                is ImportResult.Failure -> _events.send(ImportEvent.SampleFailed(ImportProblem.Sdk(result.error)))
            }
        }
    }
}
