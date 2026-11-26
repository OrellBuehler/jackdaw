package ch.orellbuehler.jackdaw.study

import ch.orellbuehler.jackdaw.format.RichText
import ch.orellbuehler.jackdaw.format.TokenKind

enum class ExerciseType(
    val code: Int,
) {
    FLIP(0),
    CHOICE(1),
    TYPE(2),
    CLOZE(3),
    TRUE_FALSE(4),
    ORDER(5),
    OPEN(6),
    ;

    companion object {
        internal fun fromCode(code: Int): ExerciseType = entries.firstOrNull { it.code == code } ?: FLIP
    }
}

data class ChoiceOption(
    val id: String,
    val text: RichText,
)

data class OrderEntry(
    val id: String,
    val text: RichText,
)

sealed class ClozePiece {
    data class Span(
        val text: String,
        val kind: TokenKind,
    ) : ClozePiece()

    data class Gap(
        val index: Int,
        val hint: String?,
        val keyboard: AnswerKeyboard = AnswerKeyboard.TEXT,
    ) : ClozePiece()
}

sealed class ClozeSegment {
    data class Text(
        val text: RichText,
    ) : ClozeSegment()

    data class Code(
        val lines: List<List<ClozePiece>>,
    ) : ClozeSegment()

    data class Gap(
        val index: Int,
        val hint: String?,
        val keyboard: AnswerKeyboard = AnswerKeyboard.TEXT,
    ) : ClozeSegment()
}

sealed class Exercise {
    abstract val itemId: String
    abstract val facet: Facet
    abstract val type: ExerciseType
}

data class FlipExercise(
    override val itemId: String,
    override val facet: Facet,
    val prompt: RichText,
) : Exercise() {
    override val type: ExerciseType get() = ExerciseType.FLIP
}

data class ChoiceExercise(
    override val itemId: String,
    override val facet: Facet,
    val question: RichText,
    val options: List<ChoiceOption>,
    val multiSelect: Boolean,
) : Exercise() {
    override val type: ExerciseType get() = ExerciseType.CHOICE
}

data class TypeExercise(
    override val itemId: String,
    override val facet: Facet,
    val prompt: RichText,
    val keyboard: AnswerKeyboard = AnswerKeyboard.TEXT,
) : Exercise() {
    override val type: ExerciseType get() = ExerciseType.TYPE
}

data class ClozeExercise(
    override val itemId: String,
    override val facet: Facet,
    val segments: List<ClozeSegment>,
    val gapCount: Int,
) : Exercise() {
    override val type: ExerciseType get() = ExerciseType.CLOZE
}

data class TrueFalseExercise(
    override val itemId: String,
    override val facet: Facet,
    val statement: RichText,
) : Exercise() {
    override val type: ExerciseType get() = ExerciseType.TRUE_FALSE
}

data class OrderExercise(
    override val itemId: String,
    override val facet: Facet,
    val prompt: RichText,
    val entries: List<OrderEntry>,
) : Exercise() {
    override val type: ExerciseType get() = ExerciseType.ORDER
}

data class OpenExercise(
    override val itemId: String,
    override val facet: Facet,
    val question: RichText,
) : Exercise() {
    override val type: ExerciseType get() = ExerciseType.OPEN
}

sealed class Answer

data class ChoiceAnswer(
    val selectedIds: List<String>,
) : Answer()

data class TextAnswer(
    val text: String,
) : Answer()

data class ClozeAnswer(
    val gaps: List<String>,
) : Answer()

data class TrueFalseAnswer(
    val value: Boolean,
) : Answer()

data class OrderAnswer(
    val entryIds: List<String>,
) : Answer()
