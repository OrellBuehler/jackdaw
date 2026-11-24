import SwiftUI
import UIKit

struct CardBackground: ViewModifier {
    var radius: CGFloat
    var fill: Color
    @Environment(\.colorScheme) private var scheme

    func body(content: Content) -> some View {
        content
            .background(fill, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: radius, style: .continuous)
                    .strokeBorder(Brand.hairline.opacity(scheme == .dark ? 0.9 : 0.6), lineWidth: 1)
            }
            .shadow(color: Color.black.opacity(scheme == .dark ? 0 : 0.05), radius: 14, x: 0, y: 6)
    }
}

extension View {
    func card(radius: CGFloat = 24, fill: Color = Brand.surface) -> some View {
        modifier(CardBackground(radius: radius, fill: fill))
    }
}

struct PressableStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .opacity(configuration.isPressed ? 0.92 : 1)
            .animation(.snappy(duration: 0.2), value: configuration.isPressed)
    }
}

struct BrandButtonStyle: ButtonStyle {
    var fill: Color = Brand.accent
    var foreground: Color = Brand.onAccent
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.brand(.headline, weight: .semibold))
            .foregroundStyle(foreground)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .padding(.horizontal, 20)
            .background(
                fill.opacity(isEnabled ? 1 : 0.3),
                in: RoundedRectangle(cornerRadius: 20, style: .continuous)
            )
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.snappy(duration: 0.18), value: configuration.isPressed)
    }
}

struct SoftButtonStyle: ButtonStyle {
    var tint: Color = Brand.accent

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.brand(.headline, weight: .semibold))
            .foregroundStyle(tint)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .padding(.horizontal, 20)
            .background(tint.opacity(0.14), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.snappy(duration: 0.18), value: configuration.isPressed)
    }
}

struct ProgressRing: View {
    let progress: Double
    var lineWidth: CGFloat = 10
    var tint: Color = Brand.accent
    var track: Color = Brand.hairline

    private var clamped: Double { progress.isFinite ? min(max(progress, 0), 1) : 0 }

    var body: some View {
        ZStack {
            Circle().stroke(track, lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: clamped)
                .stroke(tint, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
        }
        .animation(.smooth(duration: 0.6), value: clamped)
    }
}

struct ProgressBar: View {
    let value: Double
    var tint: Color = Brand.accent
    var track: Color = Brand.hairline
    var height: CGFloat = 8

    private var clamped: Double { value.isFinite ? min(max(value, 0), 1) : 0 }

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule().fill(track)
                Capsule()
                    .fill(tint)
                    .frame(width: proxy.size.width * clamped)
            }
        }
        .frame(height: height)
        .animation(.smooth(duration: 0.5), value: clamped)
    }
}

struct Chip: View {
    let text: String
    var systemImage: String?
    var tint: Color = Brand.accent
    var filled = false

    var body: some View {
        HStack(spacing: 4) {
            if let systemImage {
                Image(systemName: systemImage)
                    .font(.system(size: 11, weight: .bold))
            }
            Text(text)
                .font(.brand(.caption, weight: .semibold))
                .lineLimit(1)
        }
        .foregroundStyle(filled ? Color.white : tint)
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(filled ? tint : tint.opacity(0.14), in: Capsule())
    }
}

struct SectionTitle: View {
    let key: LocalizedStringKey

    init(_ key: LocalizedStringKey) {
        self.key = key
    }

    var body: some View {
        Text(key)
            .font(.brand(.title3, weight: .bold))
            .foregroundStyle(Brand.text)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityAddTraits(.isHeader)
    }
}

struct EmojiTile: View {
    let icon: String?
    let style: PackStyle
    var size: CGFloat = 56

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.3, style: .continuous)
                .fill(style.gradient)
            if let icon, !icon.isEmpty {
                Text(icon)
                    .font(.system(size: size * 0.52))
            } else {
                Image(systemName: "books.vertical.fill")
                    .font(.system(size: size * 0.42, weight: .semibold))
                    .foregroundStyle(style.onBase)
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

struct FlowLayout: Layout {
    var spacing: CGFloat = 6
    var lineSpacing: CGFloat = 8

    private struct Placement {
        var frames: [CGRect]
        var size: CGSize
    }

    private func arrange(_ subviews: Subviews, maxWidth: CGFloat) -> Placement {
        var rows: [[(index: Int, size: CGSize)]] = [[]]
        var rowWidth: CGFloat = 0
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            let needed = rows[rows.count - 1].isEmpty ? size.width : rowWidth + spacing + size.width
            if needed > maxWidth && !rows[rows.count - 1].isEmpty {
                rows.append([])
                rowWidth = 0
            }
            rowWidth = rows[rows.count - 1].isEmpty ? size.width : rowWidth + spacing + size.width
            rows[rows.count - 1].append((index: index, size: size))
        }

        var frames = [CGRect](repeating: .zero, count: subviews.count)
        var y: CGFloat = 0
        var totalWidth: CGFloat = 0
        for row in rows where !row.isEmpty {
            let rowHeight = row.map { $0.size.height }.max() ?? 0
            var x: CGFloat = 0
            for item in row {
                let originY = y + (rowHeight - item.size.height) / 2
                frames[item.index] = CGRect(x: x, y: originY, width: item.size.width, height: item.size.height)
                x += item.size.width + spacing
            }
            totalWidth = max(totalWidth, x - spacing)
            y += rowHeight + lineSpacing
        }
        return Placement(frames: frames, size: CGSize(width: totalWidth, height: max(0, y - lineSpacing)))
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        arrange(subviews, maxWidth: proposal.width ?? .infinity).size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let placement = arrange(subviews, maxWidth: bounds.width)
        for index in subviews.indices {
            let frame = placement.frames[index]
            subviews[index].place(
                at: CGPoint(x: bounds.minX + frame.minX, y: bounds.minY + frame.minY),
                anchor: .topLeading,
                proposal: ProposedViewSize(width: frame.width, height: frame.height)
            )
        }
    }
}

struct IssueNotice: View {
    let issue: ImportIssue

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(issue.localizedMessage)
                .font(.brand(.footnote, weight: .semibold))
                .foregroundStyle(Brand.error)
            if let detail = issue.detailMessage {
                Text(detail)
                    .font(.brand(.footnote))
                    .foregroundStyle(Brand.textSecondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

struct ShareSheet: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

struct ShareItem: Identifiable {
    let id = UUID()
    let url: URL
}
