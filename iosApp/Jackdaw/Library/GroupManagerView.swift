import SwiftUI

struct NewGroupRow: View {
    let onAdd: (String) -> Void
    @State private var name = ""

    private var trimmed: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        HStack(spacing: 10) {
            TextField("groups.new", text: $name)
                .font(.brand(.body))
                .textInputAutocapitalization(.sentences)
                .submitLabel(.done)
                .onSubmit { add() }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .card(radius: 16)
            Button {
                add()
            } label: {
                Text("common.add")
                    .font(.brand(.subheadline, weight: .bold))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .foregroundStyle(Brand.onAccent)
                    .background(Brand.accent, in: Capsule())
            }
            .buttonStyle(PressableStyle())
            .disabled(trimmed.isEmpty)
            .opacity(trimmed.isEmpty ? 0.4 : 1)
        }
    }

    private func add() {
        guard !trimmed.isEmpty else { return }
        onAdd(trimmed)
        name = ""
    }
}

struct GroupManagerView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var renamingId: String?
    @State private var renameText = ""
    @State private var showsRename = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    NewGroupRow { name in
                        Task { await model.createGroup(name) }
                    }
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
                        Text("groups.hint")
                            .font(.brand(.footnote))
                            .foregroundStyle(Brand.textSecondary)
                            .padding(.horizontal, 4)
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
        .presentationDragIndicator(.visible)
        .alert("groups.rename", isPresented: $showsRename) {
            TextField("groups.new", text: $renameText)
            Button("common.save") {
                saveRename()
            }
            Button("common.cancel", role: .cancel) {}
        }
    }

    private func row(_ group: PackGroupInfo) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "folder.fill")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Brand.accent)
                .padding(.trailing, 4)
            Text(group.name)
                .font(.brand(.subheadline, weight: .bold))
                .foregroundStyle(Brand.text)
                .lineLimit(2)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button {
                renamingId = group.id
                renameText = group.name
                showsRename = true
            } label: {
                Image(systemName: "pencil")
                    .foregroundStyle(Brand.textSecondary)
                    .frame(width: 40, height: 40)
                    .contentShape(Rectangle())
            }
            .buttonStyle(PressableStyle())
            .accessibilityLabel("groups.rename")
            Button {
                Task { await model.deleteGroup(group.id) }
            } label: {
                Image(systemName: "trash")
                    .foregroundStyle(Brand.error)
                    .frame(width: 40, height: 40)
                    .contentShape(Rectangle())
            }
            .buttonStyle(PressableStyle())
            .accessibilityLabel("groups.delete")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
    }

    private func saveRename() {
        guard let id = renamingId else { return }
        let name = renameText
        Task { await model.renameGroup(id, to: name) }
    }
}
