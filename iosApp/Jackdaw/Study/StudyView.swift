import SwiftUI

struct StudyView: View {
    let request: StudyRequest
    @Environment(\.dismiss) private var dismiss
    @State private var store: StudyStore
    @State private var confirmClose = false

    init(request: StudyRequest) {
        self.request = request
        _store = State(initialValue: StudyStore(request: request))
    }

    private var style: PackStyle {
        PackStyle(hex: request.colorHex)
    }

    var body: some View {
        ZStack {
            background
            VStack(spacing: 0) {
                header
                content
            }
        }
        .task {
            await store.run()
        }
        .sensoryFeedback(.success, trigger: store.correctTicks)
        .sensoryFeedback(.error, trigger: store.wrongTicks)
        .sensoryFeedback(.selection, trigger: store.gradeTicks)
    }

    private var background: some View {
        ZStack(alignment: .top) {
            Brand.background
            LinearGradient(
                colors: [style.base.opacity(0.2), style.base.opacity(0)],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(height: 280)
        }
        .ignoresSafeArea()
    }

    private var header: some View {
        HStack(spacing: 14) {
            Button {
                requestClose()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(Brand.text)
                    .frame(width: 36, height: 36)
                    .background(Brand.surface, in: Circle())
                    .overlay {
                        Circle().strokeBorder(Brand.hairline, lineWidth: 1)
                    }
            }
            .accessibilityLabel("study.close")
            .confirmationDialog("study.close.title", isPresented: $confirmClose, titleVisibility: .visible) {
                Button("study.close.confirm", role: .destructive) {
                    store.finishEarly()
                    dismiss()
                }
                Button("study.close.continue", role: .cancel) {}
            } message: {
                Text("study.close.message")
            }
            ProgressBar(
                value: store.progress,
                tint: style.accentText,
                track: style.base.opacity(0.2),
                height: 10
            )
            .accessibilityHidden(true)
            hintButton
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    @ViewBuilder
    private var hintButton: some View {
        if store.hintAvailable {
            Button {
                store.revealHint()
            } label: {
                Image(systemName: store.hintShown ? "lightbulb.fill" : "lightbulb")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Brand.flame)
                    .frame(width: 36, height: 36)
                    .background(Brand.amber.opacity(0.22), in: Circle())
            }
            .disabled(store.hintShown)
            .accessibilityLabel("study.hint")
        }
    }

    @ViewBuilder
    private var content: some View {
        switch store.phase {
        case .loading:
            ProgressView()
                .controlSize(.large)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .failed:
            failedView
        case .running:
            runningContent
                .animation(.snappy(duration: 0.35), value: store.questionToken)
        }
    }

    @ViewBuilder
    private var runningContent: some View {
        if let summary = store.summary {
            FinishedView(summary: summary, style: style) {
                dismiss()
            }
        } else if let current = store.current {
            ExerciseHost(store: store, current: current, style: style)
                .id(store.questionToken)
                .transition(
                    .asymmetric(
                        insertion: .move(edge: .trailing).combined(with: .opacity),
                        removal: .move(edge: .leading).combined(with: .opacity)
                    )
                )
        } else {
            ProgressView()
                .controlSize(.large)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private var failedView: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 44))
                .foregroundStyle(Brand.flame)
            Text("study.failed")
                .font(.brand(.headline))
                .foregroundStyle(Brand.text)
            Button {
                dismiss()
            } label: {
                Text("common.close")
            }
            .buttonStyle(SoftButtonStyle())
            .padding(.horizontal, 40)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func requestClose() {
        if store.hasAnswered && store.summary == nil {
            confirmClose = true
        } else {
            store.finishEarly()
            dismiss()
        }
    }
}

struct ExerciseHost: View {
    let store: StudyStore
    let current: StudyCurrent
    let style: PackStyle

    var body: some View {
        switch current.exercise.kind {
        case .flip:
            FlipExerciseView(store: store, current: current, style: style)
        case .choice:
            ChoiceExerciseView(store: store, current: current, style: style)
        case .typed:
            TypeExerciseView(store: store, current: current, style: style)
        case .cloze:
            ClozeExerciseView(store: store, current: current, style: style)
        case .trueFalse:
            TrueFalseExerciseView(store: store, current: current, style: style)
        case .order:
            OrderExerciseView(store: store, current: current, style: style)
        case .open:
            OpenExerciseView(store: store, current: current, style: style)
        }
    }
}
