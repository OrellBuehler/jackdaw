package ch.orellbuehler.jackdaw.ui.study

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.orellbuehler.jackdaw.R
import ch.orellbuehler.jackdaw.study.ChoiceExercise
import ch.orellbuehler.jackdaw.study.ClozeExercise
import ch.orellbuehler.jackdaw.study.FlipExercise
import ch.orellbuehler.jackdaw.study.OpenExercise
import ch.orellbuehler.jackdaw.study.OrderExercise
import ch.orellbuehler.jackdaw.study.SessionMode
import ch.orellbuehler.jackdaw.study.SessionState
import ch.orellbuehler.jackdaw.study.TrueFalseExercise
import ch.orellbuehler.jackdaw.study.TypeExercise
import ch.orellbuehler.jackdaw.ui.components.Mascot
import ch.orellbuehler.jackdaw.ui.components.MascotMood
import ch.orellbuehler.jackdaw.ui.components.SlimProgressBar
import ch.orellbuehler.jackdaw.ui.theme.PackPalette
import ch.orellbuehler.jackdaw.ui.theme.ensureContrast
import ch.orellbuehler.jackdaw.ui.theme.isDark
import ch.orellbuehler.jackdaw.ui.theme.packPalette

@Composable
fun StudyScreen(
    viewModel: StudyViewModel,
    onExit: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val pack by viewModel.pack.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val dark = MaterialTheme.isDark
    val palette = remember(pack?.color, scheme, dark) { packPalette(pack?.color, scheme, dark) }
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    val active = ui is StudyUiState.Active
    BackHandler(enabled = active) { confirmEnd = true }

    Surface(Modifier.fillMaxSize(), color = scheme.background) {
        AnimatedContent(
            targetState = ui,
            contentKey = {
                when (it) {
                    StudyUiState.Loading -> "loading"
                    is StudyUiState.Empty -> "empty"
                    is StudyUiState.Active -> "step-${it.step}"
                    is StudyUiState.Done -> "done"
                    StudyUiState.Closed -> "closed"
                }
            },
            transitionSpec = { transitionFor(initialState, targetState) },
            label = "study",
        ) { state ->
            when (state) {
                StudyUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is StudyUiState.Empty -> EmptyStudy(state.mode, viewModel, onExit)
                is StudyUiState.Active ->
                    ActiveSession(
                        state = state,
                        palette = palette,
                        onClose = { confirmEnd = true },
                        actions =
                            StudyActions(
                                reveal = viewModel::reveal,
                                submit = viewModel::submit,
                                selfGrade = viewModel::selfGrade,
                                next = viewModel::next,
                            ),
                        onHint = viewModel::revealHint,
                    )
                StudyUiState.Closed -> Box(Modifier.fillMaxSize())
                is StudyUiState.Done ->
                    FinishedScreen(
                        state = state,
                        palette = palette,
                        onDone = onExit,
                        onMore = viewModel::restart,
                    )
            }
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text(stringResource(R.string.study_end_title)) },
            text = { Text(stringResource(R.string.study_end_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnd = false
                    viewModel.finish()
                    onExit()
                }) { Text(stringResource(R.string.study_end_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text(stringResource(R.string.study_keep_going)) } },
        )
    }
}

private fun transitionFor(
    from: StudyUiState,
    to: StudyUiState,
): ContentTransform =
    if (from is StudyUiState.Active && to is StudyUiState.Active) {
        (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(280))) togetherWith
            (slideOutHorizontally(tween(260)) { -it / 4 } + fadeOut(tween(160)))
    } else if (to is StudyUiState.Done) {
        (fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.96f)) togetherWith fadeOut(tween(200))
    } else {
        fadeIn(tween(250)) togetherWith fadeOut(tween(200))
    }

@Composable
private fun ActiveSession(
    state: StudyUiState.Active,
    palette: PackPalette,
    onClose: () -> Unit,
    actions: StudyActions,
    onHint: () -> Unit,
) {
    val session = state.state
    val question = session as? SessionState.Question
    val feedback = session as? SessionState.Feedback
    val position: Int
    val total: Int
    val hasHint: Boolean
    when (session) {
        is SessionState.Question -> {
            position = session.position
            total = session.total
            hasHint = session.hasHint && session.hint == null
        }
        is SessionState.Feedback -> {
            position = session.position
            total = session.total
            hasHint = false
        }
        else -> return
    }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(feedback?.verdict, state.step) {
        when {
            feedback == null -> Unit
            feedback.needsSelfGrade -> Unit
            feedback.correct -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            else -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
        }
    }
    val barColor = ensureContrast(palette.accent, MaterialTheme.colorScheme.background, 3f, MaterialTheme.colorScheme.onSurface)
    val answeredForGood = feedback != null && feedback.correct && !feedback.willRepeat
    val progressDescription = stringResource(R.string.study_progress_cd, position, total)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.study_close_cd)) }
            Box(Modifier.weight(1f).semantics { contentDescription = progressDescription }) {
                SlimProgressBar(
                    progress = (if (answeredForGood) position else position - 1).toFloat() / total.coerceAtLeast(1),
                    height = 10.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    color = barColor,
                )
            }
            Text(
                "$position/$total",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            HintButton(hasHint, onHint)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val exercise = question?.exercise ?: feedback!!.exercise
            CompositionLocalProvider(LocalFigure provides (question?.figure ?: feedback?.figure)) {
                when (exercise) {
                    is FlipExercise -> FlipView(exercise, question, feedback, palette, actions)
                    is ChoiceExercise -> ChoiceView(exercise, question, feedback, state.step, actions)
                    is TypeExercise -> TypeView(exercise, question, feedback, state.step, actions)
                    is ClozeExercise -> ClozeView(exercise, question, feedback, state.step, actions)
                    is TrueFalseExercise -> TrueFalseView(exercise, question, feedback, actions)
                    is OrderExercise -> OrderView(exercise, question, feedback, state.step, actions)
                    is OpenExercise -> OpenView(exercise, question, feedback, state.step, actions)
                }
            }
        }
    }
}

@Composable
private fun HintButton(
    visible: Boolean,
    onClick: () -> Unit,
) {
    Box(Modifier.height(48.dp)) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            FilledTonalIconButton(
                onClick = onClick,
                colors =
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
            ) {
                Icon(Icons.Rounded.Lightbulb, stringResource(R.string.study_hint_cd))
            }
        }
    }
}

@Composable
private fun EmptyStudy(
    mode: SessionMode,
    viewModel: StudyViewModel,
    onExit: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Mascot(size = 140.dp, mood = MascotMood.Sleepy)
        Text(stringResource(R.string.study_empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.study_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (mode == SessionMode.LEARN) {
            Button(onClick = viewModel::practiceInstead, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.study_empty_practice), maxLines = 1)
            }
            OutlinedButton(
                onClick = onExit,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_back), maxLines = 1) }
        } else {
            Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_back), maxLines = 1) }
        }
    }
}
