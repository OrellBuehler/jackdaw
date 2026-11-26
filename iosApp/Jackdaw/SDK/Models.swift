import Foundation

struct PackInfo: Identifiable, Hashable, Sendable {
    let id: String
    let title: String
    let blurb: String?
    let icon: String?
    let colorHex: String?
    let language: String?
    let itemCount: Int
    let facetCount: Int
    let newCount: Int
    let learningCount: Int
    let reviewCount: Int
    let masteredCount: Int
    let dueCount: Int
    let progress: Double
    let lastStudiedAt: Date?
    let revision: Int
    let archived: Bool
    let sourceUrl: String?
    let edited: Bool
    let groupIds: [String]
}

struct PackGroupInfo: Identifiable, Hashable, Sendable {
    let id: String
    let name: String
    let packIds: [String]
}

struct SectionInfo: Identifiable, Hashable, Sendable {
    let id: String
    let title: String
    let blurb: String?
    let itemCount: Int
    let newCount: Int
    let learningCount: Int
    let reviewCount: Int
    let masteredCount: Int
    let dueCount: Int
    let progress: Double
    let isUngrouped: Bool
}

enum ItemKind: Hashable, Sendable, CaseIterable {
    case card
    case choice
    case text
    case cloze
    case trueFalse
    case order
    case open

    var symbolName: String {
        switch self {
        case .card: "rectangle.on.rectangle.angled"
        case .choice: "checklist"
        case .text: "keyboard"
        case .cloze: "text.append"
        case .trueFalse: "checkmark.circle.badge.xmark"
        case .order: "list.number"
        case .open: "bubble.left.and.text.bubble.right"
        }
    }

    var labelKey: String {
        switch self {
        case .card: "kind.card"
        case .choice: "kind.choice"
        case .text: "kind.text"
        case .cloze: "kind.cloze"
        case .trueFalse: "kind.truefalse"
        case .order: "kind.order"
        case .open: "kind.open"
        }
    }

    var code: String {
        switch self {
        case .card: "card"
        case .choice: "choice"
        case .text: "text"
        case .cloze: "cloze"
        case .trueFalse: "truefalse"
        case .order: "order"
        case .open: "open"
        }
    }
}

struct TypeCountInfo: Identifiable, Hashable, Sendable {
    let kind: ItemKind
    let count: Int

    var id: ItemKind { kind }
}

enum PracticeKind: Hashable, Sendable {
    case all
    case flashcards
    case quiz
    case typing
}

struct PackDetailInfo: Hashable, Sendable {
    let pack: PackInfo
    let author: String?
    let license: String?
    let source: String?
    let tags: [String]
    let revision: Int
    let newPerSession: Int
    let randomOrder: Bool
    let sections: [SectionInfo]
    let typeCounts: [TypeCountInfo]
    let practice: [PracticeKind]
}

enum ItemState: Hashable, Sendable {
    case new
    case learning
    case review
    case mastered
}

struct ItemInfo: Identifiable, Hashable, Sendable {
    let id: String
    let position: Int
    let kind: ItemKind
    let sectionId: String
    let title: String
    let subtitle: String
    let state: ItemState
    let isDue: Bool
}

struct DayActivityInfo: Identifiable, Hashable, Sendable {
    let date: Date
    let reviews: Int
    let correct: Int

    var id: Date { date }
}

struct StatsInfo: Hashable, Sendable {
    let currentStreak: Int
    let longestStreak: Int
    let reviewsToday: Int
    let correctToday: Int
    let totalReviews: Int
    let accuracy: Double
    let facetsLearned: Int
    let facetsMastered: Int
    let dueToday: Int
    let dailyGoal: Int
    let goalReached: Bool
    let activity: [DayActivityInfo]

    static let empty = StatsInfo(
        currentStreak: 0,
        longestStreak: 0,
        reviewsToday: 0,
        correctToday: 0,
        totalReviews: 0,
        accuracy: 0,
        facetsLearned: 0,
        facetsMastered: 0,
        dueToday: 0,
        dailyGoal: 20,
        goalReached: false,
        activity: []
    )
}

enum ThemeChoice: String, CaseIterable, Identifiable, Hashable, Sendable {
    case system
    case light
    case dark

    var id: String { rawValue }
}

struct SettingsInfo: Hashable, Sendable {
    var dailyGoal: Int
    var defaultSessionSize: Int
    var theme: ThemeChoice
    var onboardingSeen: Bool
    var typoTolerance: Bool

    static let initial = SettingsInfo(
        dailyGoal: 20,
        defaultSessionSize: 20,
        theme: .system,
        onboardingSeen: false,
        typoTolerance: true
    )
}

struct SamplePackInfo: Identifiable, Hashable, Sendable {
    let id: String
    let title: String
    let blurb: String
    let icon: String?
    let colorHex: String?
    let itemCount: Int
    let revision: Int
    let downloadURL: URL?
}

enum ImportIssueKind: Hashable, Sendable {
    case notAPack
    case versionTooNew
    case invalidPack
    case noItems
    case tooLarge
    case revisionDowngrade
    case storage
    case unknownSample
    case unreadableFile
    case downloadFailed
    case itemInvalid
    case packMissing
    case other
}

struct ImportIssue: Hashable, Sendable {
    let kind: ImportIssueKind
    let message: String

    var localizedMessage: String {
        switch kind {
        case .notAPack: L10n.tr("import.error.notapack")
        case .versionTooNew: L10n.tr("import.error.versiontoonew")
        case .invalidPack: L10n.tr("import.error.invalid")
        case .noItems: L10n.tr("import.error.noitems")
        case .tooLarge: L10n.tr("import.error.toolarge")
        case .revisionDowngrade: L10n.tr("import.error.downgrade")
        case .storage: L10n.tr("import.error.storage")
        case .unreadableFile: L10n.tr("import.error.unreadable")
        case .downloadFailed: L10n.tr("import.error.download")
        case .itemInvalid: L10n.tr("import.error.iteminvalid")
        case .packMissing: L10n.tr("import.error.packmissing")
        case .unknownSample, .other: message.isEmpty ? L10n.tr("import.error.generic") : message
        }
    }

    var detailMessage: String? {
        guard kind == .itemInvalid, !message.isEmpty else { return nil }
        return message
    }
}

enum ImportWarningKind: Hashable, Sendable {
    case unknownType
    case more
    case other
}

struct ImportWarningInfo: Identifiable, Hashable, Sendable {
    let id: Int
    let kind: ImportWarningKind
    let message: String
    let itemId: String?
    let itemIndex: Int
    let detail: String?
    let count: Int

    var localizedMessage: String {
        switch kind {
        case .unknownType:
            guard let detail else { return message }
            return L10n.format("import.warning.unknowntype", count, detail)
        case .more:
            return L10n.format("import.warning.more", count)
        case .other:
            return message
        }
    }
}

struct ImportCountsInfo: Hashable, Sendable {
    let added: Int
    let kept: Int
    let reset: Int
    let removed: Int
}

struct ImportPreviewInfo: Hashable, Sendable {
    let packId: String
    let title: String
    let blurb: String?
    let icon: String?
    let colorHex: String?
    let language: String?
    let revision: Int
    let itemCount: Int
    let sectionCount: Int
    let typeCounts: [TypeCountInfo]
    let skippedCount: Int
    let warnings: [ImportWarningInfo]
    let isUpdate: Bool
    let installedTitle: String?
    let installedRevision: Int
    let isDowngrade: Bool
    let counts: ImportCountsInfo
    let overwritesEdits: Bool
}

enum PreviewOutcome: Sendable {
    case valid(ImportPreviewInfo)
    case invalid(ImportIssue)
}

struct CombinePreviewInfo: Hashable, Sendable {
    let otherTitle: String
    let addedCount: Int
    let duplicateCount: Int
    let sectionCount: Int
}

enum CombineOutcome: Sendable {
    case valid(CombinePreviewInfo)
    case invalid(ImportIssue)
}

struct ImportDoneInfo: Hashable, Sendable {
    let packId: String
    let title: String
    let itemCount: Int
    let skippedCount: Int
    let isUpdate: Bool
    let counts: ImportCountsInfo
    let warnings: [ImportWarningInfo]
}

enum ImportOutcome: Sendable {
    case success(ImportDoneInfo)
    case failure(ImportIssue)
}

enum StudyMode: Hashable, Sendable {
    case learn
    case practice
}

struct StudyRequest: Identifiable, Hashable, Sendable {
    let id = UUID()
    let packId: String
    let title: String
    let colorHex: String?
    let mode: StudyMode
    let sectionId: String?
    let filter: PracticeKind

    static func learn(pack: PackInfo, sectionId: String? = nil) -> StudyRequest {
        StudyRequest(
            packId: pack.id,
            title: pack.title,
            colorHex: pack.colorHex,
            mode: .learn,
            sectionId: sectionId,
            filter: .all
        )
    }

    static func practice(pack: PackInfo, filter: PracticeKind, sectionId: String? = nil) -> StudyRequest {
        StudyRequest(
            packId: pack.id,
            title: pack.title,
            colorHex: pack.colorHex,
            mode: .practice,
            sectionId: sectionId,
            filter: filter
        )
    }
}

struct SessionPreviewInfo: Hashable, Sendable {
    let dueCount: Int
    let newCount: Int
    let totalCount: Int

    static let empty = SessionPreviewInfo(dueCount: 0, newCount: 0, totalCount: 0)
}

struct StyledSpan: Hashable, Sendable {
    let text: String
    let bold: Bool
    let italic: Bool
    let code: Bool
}

struct StyledText: Hashable, Sendable {
    let spans: [StyledSpan]

    static let empty = StyledText(spans: [])

    static func plain(_ text: String) -> StyledText {
        if text.isEmpty { return .empty }
        return StyledText(spans: [StyledSpan(text: text, bold: false, italic: false, code: false)])
    }

    var plainText: String { spans.map(\.text).joined() }

    var isEmpty: Bool { spans.isEmpty }
}

struct OptionInfo: Identifiable, Hashable, Sendable {
    let id: String
    let text: StyledText
}

enum AnswerKeyboardInfo: Hashable, Sendable {
    case text
    case integer
    case decimal
}

enum ClozeSegmentInfo: Hashable, Sendable {
    case text(StyledText)
    case gap(index: Int, hint: String?, keyboard: AnswerKeyboardInfo)
}

enum ExerciseKind: Hashable, Sendable {
    case flip
    case choice
    case typed
    case cloze
    case trueFalse
    case order
    case open
}

struct ExerciseInfo: Hashable, Sendable {
    let itemId: String
    let kind: ExerciseKind
    let prompt: StyledText
    let options: [OptionInfo]
    let multiSelect: Bool
    let segments: [ClozeSegmentInfo]
    let gapCount: Int
    var keyboard: AnswerKeyboardInfo = .text
}

struct FigureInfo: Hashable, Sendable {
    let html: String
    let alt: String?
    let aspectRatio: Double
    let isMap: Bool
}

struct QuestionInfo: Hashable, Sendable {
    let exercise: ExerciseInfo
    let position: Int
    let total: Int
    let isRetry: Bool
    let hasHint: Bool
    let hint: StyledText?
    let figure: FigureInfo?
}

enum GradeChoice: Int, CaseIterable, Identifiable, Hashable, Sendable {
    case again
    case hard
    case good
    case easy

    var id: Int { rawValue }
}

struct ChoiceResultInfo: Identifiable, Hashable, Sendable {
    let id: String
    let text: StyledText
    let correct: Bool
    let selected: Bool
    let explanation: StyledText?
}

struct GapResultInfo: Identifiable, Hashable, Sendable {
    let index: Int
    let given: String
    let canonical: String
    let correct: Bool
    let typo: Bool

    var id: Int { index }
}

struct OrderResultInfo: Identifiable, Hashable, Sendable {
    let position: Int
    let arranged: StyledText
    let expected: StyledText
    let correct: Bool

    var id: Int { position }
}

enum FeedbackVerdict: Hashable, Sendable {
    case correct
    case wrong
    case pendingSelfGrade
}

struct FeedbackInfo: Hashable, Sendable {
    let exercise: ExerciseInfo
    let position: Int
    let total: Int
    let verdict: FeedbackVerdict
    let willRepeat: Bool
    let correctAnswer: StyledText
    let userAnswer: String
    let explanation: StyledText?
    let choiceResults: [ChoiceResultInfo]
    let gapResults: [GapResultInfo]
    let orderResults: [OrderResultInfo]
    let keyPoints: [StyledText]
    let statementIsTrue: Bool
    let typo: Bool
    let figure: FigureInfo?
    let suggestedGrades: [GradeChoice]

    var needsSelfGrade: Bool { verdict == .pendingSelfGrade }

    func suggestedGrade(ticked: Int) -> GradeChoice {
        guard !suggestedGrades.isEmpty else { return .good }
        let index = min(max(ticked, 0), suggestedGrades.count - 1)
        return suggestedGrades[index]
    }
}

struct SummaryInfo: Hashable, Sendable {
    let total: Int
    let answered: Int
    let correct: Int
    let wrong: Int
    let retries: Int
    let newFacets: Int
    let accuracy: Double
    let duration: TimeInterval
    let abandoned: Bool
}

enum SessionSnapshot: Sendable {
    case question(QuestionInfo)
    case feedback(FeedbackInfo)
    case finished(SummaryInfo)
}

enum AnswerInput: Sendable {
    case choice([String])
    case text(String)
    case cloze([String])
    case trueFalse(Bool)
    case order([String])
}
