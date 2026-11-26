import Foundation
import Observation
import SwiftUI

enum AppTab: Hashable {
    case library
    case stats
    case settings
}

struct PackRoute: Hashable {
    let id: String
}

struct LinkedPackText {
    let text: String
    let url: String
}

struct AddPackRoute: Identifiable {
    let id = UUID()
    var incomingURL: URL?
    var linked: LinkedPackText?
}

enum CatalogPhase: Sendable {
    case idle
    case loading
    case loaded
    case failed
}

struct ContinueTarget {
    let pack: PackInfo
    let count: Int
    let isDue: Bool
}

@MainActor
@Observable
final class AppModel {
    private(set) var packs: [PackInfo] = []
    private(set) var packsLoaded = false
    private(set) var groups: [PackGroupInfo] = []
    private(set) var stats: StatsInfo = .empty
    private(set) var settings: SettingsInfo = .initial
    private(set) var settingsLoaded = false
    private(set) var samples: [SamplePackInfo] = []
    private(set) var library: [SamplePackInfo] = []
    private(set) var catalogPhase: CatalogPhase = .idle

    let sync = SyncModel()

    var selectedTab: AppTab = .library
    var studyRequest: StudyRequest?
    var addPackRoute: AddPackRoute?
    var libraryPath: [PackRoute] = []

    private let client = SDKClient.shared
    @ObservationIgnored private var watchers: [Task<Void, Never>] = []
    @ObservationIgnored private var writeChain: Task<Void, Never>?
    @ObservationIgnored private var pendingWrites = 0
    @ObservationIgnored private var pendingScreenshot = StoreScreenshot.requested
    @ObservationIgnored private var seeding = false

    var showOnboarding: Bool {
        settingsLoaded && !settings.onboardingSeen
    }

    var preferredScheme: ColorScheme? {
        switch settings.theme {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }

    var dailyGoalSelection: Int {
        get { settings.dailyGoal }
        set { setDailyGoal(newValue) }
    }

    var sessionSizeSelection: Int {
        get { settings.defaultSessionSize }
        set { setSessionSize(newValue) }
    }

    var typoToleranceSelection: Bool {
        get { settings.typoTolerance }
        set { setTypoTolerance(newValue) }
    }

    var themeSelection: ThemeChoice {
        get { settings.theme }
        set { setTheme(newValue) }
    }

    var activePacks: [PackInfo] {
        packs.filter { !$0.archived }
    }

    var archivedPacks: [PackInfo] {
        packs.filter { $0.archived }
    }

    var continueTarget: ContinueTarget? {
        let active = activePacks
        let due = active.filter { $0.dueCount > 0 }
        if let best = due.max(by: { $0.dueCount < $1.dueCount }) {
            return ContinueTarget(pack: best, count: best.dueCount, isDue: true)
        }
        if let fresh = active.first(where: { $0.newCount > 0 }) {
            return ContinueTarget(pack: fresh, count: fresh.newCount, isDue: false)
        }
        return nil
    }

    func start() {
        guard watchers.isEmpty, !seeding else { return }
        if pendingScreenshot != nil {
            seeding = true
            Task {
                await client.seedDemoLibrary()
                seeding = false
                watch()
            }
            return
        }
        watch()
    }

    private func watch() {
        let client = self.client
        samples = client.samplePacks()
        library = samples
        let packsTask = Task {
            await client.watchPacks { [weak self] packs in
                self?.applyPacks(packs)
            }
        }
        let groupsTask = Task {
            await client.watchGroups { [weak self] groups in
                self?.applyGroups(groups)
            }
        }
        let statsTask = Task {
            await client.watchStats { [weak self] stats in
                self?.applyStats(stats)
            }
        }
        let settingsTask = Task {
            await client.watchSettings { [weak self] settings in
                self?.applySettings(settings)
            }
        }
        watchers = [packsTask, groupsTask, statsTask, settingsTask]
        sync.start()
    }

    func refresh() {
        client.refresh()
    }

    func beginStudy(_ request: StudyRequest) {
        studyRequest = request
    }

    func continueLearning() {
        guard let target = continueTarget else { return }
        studyRequest = .learn(pack: target.pack)
    }

    func presentAddPack() {
        addPackRoute = AddPackRoute()
    }

    func presentLinkedImport(text: String, url: String) {
        addPackRoute = AddPackRoute(linked: LinkedPackText(text: text, url: url))
    }

    func handleIncoming(_ url: URL) {
        guard url.isFileURL else { return }
        addPackRoute = AddPackRoute(incomingURL: url)
    }

    func completeOnboarding() {
        settings.onboardingSeen = true
        enqueueWrite { client in
            await client.setOnboardingSeen(true)
        }
    }

    func setDailyGoal(_ goal: Int) {
        let value = min(max(goal, 1), 1000)
        guard value != settings.dailyGoal else { return }
        settings.dailyGoal = value
        enqueueWrite { client in
            await client.setDailyGoal(value)
        }
    }

    func setSessionSize(_ size: Int) {
        let value = min(max(size, 1), 500)
        guard value != settings.defaultSessionSize else { return }
        settings.defaultSessionSize = value
        enqueueWrite { client in
            await client.setDefaultSessionSize(value)
        }
    }

    func setTypoTolerance(_ enabled: Bool) {
        guard enabled != settings.typoTolerance else { return }
        settings.typoTolerance = enabled
        enqueueWrite { client in
            await client.setTypoTolerance(enabled)
        }
    }

    func setTheme(_ theme: ThemeChoice) {
        guard theme != settings.theme else { return }
        settings.theme = theme
        enqueueWrite { client in
            await client.setTheme(theme)
        }
    }

    func importSample(_ sample: SamplePackInfo) async -> ImportOutcome {
        guard let url = sample.downloadURL else {
            return await client.importSample(id: sample.id)
        }
        guard let text = await RemoteText.fetch(url) else {
            return .failure(ImportIssue(kind: .downloadFailed, message: ""))
        }
        return await client.importPack(text: text, allowDowngrade: false)
    }

    func resolveLink(_ input: String) -> String? {
        client.resolvePackURL(input)
    }

    func downloadPack(from link: String) async -> String? {
        guard let url = URL(string: link) else { return nil }
        return await RemoteText.fetch(url)
    }

    func createGroup(_ name: String, adding packId: String? = nil) async {
        guard let groupId = await client.createGroup(name) else { return }
        if let packId {
            await client.setPackInGroup(packId, groupId: groupId, member: true)
        }
    }

    func renameGroup(_ groupId: String, to name: String) async {
        await client.renameGroup(groupId, name: name)
    }

    func deleteGroup(_ groupId: String) async {
        await client.deleteGroup(groupId)
    }

    func setPack(_ packId: String, inGroup groupId: String, member: Bool) async {
        await client.setPackInGroup(packId, groupId: groupId, member: member)
    }

    func loadCatalog() async {
        guard catalogPhase != .loading, let url = client.catalogURL() else { return }
        catalogPhase = .loading
        guard let text = await RemoteText.fetch(url), let packs = client.readCatalog(text: text) else {
            catalogPhase = .failed
            return
        }
        library = packs
        catalogPhase = .loaded
    }

    func authoringPrompt(kinds: Set<ItemKind>, figures: Bool) -> String {
        client.authoringPrompt(types: ItemKind.allCases.filter { kinds.contains($0) }.map(\.code), figures: figures)
    }

    private func applyPacks(_ value: [PackInfo]) {
        packs = value
        packsLoaded = true
        openScreenshot()
    }

    private func openScreenshot() {
        guard let shot = pendingScreenshot, !packs.isEmpty else { return }
        pendingScreenshot = nil
        let pack = packs.first { $0.id == shot.packId }
        switch shot.screen {
        case "stats":
            selectedTab = .stats
        case "settings":
            selectedTab = .settings
        case "add":
            presentAddPack()
        case "pack":
            if let pack {
                libraryPath = [PackRoute(id: pack.id)]
            }
        case "study":
            if let pack {
                studyRequest = .practice(pack: pack, filter: shot.filter, sectionId: shot.sectionId)
            }
        default:
            break
        }
    }

    private func applyGroups(_ value: [PackGroupInfo]) {
        groups = value
    }

    private func applyStats(_ value: StatsInfo) {
        stats = value
    }

    private func applySettings(_ value: SettingsInfo) {
        settingsLoaded = true
        guard pendingWrites == 0 else { return }
        settings = value
    }

    private func enqueueWrite(_ operation: @escaping @Sendable (SDKClient) async -> Void) {
        pendingWrites += 1
        let previous = writeChain
        writeChain = Task { [weak self, client = self.client] in
            await previous?.value
            await operation(client)
            self?.finishWrite()
        }
    }

    private func finishWrite() {
        pendingWrites -= 1
        if pendingWrites == 0 {
            client.refresh()
        }
    }
}
