package ch.orellbuehler.jackdaw.format

data class TextSpan(
    val text: String,
    val bold: Boolean,
    val italic: Boolean,
    val code: Boolean,
)

sealed class TextBlock {
    data class Paragraph(
        val spans: List<TextSpan>,
    ) : TextBlock()

    data class Code(
        val language: String?,
        val code: String,
    ) : TextBlock() {
        val lines: List<List<CodeSpan>> by lazy { codeLines(highlight(language, code)) }
    }
}

data class RichText(
    val blocks: List<TextBlock>,
) {
    val plain: String
        get() =
            blocks.joinToString("\n") { block ->
                when (block) {
                    is TextBlock.Paragraph -> block.spans.joinToString("") { it.text }
                    is TextBlock.Code -> block.code
                }
            }

    val isEmpty: Boolean
        get() = blocks.isEmpty()

    val hasCode: Boolean
        get() = blocks.any { it is TextBlock.Code }

    val spans: List<TextSpan>
        get() = blocks.filterIsInstance<TextBlock.Paragraph>().flatMap { it.spans }

    companion object {
        fun parse(source: String): RichText = RichText(parseBlocks(source))

        fun of(spans: List<TextSpan>): RichText = RichText(if (spans.isEmpty()) emptyList() else listOf(TextBlock.Paragraph(spans)))

        fun fromPlain(text: String): RichText =
            of(if (text.isEmpty()) emptyList() else listOf(TextSpan(text, bold = false, italic = false, code = false)))
    }
}

internal fun parseBlocks(input: String): List<TextBlock> {
    val s = normalizeNewlines(input)
    val blocks = ArrayList<TextBlock>()
    for (raw in splitCodeBlocks(s)) {
        when (raw) {
            is RawBlock.Text -> parseMarkup(raw.raw).takeIf { it.isNotEmpty() }?.let { blocks += TextBlock.Paragraph(it) }
            is RawBlock.Code -> if (raw.code.isNotEmpty()) blocks += TextBlock.Code(raw.language, raw.code)
        }
    }
    return blocks
}

private const val ESCAPABLE = "\\*`{}|:"

internal fun isEscapable(c: Char): Boolean = ESCAPABLE.indexOf(c) >= 0

internal fun normalizeNewlines(text: String): String = if (text.indexOf('\r') < 0) text else text.replace("\r\n", "\n").replace('\r', '\n')

internal fun unescape(text: String): String {
    if (text.indexOf('\\') < 0) return text
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '\\' && i + 1 < text.length && isEscapable(text[i + 1])) {
            out.append(text[i + 1])
            i += 2
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString()
}

internal fun parseMarkup(input: String): List<TextSpan> {
    val s = normalizeNewlines(input)
    val spans = ArrayList<TextSpan>()
    val buf = StringBuilder()

    fun flush() {
        if (buf.isNotEmpty()) {
            spans += TextSpan(buf.toString(), bold = false, italic = false, code = false)
            buf.clear()
        }
    }

    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length && isEscapable(s[i + 1])) {
            buf.append(s[i + 1])
            i += 2
            continue
        }
        if (c == '`') {
            val end = matchCode(s, i)
            if (end > 0) {
                flush()
                spans += TextSpan(s.substring(i + 1, end - 1), bold = false, italic = false, code = true)
                i = end
                continue
            }
        } else if (c == '*') {
            val bold = if (s.startsWith("**", i)) matchBold(s, i) else null
            if (bold != null) {
                flush()
                spans += TextSpan(unescape(s.substring(i + 2, bold)), bold = true, italic = false, code = false)
                i = bold + 2
                continue
            }
            val italic = matchItalic(s, i)
            if (italic > 0) {
                flush()
                spans += TextSpan(unescape(s.substring(i + 1, italic)), bold = false, italic = true, code = false)
                i = italic + 1
                continue
            }
        }
        buf.append(c)
        i++
    }
    flush()
    return spans
}

private fun matchCode(
    s: String,
    start: Int,
): Int {
    var j = start + 1
    while (j < s.length && s[j] != '`' && s[j] != '\n') j++
    return if (j < s.length && s[j] == '`' && j > start + 1) j + 1 else -1
}

// returns the index of the closing "**" or null
private fun matchBold(
    s: String,
    start: Int,
): Int? {
    val contentStart = start + 2
    if (contentStart >= s.length || isSpecWhitespace(s[contentStart])) return null
    var k = contentStart + 1
    while (k + 1 < s.length) {
        if (s[k] == '*' && s[k + 1] == '*' && !isSpecWhitespace(s[k - 1])) return k
        if (s[k] == '\n') return null
        k++
    }
    return null
}

// returns the index of the closing "*" or -1
private fun matchItalic(
    s: String,
    start: Int,
): Int {
    val contentStart = start + 1
    if (contentStart >= s.length) return -1
    val first = s[contentStart]
    if (isSpecWhitespace(first) || first == '*') return -1
    var k = contentStart + 1
    while (k < s.length && s[k] != '*' && s[k] != '\n') k++
    if (k >= s.length || s[k] != '*') return -1
    return if (isSpecWhitespace(s[k - 1])) -1 else k
}
