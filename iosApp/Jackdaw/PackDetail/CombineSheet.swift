import SwiftUI

struct CombineSheet: View {
    let store: PackDetailStore
    let packTitle: String
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var chosen: PackInfo?
    @State private var preview: CombineOutcome?
    @State private var issue: ImportIssue?
    @State private var isWorking = false
    @State private var addedCount: Int?

    private var others: [PackInfo] {
        model.packs.filter { $0.id != store.packId }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if let addedCount {
                        doneContent(addedCount)
                    } else if let chosen {
                        previewContent(chosen)
                    } else {
                        pickContent
                    }
                }
                .padding(16)
            }
            .background(Brand.background.ignoresSafeArea())
            .navigationTitle("combine.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    if addedCount == nil {
                        Button("common.cancel") {
                            dismiss()
                        }
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if addedCount != nil {
                        Button("common.done") {
                            dismiss()
                        }
                    }
                }
            }
            .task(id: chosen?.id) {
                preview = nil
                issue = nil
                guard let chosen else { return }
                preview = await store.previewCombine(with: chosen.id)
            }
        }
        .presentationDragIndicator(.visible)
    }

    @ViewBuilder
    private var pickContent: some View {
        if others.isEmpty {
            Text("combine.none")
                .font(.brand(.subheadline))
                .foregroundStyle(Brand.textSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            Text(L10n.format("combine.pick", packTitle))
                .font(.brand(.subheadline))
                .foregroundStyle(Brand.textSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
            VStack(spacing: 0) {
                ForEach(Array(others.enumerated()), id: \.element.id) { index, pack in
                    if index > 0 {
                        Divider().padding(.leading, 68)
                    }
                    row(pack)
                }
            }
            .card(radius: 20)
        }
    }

    private func row(_ pack: PackInfo) -> some View {
        Button {
            chosen = pack
        } label: {
            HStack(spacing: 12) {
                EmojiTile(icon: pack.icon, style: PackStyle(hex: pack.colorHex), size: 40)
                VStack(alignment: .leading, spacing: 2) {
                    Text(pack.title)
                        .font(.brand(.subheadline, weight: .bold))
                        .foregroundStyle(Brand.text)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    Text(L10n.plural("items", pack.itemCount))
                        .font(.brand(.caption))
                        .foregroundStyle(Brand.textSecondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Brand.textSecondary.opacity(0.6))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func previewContent(_ pack: PackInfo) -> some View {
        Text(pack.title)
            .font(.brand(.headline, weight: .bold))
            .foregroundStyle(Brand.text)
            .frame(maxWidth: .infinity, alignment: .leading)
        switch preview {
        case .none:
            ProgressView()
                .frame(maxWidth: .infinity)
        case .some(.invalid(let problem)):
            IssueNotice(issue: problem)
        case .some(.valid(let info)):
            validContent(info, packId: pack.id)
        }
        Button("common.back") {
            chosen = nil
        }
        .buttonStyle(SoftButtonStyle(tint: Brand.textSecondary))
        .disabled(isWorking)
    }

    @ViewBuilder
    private func validContent(_ info: CombinePreviewInfo, packId: String) -> some View {
        if info.addedCount == 0 {
            Text(L10n.format("combine.nothing", info.otherTitle))
                .font(.brand(.subheadline))
                .foregroundStyle(Brand.text)
                .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            VStack(alignment: .leading, spacing: 8) {
                Text(L10n.plural("combine.added", info.addedCount))
                    .font(.brand(.body, weight: .semibold))
                    .foregroundStyle(Brand.text)
                if info.sectionCount > 0 {
                    Text(L10n.plural("combine.sections", info.sectionCount))
                        .font(.brand(.subheadline))
                        .foregroundStyle(Brand.text)
                }
                if info.duplicateCount > 0 {
                    Text(L10n.plural("combine.duplicates", info.duplicateCount))
                        .font(.brand(.subheadline))
                        .foregroundStyle(Brand.textSecondary)
                }
                Text(L10n.format("combine.note", info.otherTitle))
                    .font(.brand(.footnote))
                    .foregroundStyle(Brand.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if let issue {
                IssueNotice(issue: issue)
            }
            Button("combine.confirm") {
                combine(with: packId)
            }
            .buttonStyle(BrandButtonStyle())
            .disabled(isWorking)
        }
    }

    private func doneContent(_ count: Int) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 28))
                .foregroundStyle(Brand.accent)
            Text(L10n.plural("combine.done", count))
                .font(.brand(.body, weight: .semibold))
                .foregroundStyle(Brand.text)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(16)
        .card(radius: 20)
    }

    private func combine(with otherId: String) {
        isWorking = true
        issue = nil
        Task {
            let outcome = await store.combine(with: otherId)
            isWorking = false
            switch outcome {
            case .success(let done):
                addedCount = done.counts.added
            case .failure(let failure):
                issue = failure
            }
        }
    }
}
