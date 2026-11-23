package ch.orellbuehler.jackdaw.study

import ch.orellbuehler.jackdaw.format.Figure
import ch.orellbuehler.jackdaw.format.RichText

enum class SessionMode {
    LEARN,
    PRACTICE,
}

enum class PracticeFilter {
    ALL,
    FLASHCARDS,
    QUIZ,
    TYPING,
}

data class SessionConfig(
    val mode: SessionMode,
    val sectionId: String?,
    val size: Int,
    val newLimit: Int,
    val filter: PracticeFilter,
    val rescheduleInPractice: Boolean,
) {
    constructor(mode: SessionMode) : this(mode, null, 0, -1, PracticeFilter.ALL, false)

    fun withSection(sectionId: String?): SessionConfig = copy(sectionId = sectionId)

    fun withSize(size: Int): SessionConfig = copy(size = size)

    fun withNewLimit(newLimit: Int): SessionConfig = copy(newLimit = newLimit)

    fun withFilter(filter: PracticeFilter): SessionConfig = copy(filter = filter)

    fun withReschedule(reschedule: Boolean): SessionConfig = copy(rescheduleInPractice = reschedule)

    companion object {
        fun learn(): SessionConfig = SessionConfig(SessionMode.LEARN)

        fun practice(): SessionConfig = SessionConfig(SessionMode.PRACTICE)
    }
}

data class SessionPreview(
    val dueCount: Int,
    val newCount: Int,
    val totalCount: Int,
)

enum class Verdict {
    CORRECT,
    WRONG,
    PENDING_SELF_GRADE,
}

data class ChoiceResult(
    val id: String,
    val text: RichText,
    val correct: Boolean,
    val selected: Boolean,
    val explanation: RichText?,
)

data class GapResult(
    val index: Int,
    val given: String,
    val canonical: String,
    val correct: Boolean,
    val typo: Boolean = false,
)

data class OrderResult(
    val position: Int,
    val arranged: RichText,
    val expected: RichText,
    val correct: Boolean,
)

data class SessionSummary(
    val mode: SessionMode,
    val total: Int,
    val answered: Int,
    val correct: Int,
    val wrong: Int,
    val retries: Int,
    val newFacets: Int,
    val accuracy: Double,
    val durationMs: Long,
    val abandoned: Boolean,
)

sealed class SessionState {
    data class Question(
        val exercise: Exercise,
        val position: Int,
        val total: Int,
        val isRetry: Boolean,
        val hasHint: Boolean,
        val hint: RichText?,
        val figure: Figure? = null,
    ) : SessionState()

    data class Feedback(
        val exercise: Exercise,
        val position: Int,
        val total: Int,
        val verdict: Verdict,
        val needsSelfGrade: Boolean,
        val willRepeat: Boolean,
        val correctAnswer: RichText,
        val userAnswer: String,
        val explanation: RichText?,
        val choiceResults: List<ChoiceResult>,
        val gapResults: List<GapResult>,
        val orderResults: List<OrderResult>,
        val keyPoints: List<RichText>,
        val statementIsTrue: Boolean,
        val typo: Boolean = false,
        val figure: Figure? = null,
    ) : SessionState() {
        val correct: Boolean get() = verdict == Verdict.CORRECT

        fun suggestedGrade(tickedKeyPoints: Int): Grade {
            if (keyPoints.isEmpty()) return Grade.GOOD
            val ratio = tickedKeyPoints.toDouble() / keyPoints.size
            return when {
                ratio >= 0.75 -> Grade.GOOD
                ratio >= 0.5 -> Grade.HARD
                else -> Grade.AGAIN
            }
        }
    }

    data class Finished(
        val summary: SessionSummary,
    ) : SessionState()
}
