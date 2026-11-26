import SwiftUI

private enum ClozeToken {
    case word(AttributedString)
    case gap(index: Int, hint: String?, keyboard: AnswerKeyboardInfo)
}

private func clozeLines(_ segments: [ClozeSegmentInfo]) -> [[ClozeToken]] {
    var lines: [[ClozeToken]] = [[]]
    for segment in segments {
        switch segment {
        case .gap(let index, let hint, let keyboard):
            lines[lines.count - 1].append(.gap(index: index, hint: hint, keyboard: keyboard))
        case .code:
            break
        case .text(let text):
            for span in text.spans {
                let parts = span.text.components(separatedBy: "\n")
                for (offset, part) in parts.enumerated() {
                    if offset > 0 { lines.append([]) }
                    for word in part.split(whereSeparator: { $0.isWhitespace }) {
                        let styled = StyledText(
                            spans: [StyledSpan(text: String(word), bold: span.bold, italic: span.italic, code: span.code)]
                        )
                        lines[lines.count - 1].append(.word(styled.attributed))
                    }
                }
            }
        }
    }
    return lines.filter { !$0.isEmpty }
}

private enum ClozeGroup {
    case flow([[ClozeToken]])
    case code([[ClozeCodePiece]])
}

private func clozeGroups(_ segments: [ClozeSegmentInfo]) -> [ClozeGroup] {
    var groups: [ClozeGroup] = []
    var run: [ClozeSegmentInfo] = []
    for segment in segments {
        if case .code(let lines) = segment {
            let flow = clozeLines(run)
            if !flow.isEmpty { groups.append(.flow(flow)) }
            run = []
            groups.append(.code(lines))
        } else {
            run.append(segment)
        }
    }
    let flow = clozeLines(run)
    if !flow.isEmpty { groups.append(.flow(flow)) }
    return groups
}

private func clozeCompactGapWidth(hint: String?, text: String) -> CGFloat {
    let forHint = CGFloat(hint?.count ?? 0) * 7 + 12
    let forText = CGFloat(text.count) * 8 + 12
    return min(max(64, forHint, forText), 220)
}

private func clozeGapWidth(hint: String?, text: String) -> CGFloat {
    let forHint = CGFloat(hint?.count ?? 0) * 8 + 16
    let forText = CGFloat(text.count) * 12 + 16
    return min(max(104, forHint, forText), 250)
}

struct ClozeExerciseView: View {
    let store: StudyStore
    let current: StudyCurrent
    let style: PackStyle
    @State private var answers: [String]
    @FocusState private var focusedGap: Int?

    init(store: StudyStore, current: StudyCurrent, style: PackStyle) {
        self.store = store
        self.current = current
        self.style = style
        _answers = State(initialValue: Array(repeating: "", count: max(current.exercise.gapCount, 0)))
    }

    private var feedback: FeedbackInfo? { current.feedback }
    private var allEmpty: Bool { answers.allSatisfy { $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty } }

    var body: some View {
        ExerciseScaffold(current: current, style: style) {
            VStack(spacing: 16) {
                textCard
                if let feedback {
                    FeedbackBanner(feedback: feedback)
                }
            }
        } bar: {
            if feedback != nil {
                ContinueBar(store: store, style: style)
            } else {
                VStack(spacing: 8) {
                    Button {
                        submit(answers)
                    } label: {
                        Text("study.check")
                    }
                    .buttonStyle(BrandButtonStyle(fill: style.base, foreground: style.onBase))
                    .disabled(allEmpty)
                    Button {
                        submit(Array(repeating: "", count: answers.count))
                    } label: {
                        Text("study.dontknow")
                            .font(.brand(.subheadline, weight: .semibold))
                            .foregroundStyle(Brand.textSecondary)
                    }
                }
            }
        }
        .task {
            _ = try? await Task.sleep(nanoseconds: 400_000_000)
            focusedGap = 0
        }
        .toolbar {
            if feedback == nil && focusedGapUsesNumberPad {
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    if let index = focusedGap, index + 1 < answers.count {
                        Button("study.keyboard.next") {
                            advance(from: index)
                        }
                    } else {
                        Button("common.done") {
                            focusedGap = nil
                        }
                    }
                }
            }
        }
    }

    private var focusedGapUsesNumberPad: Bool {
        guard let index = focusedGap else { return false }
        return gapKeyboard(index) == .integer
    }

    private func gapKeyboard(_ index: Int) -> AnswerKeyboardInfo? {
        for segment in current.exercise.segments {
            switch segment {
            case .gap(let gapIndex, _, let keyboard) where gapIndex == index:
                return keyboard
            case .code(let lines):
                for line in lines {
                    for piece in line {
                        if case .gap(let gapIndex, _, let keyboard) = piece, gapIndex == index {
                            return keyboard
                        }
                    }
                }
            default:
                break
            }
        }
        return nil
    }

    private var textCard: some View {
        let groups = clozeGroups(current.exercise.segments)
        return VStack(alignment: .leading, spacing: 12) {
            Text("study.label.cloze")
                .font(.brand(.caption, weight: .bold))
                .foregroundStyle(style.accentText)
                .tracking(0.6)
                .textCase(.uppercase)
            VStack(alignment: .leading, spacing: 10) {
                ForEach(Array(groups.enumerated()), id: \.offset) { _, group in
                    groupView(group)
                }
            }
            if let hint = current.hint, !hint.isEmpty {
                RichTextView(text: hint, font: .brand(.subheadline), color: Brand.textSecondary)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 26)
    }

    @ViewBuilder
    private func groupView(_ group: ClozeGroup) -> some View {
        switch group {
        case .flow(let lines):
            VStack(alignment: .leading, spacing: 10) {
                ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                    FlowLayout(spacing: 6, lineSpacing: 10) {
                        ForEach(Array(line.enumerated()), id: \.offset) { _, token in
                            tokenView(token)
                        }
                    }
                }
            }
        case .code(let lines):
            CodeSurface {
                VStack(alignment: .leading, spacing: 2) {
                    ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                        HStack(spacing: 0) {
                            if line.isEmpty {
                                Text("\u{00A0}").font(CodePalette.font)
                            }
                            ForEach(Array(line.enumerated()), id: \.offset) { _, piece in
                                codePieceView(piece)
                            }
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func codePieceView(_ piece: ClozeCodePiece) -> some View {
        switch piece {
        case .token(let token):
            Text(CodeRendering.piece(token, spaces: true))
                .font(CodePalette.font)
                .fixedSize()
        case .gap(let index, let hint, let keyboard):
            gapView(index: index, hint: hint, keyboard: keyboard, compact: true)
        }
    }

    @ViewBuilder
    private func tokenView(_ token: ClozeToken) -> some View {
        switch token {
        case .word(let text):
            Text(text)
                .font(.brand(.title3, weight: .medium))
                .foregroundStyle(Brand.text)
        case .gap(let index, let hint, let keyboard):
            gapView(index: index, hint: hint, keyboard: keyboard, compact: false)
        }
    }

    @ViewBuilder
    private func gapView(index: Int, hint: String?, keyboard: AnswerKeyboardInfo, compact: Bool) -> some View {
        if let result = feedback?.gapResults.first(where: { $0.index == index }) {
            resultPill(result, compact: compact)
        } else if index >= 0 && index < answers.count {
            TextField("", text: $answers[index])
                .accessibilityLabel(hint ?? "")
                .font(gapFont(compact: compact, weight: .semibold))
                .multilineTextAlignment(.center)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(keyboard.keyboardType)
                .submitLabel(index == answers.count - 1 ? .done : .next)
                .focused($focusedGap, equals: index)
                .onSubmit {
                    advance(from: index)
                }
                .frame(
                    width: compact
                        ? clozeCompactGapWidth(hint: hint, text: answers[index])
                        : clozeGapWidth(hint: hint, text: answers[index])
                )
                .overlay {
                    if let hint, answers[index].isEmpty {
                        Text(hint)
                            .font(gapHintFont(compact: compact))
                            .foregroundStyle(Brand.textSecondary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                            .allowsHitTesting(false)
                            .accessibilityHidden(true)
                    }
                }
                .padding(.vertical, compact ? 2 : 6)
                .padding(.horizontal, compact ? 4 : 6)
                .background(style.base.opacity(0.12), in: RoundedRectangle(cornerRadius: compact ? 6 : 10, style: .continuous))
                .overlay(alignment: .bottom) {
                    Rectangle()
                        .fill(focusedGap == index ? style.base : style.base.opacity(0.4))
                        .frame(height: 2)
                }
        } else {
            EmptyView()
        }
    }

    private func gapFont(compact: Bool, weight: Font.Weight) -> Font {
        if compact { return CodePalette.font.weight(weight) }
        return Font.brand(.title3, weight: weight)
    }

    private func gapHintFont(compact: Bool) -> Font {
        if compact { return CodePalette.font }
        return Font.brand(.footnote, weight: .medium)
    }

    private func resultPill(_ result: GapResultInfo, compact: Bool) -> some View {
        let tint = result.correct ? Brand.success : Brand.error
        return HStack(spacing: 6) {
            Text(result.given.isEmpty ? "…" : result.given)
                .strikethrough((!result.correct || result.typo) && !result.given.isEmpty)
                .foregroundStyle(tint)
            if !result.correct || result.typo {
                Text(result.canonical)
                    .foregroundStyle(Brand.success)
            }
        }
        .font(gapFont(compact: compact, weight: .bold))
        .padding(.vertical, compact ? 1 : 5)
        .padding(.horizontal, compact ? 6 : 10)
        .background(tint.opacity(0.14), in: RoundedRectangle(cornerRadius: compact ? 6 : 10, style: .continuous))
    }

    private func advance(from index: Int) {
        if index + 1 < answers.count {
            focusedGap = index + 1
        } else {
            focusedGap = nil
            if !allEmpty { submit(answers) }
        }
    }

    private func submit(_ values: [String]) {
        focusedGap = nil
        store.submit(.cloze(values.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }))
    }
}
