import SwiftUI

private struct ItemGroup: Identifiable {
    let id: String
    let title: String
    let items: [ItemInfo]
}

private struct ItemEditorTarget: Identifiable {
    let id = UUID()
    let itemId: String?
}

struct ItemsBrowserView: View {
    let packId: String
    let title: String
    let sections: [SectionInfo]
    @State private var store: ItemsStore
    @State private var query = ""
    @State private var editor: ItemEditorTarget?

    init(packId: String, title: String, sections: [SectionInfo]) {
        self.packId = packId
        self.title = title
        self.sections = sections
        _store = State(initialValue: ItemsStore(packId: packId))
    }

    private var filtered: [ItemInfo] {
        let needle = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if needle.isEmpty { return store.items }
        return store.items.filter {
            $0.title.localizedCaseInsensitiveContains(needle) || $0.subtitle.localizedCaseInsensitiveContains(needle)
        }
    }

    private var groups: [ItemGroup] {
        let items = filtered
        var result: [ItemGroup] = []
        var covered = Set<String>()
        for section in sections {
            covered.insert(section.id)
            let members = items.filter { $0.sectionId == section.id }
            if members.isEmpty { continue }
            let name = section.isUngrouped ? L10n.tr("section.ungrouped") : section.title
            result.append(ItemGroup(id: section.id, title: name, items: members))
        }
        let rest = items.filter { !covered.contains($0.sectionId) }
        if !rest.isEmpty {
            result.append(ItemGroup(id: "~rest", title: L10n.tr("section.ungrouped"), items: rest))
        }
        return result
    }

    var body: some View {
        List {
            if store.isLoaded && filtered.isEmpty {
                Text("items.empty")
                    .font(.brand(.subheadline))
                    .foregroundStyle(Brand.textSecondary)
                    .frame(maxWidth: .infinity)
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
            }
            ForEach(groups) { group in
                Section {
                    ForEach(group.items) { item in
                        Button {
                            editor = ItemEditorTarget(itemId: item.id)
                        } label: {
                            ItemRow(item: item)
                        }
                        .buttonStyle(.plain)
                        .listRowBackground(Brand.surface)
                    }
                } header: {
                    Text(group.title)
                        .font(.brand(.footnote, weight: .bold))
                        .foregroundStyle(Brand.textSecondary)
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(Brand.background.ignoresSafeArea())
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $query, prompt: "items.search")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    editor = ItemEditorTarget(itemId: nil)
                } label: {
                    Image(systemName: "plus")
                }
                .accessibilityLabel("items.add")
            }
        }
        .sheet(item: $editor) { target in
            ItemEditorView(packId: packId, itemId: target.itemId)
        }
        .task {
            await store.watch()
        }
    }
}

private struct ItemRow: View {
    let item: ItemInfo

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: item.kind.symbolName)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Brand.accent)
                .frame(width: 28, height: 28)
                .background(Brand.accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                .accessibilityLabel(L10n.tr(item.kind.labelKey))
            VStack(alignment: .leading, spacing: 3) {
                Text(item.title)
                    .font(.brand(.subheadline, weight: .semibold))
                    .foregroundStyle(Brand.text)
                    .lineLimit(2)
                if !item.subtitle.isEmpty {
                    Text(item.subtitle)
                        .font(.brand(.footnote))
                        .foregroundStyle(Brand.textSecondary)
                        .lineLimit(2)
                }
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 4) {
                Chip(text: L10n.tr(stateKey), tint: stateTint)
                if item.isDue {
                    Image(systemName: "clock.fill")
                        .font(.system(size: 11))
                        .foregroundStyle(Brand.flame)
                        .accessibilityLabel("items.due")
                }
            }
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
    }

    private var stateKey: String {
        switch item.state {
        case .new: return "state.new"
        case .learning: return "state.learning"
        case .review: return "state.review"
        case .mastered: return "state.mastered"
        }
    }

    private var stateTint: Color {
        switch item.state {
        case .new: return Brand.accent
        case .learning: return Brand.flame
        case .review: return Brand.textSecondary
        case .mastered: return Brand.success
        }
    }
}
