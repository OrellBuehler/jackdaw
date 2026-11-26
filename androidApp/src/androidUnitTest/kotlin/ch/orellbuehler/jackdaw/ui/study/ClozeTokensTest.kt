package ch.orellbuehler.jackdaw.ui.study

import ch.orellbuehler.jackdaw.format.RichText
import ch.orellbuehler.jackdaw.format.TextSpan
import kotlin.test.Test
import kotlin.test.assertEquals

class ClozeTokensTest {
    private fun words(text: RichText) = clozeTokens(text).map { if (it is ClozeToken.Word) it.text.plain else "\n" }

    @Test
    fun wordsKeepTheirTrailingSpaces() {
        assertEquals(listOf("The ", "Berlin ", "Wall "), words(RichText.fromPlain("The Berlin Wall ")))
    }

    @Test
    fun punctuationStaysWithItsWord() {
        assertEquals(listOf(" fell ", "in "), words(RichText.fromPlain(" fell in ")))
        assertEquals(listOf(" "), words(RichText.fromPlain(" ")))
        assertEquals(listOf(". ", "Next"), words(RichText.fromPlain(". Next")))
    }

    @Test
    fun newlinesBecomeBreaks() {
        assertEquals(listOf("One", "\n", "Two"), words(RichText.fromPlain("One\nTwo")))
    }

    @Test
    fun stylesSurviveTokenisation() {
        val text =
            RichText.of(
                listOf(
                    TextSpan("a ", bold = false, italic = false, code = false),
                    TextSpan("bold word", bold = true, italic = false, code = false),
                    TextSpan(" end", bold = false, italic = false, code = false),
                ),
            )
        val tokens = clozeTokens(text).filterIsInstance<ClozeToken.Word>()
        assertEquals(listOf("a ", "bold ", "word ", "end"), tokens.map { it.text.plain })
        assertEquals(
            true,
            tokens[1]
                .text.spans
                .single()
                .bold,
        )
        assertEquals(
            false,
            tokens[3]
                .text.spans
                .single()
                .bold,
        )
    }

    @Test
    fun emptyTextHasNoTokens() {
        assertEquals(emptyList(), clozeTokens(RichText(emptyList())))
    }
}
