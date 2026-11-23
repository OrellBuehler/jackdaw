package ch.orellbuehler.jackdaw.study

import ch.orellbuehler.jackdaw.FakeClock
import ch.orellbuehler.jackdaw.format.Pack
import kotlin.random.Random

internal class RecordingRecorder : AttemptRecorder {
    val attempts = ArrayList<Attempt>()

    override suspend fun record(attempt: Attempt) {
        attempts += attempt
    }
}

internal fun loaded(
    pack: Pack,
    schedules: Map<FacetKey, Schedule> = emptyMap(),
): LoadedPack = LoadedPack(pack.id, PackContext(pack.study, pack.items), schedules)

internal fun plan(
    pack: LoadedPack,
    config: SessionConfig,
    clock: FakeClock = FakeClock(),
    random: Random = Random(1),
    defaultSize: Int = 20,
): Plan {
    val resolved = ResolvedConfig.resolve(config, defaultSize, pack.context.study.newPerSession)
    return Planner.plan(pack, resolved, DueCutoffs(clock.millis + 20 * 60_000L, clock.millis + 86_400_000L), random)
}

internal fun session(
    pack: LoadedPack,
    config: SessionConfig,
    recorder: AttemptRecorder = RecordingRecorder(),
    clock: FakeClock = FakeClock(),
    random: Random = Random(1),
    tolerateTypos: Boolean = false,
): StudySession {
    val resolved = ResolvedConfig.resolve(config, 20, pack.context.study.newPerSession)
    val plan = plan(pack, config, clock, random)
    return StudySession(pack.packId, config.mode, pack, plan.entries, resolved, recorder, clock, random, tolerateTypos)
}

internal fun key(
    itemId: String,
    facet: Facet = Facet.FORWARD,
) = FacetKey(itemId, facet)
