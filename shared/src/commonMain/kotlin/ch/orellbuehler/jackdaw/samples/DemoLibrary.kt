package ch.orellbuehler.jackdaw.samples

import ch.orellbuehler.jackdaw.db.JackdawDatabase
import ch.orellbuehler.jackdaw.format.ItemType
import ch.orellbuehler.jackdaw.store.Env
import ch.orellbuehler.jackdaw.store.PackStore
import ch.orellbuehler.jackdaw.store.toLong
import ch.orellbuehler.jackdaw.study.ExerciseType
import ch.orellbuehler.jackdaw.study.Grade
import ch.orellbuehler.jackdaw.study.LearningState
import ch.orellbuehler.jackdaw.study.Schedule
import ch.orellbuehler.jackdaw.study.Scheduler
import kotlin.random.Random

internal class DemoPack(
    val sample: String,
    val studied: Double,
    val startedDaysAgo: Int,
    val idleDays: Int,
)

internal class DemoLibrary(
    private val db: JackdawDatabase,
    private val env: Env,
    private val packs: PackStore,
) {
    fun seed(install: (String) -> Unit): Boolean {
        if (packs.summaries().isNotEmpty()) return false
        val now = env.now()
        val random = Random(SEED)
        for (demo in PACKS) {
            val sample = Samples.all.firstOrNull { it.pack.id == PREFIX + demo.sample } ?: continue
            install(sample.json)
            db.transaction {
                study(sample.pack.id, demo, now, random)
                packs.recompute(sample.pack.id, null)
            }
        }
        db.transaction { keepStreak(now) }
        env.changed()
        return true
    }

    private fun study(
        packId: String,
        demo: DemoPack,
        now: Long,
        random: Random,
    ) {
        val items = packs.items(packId)
        val count = (items.size * demo.studied).toInt()
        val until = now - demo.idleDays * Scheduler.DAY_MS
        items.take(count).forEachIndexed { index, item ->
            val introducedDaysAgo = demo.startedDaysAgo - index * demo.startedDaysAgo * 2 / (count * 3)
            for (facet in item.facets) {
                var ts = now - introducedDaysAgo * Scheduler.DAY_MS - random.nextLong(5, 120) * MINUTE_MS
                var schedule = Schedule.fresh()
                while (ts <= until) {
                    val grade = grade(random)
                    log(packId, item.id, facet.facet.code, item.type, grade, ts, practice = false)
                    schedule = Scheduler.review(schedule, grade, ts)
                    ts =
                        if (schedule.state == LearningState.LEARNING) {
                            schedule.dueAt + MINUTE_MS
                        } else {
                            schedule.dueAt + random.nextInt(0, 1 + schedule.intervalDays / 4) * Scheduler.DAY_MS
                        }
                }
            }
        }
    }

    private fun keepStreak(now: Long) {
        val active =
            db.reviewQueries
                .logByDay()
                .executeAsList()
                .map { it.day.toInt() }
                .toSet()
        val pack =
            db.reviewQueries
                .lastStudied()
                .executeAsList()
                .firstOrNull() ?: return
        val item = packs.items(pack.pack_id).firstOrNull() ?: return
        val facet = item.facets.firstOrNull() ?: return
        for (daysAgo in 0 until STREAK_DAYS) {
            val ts = now - daysAgo * Scheduler.DAY_MS - MINUTE_MS
            if (env.epochDay(ts) in active) continue
            for (n in 0 until STREAK_REVIEWS) {
                log(pack.pack_id, item.id, facet.facet.code, item.type, Grade.GOOD, ts - n * MINUTE_MS, practice = true)
            }
        }
    }

    private fun log(
        packId: String,
        itemId: String,
        facet: Int,
        type: ItemType,
        grade: Grade,
        ts: Long,
        practice: Boolean,
    ) {
        db.reviewQueries.insertLog(
            env.newId(),
            ts,
            env.epochDay(ts).toLong(),
            packId,
            itemId,
            facet.toLong(),
            exerciseOf(type).code.toLong(),
            grade.code.toLong(),
            (grade != Grade.AGAIN).toLong(),
            practice.toLong(),
            (!practice).toLong(),
            type.code,
        )
    }

    private fun grade(random: Random): Grade {
        val roll = random.nextInt(100)
        return when {
            roll < 7 -> Grade.AGAIN
            roll < 17 -> Grade.HARD
            roll < 85 -> Grade.GOOD
            else -> Grade.EASY
        }
    }

    private fun exerciseOf(type: ItemType): ExerciseType =
        when (type) {
            ItemType.CARD -> ExerciseType.FLIP
            ItemType.CHOICE -> ExerciseType.CHOICE
            ItemType.TEXT -> ExerciseType.TYPE
            ItemType.CLOZE -> ExerciseType.CLOZE
            ItemType.TRUE_FALSE -> ExerciseType.TRUE_FALSE
            ItemType.ORDER -> ExerciseType.ORDER
            ItemType.OPEN -> ExerciseType.OPEN
        }

    companion object {
        const val PREFIX = "com.github.orellbuehler.jackdaw.examples."
        private const val SEED = 7
        private const val MINUTE_MS = 60_000L
        private const val STREAK_DAYS = 12
        private const val STREAK_REVIEWS = 6

        val PACKS =
            listOf(
                DemoPack("swiss-cantons", studied = 1.0, startedDaysAgo = 80, idleDays = 0),
                DemoPack("flags-of-the-world", studied = 0.45, startedDaysAgo = 60, idleDays = 2),
                DemoPack("world-capitals", studied = 0.3, startedDaysAgo = 45, idleDays = 0),
                DemoPack("solar-system", studied = 0.8, startedDaysAgo = 40, idleDays = 3),
                DemoPack("git-essentials", studied = 0.6, startedDaysAgo = 30, idleDays = 0),
                DemoPack("spanish-basics", studied = 0.5, startedDaysAgo = 20, idleDays = 1),
                DemoPack("history-on-the-map", studied = 0.0, startedDaysAgo = 0, idleDays = 0),
                DemoPack("rivers-on-the-map", studied = 0.0, startedDaysAgo = 0, idleDays = 0),
            )
    }
}
