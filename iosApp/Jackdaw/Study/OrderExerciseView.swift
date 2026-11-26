import SwiftUI

struct OrderExerciseView: View {
    let store: StudyStore
    let current: StudyCurrent
    let style: PackStyle
    @State private var entries: [OptionInfo]
    @State private var moves = 0
    @State private var heights: [String: CGFloat] = [:]
    @State private var draggingID: String?
    @State private var dragTranslation: CGFloat = 0

    private let rowSpacing: CGFloat = 10

    init(store: StudyStore, current: StudyCurrent, style: PackStyle) {
        self.store = store
        self.current = current
        self.style = style
        _entries = State(initialValue: current.exercise.options)
    }

    private var feedback: FeedbackInfo? { current.feedback }

    var body: some View {
        ExerciseScaffold(current: current, style: style) {
            VStack(spacing: 16) {
                QuestionCard(
                    labelKey: "study.label.order",
                    prompt: current.exercise.prompt,
                    hint: current.hint,
                    style: style
                )
                if let feedback {
                    resultList(feedback)
                    FeedbackBanner(feedback: feedback)
                } else {
                    editableList
                }
            }
            .sensoryFeedback(.selection, trigger: moves)
        } bar: {
            if feedback != nil {
                ContinueBar(store: store, style: style)
            } else {
                Button {
                    store.submit(.order(entries.map { $0.id }))
                } label: {
                    Text("study.check")
                }
                .buttonStyle(BrandButtonStyle(fill: style.base, foreground: style.onBase))
            }
        }
    }

    private var editableList: some View {
        VStack(spacing: rowSpacing) {
            Text("study.order.hint")
                .font(.brand(.footnote))
                .foregroundStyle(Brand.textSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
            VStack(spacing: rowSpacing) {
                ForEach(Array(entries.enumerated()), id: \.element.id) { index, entry in
                    row(index: index, entry: entry)
                }
            }
        }
        .onChange(of: dragTarget) { _, target in
            if target != nil { moves += 1 }
        }
    }

    private func row(index: Int, entry: OptionInfo) -> some View {
        let isDragged = entry.id == draggingID
        let shift = rowOffset(at: index)
        return HStack(spacing: 12) {
            Text(String(displayedPosition(at: index)))
                .font(.brand(.subheadline, weight: .bold))
                .foregroundStyle(style.onBase)
                .frame(width: 30, height: 30)
                .background(style.base, in: Circle())
            RichTextView(text: entry.text, font: .brand(.body, weight: .medium))
                .frame(maxWidth: .infinity, alignment: .leading)
            dragHandle(id: entry.id)
        }
        .padding(.vertical, 10)
        .padding(.horizontal, 12)
        .background(Brand.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(isDragged ? style.base : Brand.hairline, lineWidth: isDragged ? 2 : 1)
        }
        .onGeometryChange(for: CGFloat.self) { proxy in
            proxy.size.height
        } action: { height in
            heights[entry.id] = height
        }
        .scaleEffect(isDragged ? 1.02 : 1)
        .shadow(color: .black.opacity(isDragged ? 0.18 : 0), radius: 10, y: 4)
        .offset(y: shift)
        .animation(.snappy(duration: 0.2), value: isDragged ? 0 : shift)
        .zIndex(isDragged ? 1 : 0)
        .accessibilityElement(children: .combine)
        .accessibilityAction(named: Text("study.order.up")) {
            move(index, by: -1)
        }
        .accessibilityAction(named: Text("study.order.down")) {
            move(index, by: 1)
        }
    }

    private func dragHandle(id: String) -> some View {
        Image(systemName: "line.3.horizontal")
            .font(.system(size: 18, weight: .semibold))
            .foregroundStyle(Brand.textSecondary)
            .frame(width: 36, height: 44)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0, coordinateSpace: .global)
                    .onChanged { value in
                        if draggingID == nil { draggingID = id }
                        if draggingID == id { dragTranslation = value.translation.height }
                    }
                    .onEnded { _ in
                        finishDrag(id: id)
                    }
            )
            .accessibilityHidden(true)
    }

    private func height(of id: String) -> CGFloat {
        heights[id] ?? 0
    }

    private func top(of index: Int) -> CGFloat {
        entries[..<index].reduce(0) { $0 + height(of: $1.id) + rowSpacing }
    }

    private var draggedIndex: Int? {
        guard let draggingID else { return nil }
        return entries.firstIndex { $0.id == draggingID }
    }

    private func clampedShift(from index: Int) -> CGFloat {
        let own = height(of: entries[index].id)
        let total = top(of: entries.count) - rowSpacing
        let lowest = -top(of: index)
        let highest = total - top(of: index) - own
        return min(max(dragTranslation, lowest), highest)
    }

    private var dragTarget: Int? {
        guard let from = draggedIndex else { return nil }
        let center = top(of: from) + height(of: entries[from].id) / 2 + clampedShift(from: from)
        var target = 0
        for (index, entry) in entries.enumerated() where index != from {
            if top(of: index) + height(of: entry.id) / 2 < center { target += 1 }
        }
        return target
    }

    private func rowOffset(at index: Int) -> CGFloat {
        guard let from = draggedIndex, let target = dragTarget else { return 0 }
        if index == from { return clampedShift(from: from) }
        let step = height(of: entries[from].id) + rowSpacing
        if target < from && index >= target && index < from { return step }
        if target > from && index > from && index <= target { return -step }
        return 0
    }

    private func displayedPosition(at index: Int) -> Int {
        guard let from = draggedIndex, let target = dragTarget else { return index + 1 }
        if index == from { return target + 1 }
        if target < from && index >= target && index < from { return index + 2 }
        if target > from && index > from && index <= target { return index }
        return index + 1
    }

    private func finishDrag(id: String) {
        guard draggingID == id else { return }
        let from = draggedIndex
        let target = dragTarget
        withAnimation(.snappy(duration: 0.25)) {
            if let from, let target, from != target {
                let moved = entries.remove(at: from)
                entries.insert(moved, at: target)
            }
            draggingID = nil
            dragTranslation = 0
        }
    }

    private func resultList(_ feedback: FeedbackInfo) -> some View {
        VStack(spacing: 10) {
            ForEach(feedback.orderResults) { result in
                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: result.correct ? "checkmark.circle.fill" : "xmark.circle.fill")
                        .font(.system(size: 22))
                        .foregroundStyle(result.correct ? Brand.success : Brand.error)
                    VStack(alignment: .leading, spacing: 4) {
                        RichTextView(text: result.arranged, font: .brand(.body, weight: .medium))
                        if !result.correct {
                            HStack(alignment: .top, spacing: 6) {
                                Text("study.order.expected")
                                    .font(.brand(.footnote, weight: .bold))
                                    .foregroundStyle(Brand.textSecondary)
                                RichTextView(text: result.expected, font: .brand(.footnote), color: Brand.success)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    Text(String(result.position))
                        .font(.brand(.caption, weight: .bold))
                        .foregroundStyle(Brand.textSecondary)
                }
                .padding(12)
                .background(
                    (result.correct ? Brand.success : Brand.error).opacity(0.1),
                    in: RoundedRectangle(cornerRadius: 18, style: .continuous)
                )
            }
        }
    }

    private func move(_ index: Int, by delta: Int) {
        let target = index + delta
        guard entries.indices.contains(index), entries.indices.contains(target) else { return }
        withAnimation(.snappy(duration: 0.25)) {
            entries.swapAt(index, target)
        }
        moves += 1
    }
}
