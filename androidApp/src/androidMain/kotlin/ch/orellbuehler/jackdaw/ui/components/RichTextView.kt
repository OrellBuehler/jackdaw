package ch.orellbuehler.jackdaw.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import ch.orellbuehler.jackdaw.format.RichText
import ch.orellbuehler.jackdaw.format.TextBlock
import ch.orellbuehler.jackdaw.format.TextSpan

fun RichText.toAnnotated(codeBackground: Color): AnnotatedString = spans.toAnnotated(codeBackground)

fun List<TextSpan>.toAnnotated(codeBackground: Color): AnnotatedString =
    buildAnnotatedString {
        forEach { span ->
            val style =
                SpanStyle(
                    fontWeight = if (span.bold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    fontFamily = if (span.code) FontFamily.Monospace else null,
                    background = if (span.code) codeBackground else Color.Unspecified,
                )
            withStyle(style) { append(span.text) }
        }
    }

@Composable
fun rememberAnnotated(text: RichText): AnnotatedString {
    val background = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    return remember(text, background) { text.toAnnotated(background) }
}

@Composable
fun RichTextView(
    text: RichText,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    fontWeight: FontWeight? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
) {
    if (!text.hasCode) {
        Text(
            text = rememberAnnotated(text),
            modifier = modifier,
            style = style,
            color = color,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            fontWeight = fontWeight,
            lineHeight = lineHeight,
            inlineContent = inlineContent,
        )
        return
    }
    val background = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        text.blocks.forEach { block ->
            when (block) {
                is TextBlock.Paragraph ->
                    Text(
                        text = remember(block, background) { block.spans.toAnnotated(background) },
                        modifier = if (textAlign != null) Modifier.fillMaxWidth() else Modifier,
                        style = style,
                        color = color,
                        textAlign = textAlign,
                        maxLines = maxLines,
                        overflow = overflow,
                        fontWeight = fontWeight,
                        lineHeight = lineHeight,
                        inlineContent = inlineContent,
                    )
                is TextBlock.Code -> CodeBlockView(block.lines)
            }
        }
    }
}
