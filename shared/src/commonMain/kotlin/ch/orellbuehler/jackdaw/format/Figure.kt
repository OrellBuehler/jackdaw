package ch.orellbuehler.jackdaw.format

data class Figure(
    val svg: String,
    val alt: String?,
    val aspectRatio: Double,
    val isMap: Boolean = false,
) {
    fun html(textColor: String?): String {
        val color = if (textColor != null) "color:$textColor" else "color-scheme:light dark;color:CanvasText"
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,minimum-scale=1,maximum-scale=10,user-scalable=yes\">" +
            "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'\">" +
            "<style>html,body{margin:0;padding:0;height:100%;background:transparent;$color}" +
            "body>svg{display:block;width:100%;height:100%}</style></head><body>$svg</body></html>"
    }
}

internal object FigureRules {
    const val MAX_LENGTH = 200_000

    private val ELEMENTS =
        setOf(
            "svg",
            "g",
            "defs",
            "symbol",
            "use",
            "title",
            "desc",
            "path",
            "rect",
            "circle",
            "ellipse",
            "line",
            "polyline",
            "polygon",
            "text",
            "tspan",
            "textPath",
            "linearGradient",
            "radialGradient",
            "stop",
            "pattern",
            "clipPath",
            "mask",
            "marker",
            "style",
            "animate",
            "animateTransform",
            "animateMotion",
            "mpath",
            "set",
            "filter",
            "feBlend",
            "feColorMatrix",
            "feComponentTransfer",
            "feComposite",
            "feConvolveMatrix",
            "feDiffuseLighting",
            "feDisplacementMap",
            "feDistantLight",
            "feDropShadow",
            "feFlood",
            "feFuncA",
            "feFuncB",
            "feFuncG",
            "feFuncR",
            "feGaussianBlur",
            "feMerge",
            "feMergeNode",
            "feMorphology",
            "feOffset",
            "fePointLight",
            "feSpecularLighting",
            "feSpotLight",
            "feTile",
            "feTurbulence",
        )
    private val FORBIDDEN = listOf("<!doctype", "<!entity", "javascript:", "@import")
    private const val SPACE = " \t\n\r\u000C"
    private const val BEFORE_ATTRIBUTE = "$SPACE\"'/"
    private const val NUMBER_CHARS = "0123456789+-.eE"

    class Checked(
        val svg: String,
        val aspectRatio: Double,
    )

    fun read(
        text: String,
        alt: String?,
    ): Figure? = check(text)?.let { Figure(it.svg, alt, it.aspectRatio) }

    fun check(text: String): Checked? {
        if (text.length > MAX_LENGTH) return null
        var svg = text.trim { it in SPACE }
        if (svg.startsWith("<?xml")) {
            val end = svg.indexOf("?>")
            if (end < 0) return null
            svg = svg.substring(end + 2).trim { it in SPACE }
        }
        val lower = svg.lowercase()
        if (lower.contains("<?") || FORBIDDEN.any { lower.contains(it) }) return null
        val root = rootTag(svg) ?: return null
        val box = viewBox(root) ?: return null
        if (hasEventHandler(lower) || hasForeignReference(lower, "href", true) || hasForeignReference(lower, "url(", false)) return null
        return Checked(svg, box[2] / box[3])
    }

    private fun rootTag(svg: String): String? {
        var root: String? = null
        var at = svg.indexOf('<')
        while (at >= 0) {
            if (!svg.startsWith("<!--", at) && !svg.startsWith("<![CDATA[", at)) {
                var start = at + 1
                val closing = start < svg.length && svg[start] == '/'
                if (closing) start++
                var end = start
                while (end < svg.length && (svg[end].isAsciiLetterOrDigit() || svg[end] in ":_.-")) end++
                val name = svg.substring(start, end)
                if (name !in ELEMENTS) return null
                if (root == null) {
                    val close = svg.indexOf('>', end)
                    if (closing || name != "svg" || close < 0) return null
                    root = svg.substring(end, close)
                }
            }
            at = svg.indexOf('<', at + 1)
        }
        return root
    }

    private fun Char.isAsciiLetterOrDigit(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    private fun viewBox(root: String): DoubleArray? {
        var at = root.indexOf("viewBox")
        while (at >= 0) {
            if (at > 0 && root[at - 1] in SPACE) {
                var i = skipSpace(root, at + "viewBox".length)
                if (i < root.length && root[i] == '=') {
                    i = skipSpace(root, i + 1)
                    val quote = root.getOrNull(i)
                    val end = if (quote == '"' || quote == '\'') root.indexOf(quote, i + 1) else -1
                    if (end < 0) return null
                    val parts = root.substring(i + 1, end).split(' ', '\t', '\n', '\r', '\u000C', ',').filter { it.isNotEmpty() }
                    if (parts.size != 4 || parts.any { part -> part.any { it !in NUMBER_CHARS } }) return null
                    val numbers = parts.map { it.toDoubleOrNull() ?: return null }
                    if (numbers.any { !it.isFinite() } || numbers[2] <= 0.0 || numbers[3] <= 0.0) return null
                    return numbers.toDoubleArray()
                }
            }
            at = root.indexOf("viewBox", at + 1)
        }
        return null
    }

    private fun skipSpace(
        text: String,
        from: Int,
    ): Int {
        var i = from
        while (i < text.length && text[i] in SPACE) i++
        return i
    }

    private fun hasEventHandler(lower: String): Boolean {
        var at = lower.indexOf("on")
        while (at >= 0) {
            if (at > 0 && lower[at - 1] in BEFORE_ATTRIBUTE) {
                var i = at + 2
                while (i < lower.length && lower[i] in 'a'..'z') i++
                if (i > at + 2 && lower.getOrNull(skipSpace(lower, i)) == '=') return true
            }
            at = lower.indexOf("on", at + 1)
        }
        return false
    }

    private fun hasForeignReference(
        lower: String,
        marker: String,
        attribute: Boolean,
    ): Boolean {
        var at = lower.indexOf(marker)
        while (at >= 0) {
            var i = skipSpace(lower, at + marker.length)
            var isReference = true
            if (attribute) {
                isReference = lower.getOrNull(i) == '='
                i = skipSpace(lower, i + 1)
                if (isReference && lower.getOrNull(i) != '"' && lower.getOrNull(i) != '\'') return true
            }
            if (isReference) {
                if (lower.getOrNull(i) == '"' || lower.getOrNull(i) == '\'') i = skipSpace(lower, i + 1)
                if (lower.getOrNull(i) != '#') return true
            }
            at = lower.indexOf(marker, at + 1)
        }
        return false
    }
}
