package ch.orellbuehler.jackdaw.ui.study

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ch.orellbuehler.jackdaw.R
import ch.orellbuehler.jackdaw.format.RichText
import ch.orellbuehler.jackdaw.study.OrderAnswer
import ch.orellbuehler.jackdaw.study.OrderExercise
import ch.orellbuehler.jackdaw.study.SessionState
import ch.orellbuehler.jackdaw.ui.components.RichTextView
import ch.orellbuehler.jackdaw.ui.theme.cardSurface
import ch.orellbuehler.jackdaw.ui.theme.jackdaw
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun OrderView(
    exercise: OrderExercise,
    question: SessionState.Question?,
    feedback: SessionState.Feedback?,
    step: Int,
    actions: StudyActions,
) {
    val ids = rememberStringList(step) { exercise.entries.map { it.id } }
    val byId = exercise.entries.associateBy { it.id }
    val haptics = LocalHapticFeedback.current
    val moveUp = stringResource(R.string.order_move_up)
    val moveDown = stringResource(R.string.order_move_down)
    ExerciseFrame(
        hint = question?.hint,
        isRetry = question?.isRetry == true,
        scrollable = false,
        bottom = {
            if (feedback == null) {
                BottomPanel(Tone.Neutral) {
                    PrimaryAction(stringResource(R.string.choice_check)) { actions.submit(OrderAnswer(ids.toList())) }
                }
            } else {
                VerdictPanel(feedback, actions.next)
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (question?.isRetry == true) RetryPill()
            HintCard(question?.hint)
            FigureCard(maxHeight = 140.dp)
            PromptText(exercise.prompt)
            if (feedback == null) {
                Text(
                    stringResource(R.string.order_instructions),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (feedback == null) {
            val lazy = rememberLazyListState()
            val reorder =
                rememberReorderableLazyListState(lazy) { from, to ->
                    ids.add(to.index, ids.removeAt(from.index))
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = lazy,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(ids, key = { _, id -> id }) { index, id ->
                    ReorderableItem(reorder, key = id) { dragging ->
                        val elevation by animateDpAsState(if (dragging) 8.dp else 0.dp, label = "drag")
                        val text = byId[id]?.text ?: RichText(emptyList())
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color =
                                if (dragging) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.cardSurface
                                },
                            shadowElevation = elevation,
                            modifier =
                                Modifier.semantics {
                                    customActions =
                                        listOf(
                                            CustomAccessibilityAction(moveUp) {
                                                if (index > 0) ids.add(index - 1, ids.removeAt(index))
                                                true
                                            },
                                            CustomAccessibilityAction(moveDown) {
                                                if (index < ids.lastIndex) ids.add(index + 1, ids.removeAt(index))
                                                true
                                            },
                                        )
                                },
                        ) {
                            Row(
                                Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                NumberBadge(
                                    index + 1,
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                RichTextView(
                                    text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                                )
                                Box(
                                    Modifier
                                        .size(48.dp)
                                        .draggableHandle(
                                            onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                                            onDragStopped = { haptics.performHapticFeedback(HapticFeedbackType.GestureEnd) },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.DragHandle,
                                        stringResource(R.string.order_drag_cd),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val success = MaterialTheme.jackdaw
                val scheme = MaterialTheme.colorScheme
                feedback.orderResults.forEach { result ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (result.correct) success.successContainer else scheme.errorContainer)
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val content = if (result.correct) success.onSuccessContainer else scheme.onErrorContainer
                        NumberBadge(result.position, content.copy(alpha = 0.14f), content)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            RichTextView(result.arranged, style = MaterialTheme.typography.bodyLarge, color = content)
                            if (!result.correct) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        stringResource(R.string.order_expected_label),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = content.copy(alpha = 0.8f),
                                    )
                                    RichTextView(
                                        result.expected,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = content,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
                ExplanationCard(feedback.explanation)
            }
        }
    }
}

@Composable
private fun NumberBadge(
    number: Int,
    container: Color,
    content: Color,
) {
    Box(Modifier.size(32.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Text("$number", style = MaterialTheme.typography.labelLarge, color = content)
    }
}
