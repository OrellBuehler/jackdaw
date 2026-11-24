import SwiftUI

private enum AddRoute: Hashable {
    case paste
    case link
    case samples
    case authoring
    case preview
}

struct AddPackSheet: View {
    let route: AddPackRoute
    @Environment(AppModel.self) private var model
    @State private var flow = ImportFlow()
    @State private var path: [AddRoute] = []
    @State private var showImporter = false
    @State private var fileError: String?
    @State private var showsFileError = false

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                options
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    .padding(.bottom, 32)
            }
            .background(Brand.background.ignoresSafeArea())
            .navigationTitle("add.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.close") {
                        model.addPackRoute = nil
                    }
                }
            }
            .navigationDestination(for: AddRoute.self) { destination in
                destinationView(destination)
            }
        }
        .environment(flow)
        .fileImporter(
            isPresented: $showImporter,
            allowedContentTypes: PackFile.contentTypes,
            allowsMultipleSelection: false
        ) { result in
            handleFile(result)
        }
        .alert("import.failed.title", isPresented: $showsFileError) {
            Button("common.ok", role: .cancel) {}
        } message: {
            Text(fileError ?? "")
        }
        .presentationDragIndicator(.visible)
        .task {
            if let url = route.incomingURL {
                path = [.preview]
                await flow.begin(url: url)
            } else if let linked = route.linked {
                path = [.preview]
                await flow.begin(text: linked.text, sourceURL: linked.url)
            }
        }
    }

    private var options: some View {
        VStack(spacing: 14) {
            Text("add.intro")
                .font(.brand(.subheadline))
                .foregroundStyle(Brand.textSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 4)
            AddOptionRow(
                symbol: "folder.fill",
                tint: Brand.accent,
                foreground: Color.white,
                title: "add.file.title",
                subtitle: "add.file.subtitle"
            ) {
                showImporter = true
            }
            AddOptionRow(
                symbol: "doc.on.clipboard.fill",
                tint: Color(hex: 0x2F9E8F),
                foreground: Color.white,
                title: "add.paste.title",
                subtitle: "add.paste.subtitle"
            ) {
                path.append(.paste)
            }
            AddOptionRow(
                symbol: "link",
                tint: Color(hex: 0x3E7BDE),
                foreground: Color.white,
                title: "add.link.title",
                subtitle: "add.link.subtitle"
            ) {
                path.append(.link)
            }
            AddOptionRow(
                symbol: "square.stack.3d.up.fill",
                tint: Color(hex: 0xE5484D),
                foreground: Color.white,
                title: "add.samples.title",
                subtitle: "add.samples.subtitle"
            ) {
                path.append(.samples)
            }
            AddOptionRow(
                symbol: "sparkles",
                tint: Brand.amber,
                foreground: Brand.ink,
                title: "add.ai.title",
                subtitle: "add.ai.subtitle"
            ) {
                path.append(.authoring)
            }
        }
    }

    @ViewBuilder
    private func destinationView(_ destination: AddRoute) -> some View {
        switch destination {
        case .paste:
            PasteScreen { text in
                path.append(.preview)
                Task { await flow.begin(text: text) }
            }
        case .link:
            LinkScreen { text, url in
                path.append(.preview)
                Task { await flow.begin(text: text, sourceURL: url) }
            }
        case .samples:
            SampleLibraryScreen()
        case .authoring:
            AuthoringScreen {
                path.append(.paste)
            }
        case .preview:
            ImportPreviewScreen()
        }
    }

    private func handleFile(_ result: Result<[URL], Error>) {
        switch result {
        case .success(let urls):
            guard let url = urls.first else { return }
            path = [.preview]
            Task { await flow.begin(url: url) }
        case .failure(let error):
            fileError = error.localizedDescription
            showsFileError = true
        }
    }
}

private struct AddOptionRow: View {
    let symbol: String
    let tint: Color
    let foreground: Color
    let title: LocalizedStringKey
    let subtitle: LocalizedStringKey
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: symbol)
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(foreground)
                    .frame(width: 50, height: 50)
                    .background(tint, in: RoundedRectangle(cornerRadius: 15, style: .continuous))
                VStack(alignment: .leading, spacing: 3) {
                    Text(title)
                        .font(.brand(.headline, weight: .bold))
                        .foregroundStyle(Brand.text)
                    Text(subtitle)
                        .font(.brand(.footnote))
                        .foregroundStyle(Brand.textSecondary)
                        .multilineTextAlignment(.leading)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Brand.textSecondary.opacity(0.6))
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .card(radius: 22)
        }
        .buttonStyle(PressableStyle())
    }
}
