package ch.orellbuehler.jackdaw.format

internal object AuthoringPrompt {
    private val typeLine = Regex("""^\d+\. "([a-z]+)": """)
    private val exampleItem = Regex("""^ {4}\{ "id": "[^"]+", "type": "([a-z]+)"""")
    private const val PREFERRED_TYPES = "Preferred item types: "
    private val FIGURE_HEADINGS = listOf("FIGURES", "MAPS", "CODE BLOCKS")
    private const val EXAMPLE_ITEMS = "  \"items\": ["
    private const val EXAMPLE_ITEMS_END = "  ]"
    private const val VERSION_WITH_FIGURES =
        "(required; the number 1, or 2 if any item has a \"figure\" or \"answerFigure\", or 3 if any item has a \"map\" or \"answerMap\", or 4 if any text contains a code block)"
    private const val VERSION_WITHOUT_FIGURES = "(required, always the number 1)"
    private const val EXAMPLE_VERSION = "  \"version\": 2,"
    private val exampleFigure = Regex("""^ +"(figure|answerFigure)": """)

    fun build(
        source: String,
        typeCodes: Collection<String>,
        figures: Boolean,
    ): String {
        val all = ItemType.entries.map { it.code }
        val types = all.filter { it in typeCodes }.ifEmpty { all }
        val lines = source.lines()
        val out = ArrayList<String>(lines.size)
        var number = 0
        var i = 0
        while (i < lines.size) {
            val line = lines[i++]
            val type = typeLine.find(line)?.groupValues?.get(1)
            when {
                line.startsWith(PREFERRED_TYPES) && types.size < all.size ->
                    out += "Item types: use only ${quotedList(types)}. Do not use any other item type."
                !figures && FIGURE_HEADINGS.any { line.startsWith(it) } -> {
                    while (i < lines.size && lines[i].isNotBlank()) i++
                    i++
                }
                type != null -> if (type in types) out += "${++number}." + line.substringAfter('.')
                line == EXAMPLE_ITEMS -> {
                    out += line
                    val items = ArrayList<Pair<String, ArrayList<String>>>()
                    while (i < lines.size && lines[i] != EXAMPLE_ITEMS_END) {
                        val start = exampleItem.find(lines[i])?.groupValues?.get(1)
                        if (start != null) {
                            items += start to ArrayList()
                        } else if (items.isEmpty()) {
                            break
                        }
                        items.last().second += lines[i++]
                    }
                    val kept =
                        items.filter { it.first in types }.map { item ->
                            val plain = item.second.filterNot { !figures && exampleFigure.containsMatchIn(it) }
                            val text = plain.joinToString("\n").trimEnd().removeSuffix(",")
                            if (plain.size < item.second.size) "$text }" else text
                        }
                    out += kept.joinToString(",\n")
                }
                !figures && line == EXAMPLE_VERSION -> out += line.replace('2', '1')
                else -> out += if (figures) line else line.replace(VERSION_WITH_FIGURES, VERSION_WITHOUT_FIGURES)
            }
        }
        return out.joinToString("\n")
    }

    private fun quotedList(types: List<String>): String {
        val quoted = types.map { "\"$it\"" }
        return if (quoted.size == 1) quoted[0] else quoted.dropLast(1).joinToString(", ") + " and " + quoted.last()
    }
}
