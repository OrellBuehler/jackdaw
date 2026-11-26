@preconcurrency import shared
import Foundation

// Nested sealed subclasses (SessionState, ClozeSegment) go through SKIE `onEnum(of:)`; the Exercise
// subclasses are top-level Kotlin classes, so they are plain casts and need no SKIE naming guess.
enum SessionAdapter {
    static func snapshot(_ state: SessionState) -> SessionSnapshot? {
        switch onEnum(of: state) {
        case .question(let q):
            return .question(
                QuestionInfo(
                    exercise: exercise(q.exercise),
                    position: Int(q.position),
                    total: Int(q.total),
                    isRetry: q.isRetry,
                    hasHint: q.hasHint,
                    hint: q.hint.map { Mapping.styled($0) },
                    figure: q.figure.map { Mapping.figure($0) }
                )
            )
        case .feedback(let f):
            let grades = (0 ... f.keyPoints.count).map { ticked in
                gradeChoice(f.suggestedGrade(tickedKeyPoints: Int32(ticked)))
            }
            return .feedback(
                feedback(
                    exercise: f.exercise,
                    position: f.position,
                    total: f.total,
                    needsSelfGrade: f.needsSelfGrade,
                    correct: f.correct,
                    willRepeat: f.willRepeat,
                    correctAnswer: f.correctAnswer,
                    userAnswer: f.userAnswer,
                    explanation: f.explanation,
                    choiceResults: f.choiceResults,
                    gapResults: f.gapResults,
                    orderResults: f.orderResults,
                    keyPoints: f.keyPoints,
                    statementIsTrue: f.statementIsTrue,
                    typo: f.typo,
                    figure: f.figure,
                    suggestedGrades: grades
                )
            )
        case .finished(let done):
            return .finished(summary(done.summary))
        default:
            return nil
        }
    }

    static func feedback(
        exercise exerciseValue: Exercise,
        position: Int32,
        total: Int32,
        needsSelfGrade: Bool,
        correct: Bool,
        willRepeat: Bool,
        correctAnswer: RichText,
        userAnswer: String,
        explanation: RichText?,
        choiceResults: [ChoiceResult],
        gapResults: [GapResult],
        orderResults: [OrderResult],
        keyPoints: [RichText],
        statementIsTrue: Bool,
        typo: Bool,
        figure: Figure?,
        suggestedGrades: [GradeChoice]
    ) -> FeedbackInfo {
        let verdict: FeedbackVerdict
        if needsSelfGrade {
            verdict = .pendingSelfGrade
        } else if correct {
            verdict = .correct
        } else {
            verdict = .wrong
        }
        return FeedbackInfo(
            exercise: exercise(exerciseValue),
            position: Int(position),
            total: Int(total),
            verdict: verdict,
            willRepeat: willRepeat,
            correctAnswer: Mapping.styled(correctAnswer),
            userAnswer: userAnswer,
            explanation: explanation.map { Mapping.styled($0) },
            choiceResults: choiceResults.map { result in
                ChoiceResultInfo(
                    id: result.id,
                    text: Mapping.styled(result.text),
                    correct: result.correct,
                    selected: result.selected,
                    explanation: result.explanation.map { Mapping.styled($0) }
                )
            },
            gapResults: gapResults.map { gap in
                GapResultInfo(index: Int(gap.index), given: gap.given, canonical: gap.canonical, correct: gap.correct, typo: gap.typo)
            },
            orderResults: orderResults.map { entry in
                OrderResultInfo(
                    position: Int(entry.position),
                    arranged: Mapping.styled(entry.arranged),
                    expected: Mapping.styled(entry.expected),
                    correct: entry.correct
                )
            },
            keyPoints: keyPoints.map { Mapping.styled($0) },
            statementIsTrue: statementIsTrue,
            typo: typo,
            figure: figure.map { Mapping.figure($0) },
            suggestedGrades: suggestedGrades
        )
    }

    static func summary(_ s: SessionSummary) -> SummaryInfo {
        SummaryInfo(
            total: Int(s.total),
            answered: Int(s.answered),
            correct: Int(s.correct),
            wrong: Int(s.wrong),
            retries: Int(s.retries),
            newFacets: Int(s.newFacets),
            accuracy: s.accuracy,
            duration: Double(s.durationMs) / 1000,
            abandoned: s.abandoned
        )
    }

    static func exercise(_ e: Exercise) -> ExerciseInfo {
        if let x = e as? FlipExercise {
            return info(e, kind: .flip, prompt: Mapping.styled(x.prompt))
        }
        if let x = e as? ChoiceExercise {
            return ExerciseInfo(
                itemId: x.itemId,
                kind: .choice,
                prompt: Mapping.styled(x.question),
                options: x.options.map { OptionInfo(id: $0.id, text: Mapping.styled($0.text)) },
                multiSelect: x.multiSelect,
                segments: [],
                gapCount: 0
            )
        }
        if let x = e as? TypeExercise {
            return ExerciseInfo(
                itemId: x.itemId,
                kind: .typed,
                prompt: Mapping.styled(x.prompt),
                options: [],
                multiSelect: false,
                segments: [],
                gapCount: 0,
                keyboard: answerKeyboard(x.keyboard)
            )
        }
        if let x = e as? ClozeExercise {
            return ExerciseInfo(
                itemId: x.itemId,
                kind: .cloze,
                prompt: .empty,
                options: [],
                multiSelect: false,
                segments: x.segments.map { clozeSegment($0) },
                gapCount: Int(x.gapCount)
            )
        }
        if let x = e as? TrueFalseExercise {
            return info(e, kind: .trueFalse, prompt: Mapping.styled(x.statement))
        }
        if let x = e as? OrderExercise {
            return ExerciseInfo(
                itemId: x.itemId,
                kind: .order,
                prompt: Mapping.styled(x.prompt),
                options: x.entries.map { OptionInfo(id: $0.id, text: Mapping.styled($0.text)) },
                multiSelect: false,
                segments: [],
                gapCount: 0
            )
        }
        if let x = e as? OpenExercise {
            return info(e, kind: .open, prompt: Mapping.styled(x.question))
        }
        return info(e, kind: .flip, prompt: .empty)
    }

    static func clozeSegment(_ segment: ClozeSegment) -> ClozeSegmentInfo {
        switch onEnum(of: segment) {
        case .text(let t):
            return .text(Mapping.styled(t.text))
        case .gap(let g):
            return .gap(index: Int(g.index), hint: g.hint, keyboard: answerKeyboard(g.keyboard))
        case .code(let c):
            return .code(lines: c.lines.map { line in line.map { clozePiece($0) } })
        default:
            return .text(.empty)
        }
    }

    static func clozePiece(_ piece: ClozePiece) -> ClozeCodePiece {
        switch onEnum(of: piece) {
        case .span(let s):
            return .token(CodeToken(text: s.text, kind: Mapping.tokenKind(s.kind)))
        case .gap(let g):
            return .gap(index: Int(g.index), hint: g.hint, keyboard: answerKeyboard(g.keyboard))
        default:
            return .token(CodeToken(text: "", kind: .plain))
        }
    }

    static func answerKeyboard(_ keyboard: AnswerKeyboard) -> AnswerKeyboardInfo {
        switch keyboard {
        case .integer: return .integer
        case .decimal: return .decimal
        default: return .text
        }
    }

    static func gradeChoice(_ grade: Grade) -> GradeChoice {
        switch grade {
        case .again: return .again
        case .hard: return .hard
        case .easy: return .easy
        default: return .good
        }
    }

    static func kotlinGrade(_ choice: GradeChoice) -> Grade {
        switch choice {
        case .again: return Grade.again
        case .hard: return Grade.hard
        case .good: return Grade.good
        case .easy: return Grade.easy
        }
    }

    static func kotlinAnswer(_ input: AnswerInput) -> Answer {
        switch input {
        case .choice(let ids): return ChoiceAnswer(selectedIds: ids)
        case .text(let text): return TextAnswer(text: text)
        case .cloze(let gaps): return ClozeAnswer(gaps: gaps)
        case .trueFalse(let value): return TrueFalseAnswer(value: value)
        case .order(let ids): return OrderAnswer(entryIds: ids)
        }
    }

    private static func info(_ e: Exercise, kind: ExerciseKind, prompt: StyledText) -> ExerciseInfo {
        ExerciseInfo(
            itemId: e.itemId,
            kind: kind,
            prompt: prompt,
            options: [],
            multiSelect: false,
            segments: [],
            gapCount: 0
        )
    }
}
