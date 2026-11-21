import SwiftUI

struct SampleLibraryScreen: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                Text("samples.intro")
                    .font(.brand(.subheadline))
                    .foregroundStyle(Brand.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 4)
                ForEach(model.library) { sample in
                    SamplePackRow(sample: sample)
                }
                OnlineCatalogCard()
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 32)
        }
        .background(Brand.background.ignoresSafeArea())
        .navigationTitle("samples.title")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct OnlineCatalogCard: View {
    @Environment(AppModel.self) private var model

    private var message: LocalizedStringKey {
        switch model.catalogPhase {
        case .idle, .loading: "samples.online.message"
        case .loaded: "samples.online.loaded"
        case .failed: "samples.online.failed"
        }
    }

    private var buttonTitle: LocalizedStringKey {
        model.catalogPhase == .failed ? "samples.online.retry" : "samples.online.load"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("samples.online.title")
                .font(.brand(.subheadline, weight: .bold))
                .foregroundStyle(Brand.text)
            Text(message)
                .font(.brand(.caption))
                .foregroundStyle(Brand.textSecondary)
            action
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .card(radius: 22)
    }

    @ViewBuilder
    private var action: some View {
        switch model.catalogPhase {
        case .loading:
            ProgressView()
        case .loaded:
            EmptyView()
        case .idle, .failed:
            Button {
                Task { await model.loadCatalog() }
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
}
