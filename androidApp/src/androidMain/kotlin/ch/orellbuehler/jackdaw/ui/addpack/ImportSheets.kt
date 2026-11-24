package ch.orellbuehler.jackdaw.ui.addpack

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.orellbuehler.jackdaw.R
import ch.orellbuehler.jackdaw.format.ImportWarning
import ch.orellbuehler.jackdaw.ui.components.PackIconTile
import ch.orellbuehler.jackdaw.ui.components.Pill
import ch.orellbuehler.jackdaw.ui.theme.isDark
import ch.orellbuehler.jackdaw.ui.theme.jackdaw
import ch.orellbuehler.jackdaw.ui.theme.packPalette

@Composable
fun ImportSheetHost(viewModel: ImportViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    LaunchedEffect(state is ImportUiState.Idle) {
        if (state !is ImportUiState.Idle) {
            keyboard?.hide()
            focus.clearFocus()
        }
    }
    when (val current = state) {
        ImportUiState.Idle -> Unit
        ImportUiState.Reading ->
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                icon = { CircularProgressIndicator() },
                title = { Text(stringResource(R.string.import_reading)) },
            )
        is ImportUiState.Preview -> PreviewSheet(current, onConfirm = viewModel::confirm, onDismiss = viewModel::dismiss)
        is ImportUiState.Failed -> ErrorSheet(current.problem, onDismiss = viewModel::dismiss)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PreviewSheet(
    state: ImportUiState.Preview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val preview = state.preview
    val scheme = MaterialTheme.colorScheme
    val dark = MaterialTheme.isDark
    val palette = remember(preview.color, scheme, dark) { packPalette(preview.color, scheme, dark) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = scheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PackIconTile(preview.icon, palette, size = 60.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(if (preview.isUpdate) R.string.import_preview_update else R.string.import_preview_new),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary,
                    )
                    Text(preview.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            preview.descriptionText?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(
                    pluralStringResource(R.plurals.item_count, preview.itemCount, preview.itemCount),
                    palette.container,
                    scheme.onSurface,
                )
                if (preview.sectionCount > 0) {
                    Pill(
                        pluralStringResource(R.plurals.section_count, preview.sectionCount, preview.sectionCount),
                        scheme.surfaceContainerHigh,
                        scheme.onSurface,
                    )
                }
                preview.language?.let { Pill(it, scheme.surfaceContainerHigh, scheme.onSurface) }
                preview.typeCounts.forEach {
                    Pill(
                        stringResource(R.string.type_count, it.count, stringResource(it.type.labelRes())),
                        scheme.surfaceContainerHigh,
                        scheme.onSurface,
                    )
                }
            }
            if (preview.isDowngrade) {
                Notice(
                    icon = { Icon(Icons.Rounded.Warning, null, tint = scheme.onTertiaryContainer) },
                    container = scheme.tertiaryContainer,
                    content = scheme.onTertiaryContainer,
                    title = stringResource(R.string.import_downgrade_title),
                    body = stringResource(R.string.import_downgrade_body, preview.installedRevision, preview.revision),
                )
            } else if (preview.isUpdate) {
                Notice(
                    icon = { Icon(Icons.Rounded.Update, null, tint = scheme.onPrimaryContainer) },
                    container = scheme.primaryContainer,
                    content = scheme.onPrimaryContainer,
                    title = stringResource(R.string.import_update_title, preview.installedTitle ?: preview.title),
                    body =
                        stringResource(
                            R.string.import_update_counts,
                            preview.counts.addedItems,
                            preview.counts.keptItems,
                            preview.counts.resetItems,
                            preview.counts.removedItems,
                        ) + "\n" + stringResource(R.string.import_update_progress_kept),
                )
            }
            if (preview.overwritesEdits) {
                Notice(
                    icon = { Icon(Icons.Rounded.Warning, null, tint = scheme.onErrorContainer) },
                    container = scheme.errorContainer,
                    content = scheme.onErrorContainer,
                    title = stringResource(R.string.import_overwrite_title),
                    body = stringResource(R.string.import_overwrite_body),
                )
            }
            if (preview.skippedCount > 0) {
                Notice(
                    icon = { Icon(Icons.Rounded.Info, null, tint = scheme.onSurfaceVariant) },
                    container = scheme.surfaceContainerHigh,
                    content = scheme.onSurface,
                    title = null,
                    body = pluralStringResource(R.plurals.import_skipped, preview.skippedCount, preview.skippedCount),
                )
            }
            if (preview.warnings.isNotEmpty()) WarningList(preview.warnings)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, enabled = !state.busy) {
                    Text(stringResource(R.string.action_cancel), maxLines = 1)
                }
                Button(onClick = onConfirm, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                    if (state.busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = scheme.onPrimary)
                    } else {
                        Text(
                            stringResource(
                                when {
                                    preview.isDowngrade -> R.string.import_confirm_downgrade
                                    preview.isUpdate -> R.string.import_confirm_update
                                    else -> R.string.import_confirm_add
                                },
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Notice(
    icon: @Composable () -> Unit,
    container: Color,
    content: Color,
    title: String?,
    body: String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, color = content)
            Text(body, style = MaterialTheme.typography.bodySmall, color = content)
        }
    }
}

@Composable
private fun WarningList(warnings: List<ImportWarning>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(scheme.surfaceContainerHigh)
            .animateContentSize(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable { expanded = !expanded }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Warning, null, tint = scheme.onSurfaceVariant)
            Text(
                pluralStringResource(R.plurals.import_warnings, warnings.size, warnings.size),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = stringResource(if (expanded) R.string.action_hide else R.string.action_show),
            )
        }
        if (expanded) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                warnings.take(12).forEach { warning ->
                    Column {
                        Text(stringResource(warning.code.titleRes()), style = MaterialTheme.typography.labelLarge)
                        Text(warning.message, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
                if (warnings.size > 12) {
                    Text(
                        stringResource(R.string.import_warnings_more, warnings.size - 12),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ErrorSheet(
    problem: ImportProblem,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val message = stringResource(problem.messageRes())
    val detail = problem.technicalDetail()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = scheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(scheme.errorContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = scheme.onErrorContainer)
            }
            Text(stringResource(R.string.import_error_title), style = MaterialTheme.typography.titleLarge)
            Text(message, style = MaterialTheme.typography.bodyLarge)
            if (detail != null && detail != message) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (detail != null) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Jackdaw", detail))
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.error_copy_details), maxLines = 1)
                    }
                }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_ok), maxLines = 1)
                }
            }
        }
    }
}
