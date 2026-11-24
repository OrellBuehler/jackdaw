package ch.orellbuehler.jackdaw.ui.library

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.orellbuehler.jackdaw.R
import ch.orellbuehler.jackdaw.store.PackSummary
import ch.orellbuehler.jackdaw.store.Stats
import ch.orellbuehler.jackdaw.ui.addpack.ImportViewModel
import ch.orellbuehler.jackdaw.ui.addpack.SampleRow
import ch.orellbuehler.jackdaw.ui.components.LocalBottomBarInset
import ch.orellbuehler.jackdaw.ui.components.Mascot
import ch.orellbuehler.jackdaw.ui.components.MascotMood
import ch.orellbuehler.jackdaw.ui.components.PackIconTile
import ch.orellbuehler.jackdaw.ui.components.Pill
import ch.orellbuehler.jackdaw.ui.components.ProgressRing
import ch.orellbuehler.jackdaw.ui.components.SlimProgressBar
import ch.orellbuehler.jackdaw.ui.components.bottomContentPadding
import ch.orellbuehler.jackdaw.ui.theme.Amber
import ch.orellbuehler.jackdaw.ui.theme.Ink
import ch.orellbuehler.jackdaw.ui.theme.Violet
import ch.orellbuehler.jackdaw.ui.theme.VioletDeep
import ch.orellbuehler.jackdaw.ui.theme.isDark
import ch.orellbuehler.jackdaw.ui.theme.packPalette
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    groupsViewModel: GroupsViewModel,
    importViewModel: ImportViewModel,
    onOpenPack: (String) -> Unit,
    onStudy: (String) -> Unit,
    onAddPack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val installed by importViewModel.installedIds.collectAsStateWithLifecycle()
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    var editGroups by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Mascot(size = 32.dp, animated = false)
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    FilledTonalIconButton(onClick = onAddPack, modifier = Modifier.padding(end = 8.dp)) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.library_add_pack))
                    }
                },
                scrollBehavior = scroll,
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !state.loaded -> Unit
                state.packs.isEmpty() ->
                    EmptyLibrary(
                        state = state,
                        installed = installed,
                        onAddSample = importViewModel::addSample,
                        onMore = onAddPack,
                    )
                else -> PackList(state, onOpenPack, onStudy, onFilter = viewModel::select, onEditGroups = { editGroups = true })
            }
        }
    }
    if (editGroups) GroupsSheet(groupsViewModel, packId = null, onDismiss = { editGroups = false })
}

@Composable
private fun PackList(
    state: LibraryState,
    onOpenPack: (String) -> Unit,
    onStudy: (String) -> Unit,
    onFilter: (LibraryFilter) -> Unit,
    onEditGroups: () -> Unit,
) {
    val visible = state.visible
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = bottomContentPadding(LocalBottomBarInset.current),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "today") {
            TodayCard(state, onStudy)
        }
        item(key = "header") {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.library_your_packs),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    pluralStringResource(R.plurals.pack_count, visible.size, visible.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "filters") {
            FilterRow(state, onFilter, onEditGroups)
        }
        if (visible.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.library_group_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
        items(visible, key = { it.id }) { pack ->
            PackCard(pack, onClick = { onOpenPack(pack.id) })
        }
    }
}

@Composable
private fun FilterRow(
    state: LibraryState,
    onFilter: (LibraryFilter) -> Unit,
    onEditGroups: () -> Unit,
) {
    val filter = state.filter
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = filter == LibraryFilter.All,
            onClick = { onFilter(LibraryFilter.All) },
            label = { Text(stringResource(R.string.library_filter_all), maxLines = 1) },
        )
        state.groups.forEach { group ->
            FilterChip(
                selected = filter == LibraryFilter.Group(group.id),
                onClick = { onFilter(LibraryFilter.Group(group.id)) },
                label = { Text(group.name, maxLines = 1) },
            )
        }
        if (state.archived.isNotEmpty()) {
            FilterChip(
                selected = filter == LibraryFilter.Archived,
                onClick = { onFilter(LibraryFilter.Archived) },
                label = { Text(stringResource(R.string.library_filter_archived), maxLines = 1) },
                leadingIcon = { Icon(Icons.Rounded.Inventory2, null, Modifier.size(18.dp)) },
            )
        }
        AssistChip(
            onClick = onEditGroups,
            label = { Text(stringResource(R.string.library_groups), maxLines = 1) },
            leadingIcon = { Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)) },
        )
    }
}

@Composable
private fun TodayCard(
    state: LibraryState,
    onStudy: (String) -> Unit,
) {
    val stats = state.stats
    val target = state.continuePack
    val brush = Brush.linearGradient(listOf(Violet, VioletDeep))
    val onBrand = Color.White
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(brush)
            .padding(20.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val goal = stats?.dailyGoal ?: 1
            val done = stats?.reviewsToday ?: 0
            ProgressRing(
                progress = done.toFloat() / goal,
                size = 88.dp,
                strokeWidth = 10.dp,
                trackColor = onBrand.copy(alpha = 0.2f),
                color = if (stats?.goalReached == true) Amber else onBrand,
            ) {
                if (stats?.goalReached == true) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(36.dp), tint = Amber)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$done", color = onBrand, fontSize = 24.sp, style = MaterialTheme.typography.headlineSmall)
                        Text("/ $goal", color = onBrand.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.today_title), color = onBrand.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                Text(
                    if (stats?.goalReached == true) {
                        stringResource(R.string.today_goal_reached)
                    } else {
                        stringResource(R.string.today_reviews, done, goal)
                    },
                    color = onBrand,
                    style = MaterialTheme.typography.titleMedium,
                )
                StreakPill(stats)
            }
        }
        HorizontalDivider(color = onBrand.copy(alpha = 0.18f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val due = state.totalDue
                Text(
                    if (due > 0) {
                        pluralStringResource(R.plurals.reviews_due, due, due)
                    } else {
                        stringResource(R.string.library_all_caught_up)
                    },
                    color = onBrand,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        target == null -> stringResource(R.string.library_nothing_due)
                        due > 0 -> stringResource(R.string.library_continue_in, target.title)
                        else -> stringResource(R.string.library_learn_new_in, target.title)
                    },
                    color = onBrand.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (target != null) {
                Button(
                    onClick = { onStudy(target.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(
                        stringResource(if (state.totalDue > 0) R.string.library_continue else R.string.library_learn_new),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun StreakPill(stats: Stats?) {
    val streak = stats?.currentStreak ?: 0
    val text =
        if (streak > 0) {
            pluralStringResource(R.plurals.streak_days, streak, streak)
        } else {
            stringResource(R.string.streak_none)
        }
    Row(
        Modifier
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Rounded.LocalFireDepartment,
            null,
            Modifier.size(18.dp),
            tint = if (streak > 0) Amber else Color.White.copy(alpha = 0.6f),
        )
        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PackCard(
    pack: PackSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = MaterialTheme.isDark
    val palette = remember(pack.color, scheme, dark) { packPalette(pack.color, scheme, dark) }
    val percent = (pack.progress * 100).roundToInt()
    val summary = stringResource(R.string.pack_card_description, pack.title, percent, pack.dueCount, pack.newCount)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = summary },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = palette.container, contentColor = palette.onContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PackIconTile(pack.icon, palette, size = 54.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(pack.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        pluralStringResource(R.plurals.item_count, pack.itemCount, pack.itemCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Text("$percent%", style = MaterialTheme.typography.titleMedium, color = palette.accentOnContainer)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (pack.dueCount > 0) {
                    Pill(stringResource(R.string.pack_badge_due, pack.dueCount), palette.accent, palette.onAccent)
                }
                if (pack.newCount > 0) {
                    Pill(
                        stringResource(R.string.pack_badge_new, pack.newCount),
                        scheme.surface.copy(alpha = 0.7f),
                        scheme.onSurface,
                    )
                }
                if (pack.dueCount == 0 && pack.newCount == 0) {
                    Pill(stringResource(R.string.pack_badge_done), scheme.surface.copy(alpha = 0.7f), scheme.onSurfaceVariant)
                }
            }
            SlimProgressBar(
                progress = pack.progress.toFloat(),
                trackColor = palette.accent.copy(alpha = 0.2f),
                color = palette.accentOnContainer,
            )
        }
    }
}

@Composable
private fun EmptyLibrary(
    state: LibraryState,
    installed: Set<String>,
    onAddSample: (String) -> Unit,
    onMore: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = bottomContentPadding(LocalBottomBarInset.current),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Mascot(size = 148.dp, mood = MascotMood.Idle)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
        item {
            Text(
                stringResource(R.string.empty_samples_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp),
            )
        }
        items(state.samples, key = { it.id }) { sample ->
            SampleRow(sample, installed = sample.id in installed, onAdd = { onAddSample(sample.id) })
        }
        item {
            OutlinedButton(onClick = onMore, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.empty_more_options), maxLines = 1)
            }
        }
    }
}
