import SwiftUI

struct PackInfoEditor: View {
    let store: PackDetailStore
    @Environment(\.dismiss) private var dismiss
    @State private var title: String
    @State private var summary: String
    @State private var issue: ImportIssue?
    @State private var isSaving = false

    init(store: PackDetailStore, initialTitle: String, initialDescription: String) {
        self.store = store
        _title = State(initialValue: initialTitle)
        _summary = State(initialValue: initialDescription)
    }

    private var canSave: Bool {
        !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !isSaving
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    field("edit.title") {
                        TextField("edit.title", text: $title, axis: .vertical)
                            .lineLimit(1 ... 3)
                    }
                    field("edit.description") {
                        TextField("edit.description", text: $summary, axis: .vertical)
                            .lineLimit(3 ... 8)
                    }
                    if let issue {
                        IssueNotice(issue: issue)
                    }
                }
                .padding(16)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Brand.background.ignoresSafeArea())
            .navigationTitle("detail.edit")
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
            .onChange(of: title) {
                issue = nil
            }
            .onChange(of: summary) {
                issue = nil
            }
        }
        .presentationDragIndicator(.visible)
    }

    private func field<Content: View>(_ key: LocalizedStringKey, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(key)
                .font(.brand(.caption, weight: .bold))
                .foregroundStyle(Brand.textSecondary)
                .textCase(.uppercase)
            content()
                .font(.brand(.body))
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .card(radius: 16)
        }
    }

    private func save() {
        isSaving = true
        let newTitle = title
        let newDescription = summary
        Task {
            let outcome = await store.updateInfo(title: newTitle, description: newDescription)
            isSaving = false
            switch outcome {
            case .success:
                dismiss()
            case .failure(let failure):
                issue = failure
            }
        }
    }
}
