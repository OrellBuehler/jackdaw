package ch.orellbuehler.jackdaw.ui.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.orellbuehler.jackdaw.R
import ch.orellbuehler.jackdaw.store.AppSettings
import ch.orellbuehler.jackdaw.store.ThemePreference
import ch.orellbuehler.jackdaw.ui.components.LocalBottomBarInset
import ch.orellbuehler.jackdaw.ui.components.bottomContentPadding
import ch.orellbuehler.jackdaw.ui.theme.cardSurface
import kotlin.math.roundToInt

const val FORMAT_URL = "https://github.com/OrellBuehler/jackdaw/tree/main/format"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val current = settings
        if (current != null) {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = bottomContentPadding(LocalBottomBarInset.current),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { GroupTitle(R.string.settings_study) }
                item {
                    GroupCard {
                        SliderSetting(
                            title = stringResource(R.string.settings_daily_goal),
                            value = current.dailyGoal,
                            valueText = { pluralStringResource(R.plurals.reviews_count, it, it) },
                            help = stringResource(R.string.settings_daily_goal_help),
                            range = 5..100,
                            onCommit = viewModel::setDailyGoal,
                        )
                        SliderSetting(
                            title = stringResource(R.string.settings_session_size),
                            value = current.defaultSessionSize,
                            valueText = { pluralStringResource(R.plurals.item_count, it, it) },
                            help = stringResource(R.string.settings_session_size_help),
                            range = 5..100,
                            onCommit = viewModel::setSessionSize,
                        )
                        SwitchSetting(
                            title = stringResource(R.string.settings_typos),
                            help = stringResource(R.string.settings_typos_help),
                            checked = current.typoTolerance,
                            onChange = viewModel::setTypoTolerance,
                        )
                    }
                }
                item { GroupTitle(R.string.settings_appearance) }
                item { GroupCard { ThemeChooser(current, viewModel::setTheme) } }
                item { GroupTitle(R.string.settings_about) }
                item { AboutCard() }
            }
        }
    }
}

@Composable
private fun GroupTitle(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp),
    )
}

@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.cardSurface)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        content()
    }
}

@Composable
private fun SliderSetting(
    title: String,
    value: Int,
    valueText: @Composable (Int) -> String,
    help: String,
    range: IntRange,
    onCommit: (Int) -> Unit,
) {
    var slider by remember(value) { mutableFloatStateOf(value.coerceIn(range).toFloat()) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(valueText(slider.roundToInt()), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = slider,
            onValueChange = { slider = it },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / 5 - 1,
            colors =
                SliderDefaults.colors(
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
            onValueChangeFinished = { onCommit(slider.roundToInt()) },
        )
        Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    help: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ThemeChooser(
    settings: AppSettings,
    onSelect: (ThemePreference) -> Unit,
) {
    val options =
        listOf(
            ThemePreference.SYSTEM to R.string.theme_system,
            ThemePreference.LIGHT to R.string.theme_light,
            ThemePreference.DARK to R.string.theme_dark,
        )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (preference, label) ->
            SegmentedButton(
                selected = settings.theme == preference,
                onClick = { onSelect(preference) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    val version =
        remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        }
    GroupCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.about_version), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(version, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                stringResource(R.string.about_local_only),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, FORMAT_URL.toUri())) }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.about_format), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.about_format_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    FORMAT_URL.removePrefix("https://"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
