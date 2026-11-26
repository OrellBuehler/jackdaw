package ch.orellbuehler.jackdaw.ui.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ch.orellbuehler.jackdaw.R
import ch.orellbuehler.jackdaw.study.AnswerKeyboard
import ch.orellbuehler.jackdaw.study.SessionState
import ch.orellbuehler.jackdaw.study.TextAnswer
import ch.orellbuehler.jackdaw.study.TypeExercise
import ch.orellbuehler.jackdaw.ui.components.RichTextView
import ch.orellbuehler.jackdaw.ui.theme.jackdaw

@Composable
fun TypeView(
    exercise: TypeExercise,
    question: SessionState.Question?,
    feedback: SessionState.Feedback?,
    step: Int,
    actions: StudyActions,
) {
    var text by rememberSaveable(step) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(feedback == null) {
        if (feedback == null) runCatching { focus.requestFocus() } else keyboard?.hide()
    }
    val success = MaterialTheme.jackdaw
    val scheme = MaterialTheme.colorScheme
    ExerciseFrame(
        hint = question?.hint,
        isRetry = question?.isRetry == true,
        bottom = {
            if (feedback == null) {
                BottomPanel(Tone.Neutral) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { actions.submit(TextAnswer("")) }) {
                            Text(stringResource(R.string.type_dont_know), maxLines = 1)
                        }
                        Column(Modifier.weight(1f)) {
                            PrimaryAction(
                                stringResource(R.string.choice_check),
                                enabled = text.isNotBlank(),
                            ) { actions.submit(TextAnswer(text)) }
                        }
                    }
                }
            } else {
                VerdictPanel(feedback, actions.next)
            }
        },
    ) {
        PromptText(exercise.prompt)
        val tone = feedback?.let { if (it.correct) success.success else scheme.error }
        OutlinedTextField(
            value = if (feedback != null) feedback.userAnswer else text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            readOnly = feedback != null,
            singleLine = true,
            textStyle = StudyTextStyle,
            label = { Text(stringResource(R.string.type_hint)) },
            shape = MaterialTheme.shapes.large,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = exercise.keyboard.keyboardType(),
                    imeAction = ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank() && feedback == null) actions.submit(TextAnswer(text)) }),
            colors =
                if (tone != null) {
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = tone,
                        unfocusedBorderColor = tone,
                        focusedLabelColor = tone,
                        unfocusedLabelColor = tone,
                    )
                } else {
                    OutlinedTextFieldDefaults.colors()
                },
        )
        if (feedback != null) {
            if (!feedback.correct || feedback.typo) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(if (feedback.typo) R.string.type_spelling else R.string.type_expected),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                    )
                    RichTextView(feedback.correctAnswer, style = StudyTextStyle, color = success.success)
                }
            }
            ExplanationCard(feedback.explanation)
        }
    }
}

fun AnswerKeyboard.keyboardType(): KeyboardType =
    when (this) {
        AnswerKeyboard.TEXT -> KeyboardType.Text
        AnswerKeyboard.INTEGER -> KeyboardType.Number
        AnswerKeyboard.DECIMAL -> KeyboardType.Decimal
    }
