package ch.orellbuehler.jackdaw.store

import ch.orellbuehler.jackdaw.db.JackdawDatabase
import ch.orellbuehler.jackdaw.format.CardKind
import ch.orellbuehler.jackdaw.format.ItemType
import ch.orellbuehler.jackdaw.format.Pack
import ch.orellbuehler.jackdaw.format.PackReader
import ch.orellbuehler.jackdaw.format.Study
import ch.orellbuehler.jackdaw.format.StudyOrder
import ch.orellbuehler.jackdaw.study.Attempt
import ch.orellbuehler.jackdaw.study.DueCutoffs
import ch.orellbuehler.jackdaw.study.Facet
import ch.orellbuehler.jackdaw.study.FacetKey
import ch.orellbuehler.jackdaw.study.Grade
import ch.orellbuehler.jackdaw.study.LearningState
import ch.orellbuehler.jackdaw.study.LoadedPack
import ch.orellbuehler.jackdaw.study.PackContext
import ch.orellbuehler.jackdaw.study.PracticeFilter
import ch.orellbuehler.jackdaw.study.Schedule
import ch.orellbuehler.jackdaw.study.Scheduler
import ch.orellbuehler.jackdaw.sync.SyncTracker
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import ch.orellbuehler.jackdaw.db.Pack as PackRow
import ch.orellbuehler.jackdaw.db.Review_state as StateRow

internal class ExistingPack(
    val title: String,
    val revision: Int?,
    val itemCount: Int,
)

internal class ImportDiff(
    val existing: ExistingPack?,
    val added: Int,
    val kept: Int,
    val reset: Int,
    val removed: Int,
    val oldTypes: Map<String, String>,
) {
    val counts: ImportCounts get() = ImportCounts(added, kept, reset, removed)

    fun isDowngradeFor(pack: Pack): Boolean {
        val installed = existing?.revision ?: return false
        val incoming = pack.revision ?: return false
        return incoming < installed
    }
}

internal class PackStore(
    private val db: JackdawDatabase,
    private val env: Env,
    private val tracker: SyncTracker,
) {
    private val packs get() = db.packQueries
    private val reviews get() = db.reviewQueries
    private val sync get() = db.syncQueries

    fun diff(pack: Pack): ImportDiff {
        val existing = packs.selectPack(pack.id).executeAsOneOrNull()
        val oldTypes = packs.selectItemTypes(pack.id).executeAsList().associate { it.item_id to it.type }
        var added = 0
        var kept = 0
        var reset = 0
        val newIds = HashSet<String>()
        for (item in pack.items) {
            newIds += item.id
            val old = oldTypes[item.id]
            when {
                old == null -> added++
                old == item.type.code -> kept++
                else -> reset++
            }
        }
        val removed = oldTypes.keys.count { it !in newIds }
        return ImportDiff(
            existing?.let {
                ExistingPack(it.title, it.revision?.toInt(), it.item_count.toInt())
            },
            added,
            kept,
            reset,
            removed,
            oldTypes,
        )
    }

    fun install(
        pack: Pack,
        payloads: List<String>,
        rawJson: String,
        now: Long,
    ): ImportDiff {
        val at =
            sync.selectSyncPack(pack.id).executeAsOneOrNull()?.let { maxOf(now, it.content_at + 1, it.deleted_at + 1) } ?: now
        val diff = installRaw(pack, payloads, rawJson, at, null)
        tracker.touchPack(pack.id)
        env.changed()
        return diff
    }

    fun installRaw(
        pack: Pack,
        payloads: List<String>,
        rawJson: String,
        now: Long,
        importedAtOverride: Long?,
    ): ImportDiff {
        var result: ImportDiff? = null
        db.transaction {
            val diff = diff(pack)
            result = diff
            val importedAt = importedAtOverride ?: packs.selectPack(pack.id).executeAsOneOrNull()?.imported_at ?: now
            packs.upsertPack(
                id = pack.id,
                title = pack.title,
                description = pack.description,
                language = pack.language,
                author = pack.author,
                license = pack.license,
                source = pack.source,
                tags = JsonArray(pack.tags.map { JsonPrimitive(it) }).toString(),
                icon = pack.icon,
                color = pack.color,
                revision = pack.revision?.toLong(),
                created = pack.created,
                updated = pack.updated,
                study_order = pack.study.order.code,
                new_per_session = pack.study.newPerSession.toLong(),
                reversible = pack.study.reversible.toLong(),
                ignore_diacritics = pack.study.ignoreDiacritics.toLong(),
                card_exercises = pack.study.cardExercises.joinToString(",") { it.code },
                item_count = pack.items.size.toLong(),
                imported_at = importedAt,
                updated_at = now,
            )
            packs.upsertSource(pack.id, rawJson)
            packs.deleteSections(pack.id)
            pack.sections.forEachIndexed { index, section ->
                packs.insertSection(pack.id, section.id, index.toLong(), section.title, section.description)
            }
            val facetsByItem =
                reviews
                    .selectStateKeys(pack.id)
                    .executeAsList()
                    .groupBy({ it.item_id }, { it.facet })
            val context = PackContext(pack.study, pack.items)
            val created = HashSet<Pair<String, Long>>()
            pack.items.forEachIndexed { position, item ->
                packs.upsertItem(
                    pack.id,
                    item.id,
                    position.toLong(),
                    item.section ?: "",
                    item.type.code,
                    displayTitle(item),
                    displaySubtitle(item),
                    payloads[position],
                )
                val typeChanged = diff.oldTypes[item.id]?.let { it != item.type.code } == true
                if (typeChanged) reviews.deleteItemStates(pack.id, item.id)
                val present = if (typeChanged) emptyList() else facetsByItem[item.id].orEmpty()
                val wanted = context.facetsOf(item).map { it.code.toLong() }
                wanted.filter { it !in present }.forEach {
                    reviews.insertStateIfMissing(pack.id, item.id, it)
                    created += item.id to it
                }
                present.filter { it !in wanted }.forEach { reviews.deleteFacetState(pack.id, item.id, it) }
            }
            val newIds = pack.items.map { it.id }.toSet()
            diff.oldTypes.keys.filter { it !in newIds }.forEach {
                packs.deleteItem(pack.id, it)
                reviews.deleteItemStates(pack.id, it)
            }
            val known = sync.selectSyncPack(pack.id).executeAsOneOrNull()
            sync.writeSyncPack(pack.id, now, known?.reset_at ?: 0L, known?.deleted_at ?: 0L)
            if (created.isNotEmpty()) recompute(pack.id, created)
        }
        return checkNotNull(result)
    }

    fun delete(packId: String) {
        if (packs.selectPack(packId).executeAsOneOrNull() == null) return
        val known = sync.selectSyncPack(packId).executeAsOneOrNull()
        deleteRaw(packId, maxOf(env.now(), known?.content_at ?: 0L, known?.deleted_at ?: 0L))
        tracker.touchPack(packId)
        env.changed()
    }

    fun deleteRaw(
        packId: String,
        at: Long,
    ) {
        db.transaction {
            val known = sync.selectSyncPack(packId).executeAsOneOrNull()
            reviews.deletePackStates(packId)
            packs.deleteItems(packId)
            packs.deleteSections(packId)
            packs.deleteSource(packId)
            packs.deletePack(packId)
            sync.writeSyncPack(
                packId,
                known?.content_at ?: 0L,
                maxOf(known?.reset_at ?: 0L, at),
                maxOf(known?.deleted_at ?: 0L, at),
            )
        }
    }

    fun reset(packId: String) {
        val row = packs.selectPack(packId).executeAsOneOrNull() ?: return
        val known = sync.selectSyncPack(packId).executeAsOneOrNull()
        resetRaw(packId, maxOf(env.now(), known?.reset_at ?: 0L), row.updated_at)
        tracker.touchPack(packId)
        env.changed()
    }

    fun resetRaw(
        packId: String,
        at: Long,
        fallbackContentAt: Long,
    ) {
        db.transaction {
            val known = sync.selectSyncPack(packId).executeAsOneOrNull()
            sync.writeSyncPack(
                packId,
                known?.content_at ?: fallbackContentAt,
                maxOf(known?.reset_at ?: 0L, at),
                known?.deleted_at ?: 0L,
            )
            reviews.resetPackStates(packId)
            recompute(packId, null)
        }
    }

    fun recompute(
        packId: String,
        only: Set<Pair<String, Long>>?,
    ) {
        val resetAt = sync.selectSyncPack(packId).executeAsOneOrNull()?.reset_at ?: 0L
        val types = packs.selectItemTypes(packId).executeAsList().associate { it.item_id to it.type }
        val keys =
            reviews
                .selectStateKeys(packId)
                .executeAsList()
                .map { it.item_id to it.facet }
                .filter { only == null || it in only }
        if (keys.isEmpty()) return
        val bulk =
            if (only == null || only.size > RECOMPUTE_BULK_THRESHOLD) {
                reviews
                    .replayPack(packId, resetAt)
                    .executeAsList()
                    .groupBy({ it.item_id to it.facet }, { Triple(it.item_type, it.ts, it.grade) })
            } else {
                null
            }
        for ((itemId, facet) in keys) {
            val type = types[itemId] ?: continue
            val entries =
                if (bulk != null) {
                    bulk[itemId to facet].orEmpty().filter { it.first == type }.map { it.second to it.third }
                } else {
                    reviews.replayFacet(packId, itemId, facet, type, resetAt).executeAsList().map { it.ts to it.grade }
                }
            var schedule = Schedule.fresh()
            for ((ts, grade) in entries) schedule = Scheduler.review(schedule, Grade.fromCode(grade.toInt()), ts)
            reviews.upsertState(
                packId,
                itemId,
                facet,
                schedule.state.code.toLong(),
                schedule.ease.toLong(),
                schedule.intervalDays.toLong(),
                schedule.dueAt,
                schedule.reps.toLong(),
                schedule.lapses.toLong(),
                schedule.lastReviewedAt,
            )
        }
    }

    fun source(packId: String): String? = packs.selectSource(packId).executeAsOneOrNull()

    fun load(packId: String): LoadedPack? {
        val row = packs.selectPack(packId).executeAsOneOrNull() ?: return null
        val study = studyOf(row)
        val sectionIds =
            packs
                .selectSections(packId)
                .executeAsList()
                .map { it.section_id }
                .toSet()
        val items = packs.selectItems(packId).executeAsList().mapNotNull { PackReader.readStoredItem(it.payload, study, sectionIds) }
        val schedules =
            reviews
                .selectStates(packId)
                .executeAsList()
                .associate { FacetKey(it.item_id, Facet.fromCode(it.facet.toInt())) to scheduleOf(it) }
        return LoadedPack(packId, PackContext(study, items), schedules)
    }

    fun record(
        packId: String,
        attempt: Attempt,
    ) {
        db.transaction {
            if (attempt.reschedule) {
                val row = reviews.selectState(packId, attempt.itemId, attempt.facet.code.toLong()).executeAsOneOrNull()
                if (row != null) {
                    val next = Scheduler.review(scheduleOf(row), attempt.grade, attempt.timestamp)
                    reviews.upsertState(
                        packId,
                        attempt.itemId,
                        attempt.facet.code.toLong(),
                        next.state.code.toLong(),
                        next.ease.toLong(),
                        next.intervalDays.toLong(),
                        next.dueAt,
                        next.reps.toLong(),
                        next.lapses.toLong(),
                        next.lastReviewedAt,
                    )
                }
            }
            val id = env.newId()
            reviews.insertLog(
                id,
                attempt.timestamp,
                env.epochDay(attempt.timestamp).toLong(),
                packId,
                attempt.itemId,
                attempt.facet.code.toLong(),
                attempt.type.code.toLong(),
                attempt.grade.code.toLong(),
                attempt.correct.toLong(),
                attempt.practice.toLong(),
                attempt.reschedule.toLong(),
                packs.selectItemType(packId, attempt.itemId).executeAsOneOrNull() ?: "",
            )
            tracker.touchReview(id)
        }
        env.changed()
    }

    fun summaries(): List<PackSummary> {
        val now = env.now()
        val cutoffs = env.cutoffs(now)
        val counts =
            reviews
                .packCounts(Scheduler.MASTERED_INTERVAL_DAYS.toLong(), cutoffs.learnUntil, cutoffs.reviewUntil)
                .executeAsList()
                .associate {
                    it.pack_id to
                        Counts(
                            it.total.toInt(),
                            it.new_count.toInt(),
                            it.learning_count.toInt(),
                            it.mastered_count.toInt(),
                            it.due_count.toInt(),
                        )
                }
        val lastStudied = reviews.lastStudied().executeAsList().associate { it.pack_id to it.last_ts }
        return packs.selectPacks().executeAsList().map { summary(it, counts[it.id] ?: Counts(0, 0, 0, 0, 0), lastStudied[it.id] ?: 0L) }
    }

    fun summary(packId: String): PackSummary? = summaries().firstOrNull { it.id == packId }

    fun detail(packId: String): PackDetail? {
        val row = packs.selectPack(packId).executeAsOneOrNull() ?: return null
        val summary = summaries().first { it.id == packId }
        val cutoffs = env.cutoffs(env.now())
        val sectionCounts =
            reviews
                .sectionCounts(
                    Scheduler.MASTERED_INTERVAL_DAYS.toLong(),
                    cutoffs.learnUntil,
                    cutoffs.reviewUntil,
                    packId,
                ).executeAsList()
                .associateBy {
                    it.section_id
                }
        val itemCounts = packs.countItemsBySection(packId).executeAsList().associate { it.section_id to it.item_count.toInt() }

        fun section(
            id: String,
            title: String,
            descriptionText: String?,
            ungrouped: Boolean,
        ): SectionSummary {
            val c = sectionCounts[id]
            val counts =
                Counts(
                    c?.total?.toInt() ?: 0,
                    c?.new_count?.toInt() ?: 0,
                    c?.learning_count?.toInt() ?: 0,
                    c?.mastered_count?.toInt() ?: 0,
                    c?.due_count?.toInt() ?: 0,
                )
            return SectionSummary(
                id,
                title,
                descriptionText,
                itemCounts[id] ?: 0,
                counts.total,
                counts.new,
                counts.learning,
                counts.review,
                counts.mastered,
                counts.due,
                counts.progress,
                ungrouped,
            )
        }

        val sections =
            packs
                .selectSections(
                    packId,
                ).executeAsList()
                .map { section(it.section_id, it.title, it.description, false) }
                .toMutableList()
        if ((itemCounts[""] ?: 0) > 0) sections += section("", "", null, true)
        val typeCounts =
            packs
                .countItemsByType(packId)
                .executeAsList()
                .mapNotNull { r -> ItemType.entries.firstOrNull { it.code == r.type }?.let { TypeCount(it, r.item_count.toInt()) } }
                .sortedBy { it.type.ordinal }
        val tags =
            runCatching {
                (
                    Json.parseToJsonElement(
                        row.tags,
                    ) as JsonArray
                ).map { (it as JsonPrimitive).content }
            }.getOrDefault(emptyList())
        return PackDetail(
            summary = summary,
            author = row.author,
            license = row.license,
            source = row.source,
            tags = tags,
            revision = row.revision?.toInt() ?: 0,
            newPerSession = row.new_per_session.toInt(),
            randomOrder = row.study_order == StudyOrder.RANDOM.code,
            sections = sections,
            typeCounts = typeCounts,
            practiceFilters = practiceFilters(typeCounts, studyOf(row)),
        )
    }

    private fun practiceFilters(
        typeCounts: List<TypeCount>,
        study: Study,
    ): List<PracticeFilter> {
        val counts = typeCounts.associate { it.type to it.count }

        fun has(vararg types: ItemType) = types.any { (counts[it] ?: 0) > 0 }
        val cards = counts[ItemType.CARD] ?: 0
        val filters = ArrayList<PracticeFilter>()
        filters += PracticeFilter.ALL
        if (has(ItemType.OPEN) || (cards > 0 && CardKind.FLIP in study.cardExercises)) filters += PracticeFilter.FLASHCARDS
        if (has(ItemType.CHOICE, ItemType.TRUE_FALSE, ItemType.ORDER) ||
            (cards >= 3 && CardKind.CHOICE in study.cardExercises)
        ) {
            filters += PracticeFilter.QUIZ
        }
        if (has(ItemType.TEXT, ItemType.CLOZE) || (cards > 0 && CardKind.TYPE in study.cardExercises)) filters += PracticeFilter.TYPING
        return filters
    }

    fun items(packId: String): List<ItemSummary> {
        val cutoffs = env.cutoffs(env.now())
        val schedules =
            reviews
                .selectStates(packId)
                .executeAsList()
                .groupBy { it.item_id }
        return packs.selectItemRows(packId).executeAsList().map { row ->
            val facets =
                schedules[row.item_id]
                    .orEmpty()
                    .sortedBy { it.facet }
                    .map { state ->
                        val schedule = scheduleOf(state)
                        FacetInfo(
                            Facet.fromCode(state.facet.toInt()),
                            statusOf(schedule),
                            schedule.intervalDays,
                            schedule.dueAt,
                            schedule.reps,
                            schedule.lapses,
                        )
                    }
            val status =
                when {
                    facets.isEmpty() || facets.all { it.status == ItemStatus.NEW } -> ItemStatus.NEW
                    facets.all { it.status == ItemStatus.MASTERED } -> ItemStatus.MASTERED
                    facets.all { it.status == ItemStatus.REVIEW || it.status == ItemStatus.MASTERED } -> ItemStatus.REVIEW
                    else -> ItemStatus.LEARNING
                }
            val due = schedules[row.item_id].orEmpty().any { cutoffs.isDue(scheduleOf(it)) }
            ItemSummary(
                row.item_id,
                row.position.toInt(),
                ItemType.entries.first { it.code == row.type },
                row.section_id,
                row.title,
                row.subtitle,
                status,
                due,
                facets,
            )
        }
    }

    fun dueTotals(cutoffs: DueCutoffs): Triple<Int, Int, Int> {
        val row = reviews.overallCounts(Scheduler.MASTERED_INTERVAL_DAYS.toLong(), cutoffs.learnUntil, cutoffs.reviewUntil).executeAsOne()
        return Triple(row.learned_count.toInt(), row.mastered_count.toInt(), row.due_count.toInt())
    }

    private fun statusOf(schedule: Schedule): ItemStatus =
        when {
            schedule.state == LearningState.NEW -> ItemStatus.NEW
            schedule.state == LearningState.LEARNING -> ItemStatus.LEARNING
            schedule.mastered -> ItemStatus.MASTERED
            else -> ItemStatus.REVIEW
        }

    private fun studyOf(row: PackRow): Study =
        Study(
            order = StudyOrder.entries.firstOrNull { it.code == row.study_order } ?: StudyOrder.SEQUENTIAL,
            newPerSession = row.new_per_session.toInt(),
            reversible = row.reversible != 0L,
            ignoreDiacritics = row.ignore_diacritics != 0L,
            cardExercises =
                row.card_exercises
                    ?.split(",")
                    ?.mapNotNull { CardKind.fromCode(it) }
                    ?.ifEmpty { null } ?: Study.DEFAULT_CARD_EXERCISES,
        )

    private fun scheduleOf(row: StateRow): Schedule =
        Schedule(
            LearningState.fromCode(row.state.toInt()),
            row.ease.toInt(),
            row.interval_days.toInt(),
            row.due_at,
            row.reps.toInt(),
            row.lapses.toInt(),
            row.last_reviewed_at,
        )

    private fun summary(
        row: PackRow,
        counts: Counts,
        lastStudied: Long,
    ): PackSummary =
        PackSummary(
            id = row.id,
            title = row.title,
            descriptionText = row.description,
            icon = row.icon,
            color = row.color,
            language = row.language,
            itemCount = row.item_count.toInt(),
            facetCount = counts.total,
            newCount = counts.new,
            learningCount = counts.learning,
            reviewCount = counts.review,
            masteredCount = counts.mastered,
            dueCount = counts.due,
            progress = counts.progress,
            lastStudiedAt = lastStudied,
            importedAt = row.imported_at,
            updatedAt = row.updated_at,
            revision = row.revision?.toInt() ?: 0,
        )
}

private const val RECOMPUTE_BULK_THRESHOLD = 50

internal class Counts(
    val total: Int,
    val new: Int,
    val learning: Int,
    val mastered: Int,
    val due: Int,
) {
    val review: Int get() = total - new - learning - mastered

    val progress: Double
        get() = if (total == 0) 0.0 else (mastered + 0.5 * (total - new - mastered)) / total
}

internal fun Boolean.toLong(): Long = if (this) 1L else 0L
