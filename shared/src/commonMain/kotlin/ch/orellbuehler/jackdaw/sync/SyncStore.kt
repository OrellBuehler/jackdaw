package ch.orellbuehler.jackdaw.sync

import ch.orellbuehler.jackdaw.db.JackdawDatabase
import ch.orellbuehler.jackdaw.format.PackReader
import ch.orellbuehler.jackdaw.format.ReadResult
import ch.orellbuehler.jackdaw.store.Env
import ch.orellbuehler.jackdaw.store.PackStore
import ch.orellbuehler.jackdaw.store.SettingsStore
import ch.orellbuehler.jackdaw.store.toLong

/**
 * Merge rules, all of them commutative, associative and idempotent so that devices converge whatever the order in which
 * they see each other's changes:
 *
 * - review log entries are immutable and keyed by a random id: union. A facet's schedule is always the replay of its
 *   rescheduling entries (newer than the pack's reset time, recorded for the item's current type), oldest first.
 * - a pack's content is last-writer-wins on its content time; reset time and delete time only grow. A pack is live when
 *   its content is newer than its delete time. Deleting is a tombstone, so a stale device cannot bring a pack back.
 * - a synced setting is last-writer-wins on its update time.
 */
internal class SyncStore(
    private val db: JackdawDatabase,
    private val env: Env,
    private val packStore: PackStore,
    private val tracker: SyncTracker,
) {
    private val q get() = db.syncQueries

    fun pending(): List<String> = q.dirtyNames().executeAsList()

    fun discard(recordName: String) {
        q.discardRecord(recordName)
    }

    fun packRecord(recordName: String): SyncPackRecord? {
        val row = q.dirtyRow(recordName).executeAsOneOrNull() ?: return null
        val packId = row.entity_key
        val known = q.selectSyncPack(packId).executeAsOneOrNull() ?: return null
        val pack = db.packQueries.selectPack(packId).executeAsOneOrNull()
        val live = pack != null && known.content_at > known.deleted_at
        return SyncPackRecord(
            recordName = recordName,
            packId = packId,
            source = if (live) db.packQueries.selectSource(packId).executeAsOneOrNull() else null,
            importedAt = pack?.imported_at ?: 0L,
            contentAt = known.content_at,
            resetAt = known.reset_at,
            deletedAt = known.deleted_at,
            version = row.version,
        )
    }

    fun reviewRecord(recordName: String): SyncReviewRecord? {
        val row = q.dirtyRow(recordName).executeAsOneOrNull() ?: return null
        val log = db.reviewQueries.selectLog(row.entity_key).executeAsOneOrNull() ?: return null
        return SyncReviewRecord(
            recordName = recordName,
            id = log.id,
            ts = log.ts,
            day = log.day,
            packId = log.pack_id,
            itemId = log.item_id,
            facet = log.facet,
            exercise = log.exercise,
            grade = log.grade,
            correct = log.correct != 0L,
            practice = log.practice != 0L,
            reschedule = log.reschedule != 0L,
            itemType = log.item_type,
            version = row.version,
        )
    }

    fun settingRecord(recordName: String): SyncSettingRecord? {
        val row = q.dirtyRow(recordName).executeAsOneOrNull() ?: return null
        val setting = db.settingQueries.selectSetting(row.entity_key).executeAsOneOrNull() ?: return null
        return SyncSettingRecord(recordName, setting.name, setting.content, setting.updated_at, row.version)
    }

    fun markSent(
        recordName: String,
        version: Long,
        systemFields: String?,
    ) {
        db.transaction {
            q.markSynced(version = version, name = recordName)
            if (systemFields != null) q.writeSystemFields(systemFields, recordName)
            q.forgetSyncedReview(recordName)
        }
    }

    fun systemFields(recordName: String): String? = q.selectSystemFields(recordName).executeAsOneOrNull()?.system_fields

    fun setSystemFields(
        recordName: String,
        value: String?,
    ) {
        val kind = SyncNames.kindOf(recordName)
        if (kind.isEmpty()) return
        db.transaction {
            q.touchInsert(recordName, kind, recordName.substringAfter('.'))
            q.writeSystemFields(value, recordName)
        }
    }

    fun queueEverything() {
        db.transaction {
            db.packQueries
                .selectPacks()
                .executeAsList()
                .forEach { q.insertSyncPackIfMissing(it.id, it.updated_at) }
            q.queueAllPacks()
            q.queueAllReviews()
            q.queueAllSettings(SYNCED_SETTINGS)
            q.bumpAll()
        }
        tracker.announce()
    }

    fun clearTracking() {
        q.clearSyncRecords()
    }

    fun meta(name: String): String? = q.selectMeta(name).executeAsOneOrNull()

    fun setMeta(
        name: String,
        value: String?,
    ) {
        if (value == null) q.deleteMeta(name) else q.writeMeta(name, value)
    }

    fun apply(
        packs: List<SyncPackRecord>,
        reviews: List<SyncReviewRecord>,
        settings: List<SyncSettingRecord>,
    ): SyncApplyResult {
        val upload = LinkedHashSet<String>()
        val notes = ArrayList<String>()
        var changed = false
        db.transaction {
            val affected = HashMap<String, MutableSet<Pair<String, Long>>>()
            for (record in reviews) {
                if (insertReview(record, notes)) {
                    changed = true
                    affected.getOrPut(record.packId) { HashSet() } += record.itemId to record.facet
                }
            }
            val recomputed = HashSet<String>()
            for (record in packs) {
                if (mergePack(record, upload, notes)) {
                    changed = true
                    recomputed += record.packId
                }
            }
            for ((packId, facets) in affected) {
                if (packId !in recomputed) packStore.recompute(packId, facets)
            }
            for (record in settings) {
                if (mergeSetting(record, upload, notes)) changed = true
            }
        }
        if (changed) env.changed()
        return SyncApplyResult(upload.toList(), notes)
    }

    private class Content(
        val contentAt: Long,
        val source: String?,
        val importedAt: Long,
    )

    private fun compare(
        a: Content,
        b: Content,
    ): Int {
        if (a.contentAt != b.contentAt) return a.contentAt.compareTo(b.contentAt)
        val bySource = (a.source ?: "").compareTo(b.source ?: "")
        if (bySource != 0) return bySource
        return a.importedAt.compareTo(b.importedAt)
    }

    private fun mergePack(
        remote: SyncPackRecord,
        upload: MutableSet<String>,
        notes: MutableList<String>,
    ): Boolean {
        val id = remote.packId
        if (remote.source == null && remote.contentAt > remote.deletedAt) {
            notes += "Pack ${remote.packId} was skipped: its content is missing"
            return false
        }
        val row = db.packQueries.selectPack(id).executeAsOneOrNull()
        val known = q.selectSyncPack(id).executeAsOneOrNull()
        val localSource = if (row != null) db.packQueries.selectSource(id).executeAsOneOrNull() else null
        val local = Content(known?.content_at ?: row?.updated_at ?: 0L, localSource, row?.imported_at ?: 0L)
        val incoming = Content(remote.contentAt, remote.source, remote.importedAt)
        val winner = if (compare(incoming, local) > 0) incoming else local
        val oldResetAt = known?.reset_at ?: 0L
        val resetAt = maxOf(oldResetAt, remote.resetAt)
        val deletedAt = maxOf(known?.deleted_at ?: 0L, remote.deletedAt)
        val source = winner.source?.takeIf { winner.contentAt > deletedAt }
        val live = source != null
        var changed = false
        if (source != null) {
            if (row == null || localSource != source) {
                when (val read = PackReader.read(source)) {
                    is ReadResult.Rejected -> {
                        notes += "Pack ${remote.packId} was skipped: ${read.error.code}"
                        return false
                    }
                    is ReadResult.Accepted -> {
                        packStore.installRaw(
                            read.pack,
                            read.payloads,
                            source.removePrefix("\uFEFF"),
                            winner.contentAt,
                            winner.importedAt.takeIf { it > 0L },
                        )
                        changed = true
                    }
                }
            }
        } else if (row != null) {
            packStore.deleteRaw(id, deletedAt)
            changed = true
        }
        q.writeSyncPack(id, winner.contentAt, resetAt, deletedAt)
        if (live && (changed || resetAt > oldResetAt)) {
            packStore.recompute(id, null)
            changed = true
        }
        val mergedSource = if (live) source else null
        val remoteSource = if (remote.contentAt > remote.deletedAt) remote.source else null
        val same =
            remote.contentAt == winner.contentAt &&
                remote.resetAt == resetAt &&
                remote.deletedAt == deletedAt &&
                remoteSource == mergedSource &&
                (!live || remote.importedAt == winner.importedAt)
        if (!same) {
            tracker.touchPack(id)
            upload += SyncNames.packName(id)
        }
        return changed
    }

    private fun insertReview(
        record: SyncReviewRecord,
        notes: MutableList<String>,
    ): Boolean {
        val reviews = db.reviewQueries
        if (reviews.logExists(record.id).executeAsOne()) return false
        if (record.facet !in 0L..1L || record.grade !in 1L..4L) {
            notes += "Review ${record.id} was skipped: unknown facet or grade"
            return false
        }
        reviews.insertLog(
            record.id,
            record.ts,
            record.day,
            record.packId,
            record.itemId,
            record.facet,
            record.exercise,
            record.grade,
            record.correct.toLong(),
            record.practice.toLong(),
            record.reschedule.toLong(),
            record.itemType,
        )
        return true
    }

    private fun mergeSetting(
        remote: SyncSettingRecord,
        upload: MutableSet<String>,
        notes: MutableList<String>,
    ): Boolean {
        if (remote.name !in SYNCED_SETTINGS) {
            notes += "Setting ${remote.name} was ignored: it is not synced"
            return false
        }
        val local = db.settingQueries.selectSetting(remote.name).executeAsOneOrNull()
        val order =
            when {
                local == null -> 1
                remote.updatedAt != local.updated_at -> remote.updatedAt.compareTo(local.updated_at)
                else -> remote.value.compareTo(local.content)
            }
        return when {
            order > 0 -> {
                db.settingQueries.upsertSetting(remote.name, remote.value, remote.updatedAt)
                true
            }
            order < 0 -> {
                tracker.touchSetting(remote.name)
                upload += SyncNames.settingName(remote.name)
                false
            }
            else -> false
        }
    }

    companion object {
        val SYNCED_SETTINGS: Set<String> = setOf(SettingsStore.DAILY_GOAL, SettingsStore.SESSION_SIZE, SettingsStore.TYPO_TOLERANCE)
    }
}
