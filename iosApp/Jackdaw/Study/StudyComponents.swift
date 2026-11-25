import SwiftUI
import UIKit

struct ExerciseScaffold<Content: View, Bar: View>: View {
    let current: StudyCurrent
    let style: PackStyle
    let showsBar: Bool
    let content: Content
    let bar: Bar

    init(
        current: StudyCurrent,
        style: PackStyle,
        showsBar: Bool = true,
        @ViewBuilder content: () -> Content,
        @ViewBuilder bar: () -> Bar
    ) {
        self.current = current
        self.style = style
        self.showsBar = showsBar
        self.content = content()
        self.bar = bar()
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                if current.isRetry {
                    Chip(text: L10n.tr("study.retry"), systemImage: "arrow.counterclockwise", tint: Brand.flame)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                if let figure = current.figure {
                    FigureView(figure: figure)
                }
                content
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)
            .padding(.bottom, 24)
        }
        .scrollDismissesKeyboard(.interactively)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if showsBar {
                bar
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    .frame(maxWidth: .infinity)
                    .background(.ultraThinMaterial)
            }
        }
    }
}

struct QuestionCard: View {
    let labelKey: String
    let prompt: StyledText
    var hint: StyledText?
    let style: PackStyle

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(L10n.tr(labelKey))
                .font(.brand(.caption, weight: .bold))
                .foregroundStyle(style.accentText)
                .tracking(0.6)
                .textCase(.uppercase)
            if !prompt.isEmpty {
                RichTextView(text: prompt, font: .brand(.title3, weight: .semibold))
            }
            if let hint, !hint.isEmpty {
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "lightbulb.fill")
                        .foregroundStyle(Brand.flame)
                    RichTextView(text: hint, font: .brand(.subheadline), color: Brand.textSecondary)
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Brand.amber.opacity(0.18), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .transition(.scale(scale: 0.95).combined(with: .opacity))
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 26)
        .animation(.snappy, value: hint)
    }
}

struct FeedbackBanner: View {
    let feedback: FeedbackInfo

    private var isCorrect: Bool { feedback.verdict == .correct }
    private var tint: Color { isCorrect ? Brand.success : Brand.error }
    private var title: String {
        if !isCorrect { return "feedback.wrong" }
        return feedback.typo ? "feedback.typo" : "feedback.correct"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Image(systemName: isCorrect ? "checkmark.circle.fill" : "xmark.circle.fill")
                    .font(.system(size: 26))
                    .foregroundStyle(tint)
                    .symbolEffect(.bounce, value: feedback.position)
                Text(L10n.tr(title))
                    .font(.brand(.title3, weight: .bold))
                    .foregroundStyle(tint)
                Spacer(minLength: 0)
            }
            if feedback.willRepeat {
                Label("feedback.repeat", systemImage: "arrow.counterclockwise")
                    .font(.brand(.footnote, weight: .semibold))
                    .foregroundStyle(Brand.textSecondary)
            }
            if let explanation = feedback.explanation, !explanation.isEmpty {
                RichTextView(text: explanation, font: .brand(.subheadline), color: Brand.text)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(tint.opacity(0.12), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .strokeBorder(tint.opacity(0.35), lineWidth: 1)
        }
        .accessibilityElement(children: .combine)
    }
}

struct ExplanationCard: View {
    let text: StyledText

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "text.bubble.fill")
                .foregroundStyle(Brand.accent)
            RichTextView(text: text, font: .brand(.subheadline), color: Brand.text)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Brand.accent.opacity(0.10), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

struct ContinueBar: View {
    let store: StudyStore
    let style: PackStyle

    var body: some View {
        Button {
            store.next()
        } label: {
            Text("study.continue")
        }
        .buttonStyle(BrandButtonStyle(fill: style.base, foreground: style.onBase))
    }
}

struct GradeBar: View {
    let store: StudyStore
    var suggested: GradeChoice?

    var body: some View {
        HStack(spacing: 8) {
            ForEach(GradeChoice.allCases) { grade in
                Button {
                    store.selfGrade(grade)
                } label: {
                    VStack(spacing: 4) {
                        Image(systemName: symbol(grade))
                            .font(.system(size: 18, weight: .bold))
                        Text(L10n.tr(labelKey(grade)))
                            .font(.brand(.footnote, weight: .bold))
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                    .foregroundStyle(tint(grade))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(
                        tint(grade).opacity(suggested == grade ? 0.28 : 0.14),
                        in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                    )
                    .overlay {
                        if suggested == grade {
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .strokeBorder(tint(grade), lineWidth: 2)
                        }
                    }
                }
                .buttonStyle(PressableStyle())
            }
        }
    }

    private func symbol(_ grade: GradeChoice) -> String {
        switch grade {
        case .again: return "arrow.counterclockwise"
        case .hard: return "tortoise.fill"
        case .good: return "checkmark"
        case .easy: return "hare.fill"
        }
    }

    private func labelKey(_ grade: GradeChoice) -> String {
        switch grade {
        case .again: return "grade.again"
        case .hard: return "grade.hard"
        case .good: return "grade.good"
        case .easy: return "grade.easy"
        }
    }

    private func tint(_ grade: GradeChoice) -> Color {
        switch grade {
        case .again: return Brand.error
        case .hard: return Brand.flame
        case .good: return Brand.success
        case .easy: return Brand.accent
        }
    }
}

extension AnswerKeyboardInfo {
    var keyboardType: UIKeyboardType {
        switch self {
        case .text: return .default
        case .integer: return .numberPad
        case .decimal: return .numbersAndPunctuation
        }
    }
}
