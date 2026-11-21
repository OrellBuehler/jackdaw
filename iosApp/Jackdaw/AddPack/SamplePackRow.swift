import SwiftUI

struct SamplePackRow: View {
    let sample: SamplePackInfo
    @Environment(AppModel.self) private var model
    @State private var isAdding = false
    @State private var justAdded = false
    @State private var failure: String?
    @State private var showsFailure = false

    private var installedRevision: Int? {
        model.packs.first { $0.id == sample.id }?.revision
    }

    private var needsUpdate: Bool {
        guard let installedRevision else { return false }
        return sample.revision > installedRevision
    }

    private var isInstalled: Bool {
        justAdded || installedRevision != nil
    }

    private var buttonTitle: LocalizedStringKey {
        needsUpdate ? "sample.update" : "sample.add"
    }

    private var style: PackStyle {
        PackStyle(hex: sample.colorHex)
    }

    var body: some View {
        HStack(spacing: 14) {
            EmojiTile(icon: sample.icon, style: style, size: 52)
            VStack(alignment: .leading, spacing: 3) {
                Text(sample.title)
                    .font(.brand(.subheadline, weight: .bold))
                    .foregroundStyle(Brand.text)
                    .lineLimit(2)
                Text(sample.blurb)
                    .font(.brand(.caption))
                    .foregroundStyle(Brand.textSecondary)
                    .lineLimit(2)
                Text(L10n.plural("items", sample.itemCount))
                    .font(.brand(.caption2, weight: .semibold))
                    .foregroundStyle(style.accentText)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            trailing
        }
        .padding(14)
        .card(radius: 22)
        .alert("import.failed.title", isPresented: $showsFailure) {
            Button("common.ok", role: .cancel) {}
        } message: {
            Text(failure ?? "")
        }
    }

    @ViewBuilder
    private var trailing: some View {
        if isInstalled && !needsUpdate {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 26))
                .foregroundStyle(Brand.success)
                .accessibilityLabel("sample.added")
        } else if isAdding {
            ProgressView()
        } else {
            Button {
                add()
            } label: {
                Text(buttonTitle)
                    .font(.brand(.subheadline, weight: .bold))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .foregroundStyle(Brand.onAccent)
                    .background(Brand.accent, in: Capsule())
            }
            .buttonStyle(PressableStyle())
        }
    }

    private func add() {
        isAdding = true
        Task {
            let outcome = await model.importSample(sample)
            isAdding = false
            switch outcome {
            case .success:
                justAdded = true
            case .failure(let issue):
                failure = issue.localizedMessage
                showsFailure = true
            }
        }
    }
}
