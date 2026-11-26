package ch.orellbuehler.jackdaw.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HighlighterTest {
    private val languages =
        listOf(
            "python",
            "javascript",
            "typescript",
            "kotlin",
            "java",
            "swift",
            "c",
            "cpp",
            "csharp",
            "go",
            "rust",
            "sql",
            "bash",
            "json",
            "html",
            "css",
            "py",
            "js",
            "ts",
            "kt",
            "c++",
            "cs",
            "c#",
            "sh",
            "shell",
            "golang",
            "rs",
            "PYTHON",
            "unknown",
            null,
            "",
        )

    private val awkward =
        listOf(
            "",
            " ",
            "\n\n\n",
            "\t \t\r\n",
            "\"unterminated string",
            "'unterminated char",
            "\"\"\"unterminated triple",
            "`unterminated template",
            "/* unterminated comment",
            "<!-- unterminated comment",
            "// comment without end",
            "# comment\r\nx = 1\r\ny = 2\r\n",
            "x = \"a\\\"b\" + 'c\\'d' // done\r\n",
            "x = '\\",
            "\"\\",
            "ünïcödé = \"héllo wörld ✓\" # 日本語 😀",
            "\uD83D",
            "0x1F 1_000 3.14e-10 .5 1..10 1.2.3 99bottles",
            "#include <stdio.h>\n#define X 1\n  # not a directive",
            "<div class=\"a\" id='b' data-x=1 >text</div><br/><!-- c --><p",
            "{\"a\": [1, 2.5, true, null], \"b\": {\"c\": \"d\"}}",
            "a { color: #fff; margin: 10px -2px; } @media (x) { b:hover { c: red !important } } /* c */",
            "SELECT a, b FROM t WHERE x = 'it''s' AND y -- c\n;",
            "echo \"\$HOME \${PATH} \$(ls)\" # c\nfoo=bar#baz",
            "let r = 'a'; let l: &'static str = \"s\"; let c = '\\n'; '",
            "@\"verbatim \"\"quoted\"\"\" + \$\"interp {x}\"",
            " x  \"ab\"",
            "\${\${\${\${",
            "x".repeat(50_000),
            "\"".repeat(10_001),
            "/*".repeat(5_000),
            "'".repeat(10_001),
            "<".repeat(10_001),
            "{{{{ }}}} ```` ~~~~",
            ("word ".repeat(2_000) + "\n").repeat(5),
        )

    @Test
    fun spansAlwaysReproduceTheInput() {
        for (language in languages) {
            for (input in awkward) {
                val spans = highlight(language, input)
                assertEquals(input, spans.joinToString("") { it.text }, "language $language, input ${input.take(40)}")
                assertTrue(spans.none { it.text.isEmpty() }, "empty span for $language")
                assertTrue(spans.zipWithNext().none { (a, b) -> a.kind == b.kind }, "unmerged spans for $language")
            }
        }
    }

    @Test
    fun linesReproduceTheInputWithTabsAsFourSpaces() {
        for (language in languages) {
            for (input in awkward) {
                val lines = TextBlock.Code(language, input).lines
                val expected = input.replace("\t", "    ").split('\n')
                assertEquals(expected, lines.map { line -> line.joinToString("") { it.text } }, "language $language")
            }
        }
    }

    @Test
    fun aliasesAndUnknownLanguages() {
        assertEquals("python", canonicalLanguage("PY"))
        assertEquals("cpp", canonicalLanguage("c++"))
        assertEquals("csharp", canonicalLanguage("C#"))
        assertEquals("bash", canonicalLanguage("shell"))
        assertEquals("go", canonicalLanguage("golang"))
        assertEquals(null, canonicalLanguage("brainfuck"))
        assertEquals(null, canonicalLanguage(null))
        assertEquals(listOf(CodeSpan("def x", TokenKind.PLAIN)), highlight("brainfuck", "def x"))
        assertEquals(listOf(CodeSpan("def x", TokenKind.PLAIN)), highlight(null, "def x"))
    }

    private fun kinds(
        language: String,
        code: String,
    ) = highlight(language, code).filter { it.text.isNotBlank() }.map { it.text.trim() to it.kind }

    @Test
    fun pythonTokens() {
        assertEquals(
            listOf(
                "def" to TokenKind.KEYWORD,
                "area(r):" to TokenKind.PLAIN,
                "# circle" to TokenKind.COMMENT,
                "return" to TokenKind.KEYWORD,
                "3.14" to TokenKind.NUMBER,
                "* r **" to TokenKind.PLAIN,
                "2" to TokenKind.NUMBER,
                "Circle" to TokenKind.TYPE,
                "\"\"\"doc\nstring\"\"\"" to TokenKind.STRING,
                "None" to TokenKind.KEYWORD,
            ),
            kinds("python", "def area(r):  # circle\n    return 3.14 * r ** 2\nCircle\n\"\"\"doc\nstring\"\"\" None"),
        )
    }

    @Test
    fun stringsAndCommentsHideKeywords() {
        assertEquals(listOf("\"if else\"" to TokenKind.STRING), kinds("javascript", "\"if else\""))
        assertEquals(listOf("// if else" to TokenKind.COMMENT), kinds("kotlin", "// if else"))
        assertEquals(listOf("/* a\nif */" to TokenKind.COMMENT, "x" to TokenKind.PLAIN), kinds("c", "/* a\nif */ x"))
        assertEquals(listOf("`a ${'$'}{b}\nc`" to TokenKind.STRING), kinds("javascript", "`a ${'$'}{b}\nc`"))
    }

    @Test
    fun otherLanguages() {
        assertEquals(TokenKind.KEYWORD, kinds("sql", "select").single().second)
        assertEquals(TokenKind.KEYWORD, kinds("sql", "SeLeCt").single().second)
        assertEquals(listOf("'it''s'" to TokenKind.STRING), kinds("sql", "'it''s'"))
        assertEquals(listOf("-- c" to TokenKind.COMMENT), kinds("sql", "-- c"))
        assertEquals(listOf("\"a\"" to TokenKind.TYPE, ":" to TokenKind.PLAIN, "1" to TokenKind.NUMBER), kinds("json", "\"a\": 1"))
        assertEquals(listOf("true" to TokenKind.KEYWORD), kinds("json", "true"))
        assertEquals(listOf("#!/bin/sh" to TokenKind.COMMENT, "echo" to TokenKind.KEYWORD), kinds("bash", "#!/bin/sh\necho"))
        assertEquals(listOf("${'$'}HOME" to TokenKind.TYPE), kinds("bash", "${'$'}HOME"))
        assertEquals(listOf("#include" to TokenKind.KEYWORD), kinds("c", "#include").take(1))
        assertEquals(TokenKind.TYPE, kinds("rust", "'a").single().second)
        assertEquals(TokenKind.STRING, kinds("rust", "'a'").single().second)
        assertEquals(TokenKind.STRING, kinds("rust", "'\\n'").single().second)
        assertEquals(TokenKind.STRING, kinds("csharp", "@\"a\"\"b\"").single().second)
        assertEquals(TokenKind.NUMBER, kinds("css", "#fff").single().second)
        assertEquals(TokenKind.TYPE, kinds("css", "color: red").first().second)
        assertEquals(TokenKind.TYPE, kinds("kotlin", "@Test").single().second)
    }

    @Test
    fun htmlTokens() {
        val tokens = kinds("html", "<a href=\"x\">hi</a><!-- c -->")
        assertEquals(
            listOf(
                "<a" to TokenKind.KEYWORD,
                "href" to TokenKind.TYPE,
                "=" to TokenKind.PLAIN,
                "\"x\"" to TokenKind.STRING,
                ">" to TokenKind.KEYWORD,
                "hi" to TokenKind.PLAIN,
                "</a>" to TokenKind.KEYWORD,
                "<!-- c -->" to TokenKind.COMMENT,
            ),
            tokens,
        )
    }

    @Test
    fun aGapPlaceholderCountsAsAWord() {
        val spans = highlight("python", "x = \"a${GAP_PLACEHOLDER}b\" + ${GAP_PLACEHOLDER}")
        assertEquals(2, spans.joinToString("") { it.text }.count { it == GAP_PLACEHOLDER })
        assertEquals(TokenKind.STRING, spans.first { it.text.contains('"') }.kind)
    }

    @Test
    fun hugeInputStaysFast() {
        val input = "def f(x):\n    return \"s\" + 'c' # c\n".repeat(20_000)
        for (language in listOf("python", "javascript", "html", "css", "sql")) {
            assertEquals(input, highlight(language, input).joinToString("") { it.text })
        }
    }
}
