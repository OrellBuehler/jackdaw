package ch.orellbuehler.jackdaw.study

import ch.orellbuehler.jackdaw.FakeClock
import ch.orellbuehler.jackdaw.format.GENERATED_COMPAT_PACKS
import ch.orellbuehler.jackdaw.format.RichText
import ch.orellbuehler.jackdaw.format.TextBlock
import ch.orellbuehler.jackdaw.format.TokenKind
import ch.orellbuehler.jackdaw.packJson
import ch.orellbuehler.jackdaw.readPack
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CodeBlockSessionTest {
    private val recorder = RecordingRecorder()
    private val clock = FakeClock()
    private val fence = "```"

    private fun start(
        vararg items: String,
        study: String? = null,
    ): StudySession {
        val pack = loaded(readPack(packJson(*items, study = study)), emptyMap())
        return session(pack, SessionConfig.learn(), recorder, clock, Random(1), false)
    }

    private fun StudySession.question() = assertIs<SessionState.Question>(state.value)

    private fun StudySession.feedback() = assertIs<SessionState.Feedback>(state.value)

    private val clozeItem =
        """{"id":"z","type":"cloze","text":"Complete:\n${fence}python\ndef add(a, b):\n    return {{a + b::sum}}\n$fence\nIt adds {{two}} numbers."}"""

    @Test
    fun clozeGapsInsideACodeBlockAreSegmentsOfTheBlock() =
        runTest {
            val exercise = assertIs<ClozeExercise>(start(clozeItem).question().exercise)
            assertEquals(2, exercise.gapCount)
            assertEquals(5, exercise.segments.size)
            assertEquals("Complete:", assertIs<ClozeSegment.Text>(exercise.segments[0]).text.plain)
            val code = assertIs<ClozeSegment.Code>(exercise.segments[1])
            assertEquals(2, code.lines.size)
            assertEquals(ClozePiece.Span("def", TokenKind.KEYWORD), code.lines[0].first())
            assertEquals(
                listOf(
                    ClozePiece.Span("    ", TokenKind.PLAIN),
                    ClozePiece.Span("return", TokenKind.KEYWORD),
                    ClozePiece.Span(" ", TokenKind.PLAIN),
                    ClozePiece.Gap(0, "sum"),
                ),
                code.lines[1],
            )
            assertEquals(ClozeSegment.Gap(1, null), exercise.segments[3])
            assertEquals(" numbers.", assertIs<ClozeSegment.Text>(exercise.segments[4]).text.plain)
        }

    @Test
    fun theAnswerIsTheCodeWithTheGapsFilledIn() =
        runTest {
            val session = start(clozeItem)
            session.question()
            session.submit(ClozeAnswer(listOf("a+b", "three")))
            val feedback = session.feedback()
            assertEquals(listOf(false, false), feedback.gapResults.map { it.correct })
            val blocks = feedback.correctAnswer.blocks
            assertEquals(3, blocks.size)
            assertEquals("Complete:", (blocks[0] as TextBlock.Paragraph).spans.single().text)
            assertEquals(TextBlock.Code("python", "def add(a, b):\n    return a + b"), blocks[1])
            val last = (blocks[2] as TextBlock.Paragraph).spans
            assertEquals(listOf("It adds ", "two", " numbers."), last.map { it.text })
            assertTrue(last[1].bold)
            assertEquals("Complete:\ndef add(a, b):\n    return a + b\nIt adds two numbers.", feedback.correctAnswer.plain)
        }

    @Test
    fun aCorrectAnswerInACodeGapIsAccepted() =
        runTest {
            val session = start(clozeItem)
            session.question()
            session.submit(ClozeAnswer(listOf("A + B", "two")))
            assertTrue(session.feedback().correct)
        }

    @Test
    fun gapsWorkInABlockWithoutAnyLanguage() =
        runTest {
            val item = """{"id":"z","type":"cloze","text":"$fence\n{{x}} = 1\n\tprint({{x}})\n$fence"}"""
            val exercise = assertIs<ClozeExercise>(start(item).question().exercise)
            val code = assertIs<ClozeSegment.Code>(exercise.segments.single())
            assertEquals(2, code.lines.size)
            assertEquals(listOf(ClozePiece.Gap(0, null), ClozePiece.Span(" = 1", TokenKind.PLAIN)), code.lines[0])
            assertEquals(
                listOf(ClozePiece.Span("    print(", TokenKind.PLAIN), ClozePiece.Gap(1, null), ClozePiece.Span(")", TokenKind.PLAIN)),
                code.lines[1],
            )
        }

    @Test
    fun aGapInsideAStringOfTheBlockStaysInsideTheString() =
        runTest {
            val item = """{"id":"z","type":"cloze","text":"${fence}python\nx = \"hello {{world}}\"\n$fence"}"""
            val code = assertIs<ClozeSegment.Code>(assertIs<ClozeExercise>(start(item).question().exercise).segments.single())
            assertEquals(
                listOf(
                    ClozePiece.Span("x = ", TokenKind.PLAIN),
                    ClozePiece.Span("\"hello ", TokenKind.STRING),
                    ClozePiece.Gap(0, null),
                    ClozePiece.Span("\"", TokenKind.STRING),
                ),
                code.lines.single(),
            )
        }

    @Test
    fun flipCardsCarryBlocks() =
        runTest {
            val card =
                """{"id":"c","type":"card","front":"What does this print?\n${fence}python\nprint(1)\n$fence","back":"1\n$fence\nx\n$fence"}"""
            val session = start(card, study = """{"exercises":{"card":["flip"]}}""")
            val prompt = assertIs<FlipExercise>(session.question().exercise).prompt
            assertEquals(listOf("Paragraph", "Code"), prompt.blocks.map { it::class.simpleName })
            assertEquals("What does this print?\nprint(1)", prompt.plain)
            session.reveal()
            assertEquals("1\nx", session.feedback().correctAnswer.plain)
        }

    @Test
    fun choiceOptionsAndOrderEntriesCanBeCodeBlocks() =
        runTest {
            val choice =
                """{"id":"ch","type":"choice","question":"Which?\n${fence}py\nprint(1)\n$fence","shuffle":false,"options":[
                {"text":"${fence}py\nx = []\n$fence","correct":true},{"text":"`x = {}`","explanation":"$fence\ndict\n$fence"}]}"""
            val exercise = assertIs<ChoiceExercise>(start(choice).question().exercise)
            assertTrue(exercise.question.hasCode)
            assertEquals(listOf(true, false), exercise.options.map { it.text.hasCode })
            val first = "${fence}py\\na = 1\\n$fence"
            val second = "${fence}py\\nprint(a)\\n$fence"
            val order = """{"id":"o","type":"order","prompt":"Order","sequence":["$first","$second"]}"""
            val entries = assertIs<OrderExercise>(start(order).question().exercise).entries
            assertEquals(listOf(true, true), entries.map { it.text.hasCode })
            assertEquals(setOf("a = 1", "print(a)"), entries.map { it.text.plain }.toSet())
        }

    @Test
    fun theFrozenCodePackBuildsAnExerciseForEveryItem() =
        runTest {
            val pack = readPack(GENERATED_COMPAT_PACKS.getValue("4.0-code.jackdaw"))
            for (item in pack.items) {
                val single = loaded(pack.copy(items = listOf(item)), emptyMap())
                val session = session(single, SessionConfig.learn(), recorder, clock, Random(1), false)
                assertIs<SessionState.Question>(session.state.value, item.id)
            }
        }

    @Test
    fun correctChoicesAreJoinedWithoutLosingTheirBlocks() {
        val joined =
            joinRich(
                listOf(RichText.parse("a"), RichText.parse("$fence\nx\n$fence"), RichText.parse("b"), RichText.parse("c")),
                ", ",
            )
        val shown =
            joined.blocks.map {
                if (it is TextBlock.Code) {
                    it.code
                } else {
                    (it as TextBlock.Paragraph).spans.joinToString(
                        "",
                    ) { s -> s.text }
                }
            }
        assertEquals(listOf("a", "x", "b, c"), shown)
    }
}
