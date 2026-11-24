package ch.orellbuehler.jackdaw.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.orellbuehler.jackdaw.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsSheet(
    viewModel: GroupsViewModel,
    packId: String?,
    onDismiss: () -> Unit,
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    val add = {
        if (name.isNotBlank()) {
            viewModel.create(name, packId)
            name = ""
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.library_groups), style = MaterialTheme.typography.titleLarge)
            if (groups.isEmpty()) {
                Text(
                    stringResource(R.string.groups_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            groups.forEach { group ->
                val member = packId != null && packId in group.packIds
                val row =
                    if (packId == null) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.fillMaxWidth().toggleable(value = member, role = Role.Checkbox) {
                            viewModel.setMember(packId, group.id, it)
                        }
                    }
                Row(row, verticalAlignment = Alignment.CenterVertically) {
                    if (packId != null) Checkbox(checked = member, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
                    Text(
                        group.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { renaming = group.id }) {
                        Icon(Icons.Rounded.Edit, stringResource(R.string.groups_rename))
                    }
                    IconButton(onClick = { viewModel.delete(group.id) }) {
                        Icon(Icons.Rounded.Delete, stringResource(R.string.groups_delete))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.groups_new), maxLines = 1) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    shape = MaterialTheme.shapes.large,
                )
                Button(onClick = add, enabled = name.isNotBlank()) {
                    Text(stringResource(R.string.action_add), maxLines = 1)
                }
            }
            if (groups.isNotEmpty()) {
                Text(
                    stringResource(R.string.groups_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    val target = groups.firstOrNull { it.id == renaming }
    if (target != null) {
        var newName by rememberSaveable(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.groups_rename)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.rename(target.id, newName)
                        renaming = null
                    },
                    enabled = newName.isNotBlank(),
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
