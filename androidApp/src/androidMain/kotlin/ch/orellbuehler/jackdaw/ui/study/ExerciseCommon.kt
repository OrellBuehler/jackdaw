package ch.orellbuehler.jackdaw.ui.study

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.orellbuehler.jackdaw.R
import ch.orellbuehler.jackdaw.format.RichText
import ch.orellbuehler.jackdaw.study.Answer
import ch.orellbuehler.jackdaw.study.Grade
import ch.orellbuehler.jackdaw.study.SessionState
import ch.orellbuehler.jackdaw.ui.components.Pill
import ch.orellbuehler.jackdaw.ui.components.RichTextView
import ch.orellbuehler.jackdaw.ui.theme.jackdaw

class StudyActions(
    val reveal: () -> Unit,
    val submit: (Answer) -> Unit,
    val selfGrade: (Grade) -> Unit,
    val next: () -> Unit,
)

enum class Tone {
    Neutral,
    Correct,
    Wrong,
}

@Composable
fun rememberStringList(
    vararg inputs: Any?,
    init: () -> List<String>,
): SnapshotStateList<String> =
    rememberSaveable(
        inputs = inputs,
        saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { init().toMutableStateList() }

@Composable
fun ExerciseFrame(
    hint: RichText?,
    isRetry: Boolean,
    scrollable: Boolean = true,
    bottom: @Composable () -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (scrollable) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (isRetry) RetryPill()
                HintCard(hint)
                FigureCard()
                body()
            }
        } else {
            Column(Modifier.weight(1f).fillMaxWidth()) { body() }
        }
        bottom()
    }
}

@Composable
fun RetryPill() {
    Pill(
        stringResource(R.string.study_retry_badge),
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer,
    )
}

@Composable
fun HintCard(hint: RichText?) {
    AnimatedVisibility(
        visible = hint != null && !hint.isEmpty,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        if (hint != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Rounded.Lightbulb,
                    stringResource(R.string.study_hint_label),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                RichTextView(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun ExplanationCard(
    text: RichText?,
    modifier: Modifier = Modifier,
) {
    if (text == null || text.isEmpty) return
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.feedback_explanation),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        RichTextView(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun PromptText(
    text: RichText,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    val length = text.plain.length
    val style =
        when {
            length <= 60 -> MaterialTheme.typography.headlineMedium
            length <= 160 -> MaterialTheme.typography.headlineSmall
            else -> MaterialTheme.typography.titleLarge
        }
    RichTextView(text, modifier = modifier, style = style, textAlign = textAlign)
}

@Composable
fun BottomPanel(
    tone: Tone,
    content: @Composable ColumnScope.() -> Unit,
) {
    val container by animateColorAsState(
        when (tone) {
            Tone.Neutral -> MaterialTheme.colorScheme.background
            Tone.Correct -> MaterialTheme.jackdaw.successContainer
            Tone.Wrong -> MaterialTheme.colorScheme.errorContainer
        },
        label = "panel",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(container)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun PrimaryAction(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
fun VerdictPanel(
    feedback: SessionState.Feedback,
    onContinue: () -> Unit,
) {
    val correct = feedback.correct
    val colors = MaterialTheme.colorScheme
    val success = MaterialTheme.jackdaw
    val content = if (correct) success.onSuccessContainer else colors.onErrorContainer
    BottomPanel(if (correct) Tone.Correct else Tone.Wrong) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (correct) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel, null, Modifier.size(32.dp), tint = content)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        when {
                            !correct -> R.string.feedback_wrong
                            feedback.typo -> R.string.feedback_typo
                            else -> R.string.feedback_correct
                        },
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = content,
                )
                if (feedback.willRepeat) {
                    Text(
                        stringResource(R.string.feedback_repeat),
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.85f),
                    )
                }
            }
        }
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = content,
                    contentColor = if (correct) success.successContainer else colors.errorContainer,
                ),
        ) {
            Text(stringResource(R.string.feedback_continue), style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

@Composable
fun GradeRow(
    suggested: Grade?,
    onGrade: (Grade) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val success = MaterialTheme.jackdaw

    data class Option(
        val grade: Grade,
        val label: Int,
        val container: Color,
        val content: Color,
    )
    val options =
        listOf(
            Option(Grade.AGAIN, R.string.grade_again, colors.errorContainer, colors.onErrorContainer),
            Option(Grade.HARD, R.string.grade_hard, colors.tertiaryContainer, colors.onTertiaryContainer),
            Option(Grade.GOOD, R.string.grade_good, success.success, success.onSuccess),
            Option(Grade.EASY, R.string.grade_easy, colors.primary, colors.onPrimary),
        )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSuggested = suggested == option.grade
            Button(
                onClick = { onGrade(option.grade) },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = option.container, contentColor = option.content),
                border = if (isSuggested) BorderStroke(3.dp, colors.onSurface) else null,
            ) {
                Text(stringResource(option.label), style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
fun GradePanel(
    suggested: Grade?,
    onGrade: (Grade) -> Unit,
) {
    BottomPanel(Tone.Neutral) {
        Text(
            stringResource(R.string.grade_prompt),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        GradeRow(suggested, onGrade)
    }
}
