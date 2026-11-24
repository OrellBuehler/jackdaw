package ch.orellbuehler.jackdaw.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.orellbuehler.jackdaw.JackdawSdk
import ch.orellbuehler.jackdaw.store.PackGroup
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupsViewModel(
    private val sdk: JackdawSdk,
) : ViewModel() {
    val groups: StateFlow<List<PackGroup>> =
        sdk.observeGroups().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(
        name: String,
        packId: String?,
    ) {
        viewModelScope.launch {
            val id = sdk.createGroup(name)
            if (id != null && packId != null) sdk.setPackInGroup(packId, id, true)
        }
    }

    fun rename(
        groupId: String,
        name: String,
    ) {
        viewModelScope.launch { sdk.renameGroup(groupId, name) }
    }

    fun delete(groupId: String) {
        viewModelScope.launch { sdk.deleteGroup(groupId) }
    }

    fun setMember(
        packId: String,
        groupId: String,
        member: Boolean,
    ) {
        viewModelScope.launch { sdk.setPackInGroup(packId, groupId, member) }
    }
}
