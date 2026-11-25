import SwiftUI

private enum DetailSheet: Identifiable {
    case edit
    case groups
    case combine

    var id: Self { self }
}

struct PackDetailView: View {
    let packId: String
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var store: PackDetailStore
    @State private var confirmReset = false
    @State private var confirmDelete = false
    @State private var shareItem: ShareItem?
    @State private var activeSheet: DetailSheet?

    init(packId: String) {
        self.packId = packId
        _store = State(initialValue: PackDetailStore(packId: packId))
    }

    var body: some View {
        ScrollView {
            if let detail = store.detail {
                content(detail)
            } else {
                ProgressView()
                    .controlSize(.large)
                    .padding(.top, 120)
            }
        }
        .background(Brand.background.ignoresSafeArea())
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                menu
            }
        }
        .task {
            await store.watch()
        }
        .task(id: store.previewKey) {
            await store.loadPreview()
        }
        .onChange(of: store.isGone) { _, gone in
            if gone { dismiss() }
        }
        .sheet(item: $shareItem) { item in
            ShareSheet(url: item.url)
                .presentationDetents([.medium, .large])
        }
        .sheet(item: $activeSheet) { which in
            switch which {
            case .edit:
                if let pack = store.detail?.pack {
                    PackInfoEditor(store: store, initialTitle: pack.title, initialDescription: pack.blurb ?? "")
                }
            case .groups:
                PackGroupsSheet(store: store)
            case .combine:
                if let pack = store.detail?.pack {
                    CombineSheet(store: store, packTitle: pack.title)
                }
            }
        }
    }

    private var isArchived: Bool {
        store.detail?.pack.archived ?? false
    }

    private var hasLink: Bool {
        store.detail?.pack.sourceUrl != nil
    }

    private var menu: some View {
        Menu {
            Button {
                activeSheet = .edit
            } label: {
                Label("detail.edit", systemImage: "pencil")
            }
            Button {
                activeSheet = .groups
            } label: {
                Label("groups.title", systemImage: "folder")
            }
            Button {
                activeSheet = .combine
            } label: {
                Label("detail.combine", systemImage: "plus.square.on.square")
            }
            Button {
                Task { await store.setArchived(!isArchived) }
            } label: {
                Label(
                    L10n.tr(isArchived ? "detail.unarchive" : "detail.archive"),
                    systemImage: isArchived ? "tray.and.arrow.up" : "archivebox"
                )
            }
            if hasLink {
                Button {
                    Task { await store.removeLink() }
                } label: {
                    Label("detail.link.remove", systemImage: "minus.circle")
                }
            }
            Divider()
            Button {
                Task { await exportPack() }
            } label: {
                Label("detail.export", systemImage: "square.and.arrow.up")
            }
            Button {
                confirmReset = true
            } label: {
                Label("detail.reset", systemImage: "arrow.counterclockwise")
            }
            Button(role: .destructive) {
                confirmDelete = true
            } label: {
                Label("detail.delete", systemImage: "trash")
            }
        } label: {
            Image(systemName: "ellipsis.circle")
        }
        .disabled(store.detail == nil)
        .accessibilityLabel("detail.menu")
        .confirmationDialog("detail.reset.title", isPresented: $confirmReset, titleVisibility: .visible) {
            Button("detail.reset.confirm", role: .destructive) {
                Task { await store.resetProgress() }
            }
            Button("common.cancel", role: .cancel) {}
        } message: {
            Text("detail.reset.message")
        }
        .confirmationDialog("detail.delete.title", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("detail.delete.confirm", role: .destructive) {
                Task { await store.delete() }
            }
            Button("common.cancel", role: .cancel) {}
        } message: {
            Text("detail.delete.message")
        }
    }

    private func content(_ detail: PackDetailInfo) -> some View {
        let style = PackStyle(hex: detail.pack.colorHex)
        return VStack(spacing: 28) {
            if detail.pack.archived {
                archivedNotice
            }
            PackHero(
                detail: detail,
                style: style,
                preview: store.preview,
                previewLoaded: store.previewLoaded
            ) {
                model.beginStudy(.learn(pack: detail.pack))
            }
            practiceSection(detail, style: style)
            sectionsSection(detail, style: style)
            browseRow(detail, style: style)
            if let url = detail.pack.sourceUrl {
                LinkedPackCard(url: url, edited: detail.pack.edited)
            }
            PackAboutCard(detail: detail)
        }
        .padding(.horizontal, 16)
        .padding(.top, 4)
        .padding(.bottom, 40)
    }

    private var archivedNotice: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "archivebox.fill")
                .foregroundStyle(Brand.flame)
            Text("detail.archived.notice")
                .font(.brand(.footnote))
                .foregroundStyle(Brand.text)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Brand.amber.opacity(0.2), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    @ViewBuilder
    private func practiceSection(_ detail: PackDetailInfo, style: PackStyle) -> some View {
        if detail.pack.itemCount > 0 {
            VStack(spacing: 12) {
                SectionTitle("detail.practice")
                VStack(spacing: 0) {
                    ForEach(Array(practiceKinds(detail).enumerated()), id: \.element) { index, kind in
                        if index > 0 {
                            Divider().padding(.leading, 68)
                        }
                        Button {
                            model.beginStudy(.practice(pack: detail.pack, filter: kind))
                        } label: {
                            PracticeRow(kind: kind, style: style)
                        }
                        .buttonStyle(PressableStyle())
                    }
                }
                .card(radius: 22)
            }
        }
    }

    private func practiceKinds(_ detail: PackDetailInfo) -> [PracticeKind] {
        var kinds: [PracticeKind] = [.all]
        for kind in [PracticeKind.flashcards, .quiz, .typing] where detail.practice.contains(kind) {
            kinds.append(kind)
        }
        return kinds
    }

    @ViewBuilder
    private func sectionsSection(_ detail: PackDetailInfo, style: PackStyle) -> some View {
        let visible = detail.sections.filter { $0.itemCount > 0 }
        let showsSections = visible.count > 1 || visible.contains { !$0.isUngrouped }
        if showsSections {
            VStack(spacing: 12) {
                SectionTitle("detail.sections")
                ForEach(visible) { section in
                    Button {
                        studySection(section, in: detail)
                    } label: {
                        SectionRow(section: section, style: style)
                    }
                    .buttonStyle(PressableStyle())
                }
            }
        }
    }

    private func browseRow(_ detail: PackDetailInfo, style: PackStyle) -> some View {
        NavigationLink {
            ItemsBrowserView(packId: detail.pack.id, title: detail.pack.title, sections: detail.sections)
        } label: {
            HStack(spacing: 14) {
                Image(systemName: "list.bullet.rectangle.portrait.fill")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(style.accentText)
                    .frame(width: 40, height: 40)
                    .background(style.base.opacity(0.14), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text("detail.browse")
                        .font(.brand(.headline, weight: .bold))
                        .foregroundStyle(Brand.text)
                    Text(L10n.plural("items", detail.pack.itemCount))
                        .font(.brand(.footnote))
                        .foregroundStyle(Brand.textSecondary)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Brand.textSecondary.opacity(0.6))
            }
            .padding(14)
            .card(radius: 22)
        }
        .buttonStyle(PressableStyle())
    }

    private func studySection(_ section: SectionInfo, in detail: PackDetailInfo) {
        let learn = StudyRequest.learn(pack: detail.pack, sectionId: section.id)
        Task {
            let preview = await store.sessionPreview(for: learn)
            if preview.totalCount > 0 {
                model.beginStudy(learn)
            } else {
                model.beginStudy(.practice(pack: detail.pack, filter: .all, sectionId: section.id))
            }
        }
    }

    private func exportPack() async {
        if let url = await store.exportFile() {
            shareItem = ShareItem(url: url)
        }
    }
}

private struct PackHero: View {
    let detail: PackDetailInfo
    let style: PackStyle
    let preview: SessionPreviewInfo
    let previewLoaded: Bool
    let onLearn: () -> Void

    private var pack: PackInfo { detail.pack }
    private var canLearn: Bool { !previewLoaded || preview.totalCount > 0 }

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(alignment: .center, spacing: 14) {
                Text(pack.icon ?? "📚")
                    .font(.system(size: 40))
                    .frame(width: 72, height: 72)
                    .background(style.onBase.opacity(0.16), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                VStack(alignment: .leading, spacing: 4) {
                    Text(pack.title)
                        .font(.brand(.title2, weight: .bold))
                        .lineLimit(3)
                    if let blurb = pack.blurb, !blurb.isEmpty {
                        Text(blurb)
                            .font(.brand(.footnote))
                            .opacity(0.85)
                            .lineLimit(3)
                    }
                }
                Spacer(minLength: 0)
            }
            HStack(spacing: 8) {
                statPill(value: pack.newCount, key: "stat.new")
                statPill(value: pack.learningCount, key: "stat.learning")
                statPill(value: pack.dueCount, key: "stat.due")
                statPill(value: pack.masteredCount, key: "stat.mastered")
            }
            ProgressBar(value: pack.progress, tint: style.onBase, track: style.onBase.opacity(0.22), height: 8)
            Button(action: onLearn) {
                VStack(spacing: 2) {
                    Label(L10n.tr(learnTitleKey), systemImage: "play.fill")
                    Text(learnSubtitle)
                        .font(.brand(.caption, weight: .medium))
                        .opacity(0.8)
                }
            }
            .buttonStyle(
                BrandButtonStyle(fill: style.onBase, foreground: style.isLightBase ? style.base : style.deep)
            )
            .disabled(!canLearn)
        }
        .padding(20)
        .foregroundStyle(style.onBase)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(style.gradient, in: RoundedRectangle(cornerRadius: 30, style: .continuous))
        .shadow(color: style.base.opacity(0.3), radius: 18, x: 0, y: 10)
    }

    private var learnTitleKey: String {
        canLearn ? "detail.learn" : "detail.nothingdue"
    }

    private var learnSubtitle: String {
        guard previewLoaded else { return " " }
        if preview.totalCount == 0 { return L10n.tr("detail.learn.empty") }
        return L10n.format("detail.learn.subtitle", preview.dueCount, preview.newCount)
    }

    private func statPill(value: Int, key: LocalizedStringKey) -> some View {
        VStack(spacing: 2) {
            Text(String(value))
                .font(.brand(.title3, weight: .bold))
                .monospacedDigit()
            Text(key)
                .font(.brand(.caption2, weight: .semibold))
                .opacity(0.85)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(style.onBase.opacity(0.14), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

private struct PracticeRow: View {
    let kind: PracticeKind
    let style: PackStyle

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: symbol)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(style.accentText)
                .frame(width: 40, height: 40)
                .background(style.base.opacity(0.14), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(L10n.tr(titleKey))
                    .font(.brand(.subheadline, weight: .bold))
                    .foregroundStyle(Brand.text)
                Text(L10n.tr(titleKey + ".subtitle"))
                    .font(.brand(.caption))
                    .foregroundStyle(Brand.textSecondary)
                    .multilineTextAlignment(.leading)
            }
            Spacer(minLength: 0)
            Image(systemName: "play.circle.fill")
                .font(.system(size: 24))
                .foregroundStyle(style.accentText)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }

    private var symbol: String {
        switch kind {
        case .all: return "shuffle"
        case .flashcards: return "rectangle.on.rectangle.angled"
        case .quiz: return "checklist"
        case .typing: return "keyboard"
        }
    }

    private var titleKey: String {
        switch kind {
        case .all: return "practice.all"
        case .flashcards: return "practice.flashcards"
        case .quiz: return "practice.quiz"
        case .typing: return "practice.typing"
        }
    }
}

private struct SectionRow: View {
    let section: SectionInfo
    let style: PackStyle

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(section.isUngrouped ? L10n.tr("section.ungrouped") : section.title)
                        .font(.brand(.subheadline, weight: .bold))
                        .foregroundStyle(Brand.text)
                        .multilineTextAlignment(.leading)
                    Text(subtitle)
                        .font(.brand(.caption))
                        .foregroundStyle(Brand.textSecondary)
                }
                Spacer(minLength: 0)
                Text(L10n.format("pack.progress.percent", Int((section.progress * 100).rounded())))
                    .font(.brand(.caption, weight: .bold))
                    .foregroundStyle(style.accentText)
                    .monospacedDigit()
                Image(systemName: "play.circle.fill")
                    .font(.system(size: 24))
                    .foregroundStyle(style.accentText)
            }
            ProgressBar(value: section.progress, tint: style.accentText, track: style.base.opacity(0.18), height: 6)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 20)
    }

    private var subtitle: String {
        var text = L10n.plural("items", section.itemCount)
        if section.dueCount > 0 {
            text += " · " + L10n.plural("pack.badge.due", section.dueCount)
        }
        return text
    }
}

private struct LinkedPackCard: View {
    let url: String
    let edited: Bool
    @Environment(AppModel.self) private var model
    @State private var isLoading = false
    @State private var showsFailure = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionTitle("link.title")
            VStack(alignment: .leading, spacing: 12) {
                Text(url)
                    .font(.brand(.subheadline, weight: .semibold))
                    .foregroundStyle(Brand.text)
                    .lineLimit(1)
                    .truncationMode(.middle)
                if edited {
                    Text("link.edited")
                        .font(.brand(.footnote))
                        .foregroundStyle(Brand.textSecondary)
                }
                Button {
                    update()
                } label: {
                    updateLabel
                }
                .buttonStyle(SoftButtonStyle())
                .disabled(isLoading)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .card(radius: 22)
        }
        .alert("import.failed.title", isPresented: $showsFailure) {
            Button("common.ok", role: .cancel) {}
        } message: {
            Text("import.error.download")
        }
    }

    @ViewBuilder
    private var updateLabel: some View {
        if isLoading {
            ProgressView()
        } else {
            Label("link.update", systemImage: "arrow.triangle.2.circlepath")
        }
    }

    private func update() {
        isLoading = true
        Task {
            let text = await model.downloadPack(from: url)
            isLoading = false
            if let text {
                model.presentLinkedImport(text: text, url: url)
            } else {
                showsFailure = true
            }
        }
    }
}

private struct PackAboutCard: View {
    let detail: PackDetailInfo

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionTitle("detail.about")
            VStack(alignment: .leading, spacing: 12) {
                if let author = detail.author, !author.isEmpty {
                    row("about.author", value: author)
                }
                if let license = detail.license, !license.isEmpty {
                    row("about.license", value: license)
                }
                if detail.revision > 0 {
                    row("about.revision", value: String(detail.revision))
                }
                row("about.perSession", value: String(detail.newPerSession))
                row("about.order", value: L10n.tr(detail.randomOrder ? "about.order.random" : "about.order.sequential"))
                if let source = detail.source, !source.isEmpty {
                    sourceRow(source)
                }
                if !detail.tags.isEmpty {
                    FlowLayout(spacing: 6, lineSpacing: 6) {
                        ForEach(detail.tags, id: \.self) { tag in
                            Chip(text: tag, tint: Brand.textSecondary)
                        }
                    }
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .card(radius: 22)
        }
    }

    private func row(_ key: LocalizedStringKey, value: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(key)
                .font(.brand(.subheadline))
                .foregroundStyle(Brand.textSecondary)
            Spacer(minLength: 12)
            Text(value)
                .font(.brand(.subheadline, weight: .semibold))
                .foregroundStyle(Brand.text)
                .multilineTextAlignment(.trailing)
        }
    }

    @ViewBuilder
    private func sourceRow(_ source: String) -> some View {
        if let url = URL(string: source), url.scheme == "https" || url.scheme == "http" {
            HStack(alignment: .firstTextBaseline) {
                Text("about.source")
                    .font(.brand(.subheadline))
                    .foregroundStyle(Brand.textSecondary)
                Spacer(minLength: 12)
                Link(destination: url) {
                    Text(source)
                        .font(.brand(.subheadline, weight: .semibold))
                        .lineLimit(1)
                        .truncationMode(.middle)
                }
            }
        } else {
            row("about.source", value: source)
        }
    }
}
