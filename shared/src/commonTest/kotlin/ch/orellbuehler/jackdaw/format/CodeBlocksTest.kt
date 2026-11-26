package ch.orellbuehler.jackdaw.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodeBlocksTest {
    private val fence = "```"

    private fun block(
        language: String?,
        code: String,
    ) = TextBlock.Code(language, code)

    @Test
    fun aBlockBetweenTwoTextsIsThreeBlocks() {
        val rich = RichText.parse("Before **it**\n${fence}python\nx = 1\nprint(x)\n$fence\nAfter")
        assertEquals(3, rich.blocks.size)
        val before = assertIs<TextBlock.Paragraph>(rich.blocks[0])
        assertEquals(listOf("Before ", "it"), before.spans.map { it.text })
        assertTrue(before.spans[1].bold)
        assertEquals(block("python", "x = 1\nprint(x)"), rich.blocks[1])
        assertEquals("After", assertIs<TextBlock.Paragraph>(rich.blocks[2]).spans.single().text)
        assertEquals("Before it\nx = 1\nprint(x)\nAfter", rich.plain)
        assertTrue(rich.hasCode)
        assertFalse(rich.isEmpty)
    }

    @Test
    fun theContentOfABlockIsLiteral() {
        val rich = RichText.parse("$fence\n**a** `b` \\* {{c}} <d>\n$fence")
        assertEquals(listOf(block(null, "**a** `b` \\* {{c}} <d>")), rich.blocks)
        assertEquals("**a** `b` \\* {{c}} <d>", rich.plain)
    }

    @Test
    fun anEmptyBlockShowsNothing() {
        assertTrue(RichText.parse("$fence\n$fence").isEmpty)
        assertTrue(RichText.parse("$fence\n\n$fence").isEmpty)
        assertEquals("a\nb", RichText.parse("a\n$fence\n$fence\nb").plain)
        assertFalse(RichText.parse("$fence\n$fence").hasCode)
    }

    @Test
    fun anUnclosedFenceIsText() {
        val rich = RichText.parse("${fence}python\nx = 1")
        assertFalse(rich.hasCode)
        assertEquals("${fence}python\nx = 1", rich.plain)
        val lone = RichText.parse("$fence\nx\n${fence}js")
        assertFalse(lone.hasCode)
        assertEquals("$fence\nx\n${fence}js", lone.plain)
    }

    @Test
    fun whatIsNotAFenceLine() {
        for (text in listOf(
            "text ${fence}py\nx\n$fence",
            "${fence}py\nx $fence",
            "${fence}python title=x\nx\n$fence",
            "````\nx\n````",
            "$fence\nx\n $fence",
            "${fence}abcdefghijklmnopqrstu\nx\n$fence",
        )) {
            assertFalse(RichText.parse(text).hasCode, text)
        }
        assertEquals("c#", (RichText.parse("  $fence  C#  \nx\n$fence  ").blocks.single() as TextBlock.Code).language)
        val longest = RichText.parse("${fence}abcdefghijklmnopqrst\nx\n$fence").blocks.single() as TextBlock.Code
        assertEquals("abcdefghijklmnopqrst", longest.language)
    }

    @Test
    fun lineEndingsAreNormalised() {
        assertEquals(listOf(block("py", "a\nb")), RichText.parse("${fence}py\r\na\r\nb\r\n$fence").blocks)
        assertEquals(listOf(block("py", "a\nb")), RichText.parse("${fence}py\ra\rb\r$fence").blocks)
    }

    @Test
    fun linesExpandTabsAndKeepBlankLines() {
        val lines = block(null, "\tx\n\ny  ").lines
        assertEquals(listOf("    x", "", "y  "), lines.map { it.joinToString("") { s -> s.text } })
    }

    @Test
    fun plainRichTextHasNoBlocks() {
        assertEquals(emptyList(), RichText.parse("").blocks)
        assertEquals(1, RichText.parse("just text").blocks.size)
        assertEquals("$fence\nx\n$fence", RichText.fromPlain("$fence\nx\n$fence").plain)
        assertFalse(RichText.fromPlain("$fence\nx\n$fence").hasCode)
    }

    @Test
    fun containsCodeBlockMatchesTheParser() {
        assertTrue(containsCodeBlock("$fence\n$fence"))
        assertTrue(containsCodeBlock("a\r\n${fence}sql\r\nselect 1\r\n$fence"))
        assertFalse(containsCodeBlock("$fence\nx"))
        assertFalse(containsCodeBlock("`x`"))
    }

    @Test
    fun clozeGapsAreTheSameWithAndWithoutBlocks() {
        val texts =
            listOf(
                "Fill:\n${fence}python\nreturn {{a + b}}\n$fence\nand {{x::hint}} \\{{y}}",
                "$fence\n{{a\nb}} {{c|d}}\n$fence\n{{e}}",
                "${fence}js\nlet x = {{1}};\n",
                "{{a}}\n$fence\n{{b}}\n$fence\n$fence\n{{c}}\n$fence",
            )
        for (text in texts) {
            val whole = parseCloze(text).filterIsInstance<ClozePart.Gap>().map { it.answers to it.hint }
            val byBlock = parseClozeBlocks(text).flatMap { it.parts }.filterIsInstance<ClozePart.Gap>().map { it.answers to it.hint }
            assertEquals(whole, byBlock, text)
        }
    }

    @Test
    fun clozeBlocksKeepEscapesInCode() {
        val blocks = parseClozeBlocks("$fence\n\\{{x}} {{y}}\n$fence")
        val code = assertIs<ClozeBlock.Code>(blocks.single())
        assertEquals("\\{{x}} ", (code.parts[0] as ClozePart.Text).raw)
        assertEquals(listOf("y"), (code.parts[1] as ClozePart.Gap).answers)
        assertNull(code.language)
    }
}
