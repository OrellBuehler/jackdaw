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

    func authoringPrompt() -> String {
        sdk.authoringPrompt()
    }

    func previewImport(text: String) async -> PreviewOutcome {
        do {
            let preview = try await sdk.previewImport(text: text)
            return ImportAdapter.previewOutcome(preview)
        } catch {
            return .invalid(ImportIssue(kind: .other, message: error.localizedDescription))
        }
    }

    func importPack(text: String, allowDowngrade: Bool) async -> ImportOutcome {
        do {
            let result: ImportResult
            if allowDowngrade {
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
