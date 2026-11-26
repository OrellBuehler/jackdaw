package ch.orellbuehler.jackdaw.format

internal sealed class RawBlock {
    class Text(
        val raw: String,
    ) : RawBlock()

    class Code(
        val language: String?,
        val code: String,
    ) : RawBlock()
}

private const val FENCE = "```"
private const val MAX_LANGUAGE = 20

private fun isLanguageChar(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '+' || c == '#' || c == '-'

// the language of an opening fence line s[start, end), "" when it names none, null when the line is not an opening fence
private fun openingFence(
    s: String,
    start: Int,
    end: Int,
): String? {
    var p = start
    while (p < end && s[p] == ' ') p++
    if (p + FENCE.length > end || !s.startsWith(FENCE, p)) return null
    p += FENCE.length
    while (p < end && s[p] == ' ') p++
    val nameStart = p
    while (p < end && isLanguageChar(s[p])) p++
    if (p - nameStart > MAX_LANGUAGE) return null
    val name = s.substring(nameStart, p)
    while (p < end && s[p] == ' ') p++
    return if (p == end) name.lowercase() else null
}

private fun isClosingFence(
    s: String,
    start: Int,
    end: Int,
): Boolean {
    if (end - start < FENCE.length || !s.startsWith(FENCE, start)) return false
    for (p in start + FENCE.length until end) if (s[p] != ' ') return false
    return true
}

// Splits normalised rich text (newlines are U+000A) into runs of ordinary text and fenced code blocks.
internal fun splitCodeBlocks(s: String): List<RawBlock> {
    if (s.indexOf(FENCE) < 0) return if (s.isEmpty()) emptyList() else listOf(RawBlock.Text(s))
    val starts = ArrayList<Int>()
    starts += 0
    for (k in s.indices) if (s[k] == '\n') starts += k + 1
    val lineCount = starts.size

    fun lineEnd(line: Int): Int = if (line + 1 < lineCount) starts[line + 1] - 1 else s.length

    val blocks = ArrayList<RawBlock>()
    var textFrom = 0

    fun flushText(untilLine: Int) {
        if (untilLine > textFrom) blocks += RawBlock.Text(s.substring(starts[textFrom], lineEnd(untilLine - 1)))
    }

    var line = 0
    while (line < lineCount) {
        val language = openingFence(s, starts[line], lineEnd(line))
        if (language == null) {
            line++
            continue
        }
        var close = line + 1
        while (close < lineCount && !isClosingFence(s, starts[close], lineEnd(close))) close++
        if (close >= lineCount) break
        flushText(line)
        val code = if (close > line + 1) s.substring(starts[line + 1], lineEnd(close - 1)) else ""
        blocks += RawBlock.Code(language.ifEmpty { null }, code)
        line = close + 1
        textFrom = line
    }
    flushText(lineCount)
    return blocks
}

internal fun containsCodeBlock(text: String): Boolean = splitCodeBlocks(normalizeNewlines(text)).any { it is RawBlock.Code }

// highlighted spans of a whole block to one list per line, with tabs shown as four spaces
internal fun codeLines(spans: List<CodeSpan>): List<List<CodeSpan>> {
    val lines = ArrayList<List<CodeSpan>>()
    var current = ArrayList<CodeSpan>()
    for (span in spans) {
        val text = if (span.text.indexOf('\t') >= 0) span.text.replace("\t", "    ") else span.text
        var from = 0
        while (true) {
            val nl = text.indexOf('\n', from)
            val piece = text.substring(from, if (nl < 0) text.length else nl)
            if (piece.isNotEmpty()) current += CodeSpan(piece, span.kind)
            if (nl < 0) break
            lines += current
            current = ArrayList()
            from = nl + 1
        }
    }
    lines += current
    return lines
}
