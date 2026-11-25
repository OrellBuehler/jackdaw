package ch.orellbuehler.jackdaw.format

import ch.orellbuehler.jackdaw.card
import ch.orellbuehler.jackdaw.packJson
import ch.orellbuehler.jackdaw.samples.GENERATED_SAMPLE_PACKS
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class PackReaderTest {
    private fun accepted(text: String): ReadResult.Accepted {
        val result = PackReader.read(text)
        if (result !is ReadResult.Accepted) fail("expected acceptance, got ${(result as ReadResult.Rejected).error}")
        return result
    }

    private fun rejected(text: String): ImportErrorCode {
        val result = PackReader.read(text)
        if (result !is ReadResult.Rejected) fail("expected rejection")
        return result.error.code
    }

    private val simpleCard = card("c1", "front", "back")

    @Test
    fun everyExamplePackParsesWithoutWarnings() {
        assertTrue(GENERATED_SAMPLE_PACKS.size >= 5)
        for ((name, json) in GENERATED_SAMPLE_PACKS) {
            val result = accepted(json)
            val expectedItems =
                Json
                    .parseToJsonElement(json)
                    .jsonObject
                    .getValue("items")
                    .jsonArray.size
            assertEquals(emptyList(), result.warnings, "warnings in $name")
            assertEquals(0, result.skippedItems, name)
            assertEquals(expectedItems, result.pack.items.size, name)
            assertEquals(expectedItems, result.payloads.size, name)
        }
    }

    @Test
    fun invalidFiguresAreIgnoredWithAWarning() {
        val ok = """"figure":{"svg":"  <svg viewBox='0 0 30 10'/> ","alt":" "}"""
        val script = """"answerFigure":{"svg":"<svg viewBox='0 0 1 1' onload='x()'/>","alt":"x"}"""
        val result =
            accepted(
                packJson(
                    card("a", "f", "b", ",$ok,$script"),
                    card("b", "f2", "b2", ""","figure":"<svg viewBox='0 0 1 1'/>""""),
                    card("c", "f3", "b3", ""","figure":{"alt":"no svg"}"""),
                ),
            )
        assertEquals(3, result.pack.items.size)
        val first = result.pack.items[0]
        assertEquals(Figure("<svg viewBox='0 0 30 10'/>", null, 3.0), first.figure)
        assertEquals(null, first.answerFigure)
        assertEquals(null, result.pack.items[1].figure)
        assertEquals(listOf("answerFigure", "figure", "figure"), result.warnings.map { it.detail })
        assertTrue(result.warnings.all { it.code == ImportWarningCode.PROPERTY_IGNORED })
        assertTrue(first.figure!!.html("#112233").contains("color:#112233"))
        assertTrue(first.figure!!.html(null).contains("CanvasText"))
        assertTrue(first.figure!!.html(null).contains("default-src 'none'"))
    }

    @Test
    fun mapsAreReadAndInvalidOnesIgnoredWithAWarning() {
        val pins = """[{"lat":48.86,"lng":2.35,"label":"Paris"},{"lat":200,"lng":0},{"lat":-33.87,"lng":151.21}]"""
        val ok = """"map":{"alt":"Where","line":true,"pins":$pins}"""
        val answer = """"answerMap":{"pins":[{"lat":1,"lng":2}],"line":"yes","alt":7}"""
        val result =
            accepted(
                packJson(
                    card("a", "f", "b", ",$ok,$answer"),
                    card("b", "f2", "b2", ""","map":"Paris""""),
                    card("c", "f3", "b3", ""","map":{"pins":[]}"""),
                    card("d", "f4", "b4", ""","map":{"pins":[{"lat":"1","lng":2}]}"""),
                ),
            )
        assertEquals(4, result.pack.items.size)
        val first = result.pack.items[0]
        assertEquals(
            MapSpec(listOf(MapPin(48.86, 2.35, "Paris"), MapPin(-33.87, 151.21, null)), true, "Where"),
            first.map,
        )
        assertEquals(MapSpec(listOf(MapPin(1.0, 2.0, null)), false, null), first.answerMap)
        assertEquals(
            listOf(null, null, null),
            result.pack.items
                .drop(1)
                .map { it.map },
        )
        assertEquals(
            listOf("map.pins" to ImportWarningCode.ENTRIES_DROPPED) +
                listOf("answerMap.line", "answerMap.alt", "map", "map", "map").map {
                    it to ImportWarningCode.PROPERTY_IGNORED
                },
            result.warnings.map { it.detail to it.code },
        )
        assertEquals(1, result.warnings.first().count)
        assertEquals(0, result.skippedItems)
    }

    @Test
    fun aFigureAndAMapOnTheSameSideKeepTheFigure() {
        val svg = "<svg viewBox='0 0 40 20'/>"
        val pins = """"pins":[{"lat":1,"lng":2}]"""
        val result =
            accepted(
                packJson(
                    card("a", "f", "b", ""","figure":{"svg":"$svg"},"map":{$pins},"answerFigure":{"svg":"$svg"},"answerMap":{$pins}"""),
                    card("b", "f", "b", ""","figure":{"svg":"$svg"},"answerMap":{$pins}"""),
                    card("c", "f", "b", ""","map":{$pins},"answerFigure":{"svg":"$svg"}"""),
                ),
            )
        val (conflict, figureThenMap, mapThenFigure) = result.pack.items
        assertNotNull(conflict.figure)
        assertNull(conflict.map)
        assertNotNull(conflict.answerFigure)
        assertNull(conflict.answerMap)
        assertNotNull(figureThenMap.figure)
        assertNotNull(figureThenMap.answerMap)
        assertNotNull(mapThenFigure.map)
        assertNotNull(mapThenFigure.answerFigure)
        assertEquals(listOf("map", "answerMap"), result.warnings.map { it.detail })
        assertTrue(result.warnings.all { it.code == ImportWarningCode.PROPERTY_IGNORED && it.itemId == "a" })
    }

    @Test
    fun aMapIsDrawnAsAFigureAndAnswerMapsReplaceWhatTheQuestionShowed() {
        val map = MapSpec(listOf(MapPin(48.86, 2.35, "Paris")), false, "Paris")
        val other = MapSpec(listOf(MapPin(-33.87, 151.21, "Sydney")), false, "Sydney")
        val item = TextItem("t", null, emptyList(), null, null, "Q", listOf("a"), false, map = map, answerMap = other)
        assertEquals("Paris", item.questionVisual?.alt)
        assertEquals("Sydney", item.answerVisual?.alt)
        assertTrue(item.questionVisual!!.svg.startsWith("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 2000 1250'>"))
        assertEquals(null, TextItem("t", null, emptyList(), null, null, "Q", listOf("a"), false, answerMap = other).questionVisual)
        assertEquals("Paris", TextItem("t", null, emptyList(), null, null, "Q", listOf("a"), false, map = map).answerVisual?.alt)
    }

    @Test
    fun formatTourDetails() {
        val pack = accepted(GENERATED_SAMPLE_PACKS.first { it.first == "format-tour.jackdaw" }.second).pack
        assertEquals("com.github.orellbuehler.jackdaw.examples.format-tour", pack.id)
        assertEquals(4, pack.revision)
        assertEquals("#7C3AED", pack.color)
        assertEquals(listOf("basics", "item-types"), pack.sections.map { it.id })
        assertEquals(5, pack.study.newPerSession)
        val cloze = pack.items.filterIsInstance<ClozeItem>().first { it.id == "cloze-syntax" }
        val gaps = parseCloze(cloze.text).filterIsInstance<ClozePart.Gap>()
        assertEquals(listOf("two", "|"), gaps.map { it.answers.first() })
        val open = pack.items.filterIsInstance<OpenItem>().single()
        assertEquals(4, open.keyPoints.size)
    }

    @Test
    fun spanishBasicsInheritsStudyHints() {
        val pack = accepted(GENERATED_SAMPLE_PACKS.first { it.first == "spanish-basics.jackdaw" }.second).pack
        assertTrue(pack.study.ignoreDiacritics)
        assertTrue(pack.items.filterIsInstance<CardItem>().all { it.reversible })
    }

    @Test
    fun rejectsNonJsonAndWrongTopLevel() {
        assertEquals(ImportErrorCode.NOT_JSON, rejected("not json"))
        assertEquals(ImportErrorCode.NOT_JSON, rejected(""))
        assertEquals(ImportErrorCode.NOT_JSON, rejected("{\"format\":"))
        assertEquals(ImportErrorCode.NOT_AN_OBJECT, rejected("[1,2]"))
        assertEquals(ImportErrorCode.NOT_AN_OBJECT, rejected("\"text\""))
    }

    @Test
    fun rejectsWrongFormatMarker() {
        assertEquals(ImportErrorCode.NOT_A_PACK, rejected("""{"version":1,"id":"a","title":"t","items":[]}"""))
        assertEquals(ImportErrorCode.NOT_A_PACK, rejected("""{"format":"other","version":1,"id":"a","title":"t","items":[]}"""))
        assertEquals(ImportErrorCode.NOT_A_PACK, rejected("""{"format":5,"version":1,"id":"a","title":"t","items":[]}"""))
    }

    @Test
    fun versionRules() {
        fun withVersion(version: String) = """{"format":"jackdaw-pack","version":$version,"id":"a","title":"t","items":[$simpleCard]}"""
        assertEquals(1, accepted(withVersion("1")).pack.items.size)
        assertEquals(1, accepted(withVersion("1.0")).pack.items.size)
        assertEquals(1, accepted(withVersion("2")).pack.items.size)
        assertEquals(1, accepted(withVersion("3")).pack.items.size)
        assertEquals(ImportErrorCode.VERSION_TOO_NEW, rejected(withVersion("4")))
        assertEquals(ImportErrorCode.VERSION_TOO_NEW, rejected(withVersion("1000")))
        assertEquals(ImportErrorCode.VERSION_INVALID, rejected(withVersion("0")))
        assertEquals(ImportErrorCode.VERSION_INVALID, rejected(withVersion("-1")))
        assertEquals(ImportErrorCode.VERSION_INVALID, rejected(withVersion("1.5")))
        assertEquals(ImportErrorCode.VERSION_INVALID, rejected(withVersion("\"1\"")))
        assertEquals(ImportErrorCode.VERSION_INVALID, rejected(withVersion("null")))
        assertEquals(
            ImportErrorCode.VERSION_INVALID,
            rejected("""{"format":"jackdaw-pack","id":"a","title":"t","items":[$simpleCard]}"""),
        )
    }

    @Test
    fun packIdRules() {
        fun withId(id: String) = """{"format":"jackdaw-pack","version":1,"id":$id,"title":"t","items":[$simpleCard]}"""
        assertEquals(1, accepted(withId("\"com.example.a-b_c\"")).pack.items.size)
        assertEquals(1, accepted(withId("\"${"a".repeat(128)}\"")).pack.items.size)
        assertEquals(ImportErrorCode.ID_INVALID, rejected(withId("\"${"a".repeat(129)}\"")))
        assertEquals(ImportErrorCode.ID_INVALID, rejected(withId("\"\"")))
        assertEquals(ImportErrorCode.ID_INVALID, rejected(withId("\"-start\"")))
        assertEquals(ImportErrorCode.ID_INVALID, rejected(withId("\"has space\"")))
        assertEquals(ImportErrorCode.ID_INVALID, rejected(withId("\"umlaut-ü\"")))
        assertEquals(ImportErrorCode.ID_INVALID, rejected(withId("5")))
        assertEquals(ImportErrorCode.ID_INVALID, rejected("""{"format":"jackdaw-pack","version":1,"title":"t","items":[$simpleCard]}"""))
    }

    @Test
    fun titleRules() {
        fun withTitle(title: String) = """{"format":"jackdaw-pack","version":1,"id":"a","title":$title,"items":[$simpleCard]}"""
        assertEquals("Trim me", accepted(withTitle("\"  Trim me \"")).pack.title)
        assertEquals(ImportErrorCode.TITLE_INVALID, rejected(withTitle("\"\"")))
        assertEquals(ImportErrorCode.TITLE_INVALID, rejected(withTitle("\"\u3000\u3000\"")))
        assertEquals(ImportErrorCode.TITLE_INVALID, rejected(withTitle("\"\u3000\u3000\"")))
        assertEquals(ImportErrorCode.TITLE_INVALID, rejected(withTitle("12")))
        assertEquals(ImportErrorCode.TITLE_INVALID, rejected("""{"format":"jackdaw-pack","version":1,"id":"a","items":[$simpleCard]}"""))
    }

    @Test
    fun itemsRules() {
        assertEquals(ImportErrorCode.ITEMS_MISSING, rejected("""{"format":"jackdaw-pack","version":1,"id":"a","title":"t"}"""))
        assertEquals(ImportErrorCode.ITEMS_MISSING, rejected("""{"format":"jackdaw-pack","version":1,"id":"a","title":"t","items":{}}"""))
        assertEquals(ImportErrorCode.NO_VALID_ITEMS, rejected(packJson()))
        assertEquals(ImportErrorCode.NO_VALID_ITEMS, rejected(packJson("""{"id":"x","type":"card","front":"only front"}""", "5")))
    }

    @Test
    fun byteOrderMarkIsTolerated() {
        assertEquals(1, accepted("\uFEFF" + packJson(simpleCard)).pack.items.size)
    }

    @Test
    fun limitsAreEnforced() {
        val deep = "[".repeat(100) + "]".repeat(100)
        assertEquals(ImportErrorCode.NOT_JSON, rejected(deep))
        val many = (0..50_000).joinToString(",") { """{"id":"i$it","type":"truefalse","statement":"s","answer":true}""" }
        assertEquals(ImportErrorCode.TOO_MANY_ITEMS, rejected(packJson(many)))
        val big = (0 until 2_000).joinToString(",") { """{"id":"i$it","type":"truefalse","statement":"s","answer":true}""" }
        assertEquals(2_000, accepted(packJson(big)).pack.items.size)
    }

    @Test
    fun brokenItemsAreSkippedWithWarnings() {
        val result =
            accepted(
                packJson(
                    simpleCard,
                    "5",
                    """{"type":"card","front":"a","back":"b"}""",
                    """{"id":"-bad","type":"card","front":"a","back":"b"}""",
                    """{"id":"no-type","front":"a","back":"b"}""",
                    """{"id":"num-type","type":3,"front":"a","back":"b"}""",
                    card("c1", "duplicate", "ignored"),
                    card("c2", "second", "card"),
                ),
            )
        assertEquals(listOf("c1", "c2"), result.pack.items.map { it.id })
        assertEquals("front", (result.pack.items.first() as CardItem).front)
        assertEquals(6, result.skippedItems)
        val codes = result.warnings.map { it.code }
        assertEquals(1, codes.count { it == ImportWarningCode.ITEM_DUPLICATE_ID })
        assertEquals(5, codes.count { it == ImportWarningCode.ITEM_SKIPPED })
    }

    @Test
    fun unknownItemTypesAreSkippedAndAggregated() {
        val result =
            accepted(
                packJson(
                    simpleCard,
                    """{"id":"m1","type":"matching","pairs":[]}""",
                    """{"id":"m2","type":"matching"}""",
                    """{"id":"v1","type":"video"}""",
                ),
            )
        assertEquals(1, result.pack.items.size)
        assertEquals(3, result.skippedItems)
        val matching = result.warnings.single { it.detail == "matching" }
        assertEquals(ImportWarningCode.ITEM_UNKNOWN_TYPE, matching.code)
        assertEquals(2, matching.count)
        assertTrue(result.warnings.any { it.detail == "video" })
    }

    @Test
    fun cardRequiredProperties() {
        val result =
            accepted(
                packJson(
                    simpleCard,
                    """{"id":"a","type":"card","back":"b"}""",
                    """{"id":"b","type":"card","front":"f"}""",
                    """{"id":"c","type":"card","front":"  ","back":"b"}""",
                    """{"id":"d","type":"card","front":5,"back":"b"}""",
                    """{"id":"e","type":"card","front":"f","back":["x"]}""",
                ),
            )
        assertEquals(listOf("c1"), result.pack.items.map { it.id })
        assertEquals(5, result.skippedItems)
    }

    @Test
    fun choiceValidation() {
        val result =
            accepted(
                packJson(
                    simpleCard,
                    """{"id":"ok","type":"choice","question":"q","options":[{"text":"a","correct":true},{"text":"b"}]}""",
                    """{"id":"one","type":"choice","question":"q","options":[{"text":"a","correct":true}]}""",
                    """{"id":"none","type":"choice","question":"q","options":[{"text":"a"},{"text":"b"}]}""",
                    """{"id":"strbool","type":"choice","question":"q","options":[{"text":"a","correct":"true"},{"text":"b"}]}""",
                    """{"id":"invalid","type":"choice","question":"q","options":[{"text":"a","correct":true},5,{"text":" "},{"correct":true}]}""",
                    """{"id":"mixed","type":"choice","question":"q","options":[{"text":"a","correct":true},5,{"text":"b"},{"text":"c"}]}""",
                    """{"id":"noopts","type":"choice","question":"q"}""",
                ),
            )
        assertEquals(listOf("c1", "ok", "mixed"), result.pack.items.map { it.id })
        val mixed = result.pack.items.last() as ChoiceItem
        assertEquals(3, mixed.options.size)
        assertTrue(mixed.shuffle)
        assertTrue(result.warnings.any { it.code == ImportWarningCode.ENTRIES_DROPPED && it.itemId == "mixed" })
    }

    @Test
    fun textClozeTrueFalseOrderValidation() {
        val result =
            accepted(
                packJson(
                    """{"id":"t-ok","type":"text","question":"q","answers":["a"," ",5,"b"]}""",
                    """{"id":"t-none","type":"text","question":"q","answers":[" ",5]}""",
                    """{"id":"t-missing","type":"text","question":"q"}""",
                    """{"id":"z-ok","type":"cloze","text":"a {{b}} c"}""",
                    """{"id":"z-none","type":"cloze","text":"a {{}} c {{ }} {{::h}}"}""",
                    """{"id":"z-escaped","type":"cloze","text":"a \\{{b}} c"}""",
                    """{"id":"f-ok","type":"truefalse","statement":"s","answer":false}""",
                    """{"id":"f-str","type":"truefalse","statement":"s","answer":"true"}""",
                    """{"id":"f-num","type":"truefalse","statement":"s","answer":1}""",
                    """{"id":"f-missing","type":"truefalse","statement":"s"}""",
                    """{"id":"o-ok","type":"order","prompt":"p","sequence":["a","b"]}""",
                    """{"id":"o-one","type":"order","prompt":"p","sequence":["a"]}""",
                    """{"id":"o-one-valid","type":"order","prompt":"p","sequence":["a",3,""]}""",
                ),
            )
        assertEquals(listOf("t-ok", "z-ok", "f-ok", "o-ok"), result.pack.items.map { it.id })
        assertEquals(listOf("a", "b"), (result.pack.items.first() as TextItem).answers)
        assertEquals(9, result.skippedItems)
    }

    @Test
    fun openItemsNeedOnlyAQuestion() {
        val result =
            accepted(
                packJson(
                    """{"id":"o1","type":"open","question":"q"}""",
                    """{"id":"o2","type":"open","question":"q","answer":"a","keyPoints":["x",5,"y"]}""",
                    """{"id":"o3","type":"open","answer":"a"}""",
                ),
            )
        assertEquals(listOf("o1", "o2"), result.pack.items.map { it.id })
        val o2 = result.pack.items[1] as OpenItem
        assertEquals("a", o2.answer)
        assertEquals(listOf("x", "y"), o2.keyPoints)
    }

    @Test
    fun invalidOptionalPropertiesAreIgnoredWithWarnings() {
        val result =
            accepted(
                packJson(
                    """{"id":"c","type":"card","front":"f","back":"b","hint":5,"explanation":false,"tags":["ok",3],"reversible":"yes",
                       "caseSensitive":1,"alternatives":["x",1,null],"exercises":"flip","section":7}""",
                    extra =
                        """"description":5,"language":false,"icon":"abc","color":"red","revision":0,"created":"yesterday","updated":"2026-13-45x",
                        "tags":"tag","author":[],""",
                ),
            )
        val pack = result.pack
        assertNull(pack.description)
        assertNull(pack.language)
        assertNull(pack.icon)
        assertNull(pack.color)
        assertNull(pack.revision)
        assertNull(pack.created)
        assertNull(pack.updated)
        assertNull(pack.author)
        assertEquals(emptyList(), pack.tags)
        val card = pack.items.single() as CardItem
        assertNull(card.hint)
        assertNull(card.explanation)
        assertNull(card.section)
        assertEquals(listOf("ok"), card.tags)
        assertEquals(false, card.reversible)
        assertEquals(false, card.caseSensitive)
        assertEquals(listOf("x"), card.alternatives)
        assertEquals(Study.DEFAULT_CARD_EXERCISES, card.exercises)
        assertEquals(0, result.skippedItems)
        val ignored =
            result.warnings
                .filter { it.code == ImportWarningCode.PROPERTY_IGNORED }
                .mapNotNull { it.detail }
                .toSet()
        for (name in listOf("description", "language", "icon", "color", "revision", "created", "updated", "tags", "author")) {
            assertTrue(name in ignored, "no warning for $name in $ignored")
        }
        assertTrue(result.warnings.any { it.itemId == "c" && it.detail == "hint" })
        assertTrue(result.warnings.any { it.itemId == "c" && it.detail == "exercises" })
    }

    @Test
    fun validCosmeticPropertiesAreKept() {
        val pack =
            accepted(
                packJson(
                    simpleCard,
                    extra =
                        """"description":"d","language":"es","author":"a","license":"MIT","source":"https://example.com","icon":"🇪🇸",
                        "color":"#C60B1E","revision":2,"created":"2026-11-20","updated":"2026-11-20T14:30:00+01:00","tags":["a","b"],""",
                ),
            ).pack
        assertEquals("🇪🇸", pack.icon)
        assertEquals("#C60B1E", pack.color)
        assertEquals(2, pack.revision)
        assertEquals("2026-11-20T14:30:00+01:00", pack.updated)
        assertEquals(listOf("a", "b"), pack.tags)
        assertEquals("MIT", pack.license)
    }

    @Test
    fun keycapAndZwjEmojiAreValidIcons() {
        val keycap = "1\uFE0F\u20E3"
        val technologist = "\uD83D\uDC69\u200D\uD83D\uDCBB"
        assertEquals(keycap, accepted(packJson(simpleCard, extra = "\"icon\":\"$keycap\",")).pack.icon)
        assertEquals(technologist, accepted(packJson(simpleCard, extra = "\"icon\":\"$technologist\",")).pack.icon)
    }

    @Test
    fun unknownPropertiesAndExtensionsAreIgnoredSilently() {
        val result =
            accepted(
                packJson(
                    """{"id":"c","type":"card","front":"f","back":"b","x-tool":{"a":1},"media":"cat.png","future":true}""",
                    extra = """"x-vendor":[1,2],"media":{"cover":"x.png"},"${'$'}schema":"https://example.com","somethingNew":1,""",
                ),
            )
        assertEquals(emptyList(), result.warnings)
        assertEquals(1, result.pack.items.size)
    }

    @Test
    fun sectionRules() {
        val result =
            accepted(
                packJson(
                    """{"id":"a","type":"card","front":"f","back":"b","section":"s1"}""",
                    """{"id":"b","type":"card","front":"f","back":"b","section":"missing"}""",
                    """{"id":"c","type":"card","front":"f","back":"b"}""",
                    sections =
                        """[{"id":"s1","title":"One","description":"d"},{"id":"s1","title":"Duplicate"},5,{"id":"s2"},{"title":"No id"},
                           {"id":"bad id","title":"x"},{"id":"s3","title":"  "},{"id":"s4","title":"Four"}]""",
                ),
            )
        assertEquals(listOf("s1", "s4"), result.pack.sections.map { it.id })
        assertEquals(
            "d",
            result.pack.sections
                .first()
                .description,
        )
        assertEquals("s1", result.pack.items[0].section)
        assertNull(result.pack.items[1].section)
        assertNull(result.pack.items[2].section)
        assertEquals(6, result.warnings.count { it.code == ImportWarningCode.SECTION_DROPPED })
        assertEquals(1, result.warnings.count { it.code == ImportWarningCode.ITEM_UNKNOWN_SECTION })
        assertEquals(0, result.skippedItems)
    }

    @Test
    fun sectionsOfWrongTypeAreIgnored() {
        val result = accepted(packJson(simpleCard, sections = """{"id":"x"}"""))
        assertEquals(emptyList(), result.pack.sections)
        assertTrue(result.warnings.any { it.detail == "sections" })
    }

    @Test
    fun studyHintsAndDefaults() {
        val defaults = accepted(packJson(simpleCard)).pack.study
        assertEquals(StudyOrder.SEQUENTIAL, defaults.order)
        assertEquals(10, defaults.newPerSession)
        assertEquals(false, defaults.reversible)
        assertEquals(false, defaults.ignoreDiacritics)
        assertEquals(Study.DEFAULT_CARD_EXERCISES, defaults.cardExercises)

        val custom =
            accepted(
                packJson(
                    simpleCard,
                    study =
                        """{"order":"random","newPerSession":500,"reversible":true,"ignoreDiacritics":true,
                           "exercises":{"card":["type","bogus","flip","type"],"other":["x"]}}""",
                ),
            ).pack.study
        assertEquals(StudyOrder.RANDOM, custom.order)
        assertEquals(500, custom.newPerSession)
        assertTrue(custom.reversible)
        assertTrue(custom.ignoreDiacritics)
        assertEquals(listOf(CardKind.TYPE, CardKind.FLIP), custom.cardExercises)
    }

    @Test
    fun invalidStudyHintsFallBackToDefaults() {
        val result =
            accepted(
                packJson(
                    simpleCard,
                    study =
                        """{"order":"shuffled","newPerSession":0,"reversible":"yes","ignoreDiacritics":1,
                           "exercises":{"card":["bogus"]}}""",
                ),
            )
        val study = result.pack.study
        assertEquals(StudyOrder.SEQUENTIAL, study.order)
        assertEquals(10, study.newPerSession)
        assertEquals(false, study.reversible)
        assertEquals(false, study.ignoreDiacritics)
        assertEquals(Study.DEFAULT_CARD_EXERCISES, study.cardExercises)
        assertTrue(result.warnings.isNotEmpty())
        assertEquals(10, accepted(packJson(simpleCard, study = "7")).pack.study.newPerSession)
        assertEquals(10, accepted(packJson(simpleCard, study = """{"newPerSession":501}""")).pack.study.newPerSession)
    }

    @Test
    fun cardDefaultsComeFromStudyHints() {
        val pack =
            accepted(
                packJson(
                    """{"id":"a","type":"card","front":"f","back":"b"}""",
                    """{"id":"b","type":"card","front":"f","back":"b","reversible":false,"exercises":["choice","unknown"]}""",
                    """{"id":"c","type":"card","front":"f","back":"b","exercises":[]}""",
                    study = """{"reversible":true,"exercises":{"card":["flip","type"]}}""",
                ),
            ).pack
        val cards = pack.items.filterIsInstance<CardItem>()
        assertEquals(listOf(true, false, true), cards.map { it.reversible })
        assertEquals(listOf(CardKind.FLIP, CardKind.TYPE), cards[0].exercises)
        assertEquals(listOf(CardKind.CHOICE), cards[1].exercises)
        assertEquals(listOf(CardKind.FLIP, CardKind.TYPE), cards[2].exercises)
    }

    @Test
    fun warningsAreCapped() {
        val items = (0 until 250).joinToString(",") { """{"id":"bad$it","type":"card"}""" } + ",$simpleCard"
        val result = accepted(packJson(items))
        assertEquals(250, result.skippedItems)
        assertEquals(101, result.warnings.size)
        assertEquals(ImportWarningCode.MORE_WARNINGS, result.warnings.last().code)
        assertEquals(150, result.warnings.last().count)
    }

    @Test
    fun storedItemsAreReadBackWithoutWarnings() {
        val result = accepted(packJson(simpleCard, """{"id":"t","type":"truefalse","statement":"s","answer":true}"""))
        val stored = PackReader.readStoredItem(result.payloads[1], result.pack.study, emptySet())
        assertIs<TrueFalseItem>(stored)
        assertNull(PackReader.readStoredItem("{not json", result.pack.study, emptySet()))
    }
}
