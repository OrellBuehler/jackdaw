import SwiftUI

struct ItemEditorView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var store: ItemEditorStore
    @State private var text = ""
    @State private var isLoaded = false
    @State private var isBusy = false
    @State private var issue: ImportIssue?
    @State private var confirmDelete = false

    init(packId: String, itemId: String?) {
        _store = State(initialValue: ItemEditorStore(packId: packId, itemId: itemId))
    }

    private var canSave: Bool {
        isLoaded && !isBusy && !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("item.edit.help")
                        .font(.brand(.footnote))
                        .foregroundStyle(Brand.textSecondary)
                    JSONTextEditor(text: $text)
                        .frame(height: 340)
                        .card(radius: 16)
                    if let issue {
                        IssueNotice(issue: issue)
                    }
                    if store.itemId != nil {
                        deleteButton
                    }
                }
                .padding(16)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Brand.background.ignoresSafeArea())
            .navigationTitle(L10n.tr(store.itemId == nil ? "item.new.title" : "item.edit.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.cancel") {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("common.save") {
                        save()
                    }
                    .disabled(!canSave)
                }
            }
            .onChange(of: text) {
                issue = nil
            }
            .task {
                guard let source = await store.loadSource() else {
                    dismiss()
                    return
                }
                text = source
                isLoaded = true
            }
        }
        .presentationDragIndicator(.visible)
    }

    private var deleteButton: some View {
        Button(role: .destructive) {
            confirmDelete = true
        } label: {
            Label("item.delete", systemImage: "trash")
        }
        .buttonStyle(SoftButtonStyle(tint: Brand.error))
        .disabled(isBusy)
        .confirmationDialog("item.delete.title", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("item.delete", role: .destructive) {
                delete()
            }
            Button("common.cancel", role: .cancel) {}
        } message: {
            Text("item.delete.message")
        }
    }

    private func save() {
        isBusy = true
        let json = text
        Task {
            let outcome = await store.save(json)
            finish(outcome)
        }
    }

    private func delete() {
        isBusy = true
        Task {
            guard let outcome = await store.delete() else {
                isBusy = false
                return
            }
            finish(outcome)
        }
    }

    private func finish(_ outcome: ImportOutcome) {
        isBusy = false
        switch outcome {
        case .success:
            dismiss()
        case .failure(let failure):
            issue = failure
        }
    }
}
