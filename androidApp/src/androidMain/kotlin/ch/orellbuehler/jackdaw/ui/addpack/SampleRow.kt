package ch.orellbuehler.jackdaw.ui.addpack

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.orellbuehler.jackdaw.R
import ch.orellbuehler.jackdaw.store.SamplePack
import ch.orellbuehler.jackdaw.ui.components.PackIconTile
import ch.orellbuehler.jackdaw.ui.theme.isDark
import ch.orellbuehler.jackdaw.ui.theme.packPalette

@Composable
fun SampleRow(
    sample: SamplePack,
    installed: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    updateAvailable: Boolean = false,
    busy: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = MaterialTheme.isDark
    val palette = remember(sample.color, scheme, dark) { packPalette(sample.color, scheme, dark) }
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = palette.container, contentColor = palette.onContainer),
    ) {
        Row(
            Modifier.padding(if (compact) 10.dp else 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PackIconTile(sample.icon, palette, size = if (compact) 42.dp else 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(sample.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    pluralStringResource(R.plurals.item_count, sample.itemCount, sample.itemCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (installed && !updateAvailable) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = palette.accentOnContainer)
                    Text(
                        stringResource(R.string.sample_added),
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.accentOnContainer,
                    )
                }
            } else {
                Button(
                    onClick = onAdd,
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
                ) {
                    Text(stringResource(if (installed) R.string.sample_update else R.string.action_add), maxLines = 1)
                }
            }
        }
    }
}
