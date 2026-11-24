@preconcurrency import shared
import Foundation

final class SDKClient: @unchecked Sendable {
    static let shared = SDKClient()

    private let sdk: JackdawSdk
    let syncBridge: SyncBridge

    private init() {
        let sdk = JackdawSdk(driverFactory: DatabaseDriverFactory())
        self.sdk = sdk
        syncBridge = SyncBridge(sync: sdk.sync)
    }

    func watchPacks(deliver: @MainActor @Sendable ([PackInfo]) -> Void) async {
        for await packs in sdk.observePacks() {
            let mapped = packs.map { Mapping.pack($0) }
            await deliver(mapped)
        }
    }

    func watchPack(_ packId: String, deliver: @MainActor @Sendable (PackDetailInfo?) -> Void) async {
        for await detail in sdk.observePack(packId: packId) {
            let mapped = detail.map { Mapping.detail($0) }
            await deliver(mapped)
        }
    }

    func watchGroups(deliver: @MainActor @Sendable ([PackGroupInfo]) -> Void) async {
        for await groups in sdk.observeGroups() {
            let mapped = groups.map { Mapping.group($0) }
            await deliver(mapped)
        }
    }

    func watchItems(_ packId: String, deliver: @MainActor @Sendable ([ItemInfo]) -> Void) async {
        for await items in sdk.observeItems(packId: packId) {
            let mapped = items.map { Mapping.item($0) }
            await deliver(mapped)
        }
    }

    func watchStats(deliver: @MainActor @Sendable (StatsInfo) -> Void) async {
        for await stats in sdk.observeStats() {
            let mapped = Mapping.stats(stats)
            await deliver(mapped)
        }
    }

    func watchSettings(deliver: @MainActor @Sendable (SettingsInfo) -> Void) async {
        for await settings in sdk.observeSettings() {
            let mapped = Mapping.settings(settings)
            await deliver(mapped)
        }
    }

    func refresh() {
        sdk.refresh()
    }

    func samplePacks() -> [SamplePackInfo] {
        sdk.samplePacks().map { Mapping.sample($0) }
    }

    func catalogURL() -> URL? {
        URL(string: sdk.catalogUrl())
    }

    func readCatalog(text: String) -> [SamplePackInfo]? {
        sdk.readCatalog(text: text)?.map { Mapping.sample($0) }
    }

    func authoringPrompt(types: [String], figures: Bool) -> String {
        sdk.authoringPrompt(types: types, figures: figures)
    }

    func previewImport(text: String) async -> PreviewOutcome {
        do {
            let preview = try await sdk.previewImport(text: text)
            return ImportAdapter.previewOutcome(preview)
        } catch {
            return .invalid(ImportIssue(kind: .other, message: error.localizedDescription))
        }
    }

    func resolvePackURL(_ input: String) -> String? {
        sdk.resolvePackUrl(input: input)
    }

    func importPack(text: String, allowDowngrade: Bool, sourceURL: String? = nil) async -> ImportOutcome {
        do {
            let result: ImportResult
            if let sourceURL {
                result = try await sdk.importPackFromUrl(text: text, url: sourceURL, allowDowngrade: allowDowngrade)
            } else if allowDowngrade {
                result = try await sdk.importPackAllowingDowngrade(text: text)
            } else {
                result = try await sdk.importPack(text: text)
            }
            return ImportAdapter.outcome(result)
        } catch {
            return .failure(ImportIssue(kind: .other, message: error.localizedDescription))
        }
    }

    func importSample(id: String) async -> ImportOutcome {
        do {
            let result = try await sdk.importSample(sampleId: id)
            return ImportAdapter.outcome(result)
        } catch {
            return .failure(ImportIssue(kind: .other, message: error.localizedDescription))
        }
    }

    func deletePack(_ packId: String) async {
        do {
            try await sdk.deletePack(packId: packId)
        } catch {}
    }

    func resetProgress(_ packId: String) async {
        do {
            try await sdk.resetProgress(packId: packId)
        } catch {}
    }

    func exportPack(_ packId: String) async -> String? {
        do {
            return try await sdk.exportPack(packId: packId)
        } catch {
            return nil
        }
    }

    func setPackArchived(_ packId: String, archived: Bool) async {
        do {
            try await sdk.setPackArchived(packId: packId, archived: archived)
        } catch {}
    }

    func removePackURL(_ packId: String) async {
        do {
            try await sdk.removePackUrl(packId: packId)
        } catch {}
    }

    func createGroup(_ name: String) async -> String? {
        do {
            return try await sdk.createGroup(name: name)
        } catch {
            return nil
        }
    }

    func renameGroup(_ groupId: String, name: String) async {
        do {
            try await sdk.renameGroup(groupId: groupId, name: name)
        } catch {}
    }

    func deleteGroup(_ groupId: String) async {
        do {
            try await sdk.deleteGroup(groupId: groupId)
        } catch {}
    }

    func setPackInGroup(_ packId: String, groupId: String, member: Bool) async {
        do {
            try await sdk.setPackInGroup(packId: packId, groupId: groupId, member: member)
        } catch {}
    }

    func newItemSource() -> String {
        sdk.newItemSource()
    }

    func itemSource(_ packId: String, itemId: String) async -> String? {
        do {
            return try await sdk.itemSource(packId: packId, itemId: itemId)
        } catch {
            return nil
        }
    }

    func saveItem(_ packId: String, itemId: String?, json: String) async -> ImportOutcome {
        do {
            let result = try await sdk.saveItem(packId: packId, itemId: itemId, json: json)
            return ImportAdapter.outcome(result)
        } catch {
            return .failure(ImportIssue(kind: .other, message: error.localizedDescription))
        }
    }

    func deleteItem(_ packId: String, itemId: String) async -> ImportOutcome {
        do {
            let result = try await sdk.deleteItem(packId: packId, itemId: itemId)
            return ImportAdapter.outcome(result)
        } catch {
            return .failure(ImportIssue(kind: .other, message: error.localizedDescription))
        }
    }

    func updatePackInfo(_ packId: String, title: String, description: String) async -> ImportOutcome {
        do {
            let result = try await sdk.updatePackInfo(packId: packId, title: title, descriptionText: description)
            return ImportAdapter.outcome(result)
        } catch {
            return .failure(ImportIssue(kind: .other, message: error.localizedDescription))
        }
    }

    func previewSession(_ request: StudyRequest) async -> SessionPreviewInfo {
        do {
            let preview = try await sdk.previewSession(packId: request.packId, config: Mapping.kotlinConfig(request))
            return SessionPreviewInfo(
                dueCount: Int(preview.dueCount),
                newCount: Int(preview.newCount),
                totalCount: Int(preview.totalCount)
            )
        } catch {
            return .empty
        }
    }

    func startSession(_ request: StudyRequest) async -> SessionDriver? {
        do {
            let session = try await sdk.startSession(packId: request.packId, config: Mapping.kotlinConfig(request))
            return SessionDriver(session: session)
        } catch {
            return nil
        }
    }

    func setDailyGoal(_ goal: Int) async {
        do {
            try await sdk.setDailyGoal(goal: Int32(clamping: goal))
        } catch {}
    }

    func setDefaultSessionSize(_ size: Int) async {
        do {
            try await sdk.setDefaultSessionSize(size: Int32(clamping: size))
        } catch {}
    }

    func setTypoTolerance(_ enabled: Bool) async {
        do {
            try await sdk.setTypoTolerance(enabled: enabled)
        } catch {}
    }

    func setTheme(_ theme: ThemeChoice) async {
        do {
            try await sdk.setTheme(theme: Mapping.kotlinTheme(theme))
        } catch {}
    }

    func setOnboardingSeen(_ seen: Bool) async {
        do {
            try await sdk.setOnboardingSeen(seen: seen)
        } catch {}
    }
}
