package ch.orellbuehler.jackdaw.study

import ch.orellbuehler.jackdaw.FakeClock
import ch.orellbuehler.jackdaw.card
import ch.orellbuehler.jackdaw.packJson
import ch.orellbuehler.jackdaw.readPack
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AnswerKeyboardTest {
    private fun start(
        vararg items: String,
        schedules: Map<FacetKey, Schedule> = emptyMap(),
    ): SessionState.Question {
        val pack = loaded(readPack(packJson(*items, study = """{"exercises":{"card":["type"]}}""")), schedules)
        val session = session(pack, SessionConfig.learn(), random = Random(1))
        return assertIs<SessionState.Question>(session.state.value)
    }

    @Test
    fun classifiesCanonicalAnswers() {
        assertEquals(AnswerKeyboard.INTEGER, answerKeyboard("1989"))
        assertEquals(AnswerKeyboard.INTEGER, answerKeyboard(" 42 "))
        assertEquals(AnswerKeyboard.DECIMAL, answerKeyboard("3.14"))
        assertEquals(AnswerKeyboard.DECIMAL, answerKeyboard("3,14"))
        assertEquals(AnswerKeyboard.DECIMAL, answerKeyboard("1,000.5"))
        assertEquals(AnswerKeyboard.DECIMAL, answerKeyboard("-7"))
        assertEquals(AnswerKeyboard.DECIMAL, answerKeyboard("+7"))
        assertEquals(AnswerKeyboard.DECIMAL, answerKeyboard("-0.5"))
        assertEquals(AnswerKeyboard.DECIMAL, answerKeyboard(".5"))
        assertEquals(AnswerKeyboard.TEXT, answerKeyboard(""))
        assertEquals(AnswerKeyboard.TEXT, answerKeyboard("-"))
        assertEquals(AnswerKeyboard.TEXT, answerKeyboard("."))
        assertEquals(AnswerKeyboard.TEXT, answerKeyboard("+-"))
        assertEquals(AnswerKeyboard.TEXT, answerKeyboard("Paris"))
        assertEquals(AnswerKeyboard.TEXT, answerKeyboard("42 km"))
        assertEquals(AnswerKeyboard.TEXT, answerKeyboard("1 000"))
        assertEquals(AnswerKeyboard.TEXT, answerKeyboard("5%"))
        assertEquals(AnswerKeyboard.TEXT, answerKeyboard("1e5"))
        assertEquals(AnswerKeyboard.TEXT, answerKeyboard("١٢"))
        assertEquals(AnswerKeyboard.TEXT, answerKeyboard("1-2"))
    }

    @Test
    fun textItemUsesTheFirstAnswerOnly() {
        val year = start("""{"id":"t","type":"text","question":"Year?","answers":["1989","nineteen eighty-nine"]}""")
        assertEquals(AnswerKeyboard.INTEGER, assertIs<TypeExercise>(year.exercise).keyboard)
        val words = start("""{"id":"t","type":"text","question":"Year?","answers":["nineteen","1989"]}""")
        assertEquals(AnswerKeyboard.TEXT, assertIs<TypeExercise>(words.exercise).keyboard)
        val decimal = start("""{"id":"t","type":"text","question":"Pi?","answers":["3.14"]}""")
        assertEquals(AnswerKeyboard.DECIMAL, assertIs<TypeExercise>(decimal.exercise).keyboard)
    }

    @Test
    fun typedCardsFollowTheAnswerSide() {
        val forward = start(card("a", "Boiling point of water in C", "100"))
        assertEquals(AnswerKeyboard.INTEGER, assertIs<TypeExercise>(forward.exercise).keyboard)
        val markup = start(card("b", "Absolute zero in C", "**-273.15**"))
        assertEquals(AnswerKeyboard.DECIMAL, assertIs<TypeExercise>(markup.exercise).keyboard)
        val text = start(card("c", "Capital of France", "Paris"))
        assertEquals(AnswerKeyboard.TEXT, assertIs<TypeExercise>(text.exercise).keyboard)
    }

    @Test
    fun reverseFacetLooksAtTheFrontSide() {
        val clock = FakeClock()
        val done = Schedule(LearningState.REVIEW, 2500, 3, clock.millis + 10 * Scheduler.DAY_MS, 3, 0, clock.millis)
        val schedules = mapOf(key("a", Facet.FORWARD) to done, key("a", Facet.REVERSE) to Schedule.fresh())
        val question = start(card("a", "100", "Boiling point of water in C", ""","reversible":true"""), schedules = schedules)
        val exercise = assertIs<TypeExercise>(question.exercise)
        assertEquals(Facet.REVERSE, exercise.facet)
        assertEquals(AnswerKeyboard.INTEGER, exercise.keyboard)
    }

    @Test
    fun clozeGapsAreClassifiedSeparately() {
        val question = start("""{"id":"z","type":"cloze","text":"In {{1989|nineteen eighty-nine}} the {{Wall}} fell, pi is {{3.14}}."}""")
        val gaps = assertIs<ClozeExercise>(question.exercise).segments.filterIsInstance<ClozeSegment.Gap>()
        assertEquals(listOf(AnswerKeyboard.INTEGER, AnswerKeyboard.TEXT, AnswerKeyboard.DECIMAL), gaps.map { it.keyboard })
    }
}
