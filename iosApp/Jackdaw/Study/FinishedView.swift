import SwiftUI

struct FinishedView: View {
    let summary: SummaryInfo
    let style: PackStyle
    let onDone: () -> Void
    @State private var appeared = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var isEmpty: Bool { summary.total == 0 }
    private var celebrates: Bool { !summary.abandoned && summary.answered > 0 && summary.accuracy >= 0.7 }

    var body: some View {
        ZStack {
            ViewThatFits(in: .vertical) {
                content(compact: false)
                content(compact: true)
                ScrollView {
                    content(compact: true)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .safeAreaInset(edge: .bottom) {
                Button {
                    onDone()
                } label: {
                    Text("common.done")
                }
                .buttonStyle(BrandButtonStyle(fill: style.base, foreground: style.onBase))
                .padding(.horizontal, 24)
                .padding(.vertical, 12)
            }
            if celebrates && !reduceMotion {
                ConfettiView()
                    .ignoresSafeArea()
                    .allowsHitTesting(false)
            }
        }
        .animation(.spring(response: 0.6, dampingFraction: 0.62), value: appeared)
        .sensoryFeedback(.success, trigger: appeared)
        .onAppear {
            appeared = true
        }
    }

    private func content(compact: Bool) -> some View {
        VStack(spacing: compact ? 12 : 18) {
            Mascot(size: compact ? 80 : 112, happy: !isEmpty, animated: true)
                .scaleEffect(appeared ? 1 : 0.5)
                .opacity(appeared ? 1 : 0)
            VStack(spacing: compact ? 2 : 6) {
                Text(L10n.tr(titleKey))
                    .font(.brand(compact ? .title : .largeTitle, weight: .bold))
                    .foregroundStyle(Brand.text)
                    .multilineTextAlignment(.center)
                Text(L10n.tr(subtitleKey))
                    .font(.brand(compact ? .subheadline : .body))
                    .foregroundStyle(Brand.textSecondary)
                    .multilineTextAlignment(.center)
            }
            .fixedSize(horizontal: false, vertical: true)
            if !isEmpty && summary.answered > 0 {
                accuracyRing(compact: compact)
                statsGrid(compact: compact)
            }
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 8)
    }

    private var titleKey: String {
        if isEmpty { return "finished.empty.title" }
        if summary.abandoned { return "finished.abandoned.title" }
        if summary.accuracy >= 0.9 { return "finished.great.title" }
        if summary.accuracy >= 0.7 { return "finished.good.title" }
        return "finished.keep.title"
    }

    private var subtitleKey: String {
        if isEmpty { return "finished.empty.message" }
        if summary.abandoned { return "finished.abandoned.message" }
        if summary.accuracy >= 0.7 { return "finished.good.message" }
        return "finished.keep.message"
    }

    private func accuracyRing(compact: Bool) -> some View {
        let side: CGFloat = compact ? 104 : 128
        return ZStack {
            ProgressRing(
                progress: summary.accuracy,
                lineWidth: compact ? 10 : 12,
                tint: summary.accuracy >= 0.7 ? Brand.success : Brand.flame,
                track: Brand.hairline
            )
            VStack(spacing: 0) {
                Text(L10n.format("pack.progress.percent", Int((summary.accuracy * 100).rounded())))
                    .font(.brand(compact ? .title2 : .title, weight: .bold))
                    .foregroundStyle(Brand.text)
                    .monospacedDigit()
                Text("finished.accuracy")
                    .font(.brand(.caption, weight: .semibold))
                    .foregroundStyle(Brand.textSecondary)
            }
        }
        .frame(width: side, height: side)
        .accessibilityElement(children: .combine)
    }

    private func statsGrid(compact: Bool) -> some View {
        Grid(horizontalSpacing: 12, verticalSpacing: 12) {
            GridRow {
                statTile(value: String(summary.answered), key: "finished.answered", symbol: "checkmark.circle", compact: compact)
                statTile(value: String(summary.correct), key: "finished.correct", symbol: "hand.thumbsup.fill", compact: compact)
            }
            GridRow {
                statTile(value: String(summary.newFacets), key: "finished.new", symbol: "sparkles", compact: compact)
                statTile(value: duration, key: "finished.time", symbol: "clock.fill", compact: compact)
            }
        }
    }

    private func statTile(value: String, key: LocalizedStringKey, symbol: String, compact: Bool) -> some View {
        let layout = compact ? AnyLayout(HStackLayout(spacing: 10)) : AnyLayout(VStackLayout(alignment: .leading, spacing: 4))
        return layout {
            Image(systemName: symbol)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(style.accentText)
            VStack(alignment: .leading, spacing: 0) {
                Text(value)
                    .font(.brand(compact ? .title3 : .title2, weight: .bold))
                    .foregroundStyle(Brand.text)
                    .monospacedDigit()
                Text(key)
                    .font(.brand(.caption))
                    .foregroundStyle(Brand.textSecondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
        }
        .padding(compact ? 12 : 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 20)
        .accessibilityElement(children: .combine)
    }

    private var duration: String {
        let total = Int(summary.duration.rounded())
        return String(format: "%ld:%02ld", total / 60, total % 60)
    }
}

private struct ConfettiPiece: Identifiable {
    let id: Int
    let x: CGFloat
    let width: CGFloat
    let delay: Double
    let duration: Double
    let spin: Double
    let color: Color
}

struct ConfettiView: View {
    @State private var pieces: [ConfettiPiece] = ConfettiView.makePieces()
    @State private var fallen = false

    private static func makePieces() -> [ConfettiPiece] {
        let colors: [Color] = [Brand.violet, Brand.amber, Brand.success, Brand.error, Brand.accent]
        return (0 ..< 36).map { index in
            ConfettiPiece(
                id: index,
                x: CGFloat.random(in: 0.04 ... 0.96),
                width: CGFloat.random(in: 6 ... 11),
                delay: Double.random(in: 0 ... 0.7),
                duration: Double.random(in: 1.6 ... 2.8),
                spin: Double.random(in: 180 ... 720),
                color: colors[index % colors.count]
            )
        }
    }

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                ForEach(pieces) { piece in
                    RoundedRectangle(cornerRadius: 2, style: .continuous)
                        .fill(piece.color)
                        .frame(width: piece.width, height: piece.width * 1.7)
                        .rotationEffect(.degrees(fallen ? piece.spin : 0))
                        .position(x: piece.x * proxy.size.width, y: fallen ? proxy.size.height + 40 : -30)
                        .opacity(fallen ? 0.9 : 1)
                        .animation(.easeIn(duration: piece.duration).delay(piece.delay), value: fallen)
                }
            }
        }
        .onAppear {
            fallen = true
        }
        .accessibilityHidden(true)
    }
}
