package ch.orellbuehler.jackdaw.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TypoTest {
    private fun match(
        typed: String,
        vararg accepted: String,
        caseSensitive: Boolean = false,
        others: Set<String> = emptySet(),
    ) = matchAnswer(typed, accepted.toList(), caseSensitive, false, true, others)

    @Test
    fun likelySlipsAreTypos() {
        assertTrue(isTypoOf("budapedt", "budapest"))
        assertTrue(isTypoOf("budapset", "budapest"))
        assertTrue(isTypoOf("mats", "mars"))
        assertTrue(isTypoOf("budpest", "budapest"))
        assertTrue(isTypoOf("budappest", "budapest"))
        assertTrue(isTypoOf("mussollini", "mussolini"))
        assertTrue(isTypoOf("cuban missle crissis", "cuban missile crisis"))
        assertTrue(isTypoOf("saint-gallen", "saint gallen"))
        assertTrue(isTypoOf("pearlharbor", "pearl harbor"))
    }

    @Test
    fun keysThatAreFarApartAreNotTypos() {
        assertFalse(isTypoOf("budapelt", "budapest"))
        assertFalse(isTypoOf("buenas dias", "buenos dias"))
        assertFalse(isTypoOf("la casa", "el casa"))
    }

    @Test
    fun neighboursOnQwertyQwertzAndAzertyCount() {
        assertTrue(isTypoOf("zurixh", "zurich"))
        assertTrue(isTypoOf("turich", "zurich"))
        assertTrue(isTypoOf("xurich", "zurich"))
        assertTrue(isTypoOf("eurich", "zurich"))
        assertFalse(isTypoOf("murich", "zurich"))
    }

    @Test
    fun wordEndingsShortAnswersAndNumbersAreStrict() {
        assertFalse(isTypoOf("gato", "gatos"))
        assertFalse(isTypoOf("una casa", "un casa"))
        assertFalse(isTypoOf("rom", "rome"))
        assertFalse(isTypoOf("pb", "pn"))
        assertFalse(isTypoOf("tin", "tim"))
        assertFalse(isTypoOf("1998", "1989"))
        assertFalse(isTypoOf("1 august", "2 august"))
        assertFalse(isTypoOf("apollo 1", "apollo 11"))
        assertFalse(isTypoOf("budapest", "budapest"))
        assertFalse(isTypoOf("bdapst", "budapest"))
        assertFalse(isTypoOf("mussolni b", "mussolini"))
    }

    @Test
    fun budgetGrowsWithLength() {
        assertEquals(0, typoBudget("abc"))
        assertEquals(1, typoBudget("abcd"))
        assertEquals(1, typoBudget("abcdefgh"))
        assertEquals(2, typoBudget("abcdefghi"))
    }

    @Test
    fun matchPrefersExactAndRespectsFlags() {
        assertEquals(AnswerMatch.EXACT, match("Budapest!", "budapest"))
        assertEquals(AnswerMatch.TYPO, match("Budapets", "Budapest"))
        assertEquals(AnswerMatch.TYPO, match("gorbatchec", "Gorbachev", "Gorbatchev"))
        assertEquals(AnswerMatch.NONE, match("Bucharest", "Budapest"))
        assertEquals(AnswerMatch.NONE, match("", "Budapest"))
        assertEquals(AnswerMatch.NONE, match("Budapets", "Budapest", caseSensitive = true))
        assertEquals(AnswerMatch.NONE, match("perro", "pero", others = setOf("perro")))
        assertEquals(AnswerMatch.NONE, matchAnswer("Budapets", listOf("Budapest"), false, false, false))
    }
}
