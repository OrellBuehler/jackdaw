package ch.orellbuehler.jackdaw.study

import ch.orellbuehler.jackdaw.FakeClock
import ch.orellbuehler.jackdaw.card
import ch.orellbuehler.jackdaw.packJson
import ch.orellbuehler.jackdaw.readPack
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudySessionTest {
    private val recorder = RecordingRecorder()
    private val clock = FakeClock()

    private fun start(
        vararg items: String,
        study: String? = null,
        config: SessionConfig = SessionConfig.learn(),
        random: Random = Random(1),
        schedules: Map<FacetKey, Schedule> = emptyMap(),
        tolerateTypos: Boolean = false,
    ): StudySession {
        val pack = loaded(readPack(packJson(*items, study = study)), schedules)
        return session(pack, config, recorder, clock, random, tolerateTypos)
    }

    private fun StudySession.question() = assertIs<SessionState.Question>(state.value)

    private fun StudySession.feedback() = assertIs<SessionState.Feedback>(state.value)

    private fun StudySession.finished() = assertIs<SessionState.Finished>(state.value).summary

    private val flipCards = arrayOf(card("a", "fa", "ba"), card("b", "fb", "bb"), card("c", "fc", "bc"))
    private val flipStudy = """{"exercises":{"card":["flip"]}}"""

    @Test
    fun configFactoriesAndCopies() {
        assertEquals(SessionConfig.learn(), SessionConfig(SessionMode.LEARN))
        val config =
            SessionConfig
                .practice()
                .withSection(
                    "a",
                ).withSize(5)
                .withNewLimit(2)
                .withFilter(PracticeFilter.QUIZ)
                .withReschedule(true)
        assertEquals(SessionConfig(SessionMode.PRACTICE, "a", 5, 2, PracticeFilter.QUIZ, true), config)
        assertEquals(0, SessionConfig.learn().size)
        assertEquals(-1, SessionConfig.learn().newLimit)
    }

    @Test
    fun emptyPlanFinishesImmediately() {
        val session = start(*flipCards, config = SessionConfig.learn().withNewLimit(0))
        val summary = session.finished()
        assertEquals(0, summary.total)
        assertEquals(0, summary.answered)
        assertEquals(0.0, summary.accuracy)
        assertFalse(summary.abandoned)
    }

    @Test
    fun flipFlowRevealsThenSelfGradesEveryCard() =
        runTest {
            val session = start(*flipCards, study = flipStudy)
            repeat(3) { index ->
                val question = session.question()
                assertEquals(index + 1, question.position)
                assertEquals(3, question.total)
                assertFalse(question.isRetry)
                val exercise = assertIs<FlipExercise>(question.exercise)
                assertEquals("f${"abc"[index]}", exercise.prompt.plain)
                session.reveal()
                val feedback = session.feedback()
                assertTrue(feedback.needsSelfGrade)
                assertEquals(Verdict.PENDING_SELF_GRADE, feedback.verdict)
                assertEquals("b${"abc"[index]}", feedback.correctAnswer.plain)
                session.selfGrade(Grade.GOOD)
            }
            val summary = session.finished()
            assertEquals(3, summary.total)
            assertEquals(3, summary.answered)
            assertEquals(3, summary.correct)
            assertEquals(0, summary.wrong)
            assertEquals(3, summary.newFacets)
            assertEquals(1.0, summary.accuracy)
            assertEquals(listOf("a", "b", "c"), recorder.attempts.map { it.itemId })
            assertTrue(recorder.attempts.all { it.grade == Grade.GOOD && it.correct && it.type == ExerciseType.FLIP && it.reschedule })
            assertTrue(recorder.attempts.none { it.practice })
        }

    @Test
    fun failedFlipIsRequeuedAndOnlyTheFirstAttemptCounts() =
        runTest {
            val session = start(*flipCards, study = flipStudy)
            session.reveal()
            session.selfGrade(Grade.AGAIN)
            assertEquals("fb", (session.question().exercise as FlipExercise).prompt.plain)
            assertEquals(1, session.question().position)
            session.reveal()
            session.selfGrade(Grade.GOOD)
            session.reveal()
            session.selfGrade(Grade.GOOD)
            val retry = session.question()
            assertTrue(retry.isRetry)
            assertEquals("fa", (retry.exercise as FlipExercise).prompt.plain)
            assertEquals(3, retry.position)
            session.reveal()
            session.selfGrade(Grade.EASY)
            val summary = session.finished()
            assertEquals(3, summary.total)
            assertEquals(2, summary.correct)
            assertEquals(1, summary.wrong)
            assertEquals(1, summary.retries)
            assertEquals(listOf("a", "b", "c"), recorder.attempts.map { it.itemId })
            assertEquals(Grade.AGAIN, recorder.attempts.first().grade)
            assertFalse(recorder.attempts.first().correct)
            assertEquals(3, recorder.attempts.size)
        }

    @Test
    fun hardCountsAsCorrectButEasyAndHardAreRecorded() =
        runTest {
            val session = start(*flipCards, study = flipStudy)
            session.reveal()
            session.selfGrade(Grade.HARD)
            session.reveal()
            session.selfGrade(Grade.EASY)
            session.reveal()
            session.selfGrade(Grade.AGAIN)
            assertEquals(listOf(Grade.HARD, Grade.EASY, Grade.AGAIN), recorder.attempts.map { it.grade })
            assertEquals(listOf(true, true, false), recorder.attempts.map { it.correct })
            assertTrue(session.question().isRetry)
        }

    @Test
    fun singleChoiceItemFlow() =
        runTest {
            val session =
                start(
                    """{"id":"q","type":"choice","question":"Capital of France?","shuffle":false,
                    "options":[{"text":"Berlin","explanation":"Germany."},{"text":"Paris","correct":true,"explanation":"Right."},{"text":"Rome"}]}""",
                    """{"id":"q2","type":"truefalse","statement":"s","answer":true}""",
                )
            val question = session.question()
            val exercise = assertIs<ChoiceExercise>(question.exercise)
            assertFalse(exercise.multiSelect)
            assertEquals(listOf("Berlin", "Paris", "Rome"), exercise.options.map { it.text.plain })
            session.submit(ChoiceAnswer(listOf("0")))
            val wrong = session.feedback()
            assertEquals(Verdict.WRONG, wrong.verdict)
            assertTrue(wrong.willRepeat)
            assertFalse(wrong.needsSelfGrade)
            assertEquals("Paris", wrong.correctAnswer.plain)
            assertEquals("Berlin", wrong.userAnswer)
            assertEquals(listOf(true, true, false), wrong.choiceResults.map { it.correct || it.selected })
            assertEquals("Germany.", wrong.choiceResults[0].explanation?.plain)
            assertEquals("Right.", wrong.choiceResults[1].explanation?.plain)
            assertNull(wrong.choiceResults[2].explanation)
            session.next()
            session.submit(TrueFalseAnswer(true))
            session.next()
            val retry = session.question()
            assertTrue(retry.isRetry)
            session.submit(ChoiceAnswer(listOf("1")))
            assertEquals(Verdict.CORRECT, session.feedback().verdict)
            assertFalse(session.feedback().willRepeat)
            session.next()
            val summary = session.finished()
            assertEquals(2, summary.total)
            assertEquals(1, summary.correct)
            assertEquals(1, summary.wrong)
            assertEquals(1, summary.retries)
            assertEquals(2, recorder.attempts.size)
            assertEquals(listOf(Grade.AGAIN, Grade.GOOD), recorder.attempts.map { it.grade })
        }

    @Test
    fun multiSelectNeedsTheExactSet() =
        runTest {
            val item =
                """{"id":"m","type":"choice","question":"q","shuffle":false,
                "options":[{"text":"a","correct":true},{"text":"b"},{"text":"c","correct":true}]}"""
            for ((selection, expected) in listOf(
                listOf("0") to Verdict.WRONG,
                listOf("0", "1", "2") to Verdict.WRONG,
                listOf("0", "1") to Verdict.WRONG,
                emptyList<String>() to Verdict.WRONG,
                listOf("2", "0") to Verdict.CORRECT,
                listOf("0", "2", "2") to Verdict.CORRECT,
            )) {
                val session = start(item)
                assertTrue(assertIs<ChoiceExercise>(session.question().exercise).multiSelect)
                session.submit(ChoiceAnswer(selection))
                assertEquals(expected, session.feedback().verdict, selection.toString())
            }
        }

    @Test
    fun choiceItemsShuffleWithTheInjectedRandom() {
        val item =
            """{"id":"m","type":"choice","question":"q",
            "options":[{"text":"a","correct":true},{"text":"b"},{"text":"c"},{"text":"d"},{"text":"e"}]}"""

        fun order(seed: Int) = (start(item, random = Random(seed)).question().exercise as ChoiceExercise).options.map { it.text.plain }
        assertEquals(order(1), order(1))
        assertTrue((1..10).map { order(it) }.toSet().size > 1)
        assertEquals(setOf("a", "b", "c", "d", "e"), order(4).toSet())
    }

    @Test
    fun cardsBuildMultipleChoiceWithDistractors() =
        runTest {
            val items =
                arrayOf(
                    card("fr", "France", "Paris", ""","distractors":["Lyon","Marseille","Nice","Lille"]"""),
                    card("de", "Germany", "Berlin"),
                    card("it", "Italy", "Rome"),
                    card("es", "Spain", "Madrid"),
                )
            val session = start(*items, study = """{"exercises":{"card":["choice"]}}""", random = Random(2))
            val exercise = assertIs<ChoiceExercise>(session.question().exercise)
            assertEquals("France", exercise.question.plain)
            assertEquals(4, exercise.options.size)
            assertFalse(exercise.multiSelect)
            val texts = exercise.options.map { it.text.plain }
            assertTrue("Paris" in texts)
            assertEquals(3, texts.count { it in setOf("Lyon", "Marseille", "Nice", "Lille") })
            session.submit(ChoiceAnswer(listOf(exercise.options.first { it.text.plain == "Paris" }.id)))
            assertTrue(session.feedback().correct)
            assertEquals(ExerciseType.CHOICE, recorder.attempts.single().type)
        }

    @Test
    fun automaticDistractorsPreferTheSameSectionAndSkipEqualAnswers() {
        val items =
            arrayOf(
                card("a1", "A1", "same", ""","section":"s1""""),
                card("a2", "A2", "other one", ""","section":"s1""""),
                card("a3", "A3", "other two", ""","section":"s1""""),
                card("a4", "A4", "other three", ""","section":"s1""""),
                card("b1", "B1", "far away", ""","section":"s2""""),
                card("b2", "B2", "SAME", ""","section":"s2""""),
                card("b3", "B3", "Same!", ""","section":"s2""""),
            )
        val pack =
            loaded(
                readPack(
                    packJson(
                        *items,
                        study = """{"exercises":{"card":["choice"]}}""",
                        sections = """[{"id":"s1","title":"One"},{"id":"s2","title":"Two"}]""",
                    ),
                ),
            )
        repeat(20) { seed ->
            val built = ExerciseFactory.build(pack.context, pack.context.items.first(), Facet.FORWARD, ExerciseType.CHOICE, Random(seed))
            val options = (built.exercise as ChoiceExercise).options.map { it.text.plain }
            assertEquals(4, options.size)
            assertEquals(1, options.count { it.lowercase().trimEnd('!') == "same" })
            assertEquals(setOf("same", "other one", "other two", "other three"), options.toSet())
        }
    }

    @Test
    fun choiceFallsBackToFlipWhenTooFewOptionsExist() =
        runTest {
            val session = start(card("a", "f", "b"), card("c", "g", "b"), study = """{"exercises":{"card":["choice"]}}""")
            assertIs<FlipExercise>(session.question().exercise)
        }

    @Test
    fun typedAnswersAreNormalisedAndAlternativesAccepted() =
        runTest {
            val items =
                arrayOf(
                    card("a", "good morning", "**buenos días**", ""","alternatives":["buen día"]"""),
                    card("b", "thanks", "gracias"),
                )
            val study = """{"ignoreDiacritics":true,"exercises":{"card":["type"]}}"""

            suspend fun answers(vararg typed: String): List<Verdict> =
                typed.map { text ->
                    val session = start(*items, study = study)
                    assertIs<TypeExercise>(session.question().exercise)
                    session.submit(TextAnswer(text))
                    session.feedback().verdict
                }
            assertEquals(
                listOf(Verdict.CORRECT, Verdict.CORRECT, Verdict.CORRECT, Verdict.CORRECT, Verdict.WRONG, Verdict.WRONG),
                answers("buenos días", "  BUENOS   DIAS! ", "buen dia", "buen día.", "buenas dias", ""),
            )
            val session = start(*items, study = study)
            session.submit(TextAnswer("wrong"))
            val feedback = session.feedback()
            assertEquals("buenos días", feedback.correctAnswer.plain)
            assertEquals("wrong", feedback.userAnswer)
            assertTrue(
                feedback.correctAnswer.spans
                    .single()
                    .bold,
            )
        }

    @Test
    fun typosAreAcceptedAndMarkedWhenTolerated() =
        runTest {
            val text = """{"id":"t","type":"text","question":"Crisis?","answers":["Cuban Missile Crisis"]}"""
            val off = start(text)
            off.submit(TextAnswer("Cuban Missle Crisis"))
            assertFalse(off.feedback().correct)
            assertFalse(off.feedback().typo)

            val on = start(text, tolerateTypos = true)
            on.submit(TextAnswer("cuban missle crisis"))
            assertTrue(on.feedback().correct)
            assertTrue(on.feedback().typo)
            assertFalse(on.feedback().willRepeat)
            assertEquals("Cuban Missile Crisis", on.feedback().correctAnswer.plain)
            assertTrue(recorder.attempts.last().correct)

            val exact = start(text, tolerateTypos = true)
            exact.submit(TextAnswer("cuban missile crisis"))
            assertTrue(exact.feedback().correct)
            assertFalse(exact.feedback().typo)

            val strict =
                start(
                    """{"id":"t","type":"text","question":"Command?","answers":["git commit"],"caseSensitive":true}""",
                    tolerateTypos = true,
                )
            strict.submit(TextAnswer("git comit"))
            assertFalse(strict.feedback().correct)

            val cloze = start("""{"id":"c","type":"cloze","text":"{{Jupiter}} and {{Saturn}} in {{1989}}"}""", tolerateTypos = true)
            cloze.submit(ClozeAnswer(listOf("Jupitre", "saturn", "1989")))
            val feedback = cloze.feedback()
            assertTrue(feedback.correct)
            assertTrue(feedback.typo)
            assertEquals(listOf(true, false, false), feedback.gapResults.map { it.typo })

            val year = start("""{"id":"c","type":"cloze","text":"{{Jupiter}} in {{1989}}"}""", tolerateTypos = true)
            year.submit(ClozeAnswer(listOf("Jupiter", "1998")))
            assertFalse(year.feedback().correct)
        }

    @Test
    fun aTypoThatIsAnotherAnswerOfThePackIsWrong() =
        runTest {
            val items = arrayOf(card("a", "but", "pero"), card("b", "dog", "perro"), card("c", "cat", "gato"))
            val study = """{"exercises":{"card":["type"]}}"""
            val session = start(*items, study = study, tolerateTypos = true)
            assertEquals("a", session.question().exercise.itemId)
            session.submit(TextAnswer("perro"))
            assertFalse(session.feedback().correct)
            val other = start(*items, study = study, tolerateTypos = true)
            other.submit(TextAnswer("perp"))
            assertTrue(other.feedback().typo)
        }

    @Test
    fun caseSensitiveCardsAndTextItems() =
        runTest {
            val text = """{"id":"t","type":"text","question":"Sodium?","answers":["Na"],"caseSensitive":true}"""
            val strict = start(text)
            strict.submit(TextAnswer("na"))
            assertFalse(strict.feedback().correct)
            val exact = start(text)
            exact.submit(TextAnswer("Na"))
            assertTrue(exact.feedback().correct)
            val lenient = start("""{"id":"t","type":"text","question":"Sodium?","answers":["Na"]}""")
            lenient.submit(TextAnswer("na"))
            assertTrue(lenient.feedback().correct)
        }

    @Test
    fun reverseFacetAsksForTheFrontSide() =
        runTest {
            val schedules = mapOf(key("a", Facet.FORWARD) to review(), key("a", Facet.REVERSE) to Schedule.fresh())
            val session =
                start(
                    card("a", "el agua", "water", ""","reversible":true,"frontAlternatives":["agua"],"alternatives":["the water"]"""),
                    study = """{"exercises":{"card":["type"]}}""",
                    schedules = schedules,
                )
            val question = session.question()
            assertEquals(Facet.REVERSE, question.exercise.facet)
            assertEquals("water", (question.exercise as TypeExercise).prompt.plain)
            session.submit(TextAnswer("agua"))
            assertTrue(session.feedback().correct)
            assertEquals(Facet.REVERSE, recorder.attempts.single().facet)
        }

    @Test
    fun clozeChecksEveryGapSeparately() =
        runTest {
            val item =
                """{"id":"z","type":"cloze","explanation":"Because.",
                "text":"The {{colour|color}} of {{Paris::city}} is {{blue}}."}"""
            val session = start(item)
            val exercise = assertIs<ClozeExercise>(session.question().exercise)
            assertEquals(3, exercise.gapCount)
            val gaps = exercise.segments.filterIsInstance<ClozeSegment.Gap>()
            assertEquals(listOf(0, 1, 2), gaps.map { it.index })
            assertEquals(listOf(null, "city", null), gaps.map { it.hint })
            assertEquals(7, exercise.segments.size)
            session.submit(ClozeAnswer(listOf("COLOR", "paris", "red")))
            val feedback = session.feedback()
            assertEquals(Verdict.WRONG, feedback.verdict)
            assertEquals(listOf(true, true, false), feedback.gapResults.map { it.correct })
            assertEquals(listOf("colour", "Paris", "blue"), feedback.gapResults.map { it.canonical })
            assertEquals("The colour of Paris is blue.", feedback.correctAnswer.plain)
            assertEquals("Because.", feedback.explanation?.plain)
            assertTrue(feedback.willRepeat)
            session.next()
            session.submit(ClozeAnswer(listOf("colour", "Paris", "blue")))
            assertTrue(session.feedback().correct)
        }

    @Test
    fun clozeMissingAnswersCountAsEmpty() =
        runTest {
            val session = start("""{"id":"z","type":"cloze","text":"{{a}} and {{b}}"}""")
            session.submit(ClozeAnswer(listOf("a")))
            assertEquals(listOf(true, false), session.feedback().gapResults.map { it.correct })
        }

    @Test
    fun trueFalseFlow() =
        runTest {
            val session = start("""{"id":"t","type":"truefalse","statement":"Water is wet","answer":true,"explanation":"Yes."}""")
            assertIs<TrueFalseExercise>(session.question().exercise)
            session.submit(TrueFalseAnswer(false))
            val feedback = session.feedback()
            assertEquals(Verdict.WRONG, feedback.verdict)
            assertTrue(feedback.statementIsTrue)
            assertEquals("Yes.", feedback.explanation?.plain)
        }

    @Test
    fun orderIsShuffledDifferentlyFromTheAnswerAndGradedByPosition() =
        runTest {
            val item = """{"id":"o","type":"order","prompt":"Order","sequence":["one","two","three"]}"""
            repeat(30) { seed ->
                val session = start(item, random = Random(seed))
                val exercise = assertIs<OrderExercise>(session.question().exercise)
                assertEquals(setOf("one", "two", "three"), exercise.entries.map { it.text.plain }.toSet())
                assertTrue(exercise.entries.map { it.text.plain } != listOf("one", "two", "three"), "seed $seed")
            }
            val session = start(item)
            val entries = (session.question().exercise as OrderExercise).entries

            fun id(text: String) = entries.first { it.text.plain == text }.id
            session.submit(OrderAnswer(listOf(id("one"), id("three"), id("two"))))
            val wrong = session.feedback()
            assertEquals(Verdict.WRONG, wrong.verdict)
            assertEquals(listOf(true, false, false), wrong.orderResults.map { it.correct })
            assertEquals(listOf("one", "two", "three"), wrong.orderResults.map { it.expected.plain })
            assertEquals(listOf("one", "three", "two"), wrong.orderResults.map { it.arranged.plain })
            assertEquals("one\ntwo\nthree", wrong.correctAnswer.plain)
            session.next()
            session.submit(OrderAnswer(listOf(id("one"), id("two"), id("three"))))
            assertTrue(session.feedback().correct)
        }

    @Test
    fun orderRejectsIncompleteOrDuplicatedArrangements() =
        runTest {
            val item = """{"id":"o","type":"order","prompt":"Order","sequence":["one","two"]}"""
            val ids = (start(item).question().exercise as OrderExercise).entries.map { it.id }
            for (answer in listOf(emptyList(), listOf(ids[0]), listOf(ids[0], ids[0]), listOf("x", "y"))) {
                val session = start(item)
                session.submit(OrderAnswer(answer))
                assertEquals(Verdict.WRONG, session.feedback().verdict, answer.toString())
            }
        }

    @Test
    fun orderWithRepeatedEntriesAcceptsEitherArrangement() =
        runTest {
            val session = start("""{"id":"o","type":"order","prompt":"Order","sequence":["same","same","other"]}""")
            val entries = (session.question().exercise as OrderExercise).entries
            val sameIds = entries.filter { it.text.plain == "same" }.map { it.id }
            val other = entries.first { it.text.plain == "other" }.id
            session.submit(OrderAnswer(listOf(sameIds[1], sameIds[0], other)))
            assertTrue(session.feedback().correct)
        }

    @Test
    fun openQuestionRevealsKeyPointsAndSuggestsGrades() =
        runTest {
            val session =
                start(
                    """{"id":"o","type":"open","question":"Explain **idempotency**","answer":"Same effect.",
                    "keyPoints":["one","two","three","four"],"explanation":"More."}""",
                )
            assertIs<OpenExercise>(session.question().exercise)
            session.reveal()
            val feedback = session.feedback()
            assertTrue(feedback.needsSelfGrade)
            assertEquals("Same effect.", feedback.correctAnswer.plain)
            assertEquals(4, feedback.keyPoints.size)
            assertEquals("More.", feedback.explanation?.plain)
            assertEquals(Grade.GOOD, feedback.suggestedGrade(4))
            assertEquals(Grade.GOOD, feedback.suggestedGrade(3))
            assertEquals(Grade.HARD, feedback.suggestedGrade(2))
            assertEquals(Grade.AGAIN, feedback.suggestedGrade(1))
            assertEquals(Grade.AGAIN, feedback.suggestedGrade(0))
            session.selfGrade(feedback.suggestedGrade(3))
            assertEquals(ExerciseType.OPEN, recorder.attempts.single().type)
            assertEquals(Grade.GOOD, recorder.attempts.single().grade)
            assertTrue(session.state.value is SessionState.Finished)
        }

    @Test
    fun openQuestionWithoutKeyPointsSuggestsGood() =
        runTest {
            val session = start("""{"id":"o","type":"open","question":"q"}""")
            session.reveal()
            assertEquals(Grade.GOOD, session.feedback().suggestedGrade(0))
            assertEquals("", session.feedback().correctAnswer.plain)
        }

    @Test
    fun hintIsRevealedOnRequestOnly() {
        val session =
            start(
                """{"id":"a","type":"text","question":"q","answers":["x"],"hint":"Think of *letters*"}""",
                """{"id":"b","type":"text","question":"q","answers":["x"]}""",
            )
        val before = session.question()
        assertTrue(before.hasHint)
        assertNull(before.hint)
        session.revealHint()
        val after = session.question()
        assertEquals("Think of letters", after.hint?.plain)
        assertTrue(after.hint!!.spans.any { it.italic })
        assertEquals(before.exercise, after.exercise)
    }

    @Test
    fun hintOnAnItemWithoutOneDoesNothing() =
        runTest {
            val session = start("""{"id":"b","type":"text","question":"q","answers":["x"]}""")
            assertFalse(session.question().hasHint)
            session.revealHint()
            assertNull(session.question().hint)
            session.submit(TextAnswer("x"))
            session.revealHint()
            assertIs<SessionState.Feedback>(session.state.value)
        }

    @Test
    fun hintDoesNotCarryOverToTheNextQuestion() =
        runTest {
            val session =
                start(
                    """{"id":"a","type":"text","question":"q","answers":["x"],"hint":"h"}""",
                    """{"id":"b","type":"text","question":"q","answers":["x"],"hint":"h2"}""",
                )
            session.revealHint()
            session.submit(TextAnswer("x"))
            session.next()
            assertNull(session.question().hint)
        }

    @Test
    fun callsInTheWrongStateAreIgnored() =
        runTest {
            val session = start("""{"id":"t","type":"text","question":"q","answers":["x"]}""")
            session.next()
            session.selfGrade(Grade.GOOD)
            session.reveal()
            session.submit(TrueFalseAnswer(true))
            session.submit(ChoiceAnswer(emptyList()))
            assertIs<SessionState.Question>(session.state.value)
            assertTrue(recorder.attempts.isEmpty())
            session.submit(TextAnswer("x"))
            session.submit(TextAnswer("x"))
            session.selfGrade(Grade.AGAIN)
            assertEquals(1, recorder.attempts.size)
            assertIs<SessionState.Feedback>(session.state.value)
            session.reveal()
            session.revealHint()
            assertIs<SessionState.Feedback>(session.state.value)
            session.next()
            val summary = session.finished()
            session.next()
            session.submit(TextAnswer("x"))
            session.finish()
            assertEquals(summary, session.finished())
        }

    @Test
    fun flipExercisesCannotBeSubmittedAndAutoGradedCannotBeRevealed() =
        runTest {
            val flip = start(card("a", "f", "b"), study = flipStudy)
            flip.submit(TextAnswer("b"))
            assertIs<SessionState.Question>(flip.state.value)
            val typed = start("""{"id":"t","type":"text","question":"q","answers":["x"]}""")
            typed.reveal()
            assertIs<SessionState.Question>(typed.state.value)
        }

    @Test
    fun finishingEarlyAbandonsTheSessionButKeepsWhatWasAnswered() =
        runTest {
            val session = start(*flipCards, study = flipStudy)
            session.reveal()
            session.selfGrade(Grade.GOOD)
            clock.advanceMinutes(3)
            session.finish()
            val summary = session.finished()
            assertTrue(summary.abandoned)
            assertEquals(3, summary.total)
            assertEquals(1, summary.answered)
            assertEquals(1, summary.correct)
            assertEquals(3 * 60_000L, summary.durationMs)
            assertEquals(1, recorder.attempts.size)
        }

    @Test
    fun everyAnswerIsPersistedImmediately() =
        runTest {
            val session = start(*flipCards, study = flipStudy)
            session.reveal()
            assertEquals(0, recorder.attempts.size)
            session.selfGrade(Grade.GOOD)
            assertEquals(1, recorder.attempts.size)
            session.reveal()
            session.selfGrade(Grade.HARD)
            assertEquals(2, recorder.attempts.size)
        }

    @Test
    fun attemptsCarryTheClockTime() =
        runTest {
            val session = start(*flipCards, study = flipStudy)
            session.reveal()
            session.selfGrade(Grade.GOOD)
            clock.advanceMinutes(5)
            session.reveal()
            session.selfGrade(Grade.GOOD)
            assertEquals(listOf(clock.millis - 5 * 60_000L, clock.millis), recorder.attempts.map { it.timestamp })
        }

    @Test
    fun practiceSessionsRecordButDoNotReschedule() =
        runTest {
            val session =
                start(
                    *flipCards,
                    study = flipStudy,
                    config = SessionConfig.practice().withFilter(PracticeFilter.FLASHCARDS),
                )
            repeat(3) {
                session.reveal()
                session.selfGrade(Grade.GOOD)
            }
            assertEquals(3, recorder.attempts.size)
            assertTrue(recorder.attempts.all { it.practice && !it.reschedule })
            assertEquals(SessionMode.PRACTICE, session.finished().mode)
            assertEquals(0, session.finished().newFacets)
        }

    @Test
    fun practiceCanRescheduleWhenAsked() =
        runTest {
            val session = start(*flipCards, study = flipStudy, config = SessionConfig.practice().withReschedule(true))
            session.reveal()
            session.selfGrade(Grade.GOOD)
            assertTrue(recorder.attempts.single().reschedule)
            assertTrue(recorder.attempts.single().practice)
        }

    @Test
    fun theLastWrongItemIsAskedAgainImmediately() =
        runTest {
            val session = start("""{"id":"t","type":"truefalse","statement":"s","answer":true}""")
            session.submit(TrueFalseAnswer(false))
            session.next()
            val retry = session.question()
            assertTrue(retry.isRetry)
            assertEquals(1, retry.position)
            session.submit(TrueFalseAnswer(true))
            session.next()
            assertEquals(1, session.finished().retries)
        }

    @Test
    fun wrongAnswersComeBackAfterAFewOtherItems() =
        runTest {
            val items = (1..8).map { """{"id":"t$it","type":"truefalse","statement":"s$it","answer":true}""" }.toTypedArray()
            val session = start(*items)
            val asked = ArrayList<String>()
            var first = true
            while (session.state.value is SessionState.Question) {
                val question = session.question()
                asked += question.exercise.itemId
                session.submit(TrueFalseAnswer(!(first && question.exercise.itemId == "t1")))
                first = first && question.exercise.itemId != "t1"
                session.next()
            }
            assertEquals(listOf("t1", "t2", "t3", "t4", "t1", "t5", "t6", "t7", "t8"), asked)
        }

    @Test
    fun sessionsAreDeterministicForASeed() =
        runTest {
            suspend fun run(seed: Int): List<String> {
                val items =
                    (1..6).map {
                        """{"id":"c$it","type":"choice","question":"q$it","options":[{"text":"a","correct":true},{"text":"b"},{"text":"c"}]}"""
                    }
                val session = start(*items.toTypedArray(), random = Random(seed))
                val out = ArrayList<String>()
                repeat(6) {
                    val exercise = session.question().exercise as ChoiceExercise
                    out += exercise.options.joinToString("") { it.text.plain } + exercise.itemId
                    session.submit(ChoiceAnswer(emptyList()))
                    session.next()
                }
                return out
            }
            assertEquals(run(5), run(5))
            assertTrue(run(5) != run(6))
        }

    private fun review() = Schedule(LearningState.REVIEW, 2500, 3, clock.millis + 10 * Scheduler.DAY_MS, 3, 0, clock.millis)

    private fun key(
        id: String,
        facet: Facet,
    ) = FacetKey(id, facet)
}
