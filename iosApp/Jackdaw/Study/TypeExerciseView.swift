import SwiftUI

struct TypeExerciseView: View {
    let store: StudyStore
    let current: StudyCurrent
    let style: PackStyle
    @State private var answer = ""
    @FocusState private var focused: Bool

    private var feedback: FeedbackInfo? { current.feedback }
    private var trimmed: String { answer.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        ExerciseScaffold(current: current, style: style) {
            VStack(spacing: 16) {
                QuestionCard(
                    labelKey: "study.label.type",
                    prompt: current.exercise.prompt,
                    hint: current.hint,
                    style: style
                )
                if let feedback {
                    resultCard(feedback)
                    FeedbackBanner(feedback: feedback)
                } else {
                    field
                }
            }
        } bar: {
            if feedback != nil {
                ContinueBar(store: store, style: style)
            } else {
                VStack(spacing: 8) {
                    Button {
                        submit()
                    } label: {
                        Text("study.check")
                    }
                    .buttonStyle(BrandButtonStyle(fill: style.base, foreground: style.onBase))
                    .disabled(trimmed.isEmpty)
                    Button {
                        store.submit(.text(""))
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
            focused = true
        }
    }

    private var field: some View {
        TextField("study.type.placeholder", text: $answer)
            .font(.brand(.title3, weight: .medium))
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .submitLabel(.done)
            .focused($focused)
            .onSubmit {
                if !trimmed.isEmpty { submit() }
            }
            .padding(16)
            .background(Brand.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .strokeBorder(focused ? style.base : Brand.hairline, lineWidth: focused ? 2 : 1)
            }
    }

    private func resultCard(_ feedback: FeedbackInfo) -> some View {
        let isCorrect = feedback.verdict == .correct
        return VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text("study.type.yours")
                    .font(.brand(.caption, weight: .bold))
                    .foregroundStyle(Brand.textSecondary)
                    .textCase(.uppercase)
                Text(feedback.userAnswer.isEmpty ? L10n.tr("study.type.empty") : feedback.userAnswer)
                    .font(.brand(.title3, weight: .semibold))
                    .foregroundStyle(isCorrect ? Brand.success : Brand.error)
                    .strikethrough(!isCorrect && !feedback.userAnswer.isEmpty)
            }
            if !isCorrect || feedback.typo {
                VStack(alignment: .leading, spacing: 4) {
                    Text(L10n.tr(feedback.typo ? "study.type.spelling" : "study.type.expected"))
                        .font(.brand(.caption, weight: .bold))
                        .foregroundStyle(Brand.textSecondary)
                        .textCase(.uppercase)
                    RichTextView(
                        text: feedback.correctAnswer,
                        font: .brand(.title3, weight: .semibold),
                        color: Brand.success
                    )
                }
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 22)
    }

    private func submit() {
        focused = false
        store.submit(.text(trimmed))
    }
}
