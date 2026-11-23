package ch.orellbuehler.jackdaw.study

import ch.orellbuehler.jackdaw.format.AnswerMatch
import ch.orellbuehler.jackdaw.format.CardItem
import ch.orellbuehler.jackdaw.format.CardKind
import ch.orellbuehler.jackdaw.format.ChoiceItem
import ch.orellbuehler.jackdaw.format.ClozeItem
import ch.orellbuehler.jackdaw.format.ClozePart
import ch.orellbuehler.jackdaw.format.Item
import ch.orellbuehler.jackdaw.format.OpenItem
import ch.orellbuehler.jackdaw.format.OrderItem
import ch.orellbuehler.jackdaw.format.RichText
import ch.orellbuehler.jackdaw.format.Study
import ch.orellbuehler.jackdaw.format.TextItem
import ch.orellbuehler.jackdaw.format.TextSpan
import ch.orellbuehler.jackdaw.format.TrueFalseItem
import ch.orellbuehler.jackdaw.format.matchAnswer
import ch.orellbuehler.jackdaw.format.normalize
import ch.orellbuehler.jackdaw.format.parseCloze
import kotlin.random.Random

internal class PackContext(
    val study: Study,
    val items: List<Item>,
) {
    val cards: List<CardItem> = items.filterIsInstance<CardItem>()

    val typedAnswers: Set<String> by lazy {
        val raw = ArrayList<String>()
        for (item in items) {
            when (item) {
                is CardItem -> {
                    raw += RichText.parse(item.front).plain
                    raw += RichText.parse(item.back).plain
                    raw += item.alternatives
                    raw += item.frontAlternatives
                }
                is TextItem -> raw += item.answers
                is ClozeItem -> parseCloze(item.text).filterIsInstance<ClozePart.Gap>().forEach { raw += it.answers }
                else -> Unit
            }
        }
        raw.mapTo(HashSet()) { normalize(it, caseSensitive = false, ignoreDiacritics = study.ignoreDiacritics) }
    }

    fun facetsOf(item: Item): List<Facet> =
        if (item is CardItem &&
            item.reversible
        ) {
            listOf(Facet.FORWARD, Facet.REVERSE)
        } else {
            listOf(Facet.FORWARD)
        }
}

internal class Evaluation(
    val correct: Boolean?,
    val correctAnswer: RichText,
    val userAnswer: String,
    val choiceResults: List<ChoiceResult> = emptyList(),
    val gapResults: List<GapResult> = emptyList(),
    val orderResults: List<OrderResult> = emptyList(),
    val keyPoints: List<RichText> = emptyList(),
    val statementIsTrue: Boolean = false,
    val typo: Boolean = false,
)

internal interface BuiltExercise {
    val exercise: Exercise
    val needsSelfGrade: Boolean

    fun evaluate(answer: Answer): Evaluation?

    fun reveal(): Evaluation?
}

internal object ExerciseFactory {
    private const val TYPE_FROM_INTERVAL_DAYS = 7
    private const val CHOICE_WRONG_OPTIONS = 3
    private const val MIN_WRONG_OPTIONS = 2

    fun fixedType(item: Item): ExerciseType? =
        when (item) {
            is CardItem -> null
            is ChoiceItem -> ExerciseType.CHOICE
            is TextItem -> ExerciseType.TYPE
            is ClozeItem -> ExerciseType.CLOZE
            is TrueFalseItem -> ExerciseType.TRUE_FALSE
            is OrderItem -> ExerciseType.ORDER
            is OpenItem -> ExerciseType.OPEN
        }

    fun accepts(
        filter: PracticeFilter,
        type: ExerciseType,
    ): Boolean =
        when (filter) {
            PracticeFilter.ALL -> true
            PracticeFilter.FLASHCARDS -> type == ExerciseType.FLIP || type == ExerciseType.OPEN
            PracticeFilter.QUIZ -> type == ExerciseType.CHOICE || type == ExerciseType.TRUE_FALSE || type == ExerciseType.ORDER
            PracticeFilter.TYPING -> type == ExerciseType.TYPE || type == ExerciseType.CLOZE
        }

    fun chooseType(
        context: PackContext,
        item: Item,
        facet: Facet,
        schedule: Schedule,
        filter: PracticeFilter,
    ): ExerciseType? {
        if (item !is CardItem) return fixedType(item)?.takeIf { accepts(filter, it) }
        val ladder =
            when {
                schedule.state == LearningState.NEW -> listOf(ExerciseType.FLIP, ExerciseType.CHOICE, ExerciseType.TYPE)
                schedule.state == LearningState.LEARNING || schedule.intervalDays < TYPE_FROM_INTERVAL_DAYS ->
                    listOf(ExerciseType.CHOICE, ExerciseType.FLIP, ExerciseType.TYPE)
                else -> listOf(ExerciseType.TYPE, ExerciseType.CHOICE, ExerciseType.FLIP)
            }
        val candidates = ladder.filter { cardKind(it) in item.exercises && accepts(filter, it) }
        candidates.firstOrNull { feasible(context, item, facet, it) }?.let { return it }
        val anyFeasible = item.exercises.any { kind -> feasible(context, item, facet, exerciseOf(kind)) }
        return if (!anyFeasible && accepts(filter, ExerciseType.FLIP)) ExerciseType.FLIP else null
    }

    private fun cardKind(type: ExerciseType): CardKind =
        when (type) {
            ExerciseType.CHOICE -> CardKind.CHOICE
            ExerciseType.TYPE -> CardKind.TYPE
            else -> CardKind.FLIP
        }

    private fun exerciseOf(kind: CardKind): ExerciseType =
        when (kind) {
            CardKind.FLIP -> ExerciseType.FLIP
            CardKind.CHOICE -> ExerciseType.CHOICE
            CardKind.TYPE -> ExerciseType.TYPE
        }

    private fun feasible(
        context: PackContext,
        card: CardItem,
        facet: Facet,
        type: ExerciseType,
    ): Boolean =
        when (type) {
            ExerciseType.CHOICE -> wrongOptions(context, card, facet, null).size >= MIN_WRONG_OPTIONS
            ExerciseType.TYPE ->
                acceptedAnswers(
                    card,
                    facet,
                ).any { normalize(it, card.caseSensitive, context.study.ignoreDiacritics).isNotEmpty() }
            else -> true
        }

    private fun side(
        card: CardItem,
        facet: Facet,
        answerSide: Boolean,
    ): String = if ((facet == Facet.FORWARD) == answerSide) card.back else card.front

    private fun answerSide(
        card: CardItem,
        facet: Facet,
    ): String = side(card, facet, true)

    private fun promptSide(
        card: CardItem,
        facet: Facet,
    ): String = side(card, facet, false)

    private fun acceptedAnswers(
        card: CardItem,
        facet: Facet,
    ): List<String> =
        listOf(RichText.parse(answerSide(card, facet)).plain) +
            if (facet == Facet.FORWARD) card.alternatives else card.frontAlternatives

    private fun wrongOptions(
        context: PackContext,
        card: CardItem,
        facet: Facet,
        random: Random?,
    ): List<String> {
        val ci = card.caseSensitive
        val di = context.study.ignoreDiacritics
        val taken = HashSet<String>()
        acceptedAnswers(card, facet).forEach { taken += normalize(it, ci, di) }
        val result = ArrayList<String>()

        fun tryAdd(raw: String) {
            val key = normalize(RichText.parse(raw).plain, ci, di)
            if (key.isNotEmpty() && taken.add(key)) result += raw
        }

        fun ordered(list: List<String>): List<String> = if (random != null) list.shuffled(random) else list

        for (distractor in ordered(if (facet == Facet.FORWARD) card.distractors else card.frontDistractors)) {
            if (result.size == CHOICE_WRONG_OPTIONS) break
            tryAdd(distractor)
        }
        if (result.size < CHOICE_WRONG_OPTIONS) {
            val others = context.cards.filter { it.id != card.id }
            val groups = listOf(others.filter { it.section == card.section }, others.filter { it.section != card.section })
            for (group in groups) {
                for (answer in ordered(group.map { answerSide(it, facet) })) {
                    if (result.size == CHOICE_WRONG_OPTIONS) break
                    tryAdd(answer)
                }
            }
        }
        return result
    }

    fun build(
        context: PackContext,
        item: Item,
        facet: Facet,
        type: ExerciseType,
        random: Random,
        tolerateTypos: Boolean = false,
    ): BuiltExercise =
        when (item) {
            is CardItem -> buildCard(context, item, facet, type, random, tolerateTypos)
            is ChoiceItem -> buildChoiceItem(item, facet, random)
            is TextItem ->
                BuiltType(
                    TypeExercise(item.id, facet, RichText.parse(item.question)),
                    item.answers,
                    RichText.fromPlain(item.answers.first()),
                    item.caseSensitive,
                    context,
                    tolerateTypos,
                )
            is ClozeItem -> buildCloze(context, item, facet, tolerateTypos)
            is TrueFalseItem -> BuiltTrueFalse(TrueFalseExercise(item.id, facet, RichText.parse(item.statement)), item.answer)
            is OrderItem -> buildOrder(item, facet, random)
            is OpenItem ->
                BuiltOpen(
                    OpenExercise(item.id, facet, RichText.parse(item.question)),
                    item.answer?.let { RichText.parse(it) } ?: RichText(emptyList()),
                    item.keyPoints.map { RichText.parse(it) },
                )
        }

    private fun buildCard(
        context: PackContext,
        card: CardItem,
        facet: Facet,
        type: ExerciseType,
        random: Random,
        tolerateTypos: Boolean,
    ): BuiltExercise {
        val prompt = RichText.parse(promptSide(card, facet))
        val answer = RichText.parse(answerSide(card, facet))
        if (type == ExerciseType.CHOICE) {
            val wrong = wrongOptions(context, card, facet, random)
            if (wrong.size >= MIN_WRONG_OPTIONS) {
                val all = (listOf(answerSide(card, facet)) + wrong).mapIndexed { index, raw -> (index == 0) to raw }.shuffled(random)
                val options = all.mapIndexed { index, (_, raw) -> ChoiceOption(index.toString(), RichText.parse(raw)) }
                val correctIds = all.mapIndexedNotNull { index, (isCorrect, _) -> if (isCorrect) index.toString() else null }.toSet()
                return BuiltChoice(
                    ChoiceExercise(card.id, facet, prompt, options, multiSelect = false),
                    correctIds,
                    List(options.size) { null },
                )
            }
        }
        if (type == ExerciseType.TYPE) {
            return BuiltType(
                TypeExercise(card.id, facet, prompt),
                acceptedAnswers(card, facet),
                answer,
                card.caseSensitive,
                context,
                tolerateTypos,
            )
        }
        return BuiltFlip(FlipExercise(card.id, facet, prompt), answer)
    }

    private fun buildChoiceItem(
        item: ChoiceItem,
        facet: Facet,
        random: Random,
    ): BuiltExercise {
        val ordered = if (item.shuffle) item.options.shuffled(random) else item.options
        val options = ordered.mapIndexed { index, option -> ChoiceOption(index.toString(), RichText.parse(option.text)) }
        val correctIds = ordered.mapIndexedNotNull { index, option -> if (option.correct) index.toString() else null }.toSet()
        return BuiltChoice(
            ChoiceExercise(item.id, facet, RichText.parse(item.question), options, multiSelect = correctIds.size > 1),
            correctIds,
            ordered.map { option -> option.explanation?.let { RichText.parse(it) } },
        )
    }

    private fun buildCloze(
        context: PackContext,
        item: ClozeItem,
        facet: Facet,
        tolerateTypos: Boolean,
    ): BuiltExercise {
        val parts = parseCloze(item.text)
        val gaps = parts.filterIsInstance<ClozePart.Gap>()
        var gapIndex = 0
        val segments =
            parts.map { part ->
                when (part) {
                    is ClozePart.Text -> ClozeSegment.Text(RichText.parse(part.raw))
                    is ClozePart.Gap -> ClozeSegment.Gap(gapIndex++, part.hint)
                }
            }
        return BuiltCloze(
            ClozeExercise(item.id, facet, segments, gaps.size),
            parts,
            item.caseSensitive,
            context,
            tolerateTypos,
        )
    }

    private fun buildOrder(
        item: OrderItem,
        facet: Facet,
        random: Random,
    ): BuiltExercise {
        val indexes = item.sequence.indices.toList()
        val distinct = item.sequence.toSet().size == item.sequence.size
        var shuffled = indexes.shuffled(random)
        var attempts = 0
        while (distinct && shuffled == indexes && attempts < 20) {
            shuffled = indexes.shuffled(random)
            attempts++
        }
        if (distinct && shuffled == indexes) shuffled = indexes.drop(1) + indexes.first()
        val entries = shuffled.map { OrderEntry(it.toString(), RichText.parse(item.sequence[it])) }
        return BuiltOrder(OrderExercise(item.id, facet, RichText.parse(item.prompt), entries), item.sequence)
    }
}

internal fun joinRich(
    parts: List<RichText>,
    separator: String,
): RichText {
    val spans = ArrayList<TextSpan>()
    parts.forEachIndexed { index, part ->
        if (index > 0) spans += TextSpan(separator, bold = false, italic = false, code = false)
        spans += part.spans
    }
    return RichText(spans)
}

private class BuiltFlip(
    override val exercise: FlipExercise,
    private val answer: RichText,
) : BuiltExercise {
    override val needsSelfGrade: Boolean get() = true

    override fun evaluate(answer: Answer): Evaluation? = null

    override fun reveal(): Evaluation = Evaluation(null, answer, "")
}

private class BuiltOpen(
    override val exercise: OpenExercise,
    private val modelAnswer: RichText,
    private val keyPoints: List<RichText>,
) : BuiltExercise {
    override val needsSelfGrade: Boolean get() = true

    override fun evaluate(answer: Answer): Evaluation? = null

    override fun reveal(): Evaluation = Evaluation(null, modelAnswer, "", keyPoints = keyPoints)
}

private class BuiltChoice(
    override val exercise: ChoiceExercise,
    private val correctIds: Set<String>,
    private val explanations: List<RichText?>,
) : BuiltExercise {
    override val needsSelfGrade: Boolean get() = false

    override fun reveal(): Evaluation? = null

    override fun evaluate(answer: Answer): Evaluation? {
        if (answer !is ChoiceAnswer) return null
        val selected = answer.selectedIds.toSet()
        val results =
            exercise.options.mapIndexed { index, option ->
                val isCorrect = option.id in correctIds
                val isSelected = option.id in selected
                ChoiceResult(option.id, option.text, isCorrect, isSelected, if (isCorrect || isSelected) explanations[index] else null)
            }
        val correctTexts = exercise.options.filter { it.id in correctIds }.map { it.text }
        val chosenTexts = exercise.options.filter { it.id in selected }.joinToString(", ") { it.text.plain }
        return Evaluation(selected == correctIds, joinRich(correctTexts, "\n"), chosenTexts, choiceResults = results)
    }
}

private class BuiltType(
    override val exercise: TypeExercise,
    private val accepted: List<String>,
    private val canonical: RichText,
    private val caseSensitive: Boolean,
    private val context: PackContext,
    private val tolerateTypos: Boolean,
) : BuiltExercise {
    override val needsSelfGrade: Boolean get() = false

    override fun reveal(): Evaluation? = null

    override fun evaluate(answer: Answer): Evaluation? {
        if (answer !is TextAnswer) return null
        val match = matchAnswer(answer.text, accepted, caseSensitive, context.study.ignoreDiacritics, tolerateTypos, context.typedAnswers)
        return Evaluation(match != AnswerMatch.NONE, canonical, answer.text, typo = match == AnswerMatch.TYPO)
    }
}

private class BuiltCloze(
    override val exercise: ClozeExercise,
    private val parts: List<ClozePart>,
    private val caseSensitive: Boolean,
    private val context: PackContext,
    private val tolerateTypos: Boolean,
) : BuiltExercise {
    override val needsSelfGrade: Boolean get() = false

    override fun reveal(): Evaluation? = null

    override fun evaluate(answer: Answer): Evaluation? {
        if (answer !is ClozeAnswer) return null
        val gaps = parts.filterIsInstance<ClozePart.Gap>()
        val results =
            gaps.mapIndexed { index, gap ->
                val given = answer.gaps.getOrElse(index) { "" }
                val match =
                    matchAnswer(given, gap.answers, caseSensitive, context.study.ignoreDiacritics, tolerateTypos, context.typedAnswers)
                GapResult(index, given, gap.answers.first(), match != AnswerMatch.NONE, match == AnswerMatch.TYPO)
            }
        val spans = ArrayList<TextSpan>()
        var gapIndex = 0
        for (part in parts) {
            when (part) {
                is ClozePart.Text -> spans += RichText.parse(part.raw).spans
                is ClozePart.Gap -> spans += TextSpan(results[gapIndex++].canonical, bold = true, italic = false, code = false)
            }
        }
        return Evaluation(
            results.all { it.correct },
            RichText(spans),
            results.joinToString(", ") { it.given },
            gapResults = results,
            typo = results.any { it.typo },
        )
    }
}

private class BuiltTrueFalse(
    override val exercise: TrueFalseExercise,
    private val statementIsTrue: Boolean,
) : BuiltExercise {
    override val needsSelfGrade: Boolean get() = false

    override fun reveal(): Evaluation? = null

    override fun evaluate(answer: Answer): Evaluation? {
        if (answer !is TrueFalseAnswer) return null
        return Evaluation(
            answer.value == statementIsTrue,
            RichText(emptyList()),
            answer.value.toString(),
            statementIsTrue = statementIsTrue,
        )
    }
}

private class BuiltOrder(
    override val exercise: OrderExercise,
    private val sequence: List<String>,
) : BuiltExercise {
    override val needsSelfGrade: Boolean get() = false

    override fun reveal(): Evaluation? = null

    override fun evaluate(answer: Answer): Evaluation? {
        if (answer !is OrderAnswer) return null
        val byId = exercise.entries.associateBy { it.id }
        val arranged = answer.entryIds.mapNotNull { byId[it] }
        val valid = arranged.size == sequence.size && answer.entryIds.toSet().size == sequence.size
        val results =
            sequence.mapIndexed { position, expected ->
                val entry = if (valid) arranged[position] else null
                val arrangedText = entry?.text ?: RichText(emptyList())
                val expectedText = RichText.parse(expected)
                OrderResult(position + 1, arrangedText, expectedText, entry != null && entry.text == expectedText)
            }
        return Evaluation(
            valid && results.all { it.correct },
            joinRich(sequence.map { RichText.parse(it) }, "\n"),
            arranged.joinToString(" > ") { it.text.plain },
            orderResults = results,
        )
    }
}
