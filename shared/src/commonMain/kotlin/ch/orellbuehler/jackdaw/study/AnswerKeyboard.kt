package ch.orellbuehler.jackdaw.study

enum class AnswerKeyboard {
    TEXT,
    INTEGER,
    DECIMAL,
}

internal fun answerKeyboard(canonical: String): AnswerKeyboard {
    val answer = canonical.trim()
    if (answer.isEmpty()) return AnswerKeyboard.TEXT
    if (answer.all { it in '0'..'9' }) return AnswerKeyboard.INTEGER
    val unsigned = if (answer[0] == '-' || answer[0] == '+') answer.substring(1) else answer
    val numeric = unsigned.isNotEmpty() && unsigned.all { it in '0'..'9' || it == '.' || it == ',' }
    return if (numeric && unsigned.any { it in '0'..'9' }) AnswerKeyboard.DECIMAL else AnswerKeyboard.TEXT
}
