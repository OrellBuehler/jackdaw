package ch.orellbuehler.jackdaw.sync

import ch.orellbuehler.jackdaw.format.PackReader
import ch.orellbuehler.jackdaw.store.Env
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * What a platform sync layer needs, and nothing network related: the records that changed locally, the way to apply
 * records fetched from elsewhere, and a few persisted values. The iOS CloudKit layer is the only user.
 */
class JackdawSync internal constructor(
    private val store: SyncStore,
    private val tracker: SyncTracker,
    private val env: Env,
    private val dispatcher: CoroutineDispatcher,
) {
    fun enableTracking() {
        env.syncTracking = true
    }

    fun kindOf(recordName: String): String = SyncNames.kindOf(recordName)

    val packFormatVersion: Int get() = PackReader.SUPPORTED_VERSION

    fun observeLocalChanges(): Flow<SyncSignal> = tracker.signal.map { SyncSignal(it) }

    suspend fun pendingRecords(): List<String> = withContext(dispatcher) { store.pending() }

    suspend fun discard(recordName: String) {
        withContext(dispatcher) { store.discard(recordName) }
    }

    suspend fun packRecord(recordName: String): SyncPackRecord? = withContext(dispatcher) { store.packRecord(recordName) }

    suspend fun reviewRecord(recordName: String): SyncReviewRecord? = withContext(dispatcher) { store.reviewRecord(recordName) }

    suspend fun settingRecord(recordName: String): SyncSettingRecord? = withContext(dispatcher) { store.settingRecord(recordName) }

    suspend fun markSent(
        recordName: String,
        version: Long,
        systemFields: String?,
    ) {
        withContext(dispatcher) { store.markSent(recordName, version, systemFields) }
    }

    suspend fun systemFields(recordName: String): String? = withContext(dispatcher) { store.systemFields(recordName) }

    suspend fun setSystemFields(
        recordName: String,
        value: String?,
    ) {
        withContext(dispatcher) { store.setSystemFields(recordName, value) }
    }

    suspend fun apply(
        packs: List<SyncPackRecord>,
        reviews: List<SyncReviewRecord>,
        settings: List<SyncSettingRecord>,
    ): SyncApplyResult = withContext(dispatcher) { store.apply(packs, reviews, settings) }

    suspend fun queueEverything() {
        withContext(dispatcher) { store.queueEverything() }
    }

    suspend fun clearTracking() {
        withContext(dispatcher) { store.clearTracking() }
    }

    suspend fun meta(name: String): String? = withContext(dispatcher) { store.meta(name) }

    suspend fun setMeta(
        name: String,
        value: String?,
    ) {
        withContext(dispatcher) { store.setMeta(name, value) }
    }
}
