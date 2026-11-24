import SwiftUI

enum LibraryFilter: Hashable {
    case all
    case archived
    case group(String)
}

struct LibraryView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        NavigationStack {
            ScrollView {
                if !model.packsLoaded {
                    Color.clear.frame(height: 1)
                } else if model.packs.isEmpty {
                    EmptyLibraryView()
                } else {
                    LibraryContent()
                }
            }
            .background(Brand.background.ignoresSafeArea())
            .navigationTitle("library.title")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        model.presentAddPack()
                    } label: {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("library.add")
                }
            }
            .navigationDestination(for: PackRoute.self) { route in
                PackDetailView(packId: route.id)
            }
        }
    }
}

private struct LibraryContent: View {
    @Environment(AppModel.self) private var model
    @State private var filter: LibraryFilter = .all

    private var current: LibraryFilter {
        switch filter {
        case .all:
            return .all
        case .archived:
            return model.archivedPacks.isEmpty ? .all : .archived
        case .group(let id):
            return model.groups.contains { $0.id == id } ? filter : .all
        }
    }

    private var visiblePacks: [PackInfo] {
        switch current {
        case .all:
            return model.activePacks
        case .archived:
            return model.archivedPacks
        case .group(let id):
            return model.activePacks.filter { $0.groupIds.contains(id) }
        }
    }

    var body: some View {
        LazyVStack(spacing: 16) {
            LibraryHeaderView()
            SectionTitle("library.packs")
                .padding(.top, 8)
            LibraryFilterBar(selection: current) { value in
                filter = value
            }
            if visiblePacks.isEmpty {
                Text("library.group.empty")
                    .font(.brand(.subheadline))
                    .foregroundStyle(Brand.textSecondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 24)
            }
            ForEach(visiblePacks) { pack in
                NavigationLink(value: PackRoute(id: pack.id)) {
                    PackCardView(pack: pack)
                }
                .buttonStyle(PressableStyle())
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 32)
    }
}

private struct LibraryFilterBar: View {
    let selection: LibraryFilter
    let onSelect: (LibraryFilter) -> Void
    @Environment(AppModel.self) private var model
    @State private var showsGroups = false

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                filterChip(L10n.tr("library.filter.all"), value: .all)
                ForEach(model.groups) { group in
                    filterChip(group.name, value: .group(group.id))
                }
                if !model.archivedPacks.isEmpty {
                    filterChip(L10n.tr("library.filter.archived"), value: .archived)
                }
                Button {
                    showsGroups = true
                } label: {
                    Chip(text: L10n.tr("groups.title"), systemImage: "folder", tint: Brand.textSecondary)
                        .padding(.vertical, 4)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .contentMargins(.horizontal, 16, for: .scrollContent)
        .padding(.horizontal, -16)
        .sheet(isPresented: $showsGroups) {
            GroupManagerView()
        }
    }

    private func filterChip(_ title: String, value: LibraryFilter) -> some View {
        let selected = selection == value
        return Button {
            onSelect(value)
        } label: {
            Chip(text: title, filled: selected)
                .padding(.vertical, 4)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }
}
