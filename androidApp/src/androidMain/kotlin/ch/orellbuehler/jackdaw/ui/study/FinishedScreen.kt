package ch.orellbuehler.jackdaw.ui.study

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.orellbuehler.jackdaw.R
import ch.orellbuehler.jackdaw.study.SessionMode
import ch.orellbuehler.jackdaw.ui.components.Mascot
import ch.orellbuehler.jackdaw.ui.components.MascotMood
import ch.orellbuehler.jackdaw.ui.components.ProgressRing
import ch.orellbuehler.jackdaw.ui.components.rememberReduceMotion
import ch.orellbuehler.jackdaw.ui.theme.Amber
import ch.orellbuehler.jackdaw.ui.theme.PackPalette
import ch.orellbuehler.jackdaw.ui.theme.Violet
import ch.orellbuehler.jackdaw.ui.theme.jackdaw
import kotlin.math.roundToInt
import kotlin.random.Random

private class Particle(
    val x: Float,
    val delay: Float,
    val speed: Float,
    val spin: Float,
    val size: Float,
    val color: Int,
    val sway: Float,
)

@Composable
fun FinishedScreen(
    state: StudyUiState.Done,
    palette: PackPalette,
    onDone: () -> Unit,
    onMore: () -> Unit,
) {
    val summary = state.summary
    val stats = state.stats
    val scheme = MaterialTheme.colorScheme
    val success = MaterialTheme.jackdaw
    val accuracy = summary.accuracy.toFloat()
    val reduceMotion = rememberReduceMotion()
    val totalSeconds = (summary.durationMs / 1000).toInt().coerceAtLeast(1)
    val durationText =
        if (totalSeconds < 60) {
            stringResource(R.string.duration_seconds, totalSeconds)
        } else {
            stringResource(R.string.duration_minutes, (totalSeconds + 30) / 60)
        }
    Box(Modifier.fillMaxSize()) {
        if (!reduceMotion) Confetti(listOf(Amber, Violet, success.success, palette.accent))
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Mascot(size = 128.dp, mood = MascotMood.Happy)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(if (summary.mode == SessionMode.LEARN) R.string.finish_title else R.string.finish_title_practice),
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.finish_correct_first_time, summary.correct, summary.correct + summary.wrong),
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile(
                        Modifier.weight(1f),
                        scheme.primaryContainer,
                        scheme.onPrimaryContainer,
                        stringResource(R.string.finish_accuracy),
                    ) {
                        ProgressRing(
                            accuracy,
                            size = 64.dp,
                            strokeWidth = 8.dp,
                            trackColor = scheme.onPrimaryContainer.copy(alpha = 0.15f),
                            color = scheme.primary,
                        ) {
                            Text("${(accuracy * 100).roundToInt()}%", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    Tile(
                        Modifier.weight(1f),
                        scheme.secondaryContainer,
                        scheme.onSecondaryContainer,
                        stringResource(R.string.finish_learned),
                    ) {
                        Text("${summary.newFacets}", style = MaterialTheme.typography.headlineSmall)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile(Modifier.weight(1f), scheme.surfaceContainerHigh, scheme.onSurface, stringResource(R.string.finish_time)) {
                        Text(durationText, style = MaterialTheme.typography.headlineSmall)
                    }
                    Tile(Modifier.weight(1f), scheme.tertiaryContainer, scheme.onTertiaryContainer, stringResource(R.string.stats_streak)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(28.dp), tint = scheme.onTertiaryContainer)
                            Text(
                                pluralStringResource(R.plurals.days_count, stats?.currentStreak ?: 0, stats?.currentStreak ?: 0),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                    }
                }
                if (stats != null) {
                    Text(
                        if (stats.goalReached) {
                            stringResource(R.string.finish_goal_reached)
                        } else {
                            stringResource(R.string.today_reviews, stats.reviewsToday, stats.dailyGoal)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (stats.goalReached) success.success else scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.finish_done), maxLines = 1)
                }
                OutlinedButton(onClick = onMore, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.finish_again), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Tile(
    modifier: Modifier,
    container: Color,
    content: Color,
    label: String,
    body: @Composable () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(container)
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides content) {
            Box(Modifier.size(width = 120.dp, height = 64.dp), contentAlignment = Alignment.Center) { body() }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.85f), maxLines = 1)
    }
}

@Composable
private fun Confetti(colors: List<Color>) {
    val particles =
        remember {
            val random = Random(7)
            List(46) {
                Particle(
                    x = random.nextFloat(),
                    delay = random.nextFloat() * 0.35f,
                    speed = 0.55f + random.nextFloat() * 0.45f,
                    spin = random.nextFloat() * 720f - 360f,
                    size = 6f + random.nextFloat() * 8f,
                    color = random.nextInt(colors.size),
                    sway = random.nextFloat() * 40f - 20f,
                )
            }
        }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(3200, easing = LinearEasing)) }
    Canvas(Modifier.fillMaxSize()) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        particles.forEach { p ->
            val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val y = -20.dp.toPx() + (size.height * 0.85f) * local * p.speed
            val x = size.width * p.x + p.sway.dp.toPx() * kotlin.math.sin(local * 9f)
            val alpha = (1f - local).coerceIn(0f, 1f).coerceAtLeast(0.15f) * if (local > 0.7f) (1f - local) / 0.3f else 1f
            rotate(p.spin * local, Offset(x, y)) {
                drawRect(
                    colors[p.color].copy(alpha = alpha),
                    Offset(x - p.size.dp.toPx() / 2, y - p.size.dp.toPx() / 2),
                    Size(p.size.dp.toPx(), p.size.dp.toPx() * 0.55f),
                )
            }
        }
    }
}
