@preconcurrency import shared
import Foundation

enum Mapping {
    static func pack(_ s: PackSummary) -> PackInfo {
        PackInfo(
            id: s.id,
            title: s.title,
            blurb: s.descriptionText,
            icon: s.icon,
            colorHex: s.color,
            language: s.language,
            itemCount: Int(s.itemCount),
            facetCount: Int(s.facetCount),
            newCount: Int(s.newCount),
            learningCount: Int(s.learningCount),
            reviewCount: Int(s.reviewCount),
            masteredCount: Int(s.masteredCount),
            dueCount: Int(s.dueCount),
            progress: s.progress,
            lastStudiedAt: date(fromMillis: s.lastStudiedAt),
            revision: Int(s.revision)
        )
    }

    static func section(_ s: SectionSummary) -> SectionInfo {
        SectionInfo(
            id: s.id,
            title: s.title,
            blurb: s.descriptionText,
            itemCount: Int(s.itemCount),
            newCount: Int(s.newCount),
            learningCount: Int(s.learningCount),
            reviewCount: Int(s.reviewCount),
            masteredCount: Int(s.masteredCount),
            dueCount: Int(s.dueCount),
            progress: s.progress,
            isUngrouped: s.isUngrouped
        )
    }

    static func detail(_ d: PackDetail) -> PackDetailInfo {
        PackDetailInfo(
            pack: pack(d.summary),
            author: d.author,
            license: d.license,
            source: d.source,
            tags: d.tags,
            revision: Int(d.revision),
            newPerSession: Int(d.newPerSession),
            randomOrder: d.randomOrder,
            sections: d.sections.map { section($0) },
            typeCounts: typeCounts(d.typeCounts),
            practice: d.practiceFilters.map { practiceKind(normalized($0)) }
        )
    }

    static func item(_ i: ItemSummary) -> ItemInfo {
        ItemInfo(
            id: i.id,
            position: Int(i.position),
            kind: itemKind(i.type),
            sectionId: i.sectionId,
            title: i.title,
            subtitle: i.subtitle,
            state: itemState(i.status),
            isDue: i.isDue
        )
    }

    static func stats(_ s: Stats) -> StatsInfo {
        StatsInfo(
            currentStreak: Int(s.currentStreak),
            longestStreak: Int(s.longestStreak),
            reviewsToday: Int(s.reviewsToday),
            correctToday: Int(s.correctToday),
            totalReviews: Int(s.totalReviews),
            accuracy: s.accuracy,
            facetsLearned: Int(s.facetsLearned),
            facetsMastered: Int(s.facetsMastered),
            dueToday: Int(s.dueToday),
            dailyGoal: Int(s.dailyGoal),
            goalReached: s.goalReached,
            activity: s.activity.compactMap { day -> DayActivityInfo? in
                guard let parsed = isoDay(day.date) else { return nil }
                return DayActivityInfo(date: parsed, reviews: Int(day.reviews), correct: Int(day.correct))
            }
        )
    }

    static func settings(_ s: AppSettings) -> SettingsInfo {
        SettingsInfo(
            dailyGoal: Int(s.dailyGoal),
            defaultSessionSize: Int(s.defaultSessionSize),
            theme: themeChoice(s.theme),
            onboardingSeen: s.onboardingSeen,
            typoTolerance: s.typoTolerance
        )
    }

    static func sample(_ s: SamplePack) -> SamplePackInfo {
        SamplePackInfo(
            id: s.id,
            title: s.title,
            blurb: s.descriptionText,
            icon: s.icon,
            colorHex: s.color,
            itemCount: Int(s.itemCount),
            revision: Int(s.revision),
            downloadURL: s.downloadUrl.flatMap { URL(string: $0) }
        )
    }

    static func typeCounts(_ counts: [TypeCount]) -> [TypeCountInfo] {
        counts.map { TypeCountInfo(kind: itemKind($0.type), count: Int($0.count)) }
    }

    // SKIE turns Kotlin enums into Swift enums with lowerCamelCase cases, but enums inside a Kotlin
    // List can keep the Kotlin class (`NOT_JSON`) because SKIE does not swap enums in generics. Names
    // that are not a single word are therefore compared as text without case or underscores, which
    // both spellings share. ItemStatus.NEW is the `default` branch on purpose: `new` collides with
    // NSObject and SKIE renames it to `theNew`.
    static func normalized(_ value: Any) -> String {
        String(describing: value).replacingOccurrences(of: "_", with: "").lowercased()
    }

    static func itemKind(_ type: ItemType) -> ItemKind {
        switch type {
        case .card: return .card
        case .choice: return .choice
        case .text: return .text
        case .cloze: return .cloze
        case .order: return .order
        default: return normalized(type) == "truefalse" ? .trueFalse : .open
        }
    }

    static func itemState(_ status: ItemStatus) -> ItemState {
        switch status {
        case .learning: return .learning
        case .review: return .review
        case .mastered: return .mastered
        default: return .new
        }
    }

    static func practiceKind(_ name: String) -> PracticeKind {
        switch name {
        case "flashcards": return .flashcards
        case "quiz": return .quiz
        case "typing": return .typing
        default: return .all
        }
    }

    static func kotlinPracticeFilter(_ kind: PracticeKind) -> PracticeFilter {
        switch kind {
        case .all: return PracticeFilter.all
        case .flashcards: return PracticeFilter.flashcards
        case .quiz: return PracticeFilter.quiz
        case .typing: return PracticeFilter.typing
        }
    }

    static func themeChoice(_ theme: ThemePreference) -> ThemeChoice {
        switch theme {
        case .light: return .light
        case .dark: return .dark
        default: return .system
        }
    }

    static func kotlinTheme(_ choice: ThemeChoice) -> ThemePreference {
        switch choice {
        case .system: return ThemePreference.system
        case .light: return ThemePreference.light
        case .dark: return ThemePreference.dark
        }
    }

    static func kotlinConfig(_ request: StudyRequest) -> SessionConfig {
        SessionConfig(
            mode: request.mode == .learn ? SessionMode.learn : SessionMode.practice,
            sectionId: request.sectionId,
            size: 0,
            newLimit: -1,
            filter: kotlinPracticeFilter(request.filter),
            rescheduleInPractice: false
        )
    }

    static func date(fromMillis millis: Int64) -> Date? {
        if millis <= 0 { return nil }
        return Date(timeIntervalSince1970: Double(millis) / 1000)
    }

    static func isoDay(_ iso: String) -> Date? {
        let parts = iso.components(separatedBy: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        return Calendar.current.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2]))
    }

    static func styled(_ text: RichText) -> StyledText {
        StyledText(
            spans: text.spans.map {
                StyledSpan(text: $0.text, bold: $0.bold, italic: $0.italic, code: $0.code)
            }
        )
    }
}
