package ch.orellbuehler.jackdaw.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.orellbuehler.jackdaw.format.CodeSpan
import ch.orellbuehler.jackdaw.format.TokenKind
import ch.orellbuehler.jackdaw.ui.theme.CodeColors
import ch.orellbuehler.jackdaw.ui.theme.code

val CodeTextStyle =
    TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        textAlign = TextAlign.Start,
    )

fun List<List<CodeSpan>>.toAnnotated(colors: CodeColors) =
    buildAnnotatedString {
        forEachIndexed { index, line ->
            if (index > 0) append('\n')
            line.forEach { span ->
                val style =
                    SpanStyle(
                        color = colors.of(span.kind),
                        fontStyle = if (span.kind == TokenKind.COMMENT) FontStyle.Italic else null,
                    )
                withStyle(style) { append(span.text) }
            }
        }
    }

@Composable
fun CodeSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.code
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border, shape)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) { content() }
}

@Composable
fun CodeBlockView(
    lines: List<List<CodeSpan>>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.code
    val text = remember(lines, colors) { lines.toAnnotated(colors) }
    CodeSurface(modifier) {
        Text(text, style = CodeTextStyle, color = colors.plain, softWrap = false)
    }
}
