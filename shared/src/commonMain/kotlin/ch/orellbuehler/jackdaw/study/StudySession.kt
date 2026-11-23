package ch.orellbuehler.jackdaw.study

import ch.orellbuehler.jackdaw.format.RichText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlin.random.Random
import kotlin.time.Clock

internal class Attempt(
    val itemId: String,
    val facet: Facet,
    val type: ExerciseType,
    val grade: Grade,
    val correct: Boolean,
    val timestamp: Long,
    val practice: Boolean,
    val reschedule: Boolean,
)

internal fun interface AttemptRecorder {
    suspend fun record(attempt: Attempt)
}

class StudySession internal constructor(
    val packId: String,
    val mode: SessionMode,
    private val pack: LoadedPack,
    entries: List<PlanEntry>,
    private val config: ResolvedConfig,
    private val recorder: AttemptRecorder,
    private val clock: Clock,
    private val random: Random,
    private val tolerateTypos: Boolean = false,
) {
    private class Slot(
        val entry: PlanEntry,
    ) {
        var attempts = 0
    }

    private val queue = ArrayDeque(entries.map { Slot(it) })
    private val slots = queue.toList()
    private val total = slots.size
    private val startedAt = now()
    private val lock = Mutex()
    private var current: Slot? = null
    private var built: BuiltExercise? = null
    private var completed = 0
    private var firstCorrect = 0
    private var firstWrong = 0
    private var retries = 0
    private var newFacets = 0

    private val mutableState = MutableStateFlow<SessionState>(SessionState.Finished(summary(abandoned = false)))

    val state: StateFlow<SessionState> get() = mutableState

    init {
        advance()
    }

    fun revealHint() {
        if (!lock.tryLock()) return
        try {
            val question = mutableState.value as? SessionState.Question ?: return
            val hint = current?.entry?.item?.hint ?: return
            if (question.hint == null) mutableState.value = question.copy(hint = RichText.parse(hint))
        } finally {
            lock.unlock()
        }
    }

    fun reveal() {
        if (!lock.tryLock()) return
        try {
            val question = mutableState.value as? SessionState.Question ?: return
            val exercise = built?.takeIf { it.needsSelfGrade } ?: return
            val evaluation = exercise.reveal() ?: return
            mutableState.value = feedback(question, evaluation, willRepeat = false)
        } finally {
            lock.unlock()
        }
    }

    suspend fun submit(answer: Answer) {
        if (!lock.tryLock()) return
        try {
            val question = mutableState.value as? SessionState.Question ?: return
            val slot = current ?: return
            val evaluation = built?.takeIf { !it.needsSelfGrade }?.evaluate(answer) ?: return
            val correct = evaluation.correct == true
            complete(slot, Grade.fromCorrect(correct), correct)
            mutableState.value = feedback(question, evaluation, willRepeat = !correct)
        } finally {
            lock.unlock()
        }
    }

    suspend fun selfGrade(grade: Grade) {
        if (!lock.tryLock()) return
        try {
            val feedback = mutableState.value as? SessionState.Feedback ?: return
            val slot = current ?: return
            if (!feedback.needsSelfGrade) return
            complete(slot, grade, grade != Grade.AGAIN)
            advance()
        } finally {
            lock.unlock()
        }
    }

    fun next() {
        if (!lock.tryLock()) return
        try {
            val feedback = mutableState.value as? SessionState.Feedback ?: return
            if (!feedback.needsSelfGrade) advance()
        } finally {
            lock.unlock()
        }
    }

    fun finish() {
        if (!lock.tryLock()) return
        try {
            if (mutableState.value !is SessionState.Finished) {
                mutableState.value = SessionState.Finished(summary(abandoned = true))
            }
        } finally {
            lock.unlock()
        }
    }

    private fun now(): Long = clock.now().toEpochMilliseconds()

    private suspend fun complete(
        slot: Slot,
        grade: Grade,
        correct: Boolean,
    ) {
        val first = slot.attempts == 0
        if (first) {
            val entry = slot.entry
            recorder.record(
                Attempt(
                    entry.item.id,
                    entry.facet,
                    entry.type,
                    grade,
                    correct,
                    now(),
                    config.mode == SessionMode.PRACTICE,
                    config.reschedule,
                ),
            )
            slot.attempts++
            if (correct) firstCorrect++ else firstWrong++
            if (config.reschedule && entry.schedule.state == LearningState.NEW) newFacets++
        } else {
            slot.attempts++
            retries++
        }
        if (correct) completed++ else queue.add(minOf(queue.size, RETRY_GAP), slot)
    }

    private fun advance() {
        current = null
        built = null
        val slot = queue.removeFirstOrNull()
        if (slot == null) {
            mutableState.value = SessionState.Finished(summary(abandoned = false))
            return
        }
        val entry = slot.entry
        val exercise = ExerciseFactory.build(pack.context, entry.item, entry.facet, entry.type, random, tolerateTypos)
        current = slot
        built = exercise
        mutableState.value =
            SessionState.Question(
                exercise = exercise.exercise,
                position = minOf(completed + 1, total),
                total = total,
                isRetry = slot.attempts > 0,
                hasHint = entry.item.hint != null,
                hint = null,
            )
    }

    private fun feedback(
        question: SessionState.Question,
        evaluation: Evaluation,
        willRepeat: Boolean,
    ): SessionState.Feedback =
        SessionState.Feedback(
            exercise = question.exercise,
            position = question.position,
            total = question.total,
            verdict =
                when (evaluation.correct) {
                    true -> Verdict.CORRECT
                    false -> Verdict.WRONG
                    null -> Verdict.PENDING_SELF_GRADE
                },
            needsSelfGrade = evaluation.correct == null,
            willRepeat = willRepeat,
            correctAnswer = evaluation.correctAnswer,
            userAnswer = evaluation.userAnswer,
            explanation =
                current
                    ?.entry
                    ?.item
                    ?.explanation
                    ?.let { RichText.parse(it) },
            choiceResults = evaluation.choiceResults,
            gapResults = evaluation.gapResults,
            orderResults = evaluation.orderResults,
            keyPoints = evaluation.keyPoints,
            statementIsTrue = evaluation.statementIsTrue,
            typo = evaluation.typo,
        )

    private fun summary(abandoned: Boolean): SessionSummary {
        val answered = slots.count { it.attempts > 0 }
        return SessionSummary(
            mode = mode,
            total = total,
            answered = answered,
            correct = firstCorrect,
            wrong = firstWrong,
            retries = retries,
            newFacets = newFacets,
            accuracy = if (answered == 0) 0.0 else firstCorrect.toDouble() / answered,
            durationMs = now() - startedAt,
            abandoned = abandoned,
        )
    }

    private companion object {
        const val RETRY_GAP = 3
    }
}
