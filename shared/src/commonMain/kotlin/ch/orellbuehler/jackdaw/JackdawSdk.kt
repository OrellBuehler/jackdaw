package ch.orellbuehler.jackdaw

import app.cash.sqldelight.db.SqlDriver
import ch.orellbuehler.jackdaw.db.DatabaseDriverFactory
import ch.orellbuehler.jackdaw.db.JackdawDatabase
import ch.orellbuehler.jackdaw.format.AuthoringPrompt
import ch.orellbuehler.jackdaw.format.ImportError
import ch.orellbuehler.jackdaw.format.ImportErrorCode
import ch.orellbuehler.jackdaw.format.ItemType
import ch.orellbuehler.jackdaw.format.PackCombiner
import ch.orellbuehler.jackdaw.format.PackEditor
import ch.orellbuehler.jackdaw.format.PackReader
import ch.orellbuehler.jackdaw.format.PackUrl
import ch.orellbuehler.jackdaw.format.ReadResult
import ch.orellbuehler.jackdaw.format.Study
import ch.orellbuehler.jackdaw.samples.Catalog
import ch.orellbuehler.jackdaw.samples.Samples
import ch.orellbuehler.jackdaw.store.AppSettings
import ch.orellbuehler.jackdaw.store.CombinePreview
import ch.orellbuehler.jackdaw.store.Env
import ch.orellbuehler.jackdaw.store.ImportCounts
import ch.orellbuehler.jackdaw.store.ImportPreview
import ch.orellbuehler.jackdaw.store.ImportResult
import ch.orellbuehler.jackdaw.store.ItemSummary
import ch.orellbuehler.jackdaw.store.LibraryStore
import ch.orellbuehler.jackdaw.store.PackDetail
import ch.orellbuehler.jackdaw.store.PackGroup
import ch.orellbuehler.jackdaw.store.PackStore
import ch.orellbuehler.jackdaw.store.PackSummary
import ch.orellbuehler.jackdaw.store.SamplePack
import ch.orellbuehler.jackdaw.store.SettingsStore
import ch.orellbuehler.jackdaw.store.Stats
import ch.orellbuehler.jackdaw.store.StatsStore
import ch.orellbuehler.jackdaw.store.ThemePreference
import ch.orellbuehler.jackdaw.store.TypeCount
import ch.orellbuehler.jackdaw.study.AttemptRecorder
import ch.orellbuehler.jackdaw.study.LoadedPack
import ch.orellbuehler.jackdaw.study.PackContext
import ch.orellbuehler.jackdaw.study.Plan
import ch.orellbuehler.jackdaw.study.Planner
import ch.orellbuehler.jackdaw.study.ResolvedConfig
import ch.orellbuehler.jackdaw.study.SessionConfig
import ch.orellbuehler.jackdaw.study.SessionPreview
import ch.orellbuehler.jackdaw.study.StudySession
import ch.orellbuehler.jackdaw.sync.JackdawSync
import ch.orellbuehler.jackdaw.sync.SyncStore
import ch.orellbuehler.jackdaw.sync.SyncTracker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlin.random.Random
import kotlin.time.Clock

class JackdawSdk internal constructor(
    driver: SqlDriver,
    clock: Clock,
    timeZone: () -> TimeZone,
    random: Random,
    private val dispatcher: CoroutineDispatcher,
) {
    constructor(driverFactory: DatabaseDriverFactory) : this(
        driverFactory.createDriver(),
        Clock.System,
        { TimeZone.currentSystemDefault() },
        Random.Default,
        Dispatchers.Default,
    )

    private val env = Env(clock, timeZone, random, dispatcher)
    private val database = JackdawDatabase(driver)
    private val syncTracker = SyncTracker(database, env)
    private val packStore = PackStore(database, env, syncTracker)
    private val libraryStore = LibraryStore(database, env)
    private val settingsStore = SettingsStore(database, env, syncTracker)
    private val statsStore = StatsStore(database, env, packStore, settingsStore)

    val sync: JackdawSync = JackdawSync(SyncStore(database, env, packStore, syncTracker), syncTracker, env, dispatcher)

    fun observePacks(): Flow<List<PackSummary>> = env.observe { packStore.summaries() }

    fun observePack(packId: String): Flow<PackDetail?> = env.observe { packStore.detail(packId) }

    fun observeItems(packId: String): Flow<List<ItemSummary>> = env.observe { packStore.items(packId) }

    suspend fun items(packId: String): List<ItemSummary> = withContext(dispatcher) { packStore.items(packId) }

    suspend fun previewImport(text: String): ImportPreview =
        withContext(dispatcher) {
            when (val read = PackReader.read(text)) {
                is ReadResult.Rejected -> ImportPreview.Invalid(read.error)
                is ReadResult.Accepted -> {
                    val pack = read.pack
                    val diff = packStore.diff(pack)
                    ImportPreview.Valid(
                        packId = pack.id,
                        title = pack.title,
                        descriptionText = pack.description,
                        icon = pack.icon,
                        color = pack.color,
                        language = pack.language,
                        revision = pack.revision ?: 0,
                        itemCount = pack.items.size,
                        sectionCount = pack.sections.size,
                        typeCounts =
                            ItemType.entries.map { type -> TypeCount(type, pack.items.count { it.type == type }) }.filter {
                                it.count >
                                    0
                            },
                        skippedCount = read.skippedItems,
                        warnings = read.warnings,
                        isUpdate = diff.existing != null,
                        installedTitle = diff.existing?.title,
                        installedRevision = diff.existing?.revision ?: 0,
                        isDowngrade = diff.isDowngradeFor(pack),
                        counts = diff.counts,
                        overwritesEdits = diff.existing != null && libraryStore.isEdited(pack.id),
                    )
                }
            }
        }

    suspend fun importPack(text: String): ImportResult = withContext(dispatcher) { install(text, allowDowngrade = false) }

    suspend fun importPackAllowingDowngrade(text: String): ImportResult = withContext(dispatcher) { install(text, allowDowngrade = true) }

    suspend fun importPackFromUrl(
        text: String,
        url: String,
        allowDowngrade: Boolean,
    ): ImportResult = withContext(dispatcher) { install(text, allowDowngrade, url) }

    fun resolvePackUrl(input: String): String? = PackUrl.resolve(input)

    suspend fun setPackArchived(
        packId: String,
        archived: Boolean,
    ) {
        withContext(dispatcher) { libraryStore.setArchived(packId, archived) }
    }

    suspend fun removePackUrl(packId: String) {
        withContext(dispatcher) { libraryStore.setUrl(packId, null) }
    }

    fun observeGroups(): Flow<List<PackGroup>> = env.observe { libraryStore.groups() }

    suspend fun createGroup(name: String): String? = withContext(dispatcher) { libraryStore.createGroup(name) }

    suspend fun renameGroup(
        groupId: String,
        name: String,
    ) {
        withContext(dispatcher) { libraryStore.renameGroup(groupId, name) }
    }

    suspend fun deleteGroup(groupId: String) {
        withContext(dispatcher) { libraryStore.deleteGroup(groupId) }
    }

    suspend fun setPackInGroup(
        packId: String,
        groupId: String,
        member: Boolean,
    ) {
        withContext(dispatcher) { libraryStore.setMember(groupId, packId, member) }
    }

    fun itemTemplate(): String = PackEditor.NEW_ITEM

    suspend fun itemSource(
        packId: String,
        itemId: String,
    ): String? = withContext(dispatcher) { packStore.source(packId)?.let { PackEditor.item(it, itemId) } }

    suspend fun saveItem(
        packId: String,
        itemId: String?,
        json: String,
    ): ImportResult =
        withContext(dispatcher) {
            val source = packStore.source(packId) ?: return@withContext missingPack()
            val placed =
                PackEditor.withItem(source, itemId, json)
                    ?: return@withContext invalidItem("The item is not a valid JSON object.")
            val read = PackReader.read(placed.source)
            if (read is ReadResult.Accepted) {
                val id = PackEditor.idOf(json)
                val problem = read.warnings.firstOrNull { it.itemIndex == placed.index || (id != null && it.itemId == id) }
                if (problem != null) return@withContext invalidItem(problem.message)
                if (read.pack.items.none { it.id == id }) return@withContext invalidItem("The item has a missing or unknown 'type'.")
            }
            edit(placed.source)
        }

    suspend fun deleteItem(
        packId: String,
        itemId: String,
    ): ImportResult =
        withContext(dispatcher) {
            val source = packStore.source(packId) ?: return@withContext missingPack()
            edit(PackEditor.withoutItem(source, itemId) ?: return@withContext invalidItem("There is no item '$itemId' in this pack."))
        }

    suspend fun updatePackInfo(
        packId: String,
        title: String,
        descriptionText: String,
    ): ImportResult =
        withContext(dispatcher) {
            val source = packStore.source(packId) ?: return@withContext missingPack()
            edit(PackEditor.withInfo(source, title, descriptionText) ?: return@withContext missingPack())
        }

    suspend fun previewCombine(
        packId: String,
        otherPackId: String,
    ): CombinePreview =
        withContext(dispatcher) {
            when (val outcome = combined(packId, otherPackId)) {
                is PackCombiner.Outcome.Failed -> CombinePreview.Invalid(outcome.error)
                is PackCombiner.Outcome.Merged ->
                    CombinePreview.Valid(outcome.otherTitle, outcome.addedCount, outcome.duplicateCount, outcome.sectionCount)
            }
        }

    suspend fun combinePacks(
        packId: String,
        otherPackId: String,
    ): ImportResult =
        withContext(dispatcher) {
            when (val outcome = combined(packId, otherPackId)) {
                is PackCombiner.Outcome.Failed -> ImportResult.Failure(outcome.error)
                is PackCombiner.Outcome.Merged ->
                    if (outcome.addedCount == 0) {
                        ImportResult.Success(
                            packId = packId,
                            title = outcome.title,
                            itemCount = outcome.itemCount,
                            skippedCount = 0,
                            isUpdate = true,
                            counts = ImportCounts(0, outcome.itemCount, 0, 0),
                            warnings = emptyList(),
                        )
                    } else {
                        edit(outcome.source)
                    }
            }
        }

    suspend fun deletePack(packId: String) {
        withContext(dispatcher) { packStore.delete(packId) }
    }

    suspend fun resetProgress(packId: String) {
        withContext(dispatcher) { packStore.reset(packId) }
    }

    suspend fun exportPack(packId: String): String? = withContext(dispatcher) { packStore.source(packId) }

    fun samplePacks(): List<SamplePack> =
        Samples.all.map {
            SamplePack(
                id = it.pack.id,
                title = it.pack.title,
                descriptionText = it.pack.description ?: "",
                icon = it.pack.icon,
                color = it.pack.color,
                itemCount = it.pack.items.size,
                revision = it.pack.revision ?: 0,
                downloadUrl = null,
            )
        }

    fun catalogUrl(): String = Catalog.INDEX_URL

    fun readCatalog(text: String): List<SamplePack>? = Catalog.read(text, samplePacks())

    fun authoringPrompt(
        types: List<String> = emptyList(),
        figures: Boolean = false,
    ): String = AuthoringPrompt.build(GENERATED_AUTHORING_PROMPT, types, figures)

    suspend fun importSample(sampleId: String): ImportResult {
        val sample =
            Samples.all.firstOrNull { it.pack.id == sampleId }
                ?: return ImportResult.Failure(
                    ImportError(ImportErrorCode.UNKNOWN_SAMPLE, "There is no sample pack with the id '$sampleId'."),
                )
        return withContext(dispatcher) { install(sample.json, allowDowngrade = true) }
    }

    suspend fun previewSession(
        packId: String,
        config: SessionConfig,
    ): SessionPreview =
        withContext(dispatcher) {
            val plan = prepare(packId, config).plan
            SessionPreview(plan.dueCount, plan.newCount, plan.entries.size)
        }

    suspend fun startSession(
        packId: String,
        config: SessionConfig,
    ): StudySession {
        val prepared = withContext(dispatcher) { prepare(packId, config) }
        val recorder = AttemptRecorder { attempt -> withContext(dispatcher) { packStore.record(packId, attempt) } }
        return StudySession(
            packId,
            config.mode,
            prepared.pack,
            prepared.plan.entries,
            prepared.config,
            recorder,
            env.clock,
            env.random,
            prepared.tolerateTypos,
        )
    }

    fun observeStats(): Flow<Stats> = observeStats(StatsStore.DEFAULT_DAYS)

    fun observeStats(days: Int): Flow<Stats> = env.observe { statsStore.stats(days) }

    fun observeSettings(): Flow<AppSettings> = env.observe { settingsStore.get() }

    suspend fun settings(): AppSettings = withContext(dispatcher) { settingsStore.get() }

    suspend fun setDailyGoal(goal: Int) {
        withContext(dispatcher) { settingsStore.setDailyGoal(goal) }
    }

    suspend fun setDefaultSessionSize(size: Int) {
        withContext(dispatcher) { settingsStore.setDefaultSessionSize(size) }
    }

    suspend fun setTypoTolerance(enabled: Boolean) {
        withContext(dispatcher) { settingsStore.setTypoTolerance(enabled) }
    }

    suspend fun setTheme(theme: ThemePreference) {
        withContext(dispatcher) { settingsStore.setTheme(theme) }
    }

    suspend fun setOnboardingSeen(seen: Boolean) {
        withContext(dispatcher) { settingsStore.setOnboardingSeen(seen) }
    }

    fun refresh() {
        env.changed()
    }

    private class Prepared(
        val pack: LoadedPack,
        val config: ResolvedConfig,
        val plan: Plan,
        val tolerateTypos: Boolean,
    )

    private fun prepare(
        packId: String,
        config: SessionConfig,
    ): Prepared {
        val pack = packStore.load(packId) ?: LoadedPack(packId, PackContext(Study.DEFAULT, emptyList()), emptyMap())
        val settings = settingsStore.get()
        val resolved = ResolvedConfig.resolve(config, settings.defaultSessionSize, pack.context.study.newPerSession)
        val plan = Planner.plan(pack, resolved, env.cutoffs(env.now()), env.random)
        return Prepared(pack, resolved, plan, settings.typoTolerance)
    }

    private fun missingPack() = ImportResult.Failure(ImportError(ImportErrorCode.PACK_MISSING, "The pack is no longer installed."))

    private fun invalidItem(message: String) = ImportResult.Failure(ImportError(ImportErrorCode.ITEM_INVALID, message))

    private fun combined(
        packId: String,
        otherPackId: String,
    ): PackCombiner.Outcome {
        val source = packStore.source(packId) ?: return PackCombiner.Outcome.Failed(missingPack().error)
        val other = packStore.source(otherPackId) ?: return PackCombiner.Outcome.Failed(missingPack().error)
        val outcome = PackCombiner.combine(source, other)
        if (outcome is PackCombiner.Outcome.Merged && outcome.addedCount > 0) {
            val read = PackReader.read(outcome.source)
            if (read is ReadResult.Rejected) return PackCombiner.Outcome.Failed(read.error)
        }
        return outcome
    }

    private fun edit(source: String): ImportResult = install(source, allowDowngrade = true, edited = true)

    private fun install(
        text: String,
        allowDowngrade: Boolean,
        url: String? = null,
        edited: Boolean = false,
    ): ImportResult {
        val read = PackReader.read(text)
        if (read !is ReadResult.Accepted) return ImportResult.Failure((read as ReadResult.Rejected).error)
        val pack = read.pack
        return try {
            val before = packStore.diff(pack)
            if (!allowDowngrade && before.isDowngradeFor(pack)) {
                return ImportResult.Failure(
                    ImportError(
                        ImportErrorCode.REVISION_DOWNGRADE,
                        "The installed pack has revision ${before.existing?.revision}, this file has revision ${pack.revision}.",
                    ),
                )
            }
            val diff = packStore.install(pack, read.payloads, text.removePrefix("\uFEFF"), env.now())
            libraryStore.setEdited(pack.id, edited)
            if (url != null) libraryStore.setUrl(pack.id, url)
            ImportResult.Success(
                packId = pack.id,
                title = pack.title,
                itemCount = pack.items.size,
                skippedCount = read.skippedItems,
                isUpdate = before.existing != null,
                counts = diff.counts,
                warnings = read.warnings,
            )
        } catch (e: Exception) {
            ImportResult.Failure(ImportError(ImportErrorCode.STORAGE_ERROR, "The pack could not be saved: ${e.message ?: "unknown error"}"))
        }
    }
}
