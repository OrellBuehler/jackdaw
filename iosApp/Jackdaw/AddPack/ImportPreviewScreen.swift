import SwiftUI

struct ImportPreviewScreen: View {
    @Environment(ImportFlow.self) private var flow
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            content
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 32)
        }
        .background(Brand.background.ignoresSafeArea())
        .navigationTitle("preview.title")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(flow.isFinished)
        .onDisappear {
            if !flow.isFinished { flow.reset() }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch flow.phase {
        case .idle, .loading:
            VStack(spacing: 16) {
                ProgressView()
                    .controlSize(.large)
                Text("preview.loading")
                    .font(.brand(.subheadline))
                    .foregroundStyle(Brand.textSecondary)
            }
            .padding(.top, 80)
        case .preview(let info):
            PreviewDetails(info: info, isBusy: false)
        case .importing(let info):
            PreviewDetails(info: info, isBusy: true)
        case .done(let done):
            ImportDoneView(done: done)
        case .failed(let issue):
            ImportFailedView(issue: issue) {
                dismiss()
            }
        }
    }
}

private struct PreviewDetails: View {
    let info: ImportPreviewInfo
    let isBusy: Bool
    @Environment(ImportFlow.self) private var flow
    @Environment(AppModel.self) private var model

    private var style: PackStyle { PackStyle(hex: info.colorHex) }

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            header
            if let blurb = info.blurb, !blurb.isEmpty {
                Text(blurb)
                    .font(.brand(.body))
                    .foregroundStyle(Brand.textSecondary)
            }
            countsCard
            if info.isUpdate { updateNotice }
            if info.isDowngrade { downgradeNotice }
            if info.overwritesEdits { overwriteNotice }
            if info.skippedCount > 0 || !info.warnings.isEmpty { warningsCard }
            actions
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var header: some View {
        HStack(spacing: 16) {
            EmojiTile(icon: info.icon, style: style, size: 72)
            VStack(alignment: .leading, spacing: 6) {
                Text(info.title)
                    .font(.brand(.title2, weight: .bold))
                    .foregroundStyle(Brand.text)
                    .lineLimit(3)
                HStack(spacing: 6) {
                    if let language = info.language, !language.isEmpty {
                        Chip(text: language.uppercased(), systemImage: "globe", tint: style.accentText)
                    }
                    if info.revision > 0 {
                        Chip(text: L10n.format("preview.revision", info.revision), tint: Brand.textSecondary)
                    }
                }
            }
        }
    }

    private var countsCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 24) {
                countColumn(value: info.itemCount, label: L10n.plural("items", info.itemCount))
                if info.sectionCount > 0 {
                    countColumn(value: info.sectionCount, label: L10n.plural("sections", info.sectionCount))
                }
            }
            if !info.typeCounts.isEmpty {
                FlowLayout(spacing: 8, lineSpacing: 8) {
                    ForEach(info.typeCounts) { entry in
                        Chip(
                            text: String(entry.count) + " " + L10n.tr(entry.kind.labelKey),
                            systemImage: entry.kind.symbolName,
                            tint: style.accentText
                        )
                    }
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 22)
    }

    private func countColumn(value: Int, label: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(String(value))
                .font(.brand(.largeTitle, weight: .bold))
                .foregroundStyle(Brand.text)
            Text(label)
                .font(.brand(.footnote))
                .foregroundStyle(Brand.textSecondary)
        }
    }

    private var updateNotice: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label("preview.update.title", systemImage: "arrow.triangle.2.circlepath")
                .font(.brand(.subheadline, weight: .bold))
                .foregroundStyle(Brand.accent)
            Text(L10n.format("preview.update.body", info.installedTitle ?? info.title, info.installedRevision, info.revision))
                .font(.brand(.footnote))
                .foregroundStyle(Brand.text)
            Text(
                L10n.format(
                    "preview.update.counts",
                    info.counts.added,
                    info.counts.kept,
                    info.counts.reset,
                    info.counts.removed
                )
            )
            .font(.brand(.footnote))
            .foregroundStyle(Brand.textSecondary)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Brand.accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private var downgradeNotice: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label("preview.downgrade.title", systemImage: "exclamationmark.triangle.fill")
                .font(.brand(.subheadline, weight: .bold))
                .foregroundStyle(Brand.flame)
            Text(L10n.format("preview.downgrade.body", info.installedRevision, info.revision))
                .font(.brand(.footnote))
                .foregroundStyle(Brand.text)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Brand.amber.opacity(0.2), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private var overwriteNotice: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label("import.overwrite.title", systemImage: "exclamationmark.triangle.fill")
                .font(.brand(.subheadline, weight: .bold))
                .foregroundStyle(Brand.flame)
            Text("import.overwrite.body")
                .font(.brand(.footnote))
                .foregroundStyle(Brand.text)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Brand.amber.opacity(0.2), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private var warningsCard: some View {
        DisclosureGroup {
            VStack(alignment: .leading, spacing: 8) {
                if info.skippedCount > 0 {
                    Text(L10n.plural("preview.skipped", info.skippedCount))
                        .font(.brand(.footnote, weight: .semibold))
                        .foregroundStyle(Brand.text)
                }
                ForEach(info.warnings) { warning in
                    Text("• " + warning.localizedMessage)
                        .font(.brand(.footnote))
                        .foregroundStyle(Brand.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .padding(.top, 8)
        } label: {
            Label(L10n.plural("preview.warnings", info.warnings.count + (info.skippedCount > 0 ? 1 : 0)), systemImage: "exclamationmark.circle")
                .font(.brand(.subheadline, weight: .semibold))
                .foregroundStyle(Brand.flame)
        }
        .padding(16)
        .card(radius: 18)
    }

    private var actions: some View {
        VStack(spacing: 12) {
            if info.isDowngrade {
                Button {
                    Task { await flow.confirm(allowDowngrade: true) }
                } label: {
                    confirmLabel(key: "preview.downgrade.confirm")
                }
                .buttonStyle(BrandButtonStyle(fill: Brand.flame, foreground: Brand.ink))
                .disabled(isBusy)
            } else {
                Button {
                    Task { await flow.confirm(allowDowngrade: false) }
                } label: {
                    confirmLabel(key: info.isUpdate ? "preview.update.confirm" : "preview.add.confirm")
                }
                .buttonStyle(BrandButtonStyle())
                .disabled(isBusy)
            }
            Button {
                model.addPackRoute = nil
            } label: {
                Text("common.cancel")
            }
            .buttonStyle(SoftButtonStyle(tint: Brand.textSecondary))
            .disabled(isBusy)
        }
        .padding(.top, 4)
    }

    @ViewBuilder
    private func confirmLabel(key: String) -> some View {
        if isBusy {
            ProgressView()
                .tint(Brand.onAccent)
        } else {
            Text(L10n.tr(key))
        }
    }
}

private struct ImportDoneView: View {
    let done: ImportDoneInfo
    @Environment(AppModel.self) private var model
    @State private var celebrated = false

    var body: some View {
        VStack(spacing: 20) {
            Mascot(size: 130, happy: true, animated: true)
                .padding(.top, 16)
            VStack(spacing: 6) {
                Text(L10n.tr(done.isUpdate ? "done.updated" : "done.added"))
                    .font(.brand(.title2, weight: .bold))
                    .foregroundStyle(Brand.text)
                Text(done.title)
                    .font(.brand(.headline))
                    .foregroundStyle(Brand.textSecondary)
                    .multilineTextAlignment(.center)
                Text(L10n.plural("items", done.itemCount))
                    .font(.brand(.subheadline))
                    .foregroundStyle(Brand.textSecondary)
            }
            if !done.warnings.isEmpty || done.skippedCount > 0 {
                VStack(alignment: .leading, spacing: 6) {
                    if done.skippedCount > 0 {
                        Text(L10n.plural("preview.skipped", done.skippedCount))
                            .font(.brand(.footnote, weight: .semibold))
                    }
                    ForEach(done.warnings) { warning in
                        Text("• " + warning.localizedMessage)
                            .font(.brand(.footnote))
                            .foregroundStyle(Brand.textSecondary)
                    }
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Brand.amber.opacity(0.18), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
            Button {
                model.addPackRoute = nil
            } label: {
                Text("common.done")
            }
            .buttonStyle(BrandButtonStyle())
        }
        .sensoryFeedback(.success, trigger: celebrated)
        .onAppear { celebrated = true }
    }
}

private struct ImportFailedView: View {
    let issue: ImportIssue
    let onBack: () -> Void
    @State private var shown = false

    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 44))
                .foregroundStyle(Brand.flame)
                .padding(.top, 40)
            Text("import.failed.title")
                .font(.brand(.title2, weight: .bold))
                .foregroundStyle(Brand.text)
            Text(issue.localizedMessage)
                .font(.brand(.body))
                .foregroundStyle(Brand.textSecondary)
                .multilineTextAlignment(.center)
            Button {
                onBack()
            } label: {
                Text("common.back")
            }
            .buttonStyle(SoftButtonStyle())
        }
        .sensoryFeedback(.error, trigger: shown)
        .onAppear { shown = true }
    }
}
