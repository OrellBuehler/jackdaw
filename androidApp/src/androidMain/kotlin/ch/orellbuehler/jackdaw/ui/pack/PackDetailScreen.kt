package ch.orellbuehler.jackdaw.ui.pack

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.orellbuehler.jackdaw.R
import ch.orellbuehler.jackdaw.data.PACK_MIME_TYPE
import ch.orellbuehler.jackdaw.data.packFileName
import ch.orellbuehler.jackdaw.data.writePackForSharing
import ch.orellbuehler.jackdaw.data.writePackToUri
import ch.orellbuehler.jackdaw.store.PackDetail
import ch.orellbuehler.jackdaw.store.SectionSummary
import ch.orellbuehler.jackdaw.study.PracticeFilter
import ch.orellbuehler.jackdaw.study.SessionMode
import ch.orellbuehler.jackdaw.study.SessionPreview
import ch.orellbuehler.jackdaw.ui.components.PackIconTile
import ch.orellbuehler.jackdaw.ui.components.SlimProgressBar
import ch.orellbuehler.jackdaw.ui.library.GroupsSheet
import ch.orellbuehler.jackdaw.ui.library.GroupsViewModel
import ch.orellbuehler.jackdaw.ui.theme.Ink
import ch.orellbuehler.jackdaw.ui.theme.PackPalette
import ch.orellbuehler.jackdaw.ui.theme.cardSurface
import ch.orellbuehler.jackdaw.ui.theme.isDark
import ch.orellbuehler.jackdaw.ui.theme.jackdaw
import ch.orellbuehler.jackdaw.ui.theme.packPalette
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class Confirm {
    None,
    Reset,
    Delete,
}

@Composable
fun PackDetailScreen(
    viewModel: PackDetailViewModel,
    groupsViewModel: GroupsViewModel,
    onUpdateFromLink: (String) -> Unit,
    onBack: () -> Unit,
    onStudy: (SessionMode, String?, PracticeFilter) -> Unit,
    onBrowse: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        PackDetailState.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        PackDetailState.Missing -> {
            LaunchedEffect(Unit) { onBack() }
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }
        is PackDetailState.Ready -> PackDetailContent(viewModel, groupsViewModel, current, onUpdateFromLink, onBack, onStudy, onBrowse)
    }
}

@Composable
private fun PackDetailContent(
    viewModel: PackDetailViewModel,
    groupsViewModel: GroupsViewModel,
    state: PackDetailState.Ready,
    onUpdateFromLink: (String) -> Unit,
    onBack: () -> Unit,
    onStudy: (SessionMode, String?, PracticeFilter) -> Unit,
    onBrowse: () -> Unit,
) {
    val detail = state.detail
    val summary = detail.summary
    val scheme = MaterialTheme.colorScheme
    val dark = MaterialTheme.isDark
    val palette = remember(summary.color, scheme, dark) { packPalette(summary.color, scheme, dark) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirm by rememberSaveable { mutableStateOf(Confirm.None) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var grouping by rememberSaveable { mutableStateOf(false) }
    var combining by rememberSaveable { mutableStateOf(false) }
    val exportFailed = stringResource(R.string.export_failed)
    val savedText = stringResource(R.string.export_saved)

    val saveLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PACK_MIME_TYPE)) { uri ->
            if (uri != null) {
                scope.launch {
                    val json = viewModel.exportJson()
                    val ok = json != null && writePackToUri(context, uri, json)
                    snackbar.showSnackbar(if (ok) savedText else exportFailed)
                }
            }
        }

    val listState = rememberLazyListState()
    val heroVisible by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 120 } }
    StatusBarIcons(light = if (heroVisible) palette.onHero == Ink else !dark)

    Scaffold(
        containerColor = scheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    Hero(
                        detail = detail,
                        palette = palette,
                        onBack = onBack,
                        onShare = {
                            scope.launch {
                                val json = viewModel.exportJson()
                                if (json == null) {
                                    snackbar.showSnackbar(exportFailed)
                                } else {
                                    val uri = writePackForSharing(context, summary.title, json)
                                    val send =
                                        Intent(Intent.ACTION_SEND)
                                            .setType(PACK_MIME_TYPE)
                                            .putExtra(Intent.EXTRA_STREAM, uri)
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    context.startActivity(Intent.createChooser(send, summary.title))
                                }
                            }
                        },
                        onSave = { saveLauncher.launch(packFileName(summary.title)) },
                        onReset = { confirm = Confirm.Reset },
                        onDelete = { confirm = Confirm.Delete },
                        onEdit = { editing = true },
                        onGroups = { grouping = true },
                        onCombine = { combining = true },
                        onArchive = { viewModel.setArchived(!summary.archived) },
                        onUnlink = if (summary.sourceUrl == null) null else viewModel::removeLink,
                    )
                }
                if (summary.archived) item { ArchivedNotice(Modifier.padding(horizontal = 16.dp)) }
                item { StatRow(detail, Modifier.padding(horizontal = 16.dp)) }
                item {
                    LearnBlock(state.learn, onLearn = {
                        onStudy(SessionMode.LEARN, null, PracticeFilter.ALL)
                    }, modifier = Modifier.padding(horizontal = 16.dp))
                }
                item {
                    PracticeBlock(
                        detail,
                        onPractice = { onStudy(SessionMode.PRACTICE, null, it) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                if (detail.sections.size > 1 || detail.sections.any { !it.isUngrouped }) {
                    item {
                        SectionsBlock(
                            detail.sections,
                            palette,
                            onSection = { onStudy(SessionMode.LEARN, it.id, PracticeFilter.ALL) },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                item { BrowseRow(summary.itemCount, onBrowse, Modifier.padding(horizontal = 16.dp)) }
                summary.sourceUrl?.let { url ->
                    item { LinkBlock(url, summary.edited, onUpdate = { onUpdateFromLink(url) }, Modifier.padding(horizontal = 16.dp)) }
                }
                if (hasAbout(detail)) item { AboutBlock(detail, Modifier.padding(horizontal = 16.dp)) }
            }
            AnimatedVisibility(
                visible = !heroVisible,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(scheme.background.copy(alpha = 0.97f)),
                )
            }
        }
    }

    if (editing) {
        EditDetailsDialog(
            title = summary.title,
            description = summary.descriptionText.orEmpty(),
            onSave = viewModel::updateInfo,
            onDismiss = { editing = false },
        )
    }
    if (grouping) GroupsSheet(groupsViewModel, packId = summary.id, onDismiss = { grouping = false })
    if (combining) {
        CombineSheet(
            viewModel = viewModel,
            packTitle = summary.title,
            onDone = { added ->
                combining = false
                scope.launch {
                    snackbar.showSnackbar(context.resources.getQuantityString(R.plurals.combine_done, added, added))
                }
            },
            onDismiss = { combining = false },
        )
    }

    when (confirm) {
        Confirm.None -> Unit
        Confirm.Reset ->
            AlertDialog(
                onDismissRequest = { confirm = Confirm.None },
                title = { Text(stringResource(R.string.dialog_reset_title)) },
                text = { Text(stringResource(R.string.dialog_reset_body, summary.title)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirm = Confirm.None
                        viewModel.resetProgress()
                    }) { Text(stringResource(R.string.action_reset)) }
                },
                dismissButton = { TextButton(onClick = { confirm = Confirm.None }) { Text(stringResource(R.string.action_cancel)) } },
            )
        Confirm.Delete ->
            AlertDialog(
                onDismissRequest = { confirm = Confirm.None },
                title = { Text(stringResource(R.string.dialog_delete_title)) },
                text = { Text(stringResource(R.string.dialog_delete_body, summary.title)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirm = Confirm.None
                            viewModel.delete {}
                        },
                    ) { Text(stringResource(R.string.action_delete), color = scheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirm = Confirm.None }) { Text(stringResource(R.string.action_cancel)) } },
            )
    }
}

@Composable
private fun StatusBarIcons(light: Boolean) {
    val view = LocalView.current
    val activity = LocalActivity.current
    DisposableEffect(light) {
        val window = activity?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val previous = controller.isAppearanceLightStatusBars
            controller.isAppearanceLightStatusBars = light
            onDispose { controller.isAppearanceLightStatusBars = previous }
        }
    }
}

@Composable
private fun Hero(
    detail: PackDetail,
    palette: PackPalette,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onGroups: () -> Unit,
    onCombine: () -> Unit,
    onArchive: () -> Unit,
    onUnlink: (() -> Unit)?,
) {
    val summary = detail.summary
    var menu by remember { mutableStateOf(false) }
    val on = palette.onHero
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brush.linearGradient(listOf(palette.heroStart, palette.heroEnd)))
            .statusBarsPadding()
            .padding(horizontal = 8.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), tint = on)
            }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.action_more), tint = on)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_edit)) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = {
                            menu = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_groups)) },
                        leadingIcon = { Icon(Icons.Rounded.Folder, null) },
                        onClick = {
                            menu = false
                            onGroups()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_combine)) },
                        leadingIcon = { Icon(Icons.Rounded.LibraryAdd, null) },
                        onClick = {
                            menu = false
                            onCombine()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(if (summary.archived) R.string.menu_unarchive else R.string.menu_archive)) },
                        leadingIcon = { Icon(if (summary.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, null) },
                        onClick = {
                            menu = false
                            onArchive()
                        },
                    )
                    if (onUnlink != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_unlink)) },
                            leadingIcon = { Icon(Icons.Rounded.LinkOff, null) },
                            onClick = {
                                menu = false
                                onUnlink()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_share)) },
                        leadingIcon = { Icon(Icons.Rounded.Share, null) },
                        onClick = {
                            menu = false
                            onShare()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_save)) },
                        leadingIcon = { Icon(Icons.Rounded.Save, null) },
                        onClick = {
                            menu = false
                            onSave()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_reset)) },
                        leadingIcon = { Icon(Icons.Rounded.RestartAlt, null) },
                        onClick = {
                            menu = false
                            onReset()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(on.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (summary.icon.isNullOrBlank()) {
                        PackIconTile(null, palette, size = 68.dp)
                    } else {
                        Text(summary.icon!!, style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Text(
                    summary.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = on,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            summary.descriptionText?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = on.copy(alpha = 0.9f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.detail_progress, (summary.progress * 100).roundToInt()),
                        style = MaterialTheme.typography.labelLarge,
                        color = on,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        pluralStringResource(R.plurals.item_count, summary.itemCount, summary.itemCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = on.copy(alpha = 0.85f),
                    )
                }
                SlimProgressBar(
                    summary.progress.toFloat(),
                    trackColor = on.copy(alpha = 0.2f),
                    color = on,
                    height = 8.dp,
                )
            }
        }
    }
}

@Composable
private fun StatRow(
    detail: PackDetail,
    modifier: Modifier = Modifier,
) {
    val s = detail.summary
    val learningNow = s.learningCount + s.reviewCount
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(s.newCount, R.string.stat_new, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
        StatTile(learningNow, R.string.stat_learning, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
        StatTile(s.dueCount, R.string.stat_due, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        StatTile(s.masteredCount, R.string.stat_mastered, MaterialTheme.jackdaw.success, Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(
    value: Int,
    label: Int,
    dot: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.cardSurface),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LearnBlock(
    preview: SessionPreview,
    onLearn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (preview.totalCount > 0) {
        Column(
            modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(onClick = onLearn, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(24.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.detail_learn), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
            Text(
                stringResource(R.string.detail_learn_caption, preview.dueCount, preview.newCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Row(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.jackdaw.successContainer)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.jackdaw.onSuccessContainer)
            Text(
                stringResource(R.string.detail_nothing_due),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.jackdaw.onSuccessContainer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private data class PracticeOption(
    val filter: PracticeFilter,
    val icon: ImageVector,
    val label: Int,
)

@Composable
private fun PracticeBlock(
    detail: PackDetail,
    onPractice: (PracticeFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val specific = detail.practiceFilters.filter { it != PracticeFilter.ALL }
    val options =
        buildList {
            if (specific.size != 1) add(PracticeOption(PracticeFilter.ALL, Icons.Rounded.Shuffle, R.string.practice_all))
            specific.forEach {
                add(
                    when (it) {
                        PracticeFilter.FLASHCARDS -> PracticeOption(it, Icons.Rounded.Style, R.string.practice_flashcards)
                        PracticeFilter.QUIZ -> PracticeOption(it, Icons.Rounded.Quiz, R.string.practice_quiz)
                        PracticeFilter.TYPING -> PracticeOption(it, Icons.Rounded.Keyboard, R.string.practice_typing)
                        PracticeFilter.ALL -> PracticeOption(it, Icons.Rounded.Shuffle, R.string.practice_all)
                    },
                )
            }
        }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.detail_practice),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.take(4).forEach { option ->
                Card(
                    onClick = { onPractice(option.filter) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(option.icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            stringResource(option.label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionsBlock(
    sections: List<SectionSummary>,
    palette: PackPalette,
    onSection: (SectionSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.detail_sections),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        sections.forEach { section ->
            Card(
                onClick = { onSection(section) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.cardSurface),
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (section.isUngrouped) stringResource(R.string.section_ungrouped) else section.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val meta =
                            pluralStringResource(R.plurals.item_count, section.itemCount, section.itemCount) +
                                if (section.dueCount > 0) " · " + stringResource(R.string.pack_badge_due, section.dueCount) else ""
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SlimProgressBar(
                            section.progress.toFloat(),
                            trackColor = palette.accent.copy(alpha = 0.18f),
                            color = palette.accentOnContainer,
                            height = 6.dp,
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BrowseRow(
    itemCount: Int,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onBrowse,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.AutoMirrored.Rounded.List, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.detail_browse), style = MaterialTheme.typography.titleSmall)
                Text(
                    pluralStringResource(R.plurals.item_count, itemCount, itemCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun hasAbout(detail: PackDetail): Boolean =
    detail.author != null ||
        detail.license != null ||
        detail.source != null ||
        detail.revision > 0 ||
        detail.summary.language != null ||
        detail.tags.isNotEmpty()

@Composable
private fun AboutBlock(
    detail: PackDetail,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.detail_about), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.cardSurface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            detail.author?.let { InfoRow(stringResource(R.string.about_author), it) }
            detail.license?.let { InfoRow(stringResource(R.string.about_license), it) }
            detail.summary.language?.let { InfoRow(stringResource(R.string.about_language), it) }
            if (detail.revision > 0) InfoRow(stringResource(R.string.about_revision), detail.revision.toString())
            detail.source?.let { InfoRow(stringResource(R.string.about_source), it) }
            if (detail.tags.isNotEmpty()) InfoRow(stringResource(R.string.about_tags), detail.tags.joinToString(", "))
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f),
        )
    }
}
