package ch.orellbuehler.jackdaw.ui.study

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.orellbuehler.jackdaw.R
import ch.orellbuehler.jackdaw.study.FlipExercise
import ch.orellbuehler.jackdaw.study.SessionState
import ch.orellbuehler.jackdaw.ui.theme.PackPalette
import ch.orellbuehler.jackdaw.ui.theme.cardSurface

@Composable
fun FlipView(
    exercise: FlipExercise,
    question: SessionState.Question?,
    feedback: SessionState.Feedback?,
    palette: PackPalette,
    actions: StudyActions,
) {
    val flipped = feedback != null
    val rotation by animateFloatAsState(
        if (flipped) 180f else 0f,
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow),
        label = "flip",
    )
    val density = LocalDensity.current.density
    ExerciseFrame(
        hint = question?.hint,
        isRetry = question?.isRetry == true,
        bottom = {
            if (feedback == null) {
                BottomPanel(Tone.Neutral) { PrimaryAction(stringResource(R.string.flip_show), onClick = actions.reveal) }
            } else {
                GradePanel(suggested = null, onGrade = actions.selfGrade)
            }
        },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 300.dp)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 14f * density
                }.clickable(enabled = !flipped, role = Role.Button, onClickLabel = stringResource(R.string.flip_show)) { actions.reveal() },
        ) {
            if (rotation <= 90f) {
                CardFace(
                    label = stringResource(R.string.flip_front),
                    container = MaterialTheme.cardSurface,
                    content = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    PromptText(exercise.prompt, textAlign = TextAlign.Center, large = true)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.TouchApp, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            stringResource(R.string.flip_tap),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Box(Modifier.graphicsLayer { rotationY = 180f }) {
                    CardFace(
                        label = stringResource(R.string.flip_back),
                        container = palette.accent,
                        content = palette.onAccent,
                        border = null,
                    ) {
                        if (feedback != null) PromptText(feedback.correctAnswer, textAlign = TextAlign.Center, large = true)
                    }
                }
            }
        }
        if (feedback != null) ExplanationCard(feedback.explanation)
    }
}

@Composable
private fun CardFace(
    label: String,
    container: Color,
    content: Color,
    border: BorderStroke?,
    body: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp).shadow(6.dp, RoundedCornerShape(28.dp), clip = false),
        shape = RoundedCornerShape(28.dp),
        color = container,
        contentColor = content,
        border = border,
    ) {
        Box(Modifier.heightIn(min = 300.dp).padding(24.dp)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = content.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.TopStart),
            )
            Column(
                Modifier.align(Alignment.Center).padding(top = 24.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                body()
            }
        }
    }
}
