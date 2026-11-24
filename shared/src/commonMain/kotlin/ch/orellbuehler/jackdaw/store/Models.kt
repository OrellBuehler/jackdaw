package ch.orellbuehler.jackdaw.store

import ch.orellbuehler.jackdaw.format.ImportError
import ch.orellbuehler.jackdaw.format.ImportWarning
import ch.orellbuehler.jackdaw.format.ItemType
import ch.orellbuehler.jackdaw.study.Facet
import ch.orellbuehler.jackdaw.study.PracticeFilter

data class PackSummary(
    val id: String,
    val title: String,
    val descriptionText: String?,
    val icon: String?,
    val color: String?,
    val language: String?,
    val itemCount: Int,
    val facetCount: Int,
    val newCount: Int,
    val learningCount: Int,
    val reviewCount: Int,
    val masteredCount: Int,
    val dueCount: Int,
    val progress: Double,
    val lastStudiedAt: Long,
    val importedAt: Long,
    val updatedAt: Long,
    val revision: Int,
    val archived: Boolean = false,
    val sourceUrl: String? = null,
    val edited: Boolean = false,
    val groupIds: List<String> = emptyList(),
)

data class PackGroup(
    val id: String,
    val name: String,
    val packIds: List<String>,
)

data class SectionSummary(
    val id: String,
    val title: String,
    val descriptionText: String?,
    val itemCount: Int,
    val facetCount: Int,
    val newCount: Int,
    val learningCount: Int,
    val reviewCount: Int,
    val masteredCount: Int,
    val dueCount: Int,
    val progress: Double,
    val isUngrouped: Boolean,
)

data class TypeCount(
    val type: ItemType,
    val count: Int,
)

data class PackDetail(
    val summary: PackSummary,
    val author: String?,
    val license: String?,
    val source: String?,
    val tags: List<String>,
    val revision: Int,
    val newPerSession: Int,
    val randomOrder: Boolean,
    val sections: List<SectionSummary>,
    val typeCounts: List<TypeCount>,
    val practiceFilters: List<PracticeFilter>,
)

enum class ItemStatus {
    NEW,
    LEARNING,
    REVIEW,
    MASTERED,
}

data class FacetInfo(
    val facet: Facet,
    val status: ItemStatus,
    val intervalDays: Int,
    val dueAt: Long,
    val reps: Int,
    val lapses: Int,
)

data class ItemSummary(
    val id: String,
    val position: Int,
    val type: ItemType,
    val sectionId: String,
    val title: String,
    val subtitle: String,
    val status: ItemStatus,
    val isDue: Boolean,
    val facets: List<FacetInfo>,
)

data class ImportCounts(
    val addedItems: Int,
    val keptItems: Int,
    val resetItems: Int,
    val removedItems: Int,
)

sealed class ImportResult {
    data class Success(
        val packId: String,
        val title: String,
        val itemCount: Int,
        val skippedCount: Int,
        val isUpdate: Boolean,
        val counts: ImportCounts,
        val warnings: List<ImportWarning>,
    ) : ImportResult()

    data class Failure(
        val error: ImportError,
    ) : ImportResult()
}

sealed class ImportPreview {
    data class Valid(
        val packId: String,
        val title: String,
        val descriptionText: String?,
        val icon: String?,
        val color: String?,
        val language: String?,
        val revision: Int,
        val itemCount: Int,
        val sectionCount: Int,
        val typeCounts: List<TypeCount>,
        val skippedCount: Int,
        val warnings: List<ImportWarning>,
        val isUpdate: Boolean,
        val installedTitle: String?,
        val installedRevision: Int,
        val isDowngrade: Boolean,
        val counts: ImportCounts,
        val overwritesEdits: Boolean = false,
    ) : ImportPreview()

    data class Invalid(
        val error: ImportError,
    ) : ImportPreview()
}

data class SamplePack(
    val id: String,
    val title: String,
    val descriptionText: String,
    val icon: String?,
    val color: String?,
    val itemCount: Int,
    val revision: Int,
    val downloadUrl: String?,
)

data class DayActivity(
    val date: String,
    val epochDay: Int,
    val reviews: Int,
    val correct: Int,
)

data class Stats(
    val currentStreak: Int,
    val longestStreak: Int,
    val reviewsToday: Int,
    val correctToday: Int,
    val totalReviews: Int,
    val accuracy: Double,
    val facetsLearned: Int,
    val facetsMastered: Int,
    val dueToday: Int,
    val dailyGoal: Int,
    val goalReached: Boolean,
    val activity: List<DayActivity>,
)

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

data class AppSettings(
    val dailyGoal: Int,
    val defaultSessionSize: Int,
    val theme: ThemePreference,
    val onboardingSeen: Boolean,
    val typoTolerance: Boolean = true,
)
