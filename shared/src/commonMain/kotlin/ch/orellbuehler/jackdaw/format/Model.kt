package ch.orellbuehler.jackdaw.format

internal enum class StudyOrder(
    val code: String,
) {
    SEQUENTIAL("sequential"),
    RANDOM("random"),
}

internal enum class CardKind(
    val code: String,
) {
    FLIP("flip"),
    CHOICE("choice"),
    TYPE("type"),
    ;

    companion object {
        fun fromCode(code: String): CardKind? = entries.firstOrNull { it.code == code }
    }
}

internal data class Study(
    val order: StudyOrder,
    val newPerSession: Int,
    val reversible: Boolean,
    val ignoreDiacritics: Boolean,
    val cardExercises: List<CardKind>,
) {
    companion object {
        const val DEFAULT_NEW_PER_SESSION = 10
        val DEFAULT_CARD_EXERCISES = listOf(CardKind.FLIP, CardKind.CHOICE, CardKind.TYPE)
        val DEFAULT = Study(StudyOrder.SEQUENTIAL, DEFAULT_NEW_PER_SESSION, false, false, DEFAULT_CARD_EXERCISES)
    }
}

internal data class SectionDef(
    val id: String,
    val title: String,
    val description: String?,
)

internal data class OptionDef(
    val text: String,
    val correct: Boolean,
    val explanation: String?,
)

internal sealed class Item {
    abstract val id: String
    abstract val type: ItemType
    abstract val section: String?
    abstract val tags: List<String>
    abstract val hint: String?
    abstract val explanation: String?
    abstract val figure: Figure?
    abstract val answerFigure: Figure?
}

internal data class CardItem(
    override val id: String,
    override val section: String?,
    override val tags: List<String>,
    override val hint: String?,
    override val explanation: String?,
    val front: String,
    val back: String,
    val reversible: Boolean,
    val alternatives: List<String>,
    val frontAlternatives: List<String>,
    val distractors: List<String>,
    val frontDistractors: List<String>,
    val exercises: List<CardKind>,
    val caseSensitive: Boolean,
    override val figure: Figure? = null,
    override val answerFigure: Figure? = null,
) : Item() {
    override val type: ItemType get() = ItemType.CARD
}

internal data class ChoiceItem(
    override val id: String,
    override val section: String?,
    override val tags: List<String>,
    override val hint: String?,
    override val explanation: String?,
    val question: String,
    val options: List<OptionDef>,
    val shuffle: Boolean,
    override val figure: Figure? = null,
    override val answerFigure: Figure? = null,
) : Item() {
    override val type: ItemType get() = ItemType.CHOICE
}

internal data class TextItem(
    override val id: String,
    override val section: String?,
    override val tags: List<String>,
    override val hint: String?,
    override val explanation: String?,
    val question: String,
    val answers: List<String>,
    val caseSensitive: Boolean,
    override val figure: Figure? = null,
    override val answerFigure: Figure? = null,
) : Item() {
    override val type: ItemType get() = ItemType.TEXT
}

internal data class ClozeItem(
    override val id: String,
    override val section: String?,
    override val tags: List<String>,
    override val hint: String?,
    override val explanation: String?,
    val text: String,
    val caseSensitive: Boolean,
    override val figure: Figure? = null,
    override val answerFigure: Figure? = null,
) : Item() {
    override val type: ItemType get() = ItemType.CLOZE
}

internal data class TrueFalseItem(
    override val id: String,
    override val section: String?,
    override val tags: List<String>,
    override val hint: String?,
    override val explanation: String?,
    val statement: String,
    val answer: Boolean,
    override val figure: Figure? = null,
    override val answerFigure: Figure? = null,
) : Item() {
    override val type: ItemType get() = ItemType.TRUE_FALSE
}

internal data class OrderItem(
    override val id: String,
    override val section: String?,
    override val tags: List<String>,
    override val hint: String?,
    override val explanation: String?,
    val prompt: String,
    val sequence: List<String>,
    override val figure: Figure? = null,
    override val answerFigure: Figure? = null,
) : Item() {
    override val type: ItemType get() = ItemType.ORDER
}

internal data class OpenItem(
    override val id: String,
    override val section: String?,
    override val tags: List<String>,
    override val hint: String?,
    override val explanation: String?,
    val question: String,
    val answer: String?,
    val keyPoints: List<String>,
    override val figure: Figure? = null,
    override val answerFigure: Figure? = null,
) : Item() {
    override val type: ItemType get() = ItemType.OPEN
}

internal data class Pack(
    val id: String,
    val title: String,
    val description: String?,
    val language: String?,
    val author: String?,
    val license: String?,
    val source: String?,
    val tags: List<String>,
    val icon: String?,
    val color: String?,
    val revision: Int?,
    val created: String?,
    val updated: String?,
    val sections: List<SectionDef>,
    val study: Study,
    val items: List<Item>,
)

internal fun isValidId(
    id: String,
    maxLength: Int,
): Boolean {
    if (id.isEmpty() || id.length > maxLength) return false

    fun alnum(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9'
    return alnum(id[0]) && id.all { alnum(it) || it == '.' || it == '_' || it == '-' }
}
