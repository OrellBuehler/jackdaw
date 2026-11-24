import SwiftUI

struct SettingsView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    StudySettingsCard(model: model)
                    AppearanceSettingsCard(model: model)
                    SyncSettingsCard(sync: model.sync)
                    AboutCard()
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 32)
            }
            .background(Brand.background.ignoresSafeArea())
            .navigationTitle("settings.title")
        }
    }
}

private struct StudySettingsCard: View {
    @Bindable var model: AppModel

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            SettingsHeader(symbol: "target", tint: Brand.accent, key: "settings.study")
            Stepper(value: $model.dailyGoalSelection, in: 1 ... 1000, step: 5) {
                SettingsValueRow(key: "settings.goal", value: String(model.dailyGoalSelection))
            }
            Text("settings.goal.footer")
                .font(.brand(.footnote))
                .foregroundStyle(Brand.textSecondary)
            Divider()
            Stepper(value: $model.sessionSizeSelection, in: 1 ... 500, step: 5) {
                SettingsValueRow(key: "settings.session", value: String(model.sessionSizeSelection))
            }
            Text("settings.session.footer")
                .font(.brand(.footnote))
                .foregroundStyle(Brand.textSecondary)
            Divider()
            Toggle("settings.typos", isOn: $model.typoToleranceSelection)
                .font(.brand(.body, weight: .medium))
                .tint(Brand.accent)
            Text("settings.typos.footer")
                .font(.brand(.footnote))
                .foregroundStyle(Brand.textSecondary)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 24)
    }
}

private struct AppearanceSettingsCard: View {
    @Bindable var model: AppModel

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            SettingsHeader(symbol: "paintbrush.fill", tint: Brand.flame, key: "settings.appearance")
            Picker("settings.theme", selection: $model.themeSelection) {
                Text("settings.theme.system").tag(ThemeChoice.system)
                Text("settings.theme.light").tag(ThemeChoice.light)
                Text("settings.theme.dark").tag(ThemeChoice.dark)
            }
            .pickerStyle(.segmented)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 24)
    }
}

private struct SyncSettingsCard: View {
    @Bindable var sync: SyncModel

    private var status: SyncStatus { sync.status }

    private var isUsable: Bool {
        status.isEnabled && status.availability == .available
    }

    private var stateKey: String {
        switch status.availability {
        case .unsupported: return "sync.state.unsupported"
        case .accountChanged: return "sync.state.accountChanged"
        default: break
        }
        if !status.isEnabled { return "sync.state.off" }
        switch status.availability {
        case .checking: return "sync.state.checking"
        case .available: return status.isSyncing ? "sync.state.syncing" : "sync.state.on"
        case .noAccount: return "sync.state.noAccount"
        case .restricted: return "sync.state.restricted"
        case .temporarilyUnavailable: return "sync.state.temporary"
        case .couldNotDetermine: return "sync.state.undetermined"
        case .notAuthorized: return "sync.state.notAuthorized"
        case .unsupported, .accountChanged: return "sync.state.unsupported"
        }
    }

    private var lastSyncedText: String {
        let value = status.lastSyncedAt.map { $0.formatted(date: .abbreviated, time: .shortened) } ?? L10n.tr("sync.never")
        return L10n.format("sync.last", value)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            SettingsHeader(symbol: "icloud.fill", tint: Brand.accent, key: "sync.title")
            Toggle(isOn: $sync.isEnabled) {
                Text("sync.toggle")
                    .font(.brand(.body, weight: .medium))
                    .foregroundStyle(Brand.text)
            }
            .disabled(status.availability == .unsupported)
            Text(L10n.tr(stateKey))
                .font(.brand(.subheadline, weight: .semibold))
                .foregroundStyle(isUsable ? Brand.accent : Brand.textSecondary)
            if isUsable {
                Text(lastSyncedText)
                    .font(.brand(.footnote))
                    .foregroundStyle(Brand.textSecondary)
                if status.pendingCount > 0 {
                    Text(L10n.plural("sync.pending", status.pendingCount))
                        .font(.brand(.footnote))
                        .foregroundStyle(Brand.textSecondary)
                }
                Button {
                    sync.syncNow()
                } label: {
                    HStack(spacing: 8) {
                        if status.isSyncing {
                            ProgressView()
                        }
                        Text("sync.now")
                    }
                }
                .buttonStyle(.bordered)
                .disabled(status.isSyncing)
            }
            if let error = status.lastError {
                VStack(alignment: .leading, spacing: 6) {
                    Text("sync.error.title")
                        .font(.brand(.footnote, weight: .bold))
                        .foregroundStyle(Brand.text)
                    Text(error)
                        .font(.system(.caption, design: .monospaced))
                        .foregroundStyle(Brand.textSecondary)
                        .textSelection(.enabled)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            if !status.log.isEmpty {
                DisclosureGroup("sync.log.title") {
                    Text(status.log.joined(separator: "\n"))
                        .font(.system(.caption2, design: .monospaced))
                        .foregroundStyle(Brand.textSecondary)
                        .textSelection(.enabled)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.top, 6)
                }
                .font(.brand(.footnote, weight: .bold))
                .tint(Brand.textSecondary)
            }
            Text("sync.footer")
                .font(.brand(.footnote))
                .foregroundStyle(Brand.textSecondary)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 24)
    }
}

private struct SettingsHeader: View {
    let symbol: String
    let tint: Color
    let key: LocalizedStringKey

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: symbol)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(tint)
                .frame(width: 32, height: 32)
                .background(tint.opacity(0.14), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            Text(key)
                .font(.brand(.headline, weight: .bold))
                .foregroundStyle(Brand.text)
        }
    }
}

private struct SettingsValueRow: View {
    let key: LocalizedStringKey
    let value: String

    var body: some View {
        HStack {
            Text(key)
                .font(.brand(.body, weight: .medium))
                .foregroundStyle(Brand.text)
            Spacer(minLength: 8)
            Text(value)
                .font(.brand(.body, weight: .bold))
                .foregroundStyle(Brand.accent)
                .monospacedDigit()
        }
    }
}

private struct AboutCard: View {
    private var versionText: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "1.0"
        let build = info?["CFBundleVersion"] as? String ?? "1"
        return version + " (" + build + ")"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 14) {
                Mascot(size: 56)
                VStack(alignment: .leading, spacing: 2) {
                    Text("settings.about.name")
                        .font(.brand(.title3, weight: .bold))
                        .foregroundStyle(Brand.text)
                    Text(L10n.format("settings.about.version", versionText))
                        .font(.brand(.footnote))
                        .foregroundStyle(Brand.textSecondary)
                }
            }
            Label("settings.about.local", systemImage: "lock.shield.fill")
                .font(.brand(.subheadline))
                .foregroundStyle(Brand.text)
                .labelStyle(AboutLabelStyle())
            Label("settings.about.format", systemImage: "doc.text.fill")
                .font(.brand(.subheadline))
                .foregroundStyle(Brand.text)
                .labelStyle(AboutLabelStyle())
            if let url = URL(string: "https://github.com/OrellBuehler/jackdaw/tree/main/format") {
                Link(destination: url) {
                    Label("settings.about.spec", systemImage: "arrow.up.right.square")
                        .font(.brand(.subheadline, weight: .semibold))
                }
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 24)
    }
}

private struct AboutLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(alignment: .top, spacing: 10) {
            configuration.icon
                .foregroundStyle(Brand.accent)
                .frame(width: 22)
            configuration.title
        }
    }
}
