package ch.orellbuehler.jackdaw.format

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object PackCombiner {
    sealed class Outcome {
        class Merged(
            val source: String,
            val title: String,
            val itemCount: Int,
            val addedCount: Int,
            val duplicateCount: Int,
            val sectionCount: Int,
            val otherTitle: String,
        ) : Outcome()

        class Failed(
            val error: ImportError,
        ) : Outcome()
    }

    private val ignoredKeys = setOf("id", "section", "tags")
    private val pretty = Json { prettyPrint = true }

    fun content(item: JsonObject): JsonObject = JsonObject(item.filterKeys { it !in ignoredKeys })

    fun sameContent(
        a: JsonObject,
        b: JsonObject,
    ): Boolean = content(a) == content(b)

    fun derivedId(
        packId: String,
        id: String,
        taken: Set<String>,
    ): String {
        var round = 0
        while (true) {
            val key = if (round == 0) "$packId\n$id" else "$packId\n$id\n$round"
            val candidate = id.take(MAX_ID - HASH_LENGTH - 1) + "-" + hash(key)
            if (candidate !in taken) return candidate
            round++
        }
    }

    fun combine(
        targetSource: String,
        otherSource: String,
    ): Outcome {
        val target = PackReader.read(targetSource)
        if (target !is ReadResult.Accepted) return Outcome.Failed((target as ReadResult.Rejected).error)
        val other = PackReader.read(otherSource)
        if (other !is ReadResult.Accepted) return Outcome.Failed((other as ReadResult.Rejected).error)
        val root = Json.parseToJsonElement(targetSource.removePrefix("\uFEFF")) as JsonObject
        val rawItems = (root["items"] as? JsonArray).orEmpty()
        val takenItemIds = rawItems.mapNotNullTo(HashSet()) { idOf(it) }
        val seen = HashSet<JsonObject>()
        target.payloads.forEach { seen += content(Json.parseToJsonElement(it) as JsonObject) }

        val picked = ArrayList<Pair<JsonObject, Item>>()
        var duplicates = 0
        other.pack.items.forEachIndexed { index, item ->
            val element = Json.parseToJsonElement(other.payloads[index]) as JsonObject
            if (seen.add(content(element))) picked += element to item else duplicates++
        }

        val rawSections = (root["sections"] as? JsonArray).orEmpty()
        val takenSectionIds = rawSections.mapNotNullTo(HashSet()) { idOf(it) }
        val targetSections = target.pack.sections.associateBy { it.id }
        val needed = picked.mapNotNullTo(HashSet()) { it.second.section }
        val sectionIds = HashMap<String, String>()
        val addedSections = ArrayList<JsonObject>()
        for (section in other.pack.sections) {
            if (section.id !in needed) continue
            val existing = targetSections[section.id]
            val id =
                when {
                    existing != null && existing.title == section.title -> existing.id
                    section.id !in takenSectionIds -> section.id
                    else -> derivedId(other.pack.id, section.id, takenSectionIds)
                }
            sectionIds[section.id] = id
            if (takenSectionIds.add(id)) addedSections += sectionObject(id, section)
        }

        val added =
            picked.map { (element, item) ->
                val id = item.id
                val newId = if (id in takenItemIds) derivedId(other.pack.id, id, takenItemIds) else id
                takenItemIds += newId
                val fields = LinkedHashMap<String, JsonElement>()
                for ((key, value) in element) {
                    when (key) {
                        "id" -> fields[key] = JsonPrimitive(newId)
                        "section" -> item.section?.let { fields[key] = JsonPrimitive(sectionIds.getValue(it)) }
                        else -> fields[key] = value
                    }
                }
                JsonObject(fields)
            }

        val items = target.pack.items.size + added.size
        val title = target.pack.title
        if (added.isEmpty()) return Outcome.Merged(targetSource, title, items, 0, duplicates, 0, other.pack.title)

        val fields = LinkedHashMap<String, JsonElement>(root)
        fields["items"] = JsonArray(rawItems + added)
        if (addedSections.isNotEmpty()) fields["sections"] = JsonArray(rawSections + addedSections)
        FormatVersion.raised(root, rawItems + added)?.let { fields["version"] = it }
        val source = pretty.encodeToString(JsonElement.serializer(), JsonObject(fields))
        return Outcome.Merged(source, title, items, added.size, duplicates, addedSections.size, other.pack.title)
    }

    private fun sectionObject(
        id: String,
        section: SectionDef,
    ): JsonObject {
        val fields = LinkedHashMap<String, JsonElement>()
        fields["id"] = JsonPrimitive(id)
        fields["title"] = JsonPrimitive(section.title)
        section.description?.let { fields["description"] = JsonPrimitive(it) }
        return JsonObject(fields)
    }

    private fun idOf(element: JsonElement): String? =
        ((element as? JsonObject)?.get("id") as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun hash(text: String): String {
        var h = FNV_OFFSET
        for (byte in text.encodeToByteArray()) h = (h xor (byte.toLong() and 0xff)) * FNV_PRIME
        return h.toULong().toString(16).padStart(HASH_LENGTH, '0')
    }

    private const val MAX_ID = 64
    private const val HASH_LENGTH = 16
    private const val FNV_OFFSET = -3750763034362895579L
    private const val FNV_PRIME = 1099511628211L
}
