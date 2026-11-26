package ch.orellbuehler.jackdaw.format

internal sealed class ClozePart {
    class Text(
        val raw: String,
    ) : ClozePart()

    class Gap(
        val answers: List<String>,
        val hint: String?,
    ) : ClozePart()
}

internal fun parseCloze(text: String): List<ClozePart> {
    val parts = ArrayList<ClozePart>()
    val buf = StringBuilder()

    fun flush() {
        if (buf.isNotEmpty()) {
            parts += ClozePart.Text(buf.toString())
            buf.clear()
        }
    }

    val n = text.length
    var i = 0
    while (i < n) {
        val c = text[i]
        if (c == '\\' && i + 1 < n && isEscapable(text[i + 1])) {
            buf.append(c).append(text[i + 1])
            i += 2
            continue
        }
        if (c == '{' && text.startsWith("{{", i)) {
            val end = findGapEnd(text, i + 2)
            if (end >= 0) {
                val gap = parseGap(text.substring(i + 2, end))
                if (gap != null) {
                    flush()
                    parts += gap
                    i = end + 2
                    continue
                }
            }
        }
        buf.append(c)
        i++
    }
    flush()
    return parts
}

private fun findGapEnd(
    text: String,
    from: Int,
): Int {
    var j = from
    while (j < text.length) {
        val c = text[j]
        if (c == '\n') return -1
        if (c == '\\' && j + 1 < text.length && isEscapable(text[j + 1])) {
            j += 2
            continue
        }
        if (c == '}' && text.startsWith("}}", j)) return j
        j++
    }
    return -1
}

internal fun parseGap(raw: String): ClozePart.Gap? {
    val hintAt = indexOfUnescaped(raw, "::")
    val answerPart = if (hintAt >= 0) raw.substring(0, hintAt) else raw
    val hintPart = if (hintAt >= 0) raw.substring(hintAt + 2) else ""
    val answers = splitUnescaped(answerPart, '|').map { unescape(it).trimSpec() }.filter { it.isNotEmpty() }
    if (answers.isEmpty()) return null
    return ClozePart.Gap(answers, unescape(hintPart).trimSpec().ifEmpty { null })
}

private fun indexOfUnescaped(
    s: String,
    sep: String,
): Int {
    var i = 0
    while (i < s.length) {
        if (s[i] == '\\' && i + 1 < s.length && isEscapable(s[i + 1])) {
            i += 2
        } else if (s.startsWith(sep, i)) {
            return i
        } else {
            i++
        }
    }
    return -1
}

private fun splitUnescaped(
    s: String,
    sep: Char,
): List<String> {
    val parts = ArrayList<String>()
    var start = 0
    var i = 0
    while (i < s.length) {
        if (s[i] == '\\' && i + 1 < s.length && isEscapable(s[i + 1])) {
            i += 2
        } else if (s[i] == sep) {
            parts += s.substring(start, i)
            i++
            start = i
        } else {
            i++
        }
    }
    parts += s.substring(start)
    return parts
}

internal sealed class ClozeBlock {
    abstract val parts: List<ClozePart>

    class Text(
        override val parts: List<ClozePart>,
    ) : ClozeBlock()

    class Code(
        val language: String?,
        override val parts: List<ClozePart>,
    ) : ClozeBlock()
}

// Code blocks are found on the whole text, then the gaps inside every run of text and every block. A gap never contains a newline and a
// fence is a whole line, so this finds exactly the gaps that parseCloze finds on the whole text.
internal fun parseClozeBlocks(text: String): List<ClozeBlock> {
    if (text.indexOf("```") < 0) return listOf(ClozeBlock.Text(parseCloze(text)))
    return splitCodeBlocks(normalizeNewlines(text)).map { raw ->
        when (raw) {
            is RawBlock.Text -> ClozeBlock.Text(parseCloze(raw.raw))
            is RawBlock.Code -> ClozeBlock.Code(raw.language, parseCloze(raw.code))
        }
    }
}
