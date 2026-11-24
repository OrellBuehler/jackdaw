import SwiftUI

struct PackGroupsSheet: View {
    let store: PackDetailStore
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    private var memberOf: Set<String> {
        Set(store.detail?.pack.groupIds ?? [])
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if model.groups.isEmpty {
                        Text("groups.none")
                            .font(.brand(.subheadline))
                            .foregroundStyle(Brand.textSecondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    } else {
                        VStack(spacing: 0) {
                            ForEach(Array(model.groups.enumerated()), id: \.element.id) { index, group in
                                if index > 0 {
                                    Divider().padding(.leading, 16)
                                }
                                row(group)
                            }
                        }
                        .card(radius: 20)
                    }
                    NewGroupRow { name in
                        Task { await model.createGroup(name, adding: store.packId) }
                    }
                }
                .padding(16)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Brand.background.ignoresSafeArea())
            .navigationTitle("groups.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("common.done") {
                        dismiss()
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    private func row(_ group: PackGroupInfo) -> some View {
        let isMember = memberOf.contains(group.id)
        return Button {
            Task { await model.setPack(store.packId, inGroup: group.id, member: !isMember) }
        } label: {
            HStack(spacing: 12) {
                Text(group.name)
                    .font(.brand(.subheadline, weight: .bold))
                    .foregroundStyle(Brand.text)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: isMember ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 22))
                    .foregroundStyle(isMember ? Brand.accent : Brand.textSecondary.opacity(0.5))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isMember ? [.isSelected] : [])
    }
}
