package ch.orellbuehler.jackdaw.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.orellbuehler.jackdaw.ui.theme.Amber
import ch.orellbuehler.jackdaw.ui.theme.Cream
import ch.orellbuehler.jackdaw.ui.theme.Ink
import ch.orellbuehler.jackdaw.ui.theme.Violet

enum class MascotMood {
    Idle,
    Happy,
    Sleepy,
}

private val NapeColor = Color(0xFFC9C4EC)
private val CardColor = Color(0xFFF6F3FF)
private const val BEAK = "M112 156L144 154L129 186Z"

@Composable
fun Mascot(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    mood: MascotMood = MascotMood.Idle,
    bodyColor: Color = Violet,
    animated: Boolean = true,
) {
    val animate = animated && !rememberReduceMotion()
    val beak = remember { PathParser().parsePathString(BEAK).toPath() }
    val transition = rememberInfiniteTransition(label = "mascot")
    val blink =
        if (animate && mood == MascotMood.Idle) {
            transition
                .animateFloat(
                    initialValue = 1f,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(
                            animation =
                                keyframes {
                                    durationMillis = 4200
                                    1f at 0
                                    1f at 3900
                                    0.08f at 4020 using FastOutSlowInEasing
                                    1f at 4200
                                },
                            repeatMode = RepeatMode.Restart,
                        ),
                    label = "blink",
                ).value
        } else {
            1f
        }
    val bob =
        if (animate) {
            transition
                .animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "bob",
                ).value
        } else {
            0.5f
        }
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val unit = this.size.minDimension / 256f

        fun p(
            x: Float,
            y: Float,
        ) = Offset(x * unit, y * unit)
        val badge = Path().apply { addOval(Rect(Offset.Zero, Size(256f * unit, 256f * unit))) }
        val head = Path().apply { addOval(Rect(p(44f, 62f), Size(168f * unit, 168f * unit))) }
        val lift = (0.5f - bob) * 12f * unit
        clipPath(badge) {
            drawRect(bodyColor)
            translate(top = lift) {
                drawPath(head, Ink)
                clipPath(head) {
                    drawCircle(NapeColor, radius = 70f * unit, center = p(22f, 166f))
                    drawCircle(NapeColor, radius = 70f * unit, center = p(234f, 166f))
                }
                for (x in listOf(99f, 157f)) {
                    when (mood) {
                        MascotMood.Happy ->
                            drawArc(
                                Cream,
                                startAngle = 200f,
                                sweepAngle = 140f,
                                useCenter = false,
                                topLeft = p(x - 20f, 118f),
                                size = Size(40f * unit, 40f * unit),
                                style = Stroke(width = 10f * unit, cap = StrokeCap.Round),
                            )
                        MascotMood.Sleepy ->
                            drawLine(Cream, p(x - 20f, 130f), p(x + 20f, 130f), strokeWidth = 10f * unit, cap = StrokeCap.Round)
                        MascotMood.Idle -> {
                            drawOval(Cream, topLeft = p(x - 25f, 126f - 25f * blink), size = Size(50f * unit, 50f * unit * blink))
                            drawOval(Ink, topLeft = p(x + 4f - 11f, 123f - 11f * blink), size = Size(22f * unit, 22f * unit * blink))
                        }
                    }
                }
            }
            rotate(degrees = -5f, pivot = p(128f, 257f)) {
                drawRoundRect(
                    CardColor,
                    topLeft = p(22f, 172f),
                    size = Size(212f * unit, 170f * unit),
                    cornerRadius = CornerRadius(26f * unit),
                )
            }
            translate(top = lift) {
                scale(unit, pivot = Offset.Zero) {
                    drawPath(beak, Amber)
                    drawPath(beak, Amber, style = Stroke(width = 10f, join = StrokeJoin.Round))
                }
            }
        }
    }
}
