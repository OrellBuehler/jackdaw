package ch.orellbuehler.jackdaw.format

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal sealed class ReadResult {
    class Accepted(
        val pack: Pack,
        val payloads: List<String>,
        val warnings: List<ImportWarning>,
        val skippedItems: Int,
    ) : ReadResult()

    class Rejected(
        val error: ImportError,
    ) : ReadResult()
}

internal class Warnings {
    private val list = ArrayList<ImportWarning>()
    private var overflow = 0
    private val unknownTypes = LinkedHashMap<String, Int>()
    var skippedItems = 0
        private set

    fun add(
        code: ImportWarningCode,
        message: String,
        itemId: String? = null,
        itemIndex: Int = -1,
        detail: String? = null,
        count: Int = 1,
    ) {
        if (list.size < MAX_DETAILED) {
            list += ImportWarning(code, message, itemId, itemIndex, detail, count)
        } else {
            overflow++
        }
    }

    fun skipItem(
        message: String,
        itemId: String?,
        itemIndex: Int,
        detail: String?,
        code: ImportWarningCode = ImportWarningCode.ITEM_SKIPPED,
    ) {
        skippedItems++
        add(code, message, itemId, itemIndex, detail)
    }

    fun unknownType(type: String) {
        skippedItems++
        unknownTypes[type] = (unknownTypes[type] ?: 0) + 1
    }

    fun build(): List<ImportWarning> {
        val result = ArrayList<ImportWarning>(list)
        for ((type, count) in unknownTypes) {
            result +=
                ImportWarning(
                    ImportWarningCode.ITEM_UNKNOWN_TYPE,
                    "Skipped $count ${if (count == 1) "item" else "items"} of unknown type '$type'.",
                    null,
                    -1,
                    type,
                    count,
                )
        }
        if (overflow > 0) {
            result += ImportWarning(ImportWarningCode.MORE_WARNINGS, "$overflow more warnings not shown.", null, -1, null, overflow)
        }
        return result
    }

    private companion object {
        const val MAX_DETAILED = 100
    }
}

internal object PackReader {
    const val SUPPORTED_VERSION = 1
    private const val MAX_CHARS = 25 * 1024 * 1024
    private const val MAX_ITEMS = 50_000
    private const val MAX_DEPTH = 64
    private val colorPattern = Regex("^#[0-9A-Fa-f]{6}$")
    private val datePattern = Regex("""^\d{4}-\d{2}-\d{2}(T\d{2}:\d{2}(:\d{2}(\.\d+)?)?(Z|[+-]\d{2}:\d{2}))?$""")

    fun read(source: String): ReadResult {
        if (source.length > MAX_CHARS) return reject(ImportErrorCode.TOO_LARGE, "The file is larger than 25 MB.")
        val text = source.removePrefix("\uFEFF")
        if (exceedsDepth(text)) return reject(ImportErrorCode.NOT_JSON, "The file is nested too deeply to be a pack.")
        val root =
            try {
                Json.parseToJsonElement(text)
            } catch (e: Exception) {
                return reject(ImportErrorCode.NOT_JSON, "The file is not valid JSON.")
            }
        if (root is JsonPrimitive &&
            !root.isString &&
            root.content !in setOf("true", "false", "null") &&
            root.content.toDoubleOrNull() == null
        ) {
            return reject(ImportErrorCode.NOT_JSON, "The file is not valid JSON.")
        }
        if (root !is JsonObject) return reject(ImportErrorCode.NOT_AN_OBJECT, "The top level of a pack must be a JSON object.")
        if (root["format"].asString() != "jackdaw-pack") {
            return reject(ImportErrorCode.NOT_A_PACK, "This is not a Jackdaw pack: 'format' must be \"jackdaw-pack\".")
        }
        val version = root["version"].asInt()
        if (version == null || version < 1) {
            return reject(ImportErrorCode.VERSION_INVALID, "'version' must be an integer of at least 1.")
        }
        if (version > SUPPORTED_VERSION) {
            return reject(
                ImportErrorCode.VERSION_TOO_NEW,
                "This pack uses format version $version, but this app supports up to version $SUPPORTED_VERSION. Please update the app.",
            )
        }
        val id = root["id"].asString()
        if (id == null || !isValidId(id, 128)) {
            return reject(ImportErrorCode.ID_INVALID, "'id' is missing or invalid (1 to 128 characters of A-Z a-z 0-9 . _ -).")
        }
        val title = root["title"].asString()?.trimSpec()
        if (title.isNullOrEmpty()) return reject(ImportErrorCode.TITLE_INVALID, "'title' is missing or blank.")
        val itemsElement = root["items"]
        if (itemsElement !is JsonArray) return reject(ImportErrorCode.ITEMS_MISSING, "'items' is missing or not an array.")
        if (itemsElement.size > MAX_ITEMS) {
            return reject(ImportErrorCode.TOO_MANY_ITEMS, "The pack has more than $MAX_ITEMS items. Please split it into several packs.")
        }

        val warnings = Warnings()
        val packProps = Props(root, warnings, null, -1, "")
        val sections = readSections(root, warnings)
        val study = readStudy(root, warnings)
        val reader = ItemReader(study, sections.map { it.id }.toSet(), warnings)
        val items = ArrayList<Item>()
        val payloads = ArrayList<String>()
        val seenIds = HashSet<String>()
        itemsElement.forEachIndexed { index, element ->
            val item = reader.read(index, element, seenIds)
            if (item != null) {
                items += item
                payloads += element.toString()
            }
        }
        if (items.isEmpty()) {
            return reject(ImportErrorCode.NO_VALID_ITEMS, "The pack contains no valid items.")
        }

        val pack =
            Pack(
                id = id,
                title = title,
                description = packProps.optionalText("description"),
                language = packProps.optionalText("language"),
                author = packProps.optionalText("author"),
                license = packProps.optionalText("license"),
                source = packProps.optionalText("source"),
                tags = packProps.stringList("tags"),
                icon = packProps.optionalText("icon")?.let { validIcon(it, warnings) },
                color = packProps.optionalText("color")?.let { validColor(it, warnings) },
                revision = packProps.optionalInt("revision", 1, Int.MAX_VALUE),
                created = packProps.optionalText("created")?.let { validDate("created", it, warnings) },
                updated = packProps.optionalText("updated")?.let { validDate("updated", it, warnings) },
                sections = sections,
                study = study,
                items = items,
            )
        return ReadResult.Accepted(pack, payloads, warnings.build(), warnings.skippedItems)
    }

    fun readStoredItem(
        payload: String,
        study: Study,
        sectionIds: Set<String>,
    ): Item? {
        val element = runCatching { Json.parseToJsonElement(payload) }.getOrNull() ?: return null
        return ItemReader(study, sectionIds, Warnings()).read(0, element, null)
    }

    private fun reject(
        code: ImportErrorCode,
        message: String,
    ): ReadResult = ReadResult.Rejected(ImportError(code, message))

    private fun exceedsDepth(text: String): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (c in text) {
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }
            } else if (c == '"') {
                inString = true
            } else if (c == '{' || c == '[') {
                depth++
                if (depth > MAX_DEPTH) return true
            } else if (c == '}' || c == ']') {
                depth--
            }
        }
        return false
    }

    private fun readSections(
        root: JsonObject,
        warnings: Warnings,
    ): List<SectionDef> {
        val element = root["sections"] ?: return emptyList()
        if (element !is JsonArray) {
            warnings.add(ImportWarningCode.PROPERTY_IGNORED, "Ignored 'sections': expected an array.", detail = "sections")
            return emptyList()
        }
        val result = ArrayList<SectionDef>()
        val seen = HashSet<String>()
        element.forEachIndexed { index, entry ->
            val obj = entry as? JsonObject
            val id = obj?.get("id").asString()
            val title = obj?.get("title").asString()?.trimSpec()
            when {
                obj == null || id == null || !isValidId(id, 64) || title.isNullOrEmpty() ->
                    warnings.add(
                        ImportWarningCode.SECTION_DROPPED,
                        "Dropped section #${index + 1}: it needs an object with a valid 'id' and a non-blank 'title'.",
                        itemIndex = index,
                    )
                !seen.add(id) ->
                    warnings.add(
                        ImportWarningCode.SECTION_DROPPED,
                        "Dropped section #${index + 1}: duplicate id '$id'.",
                        itemId = id,
                        itemIndex = index,
                    )
                else -> {
                    val props = Props(obj, warnings, null, index, "sections[$index].")
                    result += SectionDef(id, title, props.optionalText("description"))
                }
            }
        }
        return result
    }

    private fun readStudy(
        root: JsonObject,
        warnings: Warnings,
    ): Study {
        val element = root["study"] ?: return Study.DEFAULT
        if (element !is JsonObject) {
            warnings.add(ImportWarningCode.PROPERTY_IGNORED, "Ignored 'study': expected an object.", detail = "study")
            return Study.DEFAULT
        }
        val props = Props(element, warnings, null, -1, "study.")
        val orderText = props.optionalText("order")
        val order =
            when (orderText) {
                null -> StudyOrder.SEQUENTIAL
                else ->
                    StudyOrder.entries.firstOrNull { it.code == orderText } ?: run {
                        warnings.add(
                            ImportWarningCode.PROPERTY_IGNORED,
                            "Ignored 'study.order': unknown value '$orderText'.",
                            detail = "study.order",
                        )
                        StudyOrder.SEQUENTIAL
                    }
            }
        var cardExercises = Study.DEFAULT_CARD_EXERCISES
        val exercises = element["exercises"]
        if (exercises != null) {
            if (exercises is JsonObject) {
                val kinds = readCardKinds(exercises["card"], warnings, null, -1, "study.exercises.card")
                if (kinds.isNotEmpty()) cardExercises = kinds
            } else {
                warnings.add(
                    ImportWarningCode.PROPERTY_IGNORED,
                    "Ignored 'study.exercises': expected an object.",
                    detail = "study.exercises",
                )
            }
        }
        return Study(
            order = order,
            newPerSession = props.optionalInt("newPerSession", 1, 500) ?: Study.DEFAULT_NEW_PER_SESSION,
            reversible = props.optionalBool("reversible") ?: false,
            ignoreDiacritics = props.optionalBool("ignoreDiacritics") ?: false,
            cardExercises = cardExercises,
        )
    }

    private fun validIcon(
        icon: String,
        warnings: Warnings,
    ): String? {
        val trimmed = icon.trimSpec()
        val keycap = Regex("^[0-9#*]\uFE0F?\u20E3$")
        val plausible =
            trimmed.isNotEmpty() &&
                trimmed.length <= 16 &&
                (keycap.matches(trimmed) || trimmed.none { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' })
        if (plausible) return trimmed
        warnings.add(ImportWarningCode.PROPERTY_IGNORED, "Ignored 'icon': expected a single emoji.", detail = "icon")
        return null
    }

    private fun validColor(
        color: String,
        warnings: Warnings,
    ): String? {
        if (colorPattern.matches(color)) return color
        warnings.add(ImportWarningCode.PROPERTY_IGNORED, "Ignored 'color': expected #RRGGBB.", detail = "color")
        return null
    }

    private fun validDate(
        name: String,
        value: String,
        warnings: Warnings,
    ): String? {
        if (datePattern.matches(value)) return value
        warnings.add(ImportWarningCode.PROPERTY_IGNORED, "Ignored '$name': expected an ISO 8601 date or date-time.", detail = name)
        return null
    }
}

internal fun readCardKinds(
    element: JsonElement?,
    warnings: Warnings,
    itemId: String?,
    itemIndex: Int,
    path: String,
): List<CardKind> {
    if (element == null) return emptyList()
    if (element !is JsonArray) {
        warnings.add(ImportWarningCode.PROPERTY_IGNORED, "Ignored '$path': expected an array.", itemId, itemIndex, path)
        return emptyList()
    }
    val kinds = ArrayList<CardKind>()
    for (entry in element) {
        val kind = entry.asString()?.let { CardKind.fromCode(it) }
        if (kind == null) {
            warnings.add(ImportWarningCode.ENTRIES_DROPPED, "Dropped an unknown exercise kind from '$path'.", itemId, itemIndex, path)
        } else if (kind !in kinds) {
            kinds += kind
        }
    }
    return kinds
}

internal fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonElement?.asBoolean(): Boolean? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive.isString) return null
    return when (primitive.content) {
        "true" -> true
        "false" -> false
        else -> null
    }
}

internal fun JsonElement?.asInt(): Int? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive.isString) return null
    val content = primitive.content
    content.toIntOrNull()?.let { return it }
    val d = content.toDoubleOrNull() ?: return null
    return if (d == kotlin.math.floor(d) && d >= Int.MIN_VALUE && d <= Int.MAX_VALUE) d.toInt() else null
}

internal class Props(
    private val obj: JsonObject,
    private val warnings: Warnings,
    private val itemId: String?,
    private val itemIndex: Int,
    private val path: String,
) {
    private fun ignored(
        key: String,
        expected: String,
    ) {
        val where = if (itemId != null) "Item '$itemId': " else ""
        warnings.add(
            ImportWarningCode.PROPERTY_IGNORED,
            "${where}Ignored '$path$key': expected $expected.",
            itemId,
            itemIndex,
            "$path$key",
        )
    }

    fun requiredText(key: String): String? = obj[key].asString()?.takeUnless { it.isBlankSpec() }

    fun optionalText(key: String): String? {
        val element = obj[key] ?: return null
        val text = element.asString()
        if (text == null) {
            ignored(key, "a string")
            return null
        }
        return text.takeUnless { it.isBlankSpec() }
    }

    fun figure(key: String): Figure? {
        val element = obj[key] ?: return null
        val figure =
            (element as? JsonObject)?.let {
                FigureRules.read(
                    it["svg"].asString() ?: "",
                    it["alt"].asString()?.takeUnless { alt ->
                        alt.isBlankSpec()
                    },
                )
            }
        if (figure == null) ignored(key, "an object with a self-contained 'svg' that has a viewBox and no scripts or external references")
        return figure
    }

    fun optionalBool(key: String): Boolean? {
        val element = obj[key] ?: return null
        val value = element.asBoolean()
        if (value == null) ignored(key, "a boolean")
        return value
    }

    fun optionalInt(
        key: String,
        min: Int,
        max: Int,
    ): Int? {
        val element = obj[key] ?: return null
        val value = element.asInt()
        if (value == null || value < min || value > max) {
            ignored(key, "an integer from $min to ${if (max == Int.MAX_VALUE) "any larger value" else max.toString()}")
            return null
        }
        return value
    }

    fun stringList(key: String): List<String> {
        val element = obj[key] ?: return emptyList()
        if (element !is JsonArray) {
            ignored(key, "an array of strings")
            return emptyList()
        }
        val result = ArrayList<String>()
        var dropped = 0
        for (entry in element) {
            val text = entry.asString()
            if (text == null || text.isBlankSpec()) dropped++ else result += text
        }
        if (dropped > 0) {
            warnings.add(
                ImportWarningCode.ENTRIES_DROPPED,
                "${if (itemId != null) "Item '$itemId': " else ""}Dropped $dropped invalid ${if (dropped == 1) "entry" else "entries"} from '$path$key'.",
                itemId,
                itemIndex,
                "$path$key",
                dropped,
            )
        }
        return result
    }

    fun array(key: String): JsonArray? = obj[key] as? JsonArray
}

internal class ItemReader(
    private val study: Study,
    private val sectionIds: Set<String>,
    private val warnings: Warnings,
) {
    fun read(
        index: Int,
        element: JsonElement,
        seenIds: MutableSet<String>?,
    ): Item? {
        val obj = element as? JsonObject
        if (obj == null) {
            warnings.skipItem("Skipped item #${index + 1}: it is not an object.", null, index, "not an object")
            return null
        }
        val id = obj["id"].asString()
        if (id == null || !isValidId(id, 64)) {
            warnings.skipItem("Skipped item #${index + 1}: missing or invalid 'id'.", null, index, "id")
            return null
        }
        if (seenIds != null && !seenIds.add(id)) {
            warnings.skipItem(
                "Skipped item #${index + 1}: the id '$id' is already used by an earlier item.",
                id,
                index,
                "id",
                ImportWarningCode.ITEM_DUPLICATE_ID,
            )
            return null
        }
        val typeText = obj["type"].asString()
        if (typeText == null) {
            warnings.skipItem("Skipped item '$id': missing or invalid 'type'.", id, index, "type")
            return null
        }
        val type = ItemType.fromCode(typeText)
        if (type == null) {
            warnings.unknownType(typeText)
            return null
        }
        val props = Props(obj, warnings, id, index, "")
        return when (type) {
            ItemType.CARD -> readCard(id, index, obj, props)
            ItemType.CHOICE -> readChoice(id, index, props)
            ItemType.TEXT -> readText(id, index, props)
            ItemType.CLOZE -> readCloze(id, index, props)
            ItemType.TRUE_FALSE -> readTrueFalse(id, index, obj, props)
            ItemType.ORDER -> readOrder(id, index, props)
            ItemType.OPEN -> readOpen(id, index, props)
        }
    }

    private fun skip(
        id: String,
        index: Int,
        reason: String,
    ): Nothing? {
        warnings.skipItem("Skipped item '$id': $reason.", id, index, reason)
        return null
    }

    private fun section(
        props: Props,
        id: String,
        index: Int,
    ): String? {
        val section = props.optionalText("section") ?: return null
        if (section in sectionIds) return section
        warnings.add(
            ImportWarningCode.ITEM_UNKNOWN_SECTION,
            "Item '$id' names the unknown section '$section' and is shown ungrouped.",
            id,
            index,
            section,
        )
        return null
    }

    private fun readCard(
        id: String,
        index: Int,
        obj: JsonObject,
        props: Props,
    ): Item? {
        val front = props.requiredText("front") ?: return skip(id, index, "missing or blank 'front'")
        val back = props.requiredText("back") ?: return skip(id, index, "missing or blank 'back'")
        val own = readCardKinds(obj["exercises"], warnings, id, index, "exercises")
        return CardItem(
            id = id,
            section = section(props, id, index),
            tags = props.stringList("tags"),
            hint = props.optionalText("hint"),
            explanation = props.optionalText("explanation"),
            figure = props.figure("figure"),
            answerFigure = props.figure("answerFigure"),
            front = front,
            back = back,
            reversible = props.optionalBool("reversible") ?: study.reversible,
            alternatives = props.stringList("alternatives"),
            frontAlternatives = props.stringList("frontAlternatives"),
            distractors = props.stringList("distractors"),
            frontDistractors = props.stringList("frontDistractors"),
            exercises = own.ifEmpty { study.cardExercises },
            caseSensitive = props.optionalBool("caseSensitive") ?: false,
        )
    }

    private fun readChoice(
        id: String,
        index: Int,
        props: Props,
    ): Item? {
        val question = props.requiredText("question") ?: return skip(id, index, "missing or blank 'question'")
        val rawOptions = props.array("options") ?: return skip(id, index, "missing 'options' array")
        val options = ArrayList<OptionDef>()
        var dropped = 0
        for (entry in rawOptions) {
            val option = entry as? JsonObject
            val text = option?.get("text").asString()
            if (option == null || text == null || text.isBlankSpec()) {
                dropped++
                continue
            }
            val explanation = option["explanation"].asString()?.takeUnless { it.isBlankSpec() }
            options += OptionDef(text, option["correct"].asBoolean() == true, explanation)
        }
        if (dropped > 0) {
            warnings.add(
                ImportWarningCode.ENTRIES_DROPPED,
                "Item '$id': dropped $dropped invalid ${if (dropped == 1) "option" else "options"}.",
                id,
                index,
                "options",
                dropped,
            )
        }
        if (options.size < 2) return skip(id, index, "fewer than 2 valid options")
        if (options.none { it.correct }) return skip(id, index, "no option is marked correct")
        return ChoiceItem(
            id = id,
            section = section(props, id, index),
            tags = props.stringList("tags"),
            hint = props.optionalText("hint"),
            explanation = props.optionalText("explanation"),
            figure = props.figure("figure"),
            answerFigure = props.figure("answerFigure"),
            question = question,
            options = options,
            shuffle = props.optionalBool("shuffle") ?: true,
        )
    }

    private fun readText(
        id: String,
        index: Int,
        props: Props,
    ): Item? {
        val question = props.requiredText("question") ?: return skip(id, index, "missing or blank 'question'")
        val answers = props.stringList("answers")
        if (answers.isEmpty()) return skip(id, index, "no non-blank 'answers'")
        return TextItem(
            id = id,
            section = section(props, id, index),
            tags = props.stringList("tags"),
            hint = props.optionalText("hint"),
            explanation = props.optionalText("explanation"),
            figure = props.figure("figure"),
            answerFigure = props.figure("answerFigure"),
            question = question,
            answers = answers,
            caseSensitive = props.optionalBool("caseSensitive") ?: false,
        )
    }

    private fun readCloze(
        id: String,
        index: Int,
        props: Props,
    ): Item? {
        val text = props.requiredText("text") ?: return skip(id, index, "missing or blank 'text'")
        if (parseCloze(text).none { it is ClozePart.Gap }) return skip(id, index, "the text has no valid {{gap}}")
        return ClozeItem(
            id = id,
            section = section(props, id, index),
            tags = props.stringList("tags"),
            hint = props.optionalText("hint"),
            explanation = props.optionalText("explanation"),
            figure = props.figure("figure"),
            answerFigure = props.figure("answerFigure"),
            text = text,
            caseSensitive = props.optionalBool("caseSensitive") ?: false,
        )
    }

    private fun readTrueFalse(
        id: String,
        index: Int,
        obj: JsonObject,
        props: Props,
    ): Item? {
        val statement = props.requiredText("statement") ?: return skip(id, index, "missing or blank 'statement'")
        val answer = obj["answer"].asBoolean() ?: return skip(id, index, "'answer' must be true or false")
        return TrueFalseItem(
            id = id,
            section = section(props, id, index),
            tags = props.stringList("tags"),
            hint = props.optionalText("hint"),
            explanation = props.optionalText("explanation"),
            figure = props.figure("figure"),
            answerFigure = props.figure("answerFigure"),
            statement = statement,
            answer = answer,
        )
    }

    private fun readOrder(
        id: String,
        index: Int,
        props: Props,
    ): Item? {
        val prompt = props.requiredText("prompt") ?: return skip(id, index, "missing or blank 'prompt'")
        val sequence = props.stringList("sequence")
        if (sequence.size < 2) return skip(id, index, "'sequence' needs at least 2 entries")
        return OrderItem(
            id = id,
            section = section(props, id, index),
            tags = props.stringList("tags"),
            hint = props.optionalText("hint"),
            explanation = props.optionalText("explanation"),
            figure = props.figure("figure"),
            answerFigure = props.figure("answerFigure"),
            prompt = prompt,
            sequence = sequence,
        )
    }

    private fun readOpen(
        id: String,
        index: Int,
        props: Props,
    ): Item? {
        val question = props.requiredText("question") ?: return skip(id, index, "missing or blank 'question'")
        return OpenItem(
            id = id,
            section = section(props, id, index),
            tags = props.stringList("tags"),
            hint = props.optionalText("hint"),
            explanation = props.optionalText("explanation"),
            figure = props.figure("figure"),
            answerFigure = props.figure("answerFigure"),
            question = question,
            answer = props.optionalText("answer"),
            keyPoints = props.stringList("keyPoints"),
        )
    }
}
