package ch.orellbuehler.jackdaw.ui.addpack

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.orellbuehler.jackdaw.R
import ch.orellbuehler.jackdaw.format.ItemType
import kotlinx.coroutines.launch

private enum class AddPage {
    Menu,
    Paste,
    Samples,
    Ai,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPackScreen(
    importViewModel: ImportViewModel,
    onBack: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(AddPage.Menu) }
    var pasted by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    BackHandler(enabled = page != AddPage.Menu) { page = AddPage.Menu }
    val title =
        when (page) {
            AddPage.Menu -> R.string.add_title
            AddPage.Paste -> R.string.add_paste
            AddPage.Samples -> R.string.add_samples
            AddPage.Ai -> R.string.add_ai
        }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = { if (page == AddPage.Menu) onBack() else page = AddPage.Menu }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        AnimatedContent(
            targetState = page,
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = {
                if (targetState == AddPage.Menu) {
                    (slideInHorizontally { -it / 6 } + fadeIn()) togetherWith (slideOutHorizontally { it / 6 } + fadeOut())
                } else {
                    (slideInHorizontally { it / 6 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 6 } + fadeOut())
                }
            },
            label = "add-page",
        ) { current ->
            when (current) {
                AddPage.Menu -> MenuPage(importViewModel, onNavigate = { page = it })
                AddPage.Paste -> PastePage(text = pasted, onTextChange = { pasted = it }, onPreview = { importViewModel.openText(pasted) })
                AddPage.Samples -> SamplesPage(importViewModel)
                AddPage.Ai -> AiPage(importViewModel, snackbar, onPaste = { page = AddPage.Paste })
            }
        }
    }
}

@Composable
private fun MenuPage(
    importViewModel: ImportViewModel,
    onNavigate: (AddPage) -> Unit,
) {
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importViewModel.openUri(uri)
        }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OptionCard(
            Icons.Rounded.FolderOpen,
            R.string.add_open_file,
            R.string.add_open_file_sub,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
        ) { picker.launch(arrayOf("*/*")) }
        OptionCard(
            Icons.Rounded.ContentPaste,
            R.string.add_paste,
            R.string.add_paste_sub,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
        ) { onNavigate(AddPage.Paste) }
        OptionCard(
            Icons.Rounded.LibraryAdd,
            R.string.add_samples,
            R.string.add_samples_sub,
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
        ) { onNavigate(AddPage.Samples) }
        OptionCard(
            Icons.Rounded.AutoAwesome,
            R.string.add_ai,
            R.string.add_ai_sub,
            container = MaterialTheme.colorScheme.surfaceContainerHigh,
            content = MaterialTheme.colorScheme.onSurface,
        ) { onNavigate(AddPage.Ai) }
    }
}

@Composable
private fun OptionCard(
    icon: ImageVector,
    title: Int,
    subtitle: Int,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(content.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(subtitle), style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.8f))
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = content.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun PastePage(
    text: String,
    onTextChange: (String) -> Unit,
    onPreview: () -> Unit,
) {
    val context = LocalContext.current
    val empty = stringResource(R.string.paste_clipboard_empty)
    var notice by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 420.dp).weight(1f, fill = false),
            placeholder = { Text(stringResource(R.string.paste_hint)) },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            shape = MaterialTheme.shapes.large,
            supportingText = notice?.let { { Text(it) } },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    val clip = readClipboardText(context)
                    if (clip.isNullOrBlank()) {
                        notice = empty
                    } else {
                        notice = null
                        onTextChange(clip)
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp))
                Spacer8()
                Text(stringResource(R.string.paste_from_clipboard), maxLines = 1)
            }
            Button(onClick = onPreview, enabled = text.isNotBlank(), modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.paste_continue), maxLines = 1)
            }
        }
    }
}

@Composable
private fun Spacer8() =
    androidx.compose.foundation.layout
        .Spacer(Modifier.size(8.dp))

private fun readClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

@Composable
private fun SamplesPage(importViewModel: ImportViewModel) {
    val installed by importViewModel.installedRevisions.collectAsStateWithLifecycle()
    val library by importViewModel.library.collectAsStateWithLifecycle()
    val catalog by importViewModel.catalog.collectAsStateWithLifecycle()
    val adding by importViewModel.adding.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState(),
            ).padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        library.forEach { sample ->
            val installedRevision = installed[sample.id]
            Column(Modifier.animateContentSize()) {
                SampleRow(
                    sample,
                    installed = installedRevision != null,
                    onAdd = { importViewModel.addSample(sample.id) },
                    updateAvailable = installedRevision != null && sample.revision > installedRevision,
                    busy = sample.id in adding,
                )
                Text(
                    sample.descriptionText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        OnlineCatalogCard(catalog, onLoad = importViewModel::loadCatalog)
    }
}

@Composable
private fun OnlineCatalogCard(
    state: CatalogState,
    onLoad: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.online_title), style = MaterialTheme.typography.titleSmall)
        val body =
            when (state) {
                CatalogState.Idle, CatalogState.Loading -> R.string.online_body
                CatalogState.Loaded -> R.string.online_loaded
                CatalogState.Failed -> R.string.online_failed
            }
        Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (state) {
            CatalogState.Loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            CatalogState.Loaded -> Unit
            CatalogState.Idle, CatalogState.Failed ->
                Button(onClick = onLoad) {
                    Icon(Icons.Rounded.CloudDownload, null, Modifier.size(18.dp))
                    Spacer8()
                    Text(stringResource(if (state == CatalogState.Failed) R.string.online_retry else R.string.online_load), maxLines = 1)
                }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiPage(
    importViewModel: ImportViewModel,
    snackbar: SnackbarHostState,
    onPaste: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val types by importViewModel.promptTypes.collectAsStateWithLifecycle()
    val figures by importViewModel.promptFigures.collectAsStateWithLifecycle()
    val prompt = remember(types, figures) { importViewModel.authoringPrompt(types, figures) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val copied = stringResource(R.string.ai_copied)
    val shareSubject = stringResource(R.string.ai_share_subject)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState(),
            ).padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.ai_explainer), style = MaterialTheme.typography.bodyLarge)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.ai_types_title), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ItemType.entries.forEach { type ->
                    FilterChip(
                        selected = type in types,
                        onClick = { importViewModel.togglePromptType(type) },
                        label = { Text(stringResource(type.labelRes()), maxLines = 1) },
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().toggleable(value = figures, role = Role.Switch, onValueChange = importViewModel::setPromptFigures),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.ai_figures), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.ai_figures_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = figures, onCheckedChange = null)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                prompt,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 7,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.ai_show_less else R.string.ai_show_full), maxLines = 1)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText(shareSubject, prompt))
                    scope.launch { snackbar.showSnackbar(copied) }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                Spacer8()
                Text(stringResource(R.string.ai_copy), maxLines = 1)
            }
            OutlinedButton(
                onClick = {
                    val send =
                        Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, shareSubject)
                            .putExtra(Intent.EXTRA_TEXT, prompt)
                    context.startActivity(Intent.createChooser(send, null))
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.size(18.dp))
                Spacer8()
                Text(stringResource(R.string.action_share), maxLines = 1)
            }
        }
        OutlinedButton(onClick = onPaste, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ai_paste_answer), maxLines = 1)
        }
    }
}
