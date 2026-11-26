package ch.orellbuehler.jackdaw.format

enum class TokenKind {
    PLAIN,
    KEYWORD,
    STRING,
    COMMENT,
    NUMBER,
    TYPE,
}

data class CodeSpan(
    val text: String,
    val kind: TokenKind,
)

internal const val GAP_PLACEHOLDER = '\uE000'

internal class StringDef(
    val open: String,
    val close: String = open,
    val escapes: Boolean = true,
    val multiline: Boolean = false,
    val doubledClose: Boolean = false,
)

internal class LangDef(
    val keywords: Set<String>,
    val types: Set<String> = emptySet(),
    val lineComments: List<String> = emptyList(),
    val blockComments: List<Pair<String, String>> = emptyList(),
    val strings: List<StringDef> = emptyList(),
    val caseInsensitive: Boolean = false,
    val capitalTypes: Boolean = false,
    val annotations: Boolean = false,
    val directives: Boolean = false,
    val variables: Boolean = false,
    val dashIdentifiers: Boolean = false,
    val hashColors: Boolean = false,
    val atKeywords: Boolean = false,
    val propertyNames: Boolean = false,
    val keyStrings: Boolean = false,
    val lifetimes: Boolean = false,
    val commentNeedsBoundary: Boolean = false,
)

private fun words(text: String): Set<String> = text.split(' ', '\n').filter { it.isNotEmpty() }.toSet()

private val SLASH_COMMENTS = listOf("//")
private val C_BLOCK = listOf("/*" to "*/")
private val DOUBLE_QUOTED = StringDef("\"")
private val SINGLE_QUOTED = StringDef("'")

private val PYTHON =
    LangDef(
        keywords =
            words(
                "False None True and as assert async await break class continue def del elif else except finally for from global " +
                    "if import in is lambda nonlocal not or pass raise return try while with yield self cls match case",
            ),
        types = words("int float str bool list dict set frozenset tuple bytes bytearray object complex range type"),
        lineComments = listOf("#"),
        strings =
            listOf(
                StringDef("\"\"\"", multiline = true),
                StringDef("'''", multiline = true),
                DOUBLE_QUOTED,
                SINGLE_QUOTED,
            ),
        capitalTypes = true,
        annotations = true,
    )

private val JAVASCRIPT_KEYWORDS =
    "async await break case catch class const continue debugger default delete do else export extends false finally for from " +
        "function if import in instanceof let new null of return static super switch this throw true try typeof undefined " +
        "var void while with yield get set"

private val JAVASCRIPT =
    LangDef(
        keywords = words(JAVASCRIPT_KEYWORDS),
        types = words("Array Object String Number Boolean Map Set Promise Date Math JSON Error RegExp Symbol console"),
        lineComments = SLASH_COMMENTS,
        blockComments = C_BLOCK,
        strings = listOf(DOUBLE_QUOTED, SINGLE_QUOTED, StringDef("`", multiline = true)),
        capitalTypes = true,
    )

private val TYPESCRIPT =
    LangDef(
        keywords =
            words(
                "$JAVASCRIPT_KEYWORDS interface type enum implements namespace declare abstract readonly public private protected " +
                    "as is keyof infer never unknown any satisfies",
            ),
        types = words("string number boolean object void symbol bigint Array Object String Number Boolean Map Set Promise Record"),
        lineComments = SLASH_COMMENTS,
        blockComments = C_BLOCK,
        strings = listOf(DOUBLE_QUOTED, SINGLE_QUOTED, StringDef("`", multiline = true)),
        capitalTypes = true,
        annotations = true,
    )

private val KOTLIN =
    LangDef(
        keywords =
            words(
                "as break by catch class companion const constructor continue data do else enum false finally for fun if import in " +
                    "init inline interface internal is it lateinit lazy null object open operator override package private protected " +
                    "public reified return sealed super suspend this throw true try typealias val var vararg when where while",
            ),
        types = words("Int Long Short Byte Float Double Boolean Char String Unit Any Nothing List Map Set Array"),
        lineComments = SLASH_COMMENTS,
        blockComments = C_BLOCK,
        strings = listOf(StringDef("\"\"\"", multiline = true), DOUBLE_QUOTED, SINGLE_QUOTED),
        capitalTypes = true,
        annotations = true,
    )

private val JAVA =
    LangDef(
        keywords =
            words(
                "abstract assert break case catch class const continue default do else enum extends false final finally for goto if " +
                    "implements import instanceof interface native new null package private protected public return static strictfp " +
                    "super switch synchronized this throw throws transient true try var void volatile while record sealed permits yield",
            ),
        types = words("int long short byte float double boolean char String Object Integer Long Double Boolean List Map Set"),
        lineComments = SLASH_COMMENTS,
        blockComments = C_BLOCK,
        strings = listOf(StringDef("\"\"\"", multiline = true), DOUBLE_QUOTED, SINGLE_QUOTED),
        capitalTypes = true,
        annotations = true,
    )

private val SWIFT =
    LangDef(
        keywords =
            words(
                "actor as associatedtype async await break case catch class continue default defer deinit do else enum extension " +
                    "fallthrough false fileprivate final for func guard if import in indirect init inout internal is let nil open " +
                    "operator override private protocol public repeat rethrows return self Self static struct subscript super switch " +
                    "throw throws true try typealias var where while some any",
            ),
        types = words("Int Double Float Bool String Character Array Dictionary Set Optional Void Any"),
        lineComments = SLASH_COMMENTS,
        blockComments = C_BLOCK,
        strings = listOf(StringDef("\"\"\"", multiline = true), DOUBLE_QUOTED),
        capitalTypes = true,
        annotations = true,
    )

private val C_KEYWORDS =
    "auto break case const continue default do else enum extern for goto if inline register restrict return sizeof static " +
        "struct switch typedef union volatile while NULL true false"

private val C_LANG =
    LangDef(
        keywords = words(C_KEYWORDS),
        types =
            words(
                "int long short char float double void unsigned signed size_t bool uint8_t uint16_t uint32_t uint64_t int8_t int16_t int32_t int64_t FILE",
            ),
        lineComments = SLASH_COMMENTS,
        blockComments = C_BLOCK,
        strings = listOf(DOUBLE_QUOTED, SINGLE_QUOTED),
        directives = true,
    )

private val CPP =
    LangDef(
        keywords =
            words(
                "$C_KEYWORDS alignas alignof and asm catch class constexpr const_cast decltype delete dynamic_cast explicit " +
                    "export friend mutable namespace new noexcept nullptr operator or override private protected public " +
                    "reinterpret_cast static_assert static_cast template this throw try typeid typename using virtual final not",
            ),
        types =
            words(
                "int long short char float double void unsigned signed size_t bool string vector map set unique_ptr shared_ptr " +
                    "wchar_t uint8_t uint16_t uint32_t uint64_t int8_t int16_t int32_t int64_t auto",
            ),
        lineComments = SLASH_COMMENTS,
        blockComments = C_BLOCK,
        strings = listOf(DOUBLE_QUOTED, SINGLE_QUOTED),
        directives = true,
    )

private val CSHARP =
    LangDef(
        keywords =
            words(
                "abstract as base break case catch checked class const continue default delegate do else enum event explicit " +
                    "extern false finally fixed for foreach goto if implicit in interface internal is lock namespace new null " +
                    "operator out override params private protected public readonly ref return sealed sizeof stackalloc static " +
                    "struct switch this throw true try typeof unchecked unsafe using virtual volatile while var async await " +
                    "record init get set yield where when",
            ),
        types = words("int long short byte sbyte uint ulong ushort float double decimal bool char string object void dynamic"),
        lineComments = SLASH_COMMENTS,
        blockComments = C_BLOCK,
        strings =
            listOf(
                StringDef("@\"", "\"", escapes = false, multiline = true, doubledClose = true),
                StringDef("\"\"\"", multiline = true),
                DOUBLE_QUOTED,
                SINGLE_QUOTED,
            ),
        capitalTypes = true,
        annotations = false,
    )

private val GO =
    LangDef(
        keywords =
            words(
                "break case chan const continue default defer else fallthrough for func go goto if import interface map package " +
                    "range return select struct switch type var true false nil iota",
            ),
        types =
            words(
                "int int8 int16 int32 int64 uint uint8 uint16 uint32 uint64 uintptr float32 float64 complex64 complex128 " +
                    "string bool byte rune error any",
            ),
        lineComments = SLASH_COMMENTS,
        blockComments = C_BLOCK,
        strings = listOf(DOUBLE_QUOTED, SINGLE_QUOTED, StringDef("`", escapes = false, multiline = true)),
    )

private val RUST =
    LangDef(
        keywords =
            words(
                "as async await break const continue crate dyn else enum extern false fn for if impl in let loop match mod move " +
                    "mut pub ref return self Self static struct super trait true type unsafe use where while",
            ),
        types =
            words(
                "i8 i16 i32 i64 i128 isize u8 u16 u32 u64 u128 usize f32 f64 bool char str String Vec Option Result Box Some None Ok Err",
            ),
        lineComments = SLASH_COMMENTS,
        blockComments = C_BLOCK,
        strings = listOf(StringDef("\"", multiline = true)),
        capitalTypes = true,
        lifetimes = true,
    )

private val SQL =
    LangDef(
        keywords =
            words(
                "select from where and or not null is in like between exists insert into values update set delete create table " +
                    "alter drop add column index view join inner outer left right full cross on as group by having order asc desc " +
                    "limit offset union all distinct case when then else end primary key foreign references unique default check " +
                    "constraint begin commit rollback transaction with recursive true false over partition returning using " +
                    "truncate grant revoke if cascade",
            ),
        types =
            words(
                "int integer bigint smallint tinyint decimal numeric float real double boolean bool char varchar text date time " +
                    "timestamp datetime blob json uuid serial",
            ),
        lineComments = listOf("--"),
        blockComments = C_BLOCK,
        strings = listOf(StringDef("'", doubledClose = true, escapes = false, multiline = true), StringDef("\"", multiline = true)),
        caseInsensitive = true,
    )

private val BASH =
    LangDef(
        keywords =
            words(
                "if then else elif fi for while until do done case esac in function select time return exit break continue " +
                    "local export readonly declare unset source alias true false echo cd set shift trap eval exec",
            ),
        lineComments = listOf("#"),
        strings = listOf(StringDef("\"", multiline = true), StringDef("'", escapes = false, multiline = true)),
        variables = true,
        commentNeedsBoundary = true,
    )

private val JSON =
    LangDef(
        keywords = words("true false null"),
        strings = listOf(DOUBLE_QUOTED),
        keyStrings = true,
    )

private val CSS =
    LangDef(
        keywords = words("important inherit initial unset none auto"),
        blockComments = C_BLOCK,
        strings = listOf(DOUBLE_QUOTED, SINGLE_QUOTED),
        dashIdentifiers = true,
        hashColors = true,
        atKeywords = true,
        propertyNames = true,
    )

private val PLAIN_HTML = LangDef(keywords = emptySet())

private val LANGUAGES: Map<String, LangDef> =
    mapOf(
        "python" to PYTHON,
        "javascript" to JAVASCRIPT,
        "typescript" to TYPESCRIPT,
        "kotlin" to KOTLIN,
        "java" to JAVA,
        "swift" to SWIFT,
        "c" to C_LANG,
        "cpp" to CPP,
        "csharp" to CSHARP,
        "go" to GO,
        "rust" to RUST,
        "sql" to SQL,
        "bash" to BASH,
        "json" to JSON,
        "css" to CSS,
        "html" to PLAIN_HTML,
    )

private val ALIASES: Map<String, String> =
    mapOf(
        "py" to "python",
        "python3" to "python",
        "js" to "javascript",
        "jsx" to "javascript",
        "node" to "javascript",
        "mjs" to "javascript",
        "ts" to "typescript",
        "tsx" to "typescript",
        "kt" to "kotlin",
        "kts" to "kotlin",
        "h" to "c",
        "c++" to "cpp",
        "cc" to "cpp",
        "cxx" to "cpp",
        "hpp" to "cpp",
        "cs" to "csharp",
        "c#" to "csharp",
        "golang" to "go",
        "rs" to "rust",
        "sh" to "bash",
        "shell" to "bash",
        "zsh" to "bash",
        "htm" to "html",
        "xml" to "html",
    )

// The language an app would highlight for the identifier written after the opening fence, or null.
internal fun canonicalLanguage(identifier: String?): String? {
    if (identifier.isNullOrEmpty()) return null
    val key = identifier.lowercase()
    val name = ALIASES[key] ?: key
    return if (name in LANGUAGES) name else null
}

internal fun highlight(
    language: String?,
    code: String,
): List<CodeSpan> {
    val name = canonicalLanguage(language) ?: return if (code.isEmpty()) emptyList() else listOf(CodeSpan(code, TokenKind.PLAIN))
    if (code.isEmpty()) return emptyList()
    val out = SpanBuilder()
    if (name == "html") tokenizeHtml(code, out) else Tokenizer(code, LANGUAGES.getValue(name), out).run()
    return out.build()
}

private class SpanBuilder {
    private val spans = ArrayList<CodeSpan>()
    private var kind = TokenKind.PLAIN
    private val text = StringBuilder()

    fun add(
        source: String,
        start: Int,
        end: Int,
        kind: TokenKind,
    ) {
        if (end <= start) return
        if (kind != this.kind && text.isNotEmpty()) flush()
        this.kind = kind
        text.append(source, start, end)
    }

    private fun flush() {
        if (text.isNotEmpty()) spans += CodeSpan(text.toString(), kind)
        text.clear()
    }

    fun build(): List<CodeSpan> {
        flush()
        return spans
    }
}

private fun isIdentStart(c: Char): Boolean = c.isLetter() || c == '_' || c == '$' || c == GAP_PLACEHOLDER

private fun isIdentPart(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '$' || c == GAP_PLACEHOLDER

private fun isDigit(c: Char): Boolean = c in '0'..'9'

private class Tokenizer(
    private val s: String,
    private val def: LangDef,
    private val out: SpanBuilder,
) {
    private val n = s.length
    private var i = 0

    fun run() {
        while (i < n) {
            val start = i
            val c = s[i]
            when {
                c == '\n' || c == '\r' || c == ' ' || c == '\t' -> {
                    while (i < n && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n' || s[i] == '\r')) i++
                    out.add(s, start, i, TokenKind.PLAIN)
                }
                lineComment() -> out.add(s, start, i, TokenKind.COMMENT)
                blockComment() -> out.add(s, start, i, TokenKind.COMMENT)
                string() -> out.add(s, start, i, stringKind(start))
                c == '\'' && def.lifetimes && rustQuote() -> out.add(s, start, i, quoteKind)
                isDigit(c) || (c == '.' && i + 1 < n && isDigit(s[i + 1])) -> {
                    number()
                    out.add(s, start, i, TokenKind.NUMBER)
                }
                c == '#' && def.hashColors -> {
                    i++
                    while (i < n && (isHex(s[i]))) i++
                    out.add(s, start, i, if (i - start > 1) TokenKind.NUMBER else TokenKind.PLAIN)
                }
                c == '#' && def.directives && atLineStart(start) -> {
                    i++
                    while (i < n && (s[i] == ' ' || s[i] == '\t')) i++
                    while (i < n && s[i].isLetter()) i++
                    out.add(s, start, i, TokenKind.KEYWORD)
                }
                (c == '@' && (def.annotations || def.atKeywords)) && i + 1 < n && isIdentStart(s[i + 1]) -> {
                    i++
                    while (i < n && (isIdentPart(s[i]) || (def.dashIdentifiers && s[i] == '-'))) i++
                    out.add(s, start, i, if (def.atKeywords) TokenKind.KEYWORD else TokenKind.TYPE)
                }
                c == '$' && def.variables -> {
                    variable()
                    out.add(s, start, i, TokenKind.TYPE)
                }
                isIdentStart(c) || (def.dashIdentifiers && c == '-' && i + 1 < n && isIdentStart(s[i + 1])) -> {
                    val kind = identifier()
                    out.add(s, start, i, kind)
                }
                else -> {
                    i++
                    out.add(s, start, i, TokenKind.PLAIN)
                }
            }
        }
    }

    private var quoteKind = TokenKind.STRING

    private fun isHex(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    private fun atLineStart(pos: Int): Boolean {
        var k = pos - 1
        while (k >= 0 && (s[k] == ' ' || s[k] == '\t')) k--
        return k < 0 || s[k] == '\n' || s[k] == '\r'
    }

    private fun lineComment(): Boolean {
        for (marker in def.lineComments) {
            if (!s.startsWith(marker, i)) continue
            if (def.commentNeedsBoundary && i > 0 && !(s[i - 1] == ' ' || s[i - 1] == '\t' || s[i - 1] == '\n' || s[i - 1] == ';')) continue
            while (i < n && s[i] != '\n' && s[i] != '\r') i++
            return true
        }
        return false
    }

    private fun blockComment(): Boolean {
        for ((open, close) in def.blockComments) {
            if (!s.startsWith(open, i)) continue
            val end = s.indexOf(close, i + open.length)
            i = if (end < 0) n else end + close.length
            return true
        }
        return false
    }

    private fun string(): Boolean {
        for (d in def.strings) {
            if (!s.startsWith(d.open, i)) continue
            i += d.open.length
            scanString(d)
            return true
        }
        return false
    }

    private fun scanString(d: StringDef) {
        while (i < n) {
            val c = s[i]
            if (c == '\n' || c == '\r') {
                if (d.multiline) {
                    i++
                    continue
                }
                return
            }
            if (d.escapes && c == '\\') {
                i++
                if (i < n && s[i] != '\n' && s[i] != '\r') i++
                continue
            }
            if (s.startsWith(d.close, i)) {
                i += d.close.length
                if (d.doubledClose && s.startsWith(d.close, i)) {
                    i += d.close.length
                    continue
                }
                return
            }
            i++
        }
    }

    private fun stringKind(start: Int): TokenKind {
        if (!def.keyStrings) return TokenKind.STRING
        var k = i
        while (k < n && (s[k] == ' ' || s[k] == '\t')) k++
        return if (k < n && s[k] == ':' && s[start] == '"') TokenKind.TYPE else TokenKind.STRING
    }

    // a quote in Rust is a char literal ('a', '\n') or a lifetime ('a)
    private fun rustQuote(): Boolean {
        val start = i
        if (i + 1 < n && s[i + 1] == '\\') {
            var k = i + 2
            while (k < n && k < i + 12 && s[k] != '\'' && s[k] != '\n') k++
            if (k < n && s[k] == '\'') {
                i = k + 1
                quoteKind = TokenKind.STRING
                return true
            }
        } else if (i + 2 < n && s[i + 2] == '\'' && s[i + 1] != '\n') {
            i += 3
            quoteKind = TokenKind.STRING
            return true
        } else if (i + 1 < n && isIdentStart(s[i + 1])) {
            i++
            while (i < n && isIdentPart(s[i])) i++
            quoteKind = TokenKind.TYPE
            return true
        }
        i = start + 1
        quoteKind = TokenKind.PLAIN
        return true
    }

    private fun number() {
        if (s[i] == '.') i++
        while (i < n) {
            val c = s[i]
            if (c.isLetterOrDigit() || c == '_') {
                i++
            } else if (c == '.' && i + 1 < n && isDigit(s[i + 1]) && s[i - 1] != '.') {
                i++
            } else {
                break
            }
        }
        if (def.dashIdentifiers && i < n && s[i] == '%') i++
    }

    private fun variable() {
        i++
        if (i < n && s[i] == '{') {
            var k = i + 1
            while (k < n && k < i + 64 && s[k] != '}' && s[k] != '\n') k++
            i = if (k < n && s[k] == '}') k + 1 else i + 1
            return
        }
        if (i < n && (s[i] == '(')) return
        if (i < n && (isDigit(s[i]) || s[i] == '?' || s[i] == '@' || s[i] == '*' || s[i] == '#' || s[i] == '!' || s[i] == '$')) {
            i++
            return
        }
        while (i < n && (s[i].isLetterOrDigit() || s[i] == '_' || s[i] == GAP_PLACEHOLDER)) i++
    }

    private fun identifier(): TokenKind {
        val start = i
        i++
        while (i < n && (isIdentPart(s[i]) || (def.dashIdentifiers && s[i] == '-' && i + 1 < n && isIdentPart(s[i + 1])))) i++
        val word = s.substring(start, i)
        val key = if (def.caseInsensitive) word.lowercase() else word
        if (key in def.keywords) return TokenKind.KEYWORD
        if (key in def.types) return TokenKind.TYPE
        if (def.propertyNames && followedByColon()) return TokenKind.TYPE
        if (def.capitalTypes && word[0].isUpperCase()) return TokenKind.TYPE
        return TokenKind.PLAIN
    }

    private fun followedByColon(): Boolean {
        var k = i
        while (k < n && (s[k] == ' ' || s[k] == '\t')) k++
        return k < n && s[k] == ':' && !(k + 1 < n && s[k + 1] == ':')
    }
}

private fun tokenizeHtml(
    s: String,
    out: SpanBuilder,
) {
    val n = s.length
    var i = 0
    while (i < n) {
        val start = i
        if (s.startsWith("<!--", i)) {
            val end = s.indexOf("-->", i + 4)
            i = if (end < 0) n else end + 3
            out.add(s, start, i, TokenKind.COMMENT)
        } else if (s[i] == '<' && i + 1 < n && (s[i + 1] == '/' || s[i + 1] == '!' || s[i + 1].isLetter())) {
            i++
            if (s[i] == '/' || s[i] == '!') i++
            while (i < n && (s[i].isLetterOrDigit() || s[i] == '-' || s[i] == ':' || s[i] == '_')) i++
            out.add(s, start, i, TokenKind.KEYWORD)
            i = htmlTag(s, i, out)
        } else {
            i++
            while (i < n && s[i] != '<') i++
            out.add(s, start, i, TokenKind.PLAIN)
        }
    }
}

// the inside of a tag after its name, up to and including the closing ">"; returns the new position
private fun htmlTag(
    s: String,
    from: Int,
    out: SpanBuilder,
): Int {
    val n = s.length
    var i = from
    while (i < n) {
        val start = i
        val c = s[i]
        when {
            c == '>' -> {
                out.add(s, start, i + 1, TokenKind.KEYWORD)
                return i + 1
            }
            c == '/' && i + 1 < n && s[i + 1] == '>' -> {
                out.add(s, start, i + 2, TokenKind.KEYWORD)
                return i + 2
            }
            c == '"' || c == '\'' -> {
                i++
                while (i < n && s[i] != c) i++
                if (i < n) i++
                out.add(s, start, i, TokenKind.STRING)
            }
            c == '<' -> return i
            c.isLetter() || c == '_' || c == '@' || c == ':' -> {
                i++
                while (i < n && (s[i].isLetterOrDigit() || s[i] == '-' || s[i] == '_' || s[i] == ':' || s[i] == '.')) i++
                out.add(s, start, i, TokenKind.TYPE)
            }
            else -> {
                i++
                out.add(s, start, i, TokenKind.PLAIN)
            }
        }
    }
    return i
}
