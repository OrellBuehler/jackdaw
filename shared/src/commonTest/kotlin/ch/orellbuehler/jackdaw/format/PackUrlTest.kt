package ch.orellbuehler.jackdaw.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PackUrlTest {
    @Test
    fun plainLinksAreKept() {
        assertEquals("https://example.org/a/pack.jackdaw?x=1", PackUrl.resolve("  https://example.org/a/pack.jackdaw?x=1#top "))
        assertEquals("https://example.org/pack.json", PackUrl.resolve("example.org/pack.json"))
        assertEquals("https://example.org/Pack.json", PackUrl.resolve("HTTPS://Example.org/Pack.json"))
    }

    @Test
    fun gistPagesBecomeTheirRawFile() {
        val raw = "https://gist.githubusercontent.com/someone/0123abcd/raw"
        assertEquals(raw, PackUrl.resolve("https://gist.github.com/someone/0123abcd"))
        assertEquals(raw, PackUrl.resolve("gist.github.com/someone/0123abcd#file-pack-jackdaw"))
        assertEquals(
            "https://gist.githubusercontent.com/someone/0123abcd/raw/abc/pack.jackdaw",
            PackUrl.resolve("https://gist.githubusercontent.com/someone/0123abcd/raw/abc/pack.jackdaw"),
        )
    }

    @Test
    fun githubFilePagesBecomeRawFiles() {
        assertEquals(
            "https://raw.githubusercontent.com/someone/repo/main/packs/a.jackdaw",
            PackUrl.resolve("https://github.com/someone/repo/blob/main/packs/a.jackdaw"),
        )
    }

    @Test
    fun otherInputIsRejected() {
        val inputs =
            listOf(
                "",
                "   ",
                "http://example.org/pack.json",
                "ftp://example.org/x",
                "https://",
                "https://localhost/x",
                "two words.org/x",
                "https://exa mple.org",
            )
        for (input in inputs) assertNull(PackUrl.resolve(input), input)
    }
}
