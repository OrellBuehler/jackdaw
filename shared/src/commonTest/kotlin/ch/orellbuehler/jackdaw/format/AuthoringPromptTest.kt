package ch.orellbuehler.jackdaw.format

import ch.orellbuehler.jackdaw.GENERATED_AUTHORING_PROMPT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthoringPromptTest {
    private val all = ItemType.entries.map { it.code }

    private fun prompt(
        types: List<String> = all,
        figures: Boolean = false,
    ) = AuthoringPrompt.build(GENERATED_AUTHORING_PROMPT, types, figures)

    private fun examplePack(prompt: String) =
        assertIs<ReadResult.Accepted>(PackReader.read(prompt.substringAfter("```json pack\n").substringBefore("\n```")))

    private fun typeLines(prompt: String) = prompt.lines().filter { Regex("""^\d+\. "[a-z]+": .*""").matches(it) }

    @Test
    fun everythingSelectedIsThePromptFile() {
        assertEquals(GENERATED_AUTHORING_PROMPT, prompt(figures = true))
        assertEquals(7, typeLines(GENERATED_AUTHORING_PROMPT).size)
        assertEquals(all, examplePack(GENERATED_AUTHORING_PROMPT).pack.items.map { it.type.code })
    }

    @Test
    fun withoutFiguresThePackIsVersionOne() {
        val text = prompt()
        assertFalse(text.contains("figure", ignoreCase = true))
        assertTrue("- \"version\": 1                                  (required, always the number 1)" in text)
        assertTrue("Preferred item types: [" in text)
        assertEquals(7, typeLines(text).size)
        assertTrue("\n\nTEXT RULES\n" in text)
        assertFalse("\n\n\n" in text)
        assertTrue("\nFIGURES (" in prompt(figures = true))
    }

    @Test
    fun onlyTheChosenTypesAreDescribed() {
        val text = prompt(listOf("cloze", "card", "nonsense"))
        assertEquals(listOf("1. \"card\": ", "2. \"cloze\": "), typeLines(text).map { it.substringBefore("two").substringBefore("fill") })
        assertTrue("\nItem types: use only \"card\" and \"cloze\". Do not use any other item type.\n" in text)
        assertFalse("Preferred item types" in text)
        assertEquals(listOf("card", "cloze"), examplePack(text).pack.items.map { it.type.code })
        assertTrue("use only \"open\"." in prompt(listOf("open")))
        assertTrue("use only \"card\", \"choice\" and \"text\"." in prompt(listOf("card", "choice", "text")))
        assertEquals(prompt(), prompt(emptyList()))
    }

    @Test
    fun theExampleIsAValidPackForEverySelection() {
        for (mask in 1 until (1 shl all.size)) {
            val types = all.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
            val result = examplePack(prompt(types, figures = mask % 2 == 0))
            assertEquals(types, result.pack.items.map { it.type.code })
            assertEquals(emptyList(), result.warnings)
        }
    }
}
