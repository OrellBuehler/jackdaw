package ch.orellbuehler.jackdaw.ui.pack

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.orellbuehler.jackdaw.JackdawSdk
import ch.orellbuehler.jackdaw.store.ImportResult
import ch.orellbuehler.jackdaw.store.ItemStatus
import ch.orellbuehler.jackdaw.store.ItemSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ItemsState(
    val loaded: Boolean = false,
    val all: List<ItemSummary> = emptyList(),
    val query: String = "",
    val status: ItemStatus? = null,
    val sectionTitles: Map<String, String> = emptyMap(),
) {
    val visible: List<ItemSummary> get() = filterItems(all, query, status)
}

fun filterItems(
    items: List<ItemSummary>,
    query: String,
    status: ItemStatus?,
): List<ItemSummary> {
    val needle = query.trim()
    return items.filter { item ->
        (status == null || item.status == status) &&
            (needle.isEmpty() || item.title.contains(needle, ignoreCase = true) || item.subtitle.contains(needle, ignoreCase = true))
    }
}

class ItemsViewModel(
    private val sdk: JackdawSdk,
    private val packId: String,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val status = MutableStateFlow<ItemStatus?>(null)

    val state: StateFlow<ItemsState> =
        combine(sdk.observeItems(packId), sdk.observePack(packId), query, status) { items, detail, q, s ->
            ItemsState(
                loaded = true,
                all = items,
                query = q,
                status = s,
                sectionTitles = detail?.sections?.associate { it.id to it.title }.orEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemsState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setStatus(value: ItemStatus?) {
        status.value = value
    }

    suspend fun source(itemId: String?): String? = if (itemId == null) sdk.itemTemplate() else sdk.itemSource(packId, itemId)

    suspend fun save(
        itemId: String?,
        json: String,
    ): ImportResult = sdk.saveItem(packId, itemId, json)

    suspend fun delete(itemId: String): ImportResult = sdk.deleteItem(packId, itemId)
}
