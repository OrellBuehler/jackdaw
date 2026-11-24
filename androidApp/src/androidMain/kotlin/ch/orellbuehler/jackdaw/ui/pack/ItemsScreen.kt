package ch.orellbuehler.jackdaw.ui.pack

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.orellbuehler.jackdaw.R
import ch.orellbuehler.jackdaw.store.ItemStatus
import ch.orellbuehler.jackdaw.store.ItemSummary
import ch.orellbuehler.jackdaw.study.Facet
import ch.orellbuehler.jackdaw.ui.addpack.labelRes
import ch.orellbuehler.jackdaw.ui.theme.jackdaw

@Composable
fun statusColor(status: ItemStatus): Color =
    when (status) {
        ItemStatus.NEW -> MaterialTheme.colorScheme.outline
        ItemStatus.LEARNING -> MaterialTheme.colorScheme.tertiary
        ItemStatus.REVIEW -> MaterialTheme.colorScheme.primary
        ItemStatus.MASTERED -> MaterialTheme.jackdaw.success
    }

fun statusLabelRes(status: ItemStatus): Int =
    when (status) {
        ItemStatus.NEW -> R.string.status_new
        ItemStatus.LEARNING -> R.string.status_learning
        ItemStatus.REVIEW -> R.string.status_review
        ItemStatus.MASTERED -> R.string.status_mastered
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ItemsScreen(
    viewModel: ItemsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val ungrouped = stringResource(R.string.section_ungrouped)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.items_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.items_add)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.items_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.setQuery("") },
                        ) { Icon(Icons.Rounded.Clear, stringResource(R.string.action_clear)) }
                    }
                },
                shape = CircleShape,
                colors =
                    TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.status == null,
                        onClick = { viewModel.setStatus(null) },
                        label = { Text(stringResource(R.string.items_filter_all)) },
                    )
                }
                items(ItemStatus.entries) { status ->
                    FilterChip(
                        selected = state.status == status,
                        onClick = { viewModel.setStatus(if (state.status == status) null else status) },
                        label = { Text(stringResource(statusLabelRes(status))) },
                    )
                }
            }
            val visible = state.visible
            if (state.loaded && visible.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                    Text(
                        stringResource(R.string.items_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val grouped = visible.groupBy { it.sectionId }
                val showHeaders = state.sectionTitles.size > 1 || state.sectionTitles.keys.any { it.isNotEmpty() }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                ) {
                    grouped.forEach { (sectionId, items) ->
                        if (showHeaders) {
                            stickyHeader(key = "section-$sectionId") {
                                Text(
                                    state.sectionTitles[sectionId]?.takeIf { it.isNotEmpty() } ?: ungrouped,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.background)
                                            .padding(horizontal = 20.dp, vertical = 8.dp),
                                )
                            }
                        }
                        items(items, key = { it.id }) { item ->
                            ItemRow(item, onClick = { selected = item.id })
                        }
                    }
                }
            }
        }
    }
    val chosen = state.all.firstOrNull { it.id == selected }
    if (chosen != null) {
        ItemSheet(
            chosen,
            onEdit = {
                editing = chosen.id
                selected = null
            },
            onDismiss = { selected = null },
        )
    }
    editing?.let { id -> ItemEditor(viewModel, id, onDismiss = { editing = null }) }
    if (adding) ItemEditor(viewModel, null, onDismiss = { adding = false })
}

@Composable
private fun ItemRow(
    item: ItemSummary,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor(item.status)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.subtitle.isNotBlank()) {
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(item.type.labelRes()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.isDue) {
                Text(
                    stringResource(R.string.item_due_now),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemSheet(
    item: ItemSummary,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = scheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(item.type.labelRes()), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            Text(item.title, style = MaterialTheme.typography.titleLarge)
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            item.facets.forEach { facet ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(scheme.surfaceContainerHigh)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (item.facets.size > 1) {
                        Text(
                            stringResource(if (facet.facet == Facet.REVERSE) R.string.facet_reverse else R.string.facet_forward),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor(facet.status)))
                        Text(stringResource(statusLabelRes(facet.status)), style = MaterialTheme.typography.bodyMedium)
                    }
                    InfoRow(stringResource(R.string.facet_next), nextReviewText(facet.dueAt, facet.status))
                    if (facet.intervalDays > 0) {
                        InfoRow(
                            stringResource(R.string.facet_interval),
                            pluralStringResource(R.plurals.days_count, facet.intervalDays, facet.intervalDays),
                        )
                    }
                    InfoRow(stringResource(R.string.facet_reps), facet.reps.toString())
                    InfoRow(stringResource(R.string.facet_lapses), facet.lapses.toString())
                }
            }
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp))
                Text(stringResource(R.string.item_edit_title), maxLines = 1, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun nextReviewText(
    dueAt: Long,
    status: ItemStatus,
): String {
    if (status == ItemStatus.NEW || dueAt <= 0L) return stringResource(R.string.facet_not_scheduled)
    val now = System.currentTimeMillis()
    if (dueAt <= now) return stringResource(R.string.item_due_now)
    return DateUtils.getRelativeTimeSpanString(dueAt, now, DateUtils.DAY_IN_MILLIS).toString()
}
