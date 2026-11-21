package ch.orellbuehler.jackdaw

import ch.orellbuehler.jackdaw.format.ItemType
import ch.orellbuehler.jackdaw.store.AppSettings
import ch.orellbuehler.jackdaw.store.ItemStatus
import ch.orellbuehler.jackdaw.store.ItemSummary
import ch.orellbuehler.jackdaw.store.PackDetail
import ch.orellbuehler.jackdaw.store.PackSummary
import ch.orellbuehler.jackdaw.store.SectionSummary
import ch.orellbuehler.jackdaw.store.Stats
import ch.orellbuehler.jackdaw.store.ThemePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

fun packSummary(
    id: String = "p1",
    title: String = "Pack $id",
    due: Int = 0,
    new: Int = 0,
    lastStudiedAt: Long = 0,
    progress: Double = 0.0,
    revision: Int = 0,
) = PackSummary(
    id = id,
    title = title,
    descriptionText = null,
    icon = null,
    color = null,
    language = null,
    itemCount = 10,
    facetCount = 10,
    newCount = new,
    learningCount = 0,
    reviewCount = 0,
    masteredCount = 0,
    dueCount = due,
    progress = progress,
    lastStudiedAt = lastStudiedAt,
    importedAt = 0,
    updatedAt = 0,
    revision = revision,
)

fun packDetail(summary: PackSummary = packSummary()) =
    PackDetail(
        summary = summary,
        author = null,
        license = null,
        source = null,
        tags = emptyList(),
        revision = 0,
        newPerSession = 10,
        randomOrder = false,
        sections =
            listOf(
                SectionSummary("", "", null, 10, 10, 10, 0, 0, 0, 0, 0.0, true),
            ),
        typeCounts = emptyList(),
        practiceFilters = emptyList(),
    )

fun itemSummary(
    id: String,
    title: String = id,
    subtitle: String = "",
    status: ItemStatus = ItemStatus.NEW,
    sectionId: String = "",
) = ItemSummary(
    id = id,
    position = 0,
    type = ItemType.CARD,
    sectionId = sectionId,
    title = title,
    subtitle = subtitle,
    status = status,
    isDue = false,
    facets = emptyList(),
)

fun statsOf(
    streak: Int = 0,
    reviewsToday: Int = 0,
    goal: Int = 20,
) = Stats(
    currentStreak = streak,
    longestStreak = streak,
    reviewsToday = reviewsToday,
    correctToday = reviewsToday,
    totalReviews = reviewsToday,
    accuracy = 1.0,
    facetsLearned = 0,
    facetsMastered = 0,
    dueToday = 0,
    dailyGoal = goal,
    goalReached = reviewsToday >= goal,
    activity = emptyList(),
)

fun settingsOf(
    theme: ThemePreference = ThemePreference.SYSTEM,
    onboardingSeen: Boolean = true,
) = AppSettings(dailyGoal = 20, defaultSessionSize = 20, theme = theme, onboardingSeen = onboardingSeen)
