package ch.orellbuehler.jackdaw.format

internal enum class AnswerMatch {
    EXACT,
    TYPO,
    NONE,
}

private const val MIN_LENGTH = 4
private const val LONG_LENGTH = 9
private const val LIKELY = 1
private const val UNLIKELY = 3
private const val IMPOSSIBLE = 1_000

private val KEYBOARDS =
    listOf(
        listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
        listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnm"),
        listOf("azertyuiop", "qsdfghjklm", "wxcvbn"),
    )
private val ROW_OFFSETS = listOf(0.0, 0.5, 1.5)

private val NEIGHBOURS: Set<String> by lazy {
    val pairs = HashSet<String>()
    for (rows in KEYBOARDS) {
        val keys = rows.flatMapIndexed { row, letters -> letters.mapIndexed { column, c -> Triple(c, row, column + ROW_OFFSETS[row]) } }
        for ((a, rowA, xA) in keys) {
            for ((b, rowB, xB) in keys) {
                val distance = if (xA > xB) xA - xB else xB - xA
                val near = if (rowA == rowB) distance == 1.0 else (rowA - rowB) in -1..1 && distance <= 1.0
                if (a != b && near) pairs += "$a$b"
            }
        }
    }
    pairs
}

internal fun typoBudget(expected: String): Int =
    when {
        expected.length < MIN_LENGTH -> 0
        expected.length < LONG_LENGTH -> 1
        else -> 2
    }

internal fun isTypoOf(
    typed: String,
    expected: String,
): Boolean {
    val budget = typoBudget(expected)
    if (budget == 0 || typed == expected) return false
    val lengthGap = typed.length - expected.length
    if (lengthGap > budget || -lengthGap > budget) return false
    if (typed.filter { it.isDigit() } != expected.filter { it.isDigit() }) return false
    return typoCost(typed, expected) <= budget
}

private fun typoCost(
    a: String,
    b: String,
): Int {
    val width = b.length + 1
    val size = (a.length + 1) * width
    val kept = IntArray(size) { IMPOSSIBLE }
    val dropped = IntArray(size) { IMPOSSIBLE }
    val added = IntArray(size) { IMPOSSIBLE }
    kept[0] = 0
    for (i in 0..a.length) {
        for (j in 0..b.length) {
            val at = i * width + j
            if (i > 0) dropped[at] = minOf(kept[at - width], dropped[at - width]) + gapCost(a, i - 1)
            if (j > 0) added[at] = minOf(kept[at - 1], added[at - 1]) + gapCost(b, j - 1)
            if (i > 0 && j > 0) {
                val diagonal = at - width - 1
                var best = minOf(kept[diagonal], dropped[diagonal], added[diagonal]) + substitutionCost(a[i - 1], b[j - 1])
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1] && a[i - 1] != a[i - 2]) {
                    val swapped = diagonal - width - 1
                    best = minOf(best, minOf(kept[swapped], dropped[swapped], added[swapped]) + LIKELY)
                }
                kept[at] = best
            }
        }
    }
    return minOf(kept[size - 1], dropped[size - 1], added[size - 1])
}

private fun gapCost(
    text: String,
    index: Int,
): Int {
    val c = text[index]
    if (!c.isLetter()) return LIKELY
    val endsWord = index == text.length - 1 || !text[index + 1].isLetterOrDigit()
    return if (endsWord) UNLIKELY else LIKELY
}

private fun substitutionCost(
    a: Char,
    b: Char,
): Int =
    when {
        a == b -> 0
        !a.isLetterOrDigit() && !b.isLetterOrDigit() -> LIKELY
        "$a$b" in NEIGHBOURS -> LIKELY
        else -> UNLIKELY
    }
