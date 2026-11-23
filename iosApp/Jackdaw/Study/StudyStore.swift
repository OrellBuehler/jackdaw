import Foundation
import Observation

struct StudyCurrent {
    let exercise: ExerciseInfo
    let question: QuestionInfo?
    let feedback: FeedbackInfo?
    let isRetry: Bool
    let hint: StyledText?
    let figure: FigureInfo?
}

@MainActor
@Observable
final class StudyStore {
    enum Phase {
        case loading
        case running
        case failed
    }

    let request: StudyRequest
    private(set) var phase: Phase = .loading
    private(set) var snapshot: SessionSnapshot?
    private(set) var questionToken = 0
    private(set) var correctTicks = 0
    private(set) var wrongTicks = 0
    private(set) var gradeTicks = 0

    private(set) var answeredAny = false
    private var lastQuestion: QuestionInfo?
    private var driver: SessionDriver?
    private let client = SDKClient.shared

    init(request: StudyRequest) {
        self.request = request
    }

    var current: StudyCurrent? {
        guard let snapshot else { return nil }
        switch snapshot {
        case .question(let question):
            return StudyCurrent(
                exercise: question.exercise,
                question: question,
                feedback: nil,
                isRetry: question.isRetry,
                hint: question.hint,
                figure: question.figure
            )
        case .feedback(let feedback):
            return StudyCurrent(
                exercise: feedback.exercise,
                question: nil,
                feedback: feedback,
                isRetry: lastQuestion?.isRetry ?? false,
                hint: lastQuestion?.hint,
                figure: feedback.figure
            )
        case .finished:
            return nil
        }
    }

    var summary: SummaryInfo? {
        guard let snapshot else { return nil }
        switch snapshot {
        case .finished(let summary): return summary
        case .question, .feedback: return nil
        }
    }

    var hasAnswered: Bool {
        answeredAny && summary == nil
    }

    var hintAvailable: Bool {
        guard let snapshot else { return false }
        switch snapshot {
        case .question(let question): return question.hasHint
        case .feedback, .finished: return false
        }
    }

    var hintShown: Bool {
        guard let snapshot else { return false }
        switch snapshot {
        case .question(let question): return question.hint != nil
        case .feedback, .finished: return false
        }
    }

    var progress: Double {
        guard let snapshot else { return 0 }
        switch snapshot {
        case .question(let question):
            return fraction(question.position - 1, question.total)
        case .feedback(let feedback):
            return fraction(feedback.willRepeat ? feedback.position - 1 : feedback.position, feedback.total)
        case .finished:
            return 1
        }
    }

    func run() async {
        if driver == nil {
            guard let started = await client.startSession(request) else {
                phase = .failed
                return
            }
            driver = started
            phase = .running
        }
        await driver?.watch { [weak self] snapshot in
            self?.apply(snapshot)
        }
    }

    func revealHint() {
        driver?.revealHint()
    }

    func reveal() {
        driver?.reveal()
    }

    func next() {
        driver?.next()
    }

    func finishEarly() {
        driver?.finish()
    }

    func submit(_ answer: AnswerInput) {
        guard let driver else { return }
        Task {
            await driver.submit(answer)
        }
    }

    func selfGrade(_ grade: GradeChoice) {
        guard let driver else { return }
        gradeTicks += 1
        Task {
            await driver.selfGrade(grade)
        }
    }

    private func apply(_ value: SessionSnapshot) {
        switch value {
        case .question(let question):
            if !isSameQuestion(question) {
                questionToken += 1
            }
            lastQuestion = question
        case .feedback(let feedback):
            answeredAny = true
            switch feedback.verdict {
            case .correct: correctTicks += 1
            case .wrong: wrongTicks += 1
            case .pendingSelfGrade: break
            }
        case .finished:
            break
        }
        snapshot = value
    }

    private func isSameQuestion(_ question: QuestionInfo) -> Bool {
        guard let previous = snapshot else { return false }
        switch previous {
        case .question(let old):
            return old.exercise == question.exercise && old.position == question.position && old.isRetry == question.isRetry
        case .feedback, .finished:
            return false
        }
    }

    private func fraction(_ done: Int, _ total: Int) -> Double {
        guard total > 0 else { return 0 }
        return min(max(Double(done) / Double(total), 0), 1)
    }
}
