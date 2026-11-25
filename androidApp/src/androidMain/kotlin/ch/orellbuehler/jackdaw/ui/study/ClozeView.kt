package ch.orellbuehler.jackdaw.ui.study

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ch.orellbuehler.jackdaw.R
import ch.orellbuehler.jackdaw.format.RichText
import ch.orellbuehler.jackdaw.format.TextSpan
import ch.orellbuehler.jackdaw.study.AnswerKeyboard
import ch.orellbuehler.jackdaw.study.ClozeAnswer
import ch.orellbuehler.jackdaw.study.ClozeExercise
import ch.orellbuehler.jackdaw.study.ClozeSegment
import ch.orellbuehler.jackdaw.study.SessionState
import ch.orellbuehler.jackdaw.ui.components.RichTextView
import ch.orellbuehler.jackdaw.ui.theme.jackdaw

sealed interface ClozeToken {
    data class Word(
        val text: RichText,
    ) : ClozeToken

    data object LineBreak : ClozeToken
}

fun clozeTokens(text: RichText): List<ClozeToken> {
    val tokens = ArrayList<ClozeToken>()
    var current = ArrayList<TextSpan>()
    var hasWord = false
    var hasTrailingSpace = false

    fun flush() {
        if (current.isNotEmpty()) tokens += ClozeToken.Word(RichText(current))
        current = ArrayList()
        hasWord = false
        hasTrailingSpace = false
    }

    fun append(
        span: TextSpan,
        piece: String,
    ) {
        if (piece.isEmpty()) return
        val last = current.lastOrNull()
        if (last != null && last.bold == span.bold && last.italic == span.italic && last.code == span.code) {
            current[current.lastIndex] = last.copy(text = last.text + piece)
        } else {
            current += span.copy(text = piece)
        }
    }

    for (span in text.spans) {
        val buffer = StringBuilder()
        for (ch in span.text) {
            when {
                ch == '\n' -> {
                    append(span, buffer.toString())
                    buffer.clear()
                    flush()
                    tokens += ClozeToken.LineBreak
                }
                ch.isWhitespace() -> {
                    buffer.append(ch)
                    if (hasWord) hasTrailingSpace = true
                }
                else -> {
                    if (hasTrailingSpace) {
                        append(span, buffer.toString())
                        buffer.clear()
                        flush()
                    }
                    buffer.append(ch)
                    hasWord = true
                }
            }
        }
        append(span, buffer.toString())
    }
    flush()
    return tokens
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClozeView(
    exercise: ClozeExercise,
    question: SessionState.Question?,
    feedback: SessionState.Feedback?,
    step: Int,
    actions: StudyActions,
) {
    val answers = rememberStringList(step) { List(exercise.gapCount) { "" } }
    val keyboard = LocalSoftwareKeyboardController.current
    val first = remember { FocusRequester() }
    LaunchedEffect(feedback == null) {
        if (feedback == null) runCatching { first.requestFocus() } else keyboard?.hide()
    }
    val style = MaterialTheme.typography.titleLarge
    val scheme = MaterialTheme.colorScheme
    val success = MaterialTheme.jackdaw
    val results = feedback?.gapResults.orEmpty().associateBy { it.index }
    ExerciseFrame(
        hint = question?.hint,
        isRetry = question?.isRetry == true,
        bottom = {
            if (feedback == null) {
                BottomPanel(Tone.Neutral) {
                    PrimaryAction(stringResource(R.string.choice_check), enabled = answers.any { it.isNotBlank() }) {
                        actions.submit(ClozeAnswer(answers.toList()))
                    }
                }
            } else {
                VerdictPanel(feedback, actions.next)
            }
        },
    ) {
        Text(stringResource(R.string.cloze_instructions), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.Start,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            exercise.segments.forEach { segment ->
                when (segment) {
                    is ClozeSegment.Text ->
                        clozeTokens(segment.text).forEach { token ->
                            when (token) {
                                is ClozeToken.Word -> RichTextView(token.text, style = style)
                                ClozeToken.LineBreak -> Spacer(Modifier.fillMaxWidth().height(0.dp))
                            }
                        }
                    is ClozeSegment.Gap -> {
                        val result = results[segment.index]
                        if (result == null) {
                            GapField(
                                value = answers.getOrElse(segment.index) { "" },
                                hint = segment.hint,
                                keyboard = segment.keyboard,
                                index = segment.index,
                                isLast = segment.index == exercise.gapCount - 1,
                                focusRequester = if (segment.index == 0) first else null,
                                style = style,
                                enabled = feedback == null,
                                onChange = { answers[segment.index] = it },
                                onDone = {
                                    if (answers.any { it.isNotBlank() }) actions.submit(ClozeAnswer(answers.toList()))
                                },
                            )
                        } else {
                            GapResultView(result.given, result.canonical, result.correct, result.typo, style)
                        }
                    }
                }
            }
        }
        if (feedback != null) ExplanationCard(feedback.explanation)
    }
}

@Composable
private fun GapField(
    value: String,
    hint: String?,
    keyboard: AnswerKeyboard,
    index: Int,
    isLast: Boolean,
    focusRequester: FocusRequester?,
    style: TextStyle,
    enabled: Boolean,
    onChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val description = stringResource(R.string.cloze_gap_cd, index + 1)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        singleLine = true,
        textStyle = style.copy(color = scheme.onSurface, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(scheme.primary),
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = keyboard.keyboardType(),
                imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
            ),
        keyboardActions =
            KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Next) },
                onDone = { onDone() },
            ),
        modifier =
            Modifier
                .padding(horizontal = 2.dp)
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = description },
        decorationBox = { inner ->
            Box(
                Modifier
                    .widthIn(min = 84.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (focused) scheme.primaryContainer else scheme.surfaceContainerHigh)
                    .border(if (focused) 2.dp else 1.dp, if (focused) scheme.primary else scheme.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (value.isEmpty() && !hint.isNullOrBlank()) {
                    Text(hint, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1)
                }
                inner()
            }
        },
    )
}

@Composable
private fun GapResultView(
    given: String,
    canonical: String,
    correct: Boolean,
    typo: Boolean,
    style: TextStyle,
) {
    val success = MaterialTheme.jackdaw
    val scheme = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 2.dp),
    ) {
        if (correct) {
            Chip(given, success.successContainer, success.onSuccessContainer, style, typo)
            if (typo) Chip(canonical, success.successContainer, success.onSuccessContainer, style, false)
        } else {
            Chip(given.ifBlank { "—" }, scheme.errorContainer, scheme.onErrorContainer, style, true)
            Chip(canonical, success.successContainer, success.onSuccessContainer, style, false)
        }
    }
}

@Composable
private fun Chip(
    text: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    style: TextStyle,
    strike: Boolean,
) {
    Text(
        text,
        style = style,
        color = content,
        textDecoration = if (strike) TextDecoration.LineThrough else null,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(container).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
