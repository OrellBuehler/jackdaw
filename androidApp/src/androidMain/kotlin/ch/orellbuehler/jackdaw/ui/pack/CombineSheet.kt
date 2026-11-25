package ch.orellbuehler.jackdaw.ui.pack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.orellbuehler.jackdaw.R
import ch.orellbuehler.jackdaw.store.CombinePreview
import ch.orellbuehler.jackdaw.store.ImportResult
import ch.orellbuehler.jackdaw.ui.addpack.messageRes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CombineSheet(
    viewModel: PackDetailViewModel,
    packTitle: String,
    onDone: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val others by viewModel.otherPacks.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var chosenId by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    val chosen = others.firstOrNull { it.id == chosenId }
    val preview by produceState<CombinePreview?>(null, chosenId) {
        value = null
        value = chosenId?.let { viewModel.previewCombine(it) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.menu_combine), style = MaterialTheme.typography.titleLarge)
            if (chosen == null) {
                if (others.isEmpty()) {
                    Text(
                        stringResource(R.string.combine_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(R.string.combine_pick, packTitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    others.forEach { pack ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    error = null
                                    chosenId = pack.id
                                }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (!pack.icon.isNullOrBlank()) Text(pack.icon!!, style = MaterialTheme.typography.titleLarge)
                            Column(Modifier.weight(1f)) {
                                Text(pack.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    pluralStringResource(R.plurals.item_count, pack.itemCount, pack.itemCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                Text(chosen.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                when (val shown = preview) {
                    null -> Unit
                    is CombinePreview.Invalid ->
                        Text(
                            stringResource(shown.error.code.messageRes()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    is CombinePreview.Valid -> {
                        if (shown.addedCount == 0) {
                            Text(stringResource(R.string.combine_nothing, shown.otherTitle), style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text(
                                pluralStringResource(R.plurals.combine_added, shown.addedCount, shown.addedCount),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (shown.sectionCount > 0) {
                                Text(
                                    pluralStringResource(R.plurals.combine_sections, shown.sectionCount, shown.sectionCount),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            if (shown.duplicateCount > 0) {
                                Text(
                                    pluralStringResource(R.plurals.combine_duplicates, shown.duplicateCount, shown.duplicateCount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                stringResource(R.string.combine_note, shown.otherTitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            error?.let {
                                Text(
                                    stringResource(it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Button(
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        when (val result = viewModel.combine(chosen.id)) {
                                            is ImportResult.Success -> onDone(result.counts.addedItems)
                                            is ImportResult.Failure -> {
                                                error = result.error.code.messageRes()
                                                busy = false
                                            }
                                        }
                                    }
                                },
                            ) { Text(stringResource(R.string.combine_confirm), maxLines = 1) }
                        }
                    }
                }
                TextButton(onClick = { chosenId = null }, enabled = !busy) { Text(stringResource(R.string.action_back)) }
            }
        }
    }
}
