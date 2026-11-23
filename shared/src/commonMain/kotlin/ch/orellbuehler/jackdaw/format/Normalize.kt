package ch.orellbuehler.jackdaw.format

private const val LEADING_MARKS = "\u00BF\u00A1"
private const val TRAILING_MARKS = ".,;:!?\u2026\u3002\u3001\uFF0E\uFF0C\uFF1B\uFF1A\uFF01\uFF1F"

internal fun normalize(
    text: String,
    caseSensitive: Boolean,
    ignoreDiacritics: Boolean,
): String {
    var t = unifyQuotesAndDashes(text)
    if (ignoreDiacritics) t = toNfd(t).filterNot { it in '\u0300'..'\u036F' }
    if (!caseSensitive) t = t.lowercase()
    t = collapseWhitespace(toNfc(t))
    val stripped =
        t
            .trimStart { it in LEADING_MARKS || isSpecWhitespace(it) }
            .trimEnd { it in TRAILING_MARKS || isSpecWhitespace(it) }
    return stripped.ifEmpty { t }
}

internal fun matchesAnswer(
    typed: String,
    accepted: List<String>,
    caseSensitive: Boolean,
    ignoreDiacritics: Boolean,
): Boolean {
    val typedNormal = normalize(typed, caseSensitive, ignoreDiacritics)
    if (typedNormal.isEmpty()) return false
    return accepted.any { normalize(it, caseSensitive, ignoreDiacritics) == typedNormal }
}

internal fun matchAnswer(
    typed: String,
    accepted: List<String>,
    caseSensitive: Boolean,
    ignoreDiacritics: Boolean,
    tolerateTypos: Boolean,
    otherAnswers: Set<String> = emptySet(),
): AnswerMatch {
    val typedNormal = normalize(typed, caseSensitive, ignoreDiacritics)
    if (typedNormal.isEmpty()) return AnswerMatch.NONE
    val forms = accepted.map { normalize(it, caseSensitive, ignoreDiacritics) }
    if (typedNormal in forms) return AnswerMatch.EXACT
    if (!tolerateTypos || caseSensitive || typedNormal in otherAnswers) return AnswerMatch.NONE
    return if (forms.any { isTypoOf(typedNormal, it) }) AnswerMatch.TYPO else AnswerMatch.NONE
}

private fun unifyQuotesAndDashes(text: String): String {
    var changed = false
    for (c in text) {
        if (mapChar(c) != c) {
            changed = true
            break
        }
    }
    if (!changed) return text
    val out = StringBuilder(text.length)
    for (c in text) out.append(mapChar(c))
    return out.toString()
}

private fun mapChar(c: Char): Char =
    when (c) {
        '\u2018', '\u2019', '\u201B', '\u02BC', '\u2032' -> '\''
        '\u201C', '\u201D', '\u201E', '\u201F', '\u2033' -> '"'
        '\u2010', '\u2011', '\u2012', '\u2013', '\u2212' -> '-'
        else -> c
    }

private fun collapseWhitespace(text: String): String {
    val out = StringBuilder(text.length)
    var pendingSpace = false
    for (c in text) {
        if (isSpecWhitespace(c)) {
            pendingSpace = out.isNotEmpty()
        } else {
            if (pendingSpace) out.append(' ')
            pendingSpace = false
            out.append(c)
        }
    }
    return out.toString()
}
