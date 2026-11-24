import SwiftUI
import UIKit

struct LinkScreen: View {
    let onLoaded: (String, String) -> Void
    @Environment(AppModel.self) private var model
    @State private var input = ""
    @State private var isLoading = false
    @State private var problem: String?

    private var trimmed: String {
        input.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("link.explanation")
                    .font(.brand(.body))
                    .foregroundStyle(Brand.textSecondary)
                TextField("add.link.title", text: $input, prompt: Text(verbatim: "https://gist.github.com/…"))
                    .font(.brand(.body))
                    .keyboardType(.URL)
                    .textContentType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .submitLabel(.go)
                    .onSubmit { submit() }
                    .disabled(isLoading)
                    .padding(14)
                    .card(radius: 16)
                if let problem {
                    Text(problem)
                        .font(.brand(.footnote, weight: .semibold))
                        .foregroundStyle(Brand.error)
                }
                HStack(spacing: 12) {
                    Button {
                        if let pasted = UIPasteboard.general.string {
                            input = pasted.trimmingCharacters(in: .whitespacesAndNewlines)
                        }
                    } label: {
                        Label("paste.clipboard", systemImage: "doc.on.clipboard")
                    }
                    .buttonStyle(SoftButtonStyle())
                    .disabled(isLoading)
                    Button {
                        submit()
                    } label: {
                        continueLabel
                    }
                    .buttonStyle(BrandButtonStyle())
                    .disabled(trimmed.isEmpty || isLoading)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Brand.background.ignoresSafeArea())
        .navigationTitle("add.link.title")
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: input) {
            problem = nil
        }
    }

    @ViewBuilder
    private var continueLabel: some View {
        if isLoading {
            ProgressView()
                .tint(Brand.onAccent)
        } else {
            Text("study.continue")
        }
    }

    private func submit() {
        guard !trimmed.isEmpty, !isLoading else { return }
        guard let link = model.resolveLink(trimmed) else {
            problem = L10n.tr("link.invalid")
            return
        }
        problem = nil
        isLoading = true
        Task {
            let text = await model.downloadPack(from: link)
            isLoading = false
            if let text {
                onLoaded(text, link)
            } else {
                problem = L10n.tr("import.error.download")
            }
        }
    }
}
