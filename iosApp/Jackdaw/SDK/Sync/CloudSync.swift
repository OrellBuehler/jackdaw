import CloudKit
import Foundation
import os

// CloudKit half of iCloud sync: a CKSyncEngine over one custom zone of the private database. It owns no data: what
// changed locally, how fetched records merge and what is persisted all live in the shared module (SyncBridge).
actor CloudSync: CKSyncEngineDelegate {
    static let shared = CloudSync(bridge: SDKClient.shared.syncBridge)

    // Simulator builds (CI, previews) are unsigned and have no iCloud entitlement, where touching CKContainer traps.
    static var isSupported: Bool {
        #if targetEnvironment(simulator)
        return false
        #else
        let environment = ProcessInfo.processInfo.environment
        return environment["XCTestConfigurationFilePath"] == nil && environment["XCODE_RUNNING_FOR_PREVIEWS"] != "1"
        #endif
    }

    private enum Meta {
        static let enabled = "sync.enabled"
        static let account = "sync.account"
        static let accountChanged = "sync.accountChanged"
        static let engineState = "sync.engineState"
        static let packFormat = "sync.packFormat"
        static let lastSyncedAt = "sync.lastSyncedAt"
        static let lastError = "sync.lastError"
    }

    private static let batchLimit = 200
    private static let packsPerBatch = 3
    private static let logLimit = 40
    private static let shownErrors = 5
    private static let logger = Logger(subsystem: "ch.orellbuehler.jackdaw", category: "sync")

    private struct AccountProbe: Sendable {
        let availability: SyncAvailability
        let userName: String?
        let error: String?
    }

    private let bridge: SyncBridge
    private var engine: CKSyncEngine?
    private var started = false
    private var bringingUp = false
    private var enabled = true
    private var status = SyncStatus.initial
    private var listeners: [UUID: AsyncStream<SyncStatus>.Continuation] = [:]
    private var inflight: [String: Int64] = [:]
    private var tempFiles: [String: URL] = [:]
    private var blocked: Set<String> = []
    private var cycleErrors: [String] = []
    private var activeOperations = 0
    private var reconcileTask: Task<Void, Never>?
    private var changesTask: Task<Void, Never>?
    private var accountObserver: NSObjectProtocol?
    private var log: [String] = []

    init(bridge: SyncBridge) {
        self.bridge = bridge
    }

    // MARK: Control

    func updates() -> AsyncStream<SyncStatus> {
        let (stream, continuation) = AsyncStream.makeStream(of: SyncStatus.self, bufferingPolicy: .bufferingNewest(1))
        let id = UUID()
        listeners[id] = continuation
        continuation.onTermination = { _ in
            Task { await self.removeListener(id) }
        }
        continuation.yield(status)
        return stream
    }

    func start() async {
        guard !started else { return }
        started = true
        guard Self.isSupported else {
            status.availability = .unsupported
            publish()
            return
        }
        removeTempDirectory()
        bridge.enableTracking()
        let flag = try? await bridge.meta(Meta.enabled)
        enabled = flag != "0"
        status.isEnabled = enabled
        if let text = try? await bridge.meta(Meta.lastSyncedAt), let seconds = Double(text) {
            status.lastSyncedAt = Date(timeIntervalSince1970: seconds)
        }
        status.lastError = try? await bridge.meta(Meta.lastError)
        if (try? await bridge.meta(Meta.accountChanged)) == "1" {
            status.availability = .accountChanged
        }
        publish()
        changesTask = Task {
            await self.bridge.watchLocalChanges { await self.localChanged() }
        }
        accountObserver = NotificationCenter.default.addObserver(forName: .CKAccountChanged, object: nil, queue: nil) { _ in
            Task { await self.accountNotified() }
        }
        if enabled {
            await bringUp()
        }
    }

    func setEnabled(_ on: Bool) async {
        guard Self.isSupported else { return }
        enabled = on
        status.isEnabled = on
        try? await bridge.setMeta(Meta.enabled, on ? "1" : "0")
        if on {
            if (try? await bridge.meta(Meta.accountChanged)) == "1" {
                // The user confirmed: this device's data is merged into the new account.
                try? await bridge.setMeta(Meta.accountChanged, nil)
                try? await bridge.setMeta(Meta.account, nil)
                try? await bridge.setMeta(Meta.engineState, nil)
            }
            publish()
            await bringUp()
        } else {
            await tearDown()
            publish()
        }
    }

    // Foreground refresh and the "Sync now" button. Nothing here may throw into the UI: errors are shown in the status.
    func syncNow(userInitiated: Bool) async {
        guard Self.isSupported, enabled else { return }
        if userInitiated { blocked = [] }
        if engine == nil { await bringUp() }
        guard let engine else { return }
        status.isSyncing = true
        publish()
        await reconcilePending()
        do {
            try await engine.fetchChanges()
            try await engine.sendChanges()
        } catch {
            await handleOperationError(error)
        }
        if activeOperations == 0 {
            await finishCycle()
        }
    }

    // MARK: Bring up and tear down

    private static func probeAccount() async -> AccountProbe {
        let container = CKContainer(identifier: CloudRecords.containerID)
        do {
            let account = try await container.accountStatus()
            switch account {
            case .available:
                break
            case .noAccount:
                return AccountProbe(availability: .noAccount, userName: nil, error: nil)
            case .restricted:
                return AccountProbe(availability: .restricted, userName: nil, error: nil)
            case .temporarilyUnavailable:
                return AccountProbe(availability: .temporarilyUnavailable, userName: nil, error: nil)
            case .couldNotDetermine:
                return AccountProbe(availability: .couldNotDetermine, userName: nil, error: nil)
            @unknown default:
                return AccountProbe(availability: .couldNotDetermine, userName: nil, error: nil)
            }
            let user = try await container.userRecordID()
            return AccountProbe(availability: .available, userName: user.recordName, error: nil)
        } catch {
            let notAuthorized = (error as? CKError)?.code == .notAuthenticated
            return AccountProbe(
                availability: notAuthorized ? .notAuthorized : .couldNotDetermine,
                userName: nil,
                error: SyncErrorText.describe(error)
            )
        }
    }

    private func bringUp() async {
        guard enabled, engine == nil, !bringingUp else { return }
        bringingUp = true
        defer { bringingUp = false }
        status.availability = .checking
        publish()
        let probe = await Self.probeAccount()
        status.availability = probe.availability
        if let error = probe.error {
            await setLastError("Account check failed:\n" + error)
        }
        let knownAccount = try? await bridge.meta(Meta.account)
        var firstSync = false
        if let userName = probe.userName {
            if let knownAccount, knownAccount != userName {
                await pauseForAccountChange()
                return
            }
            if knownAccount == nil {
                do {
                    try await bridge.setMeta(Meta.account, userName)
                } catch {
                    await setLastError("Saving the account failed:\n" + SyncErrorText.describe(error))
                }
                firstSync = true
            }
        } else {
            // Without a network the account cannot be looked up. A device that synced before starts its engine
            // anyway: the engine stays dormant without an account and reports a switch itself.
            let undetermined = probe.availability == .couldNotDetermine || probe.availability == .temporarilyUnavailable
            guard knownAccount != nil, undetermined else {
                note("no engine: account " + String(describing: probe.availability))
                publish()
                return
            }
        }
        var serialization: CKSyncEngine.State.Serialization?
        let packFormat = bridge.packFormat
        let knownPackFormat = try? await bridge.meta(Meta.packFormat)
        if knownPackFormat != packFormat {
            try? await bridge.setMeta(Meta.engineState, nil)
            try? await bridge.setMeta(Meta.packFormat, packFormat)
        }
        if let text = try? await bridge.meta(Meta.engineState), let data = text.data(using: .utf8) {
            serialization = try? JSONDecoder().decode(CKSyncEngine.State.Serialization.self, from: data)
        }
        if serialization == nil {
            firstSync = true
        }
        let database = CKContainer(identifier: CloudRecords.containerID).privateCloudDatabase
        var configuration = CKSyncEngine.Configuration(database: database, stateSerialization: serialization, delegate: self)
        configuration.automaticallySync = true
        let created = CKSyncEngine(configuration)
        engine = created
        note("engine created" + (serialization == nil ? " without saved state" : "") + (firstSync ? ", first sync" : ""))
        if firstSync {
            created.state.add(pendingDatabaseChanges: [.saveZone(CKRecordZone(zoneID: CloudRecords.zoneID))])
            do {
                try await bridge.queueEverything()
            } catch {
                await setLastError("Queueing local data failed:\n" + SyncErrorText.describe(error))
            }
        }
        await reconcilePending()
        publish()
    }

    private func tearDown() async {
        reconcileTask?.cancel()
        reconcileTask = nil
        let old = engine
        engine = nil
        if old != nil { note("engine stopped") }
        activeOperations = 0
        status.isSyncing = false
        inflight = [:]
        blocked = []
        if let old {
            await old.cancelOperations()
        }
        removeTempDirectory()
    }

    private func pauseForAccountChange() async {
        await tearDown()
        try? await bridge.setMeta(Meta.engineState, nil)
        try? await bridge.clearTracking()
        try? await bridge.setMeta(Meta.accountChanged, "1")
        try? await bridge.setMeta(Meta.enabled, "0")
        enabled = false
        status.isEnabled = false
        status.availability = .accountChanged
        publish()
    }

    // The engine reports sign-in, sign-out and account switches itself. The notification only starts an engine that
    // could not be started before and refreshes what the settings screen shows.
    private func accountNotified() async {
        guard started, enabled, Self.isSupported else { return }
        note("account notification")
        if engine == nil {
            await bringUp()
        } else {
            let probe = await Self.probeAccount()
            status.availability = probe.availability
            publish()
        }
    }

    private func recoverByUploadingEverything(reason: String) async {
        cycleErrors.append("Recovering from: \(reason). Local data is uploaded again.")
        await tearDown()
        try? await bridge.setMeta(Meta.engineState, nil)
        await bringUp()
    }

    // MARK: Pending changes

    private func localChanged() {
        guard engine != nil, reconcileTask == nil else { return }
        reconcileTask = Task {
            try? await Task.sleep(for: .seconds(1))
            await self.runReconcile()
        }
    }

    private func runReconcile() async {
        guard !Task.isCancelled else { return }
        reconcileTask = nil
        await reconcilePending()
    }

    private func reconcilePending() async {
        guard let engine else { return }
        do {
            let names = try await bridge.pending()
            status.pendingCount = names.count
            let changes = names.filter { !blocked.contains($0) }.map {
                CKSyncEngine.PendingRecordZoneChange.saveRecord(CloudRecords.recordID($0))
            }
            if !changes.isEmpty {
                engine.state.add(pendingRecordZoneChanges: changes)
            }
        } catch is CancellationError {
            note("reading pending changes was cancelled")
        } catch {
            await setLastError("Reading pending changes failed:\n" + SyncErrorText.describe(error))
        }
        publish()
    }

    // MARK: CKSyncEngineDelegate

    func handleEvent(_ event: CKSyncEngine.Event, syncEngine: CKSyncEngine) async {
        guard syncEngine === engine else {
            note("ignored event of a stopped engine: " + Self.name(of: event))
            return
        }
        if case .stateUpdate = event {} else { note(Self.name(of: event)) }
        switch event {
        case .stateUpdate(let update):
            await persist(update.stateSerialization)
        case .accountChange(let change):
            await handleAccountChange(change)
        case .fetchedDatabaseChanges(let changes):
            await handleDatabaseChanges(changes)
        case .fetchedRecordZoneChanges(let changes):
            await handleFetched(changes)
        case .sentDatabaseChanges(let sent):
            if !sent.savedZones.isEmpty { note("zone saved") }
            for failed in sent.failedZoneSaves {
                cycleErrors.append("Creating the iCloud zone failed:\n" + SyncErrorText.describe(failed.error))
            }
        case .sentRecordZoneChanges(let sent):
            await handleSent(sent, engine: syncEngine)
        case .willFetchChanges, .willSendChanges:
            activeOperations += 1
            status.isSyncing = true
            publish()
        case .didFetchChanges, .didSendChanges:
            activeOperations = max(0, activeOperations - 1)
            if activeOperations == 0 {
                await finishCycle()
            }
        case .willFetchRecordZoneChanges, .didFetchRecordZoneChanges:
            break
        @unknown default:
            break
        }
    }

    func nextRecordZoneChangeBatch(
        _ context: CKSyncEngine.SendChangesContext,
        syncEngine: CKSyncEngine
    ) async -> CKSyncEngine.RecordZoneChangeBatch? {
        guard syncEngine === engine else { return nil }
        let scope = context.options.scope
        let changes = syncEngine.state.pendingRecordZoneChanges.filter { scope.contains($0) }
        var records: [CKRecord] = []
        var packs = 0
        var stale: [CKSyncEngine.PendingRecordZoneChange] = []
        for change in changes {
            if records.count >= Self.batchLimit || packs >= Self.packsPerBatch { break }
            switch change {
            case .saveRecord(let id):
                if blocked.contains(id.recordName) { continue }
                if let record = await buildRecord(id) {
                    if record.recordType == CloudRecords.RecordType.pack { packs += 1 }
                    records.append(record)
                } else if !blocked.contains(id.recordName) {
                    stale.append(change)
                }
            case .deleteRecord:
                stale.append(change)
            @unknown default:
                break
            }
        }
        if !stale.isEmpty {
            syncEngine.state.remove(pendingRecordZoneChanges: stale)
        }
        note("batch: \(records.count) of \(changes.count) pending")
        if records.isEmpty { return nil }
        return CKSyncEngine.RecordZoneChangeBatch(recordsToSave: records, recordIDsToDelete: [], atomicByZone: false)
    }

    // MARK: Building records

    // nil means there is nothing to send for this name any more (or it cannot be built, in which case it is blocked).
    private func buildRecord(_ id: CKRecord.ID) async -> CKRecord? {
        let name = id.recordName
        guard let kind = bridge.kind(of: name) else {
            try? await bridge.discard(name)
            return nil
        }
        do {
            let type = CloudRecords.recordType(for: kind)
            let fields = try await bridge.systemFields(name)
            let record = CloudRecords.baseRecord(type: type, name: name, systemFields: fields)
            switch kind {
            case .pack:
                guard let wire = try await bridge.packRecord(name) else {
                    try await bridge.discard(name)
                    return nil
                }
                var assetURL: URL?
                if let source = wire.source {
                    assetURL = try writeTemp(source, for: name)
                }
                CloudRecords.fill(record, pack: wire, assetURL: assetURL)
                inflight[name] = wire.version
            case .review:
                guard let wire = try await bridge.reviewRecord(name) else {
                    try await bridge.discard(name)
                    return nil
                }
                CloudRecords.fill(record, review: wire)
                inflight[name] = wire.version
            case .setting:
                guard let wire = try await bridge.settingRecord(name) else {
                    try await bridge.discard(name)
                    return nil
                }
                CloudRecords.fill(record, setting: wire)
                inflight[name] = wire.version
            }
            return record
        } catch {
            cycleErrors.append("Preparing \(name) failed:\n" + SyncErrorText.describe(error))
            blocked.insert(name)
            return nil
        }
    }

    private static var tempDirectory: URL {
        FileManager.default.temporaryDirectory.appendingPathComponent("JackdawSync", isDirectory: true)
    }

    private func writeTemp(_ text: String, for name: String) throws -> URL {
        try FileManager.default.createDirectory(at: Self.tempDirectory, withIntermediateDirectories: true)
        let url = Self.tempDirectory.appendingPathComponent(UUID().uuidString + ".json")
        try text.write(to: url, atomically: true, encoding: .utf8)
        if let previous = tempFiles[name] {
            try? FileManager.default.removeItem(at: previous)
        }
        tempFiles[name] = url
        return url
    }

    private func dropTemp(_ name: String) {
        if let url = tempFiles.removeValue(forKey: name) {
            try? FileManager.default.removeItem(at: url)
        }
    }

    private func removeTempDirectory() {
        tempFiles = [:]
        try? FileManager.default.removeItem(at: Self.tempDirectory)
    }

    // MARK: Fetched changes

    private func handleFetched(_ changes: CKSyncEngine.Event.FetchedRecordZoneChanges) async {
        let records = changes.modifications.map { $0.record }
        if !records.isEmpty {
            do {
                let outcome = try await apply(records)
                for note in outcome.notes {
                    cycleErrors.append("Skipped: " + note)
                }
                if !outcome.needsUpload.isEmpty {
                    engine?.state.add(
                        pendingRecordZoneChanges: outcome.needsUpload.map { .saveRecord(CloudRecords.recordID($0)) }
                    )
                }
            } catch {
                cycleErrors.append("Applying fetched changes failed:\n" + SyncErrorText.describe(error))
            }
        }
        // Records are never deleted by this app (a deleted pack is a tombstone), so a deletion can only come from
        // outside, for example the CloudKit Console. The local copy is kept.
        if !changes.deletions.isEmpty {
            cycleErrors.append("Skipped: \(changes.deletions.count) record(s) were deleted in iCloud; this device keeps its copy.")
        }
        await refreshPendingCount()
    }

    // Decodes fetched CKRecords, keeps their change tags and merges them. Shared by fetches and conflict resolution.
    private func apply(_ records: [CKRecord]) async throws -> ApplyOutcome {
        var packs: [PackWire] = []
        var reviews: [ReviewWire] = []
        var settings: [SettingWire] = []
        var notes: [String] = []
        var tags: [(String, String)] = []
        for record in records {
            let name = record.recordID.recordName
            switch record.recordType {
            case CloudRecords.RecordType.pack:
                var source: String?
                if let asset = record["source"] as? CKAsset {
                    guard let url = asset.fileURL, let text = try? String(contentsOf: url, encoding: .utf8) else {
                        notes.append("\(name): the pack file could not be read")
                        continue
                    }
                    source = text
                }
                if let wire = CloudRecords.pack(from: record, source: source) {
                    packs.append(wire)
                    tags.append((name, CloudRecords.encodeSystemFields(record)))
                } else {
                    notes.append("\(name): incomplete pack record")
                }
            case CloudRecords.RecordType.review:
                if let wire = CloudRecords.review(from: record) {
                    reviews.append(wire)
                } else {
                    notes.append("\(name): incomplete review record")
                }
            case CloudRecords.RecordType.setting:
                if let wire = CloudRecords.setting(from: record) {
                    settings.append(wire)
                    tags.append((name, CloudRecords.encodeSystemFields(record)))
                } else {
                    notes.append("\(name): incomplete setting record")
                }
            default:
                notes.append("\(name): unknown record type \(record.recordType)")
            }
        }
        let outcome = try await bridge.apply(packs: packs, reviews: reviews, settings: settings)
        for (name, value) in tags {
            try await bridge.setSystemFields(name, value)
        }
        return ApplyOutcome(needsUpload: outcome.needsUpload, notes: notes + outcome.notes)
    }

    private func handleDatabaseChanges(_ changes: CKSyncEngine.Event.FetchedDatabaseChanges) async {
        for deletion in changes.deletions where deletion.zoneID == CloudRecords.zoneID {
            cycleErrors.append("The iCloud data of this app was removed (\(deletion.reason)). Local data is uploaded again.")
            await recoverZone()
        }
    }

    private func recoverZone() async {
        guard let engine else { return }
        do {
            try await bridge.queueEverything()
        } catch {
            cycleErrors.append("Queueing local data failed:\n" + SyncErrorText.describe(error))
        }
        blocked = []
        engine.state.add(pendingDatabaseChanges: [.saveZone(CKRecordZone(zoneID: CloudRecords.zoneID))])
        await reconcilePending()
    }

    // MARK: Sent changes

    private func handleSent(_ sent: CKSyncEngine.Event.SentRecordZoneChanges, engine: CKSyncEngine) async {
        var retry: [CKSyncEngine.PendingRecordZoneChange] = []
        var zoneLost = false
        note("sent: \(sent.savedRecords.count) saved, \(sent.failedRecordSaves.count) failed")
        for record in sent.savedRecords {
            let name = record.recordID.recordName
            let version = inflight.removeValue(forKey: name) ?? 0
            let tag = CloudRecords.keepsSystemFields(record.recordType) ? CloudRecords.encodeSystemFields(record) : nil
            do {
                try await bridge.markSent(name, version: version, systemFields: tag)
            } catch {
                cycleErrors.append("Saving the result of \(name) failed:\n" + SyncErrorText.describe(error))
            }
            dropTemp(name)
        }
        for failure in sent.failedRecordSaves {
            let id = failure.record.recordID
            let name = id.recordName
            let version = inflight.removeValue(forKey: name) ?? 0
            dropTemp(name)
            switch failure.error.code {
            case .serverRecordChanged:
                guard let server = failure.error.serverRecord else {
                    cycleErrors.append("Conflict on \(name) without a server record:\n" + SyncErrorText.describe(failure.error))
                    blocked.insert(name)
                    continue
                }
                do {
                    let outcome = try await apply([server])
                    for note in outcome.notes {
                        cycleErrors.append("Skipped: " + note)
                    }
                    if outcome.needsUpload.contains(name) {
                        retry.append(.saveRecord(id))
                    } else {
                        // The server already has everything this device has. `version` is 0 when the app restarted
                        // between sending and hearing back; the record is then clean at its current version.
                        let tag = CloudRecords.keepsSystemFields(server.recordType) ? CloudRecords.encodeSystemFields(server) : nil
                        if version > 0 {
                            try await bridge.markSent(name, version: version, systemFields: tag)
                        } else {
                            if let tag {
                                try await bridge.setSystemFields(name, tag)
                            }
                            try await bridge.discard(name)
                        }
                    }
                    for other in outcome.needsUpload where other != name {
                        retry.append(.saveRecord(CloudRecords.recordID(other)))
                    }
                } catch {
                    cycleErrors.append("Merging \(name) failed:\n" + SyncErrorText.describe(error))
                    blocked.insert(name)
                }
            case .zoneNotFound, .userDeletedZone:
                zoneLost = true
            case .unknownItem:
                try? await bridge.setSystemFields(name, nil)
                retry.append(.saveRecord(id))
            case .networkFailure, .networkUnavailable, .zoneBusy, .serviceUnavailable, .requestRateLimited,
                 .notAuthenticated, .operationCancelled, .accountTemporarilyUnavailable:
                // The engine retries these itself, so the record must stay in its queue.
                cycleErrors.append("Sending \(name) will be retried:\n" + SyncErrorText.describe(failure.error))
                if failure.error.code == .notAuthenticated {
                    status.availability = .notAuthorized
                }
            case .quotaExceeded:
                cycleErrors.append("iCloud storage is full, changes are kept on this device until there is room:\n"
                    + SyncErrorText.describe(failure.error))
                blocked.insert(name)
            default:
                cycleErrors.append("Sending \(name) failed:\n" + SyncErrorText.describe(failure.error))
                blocked.insert(name)
            }
        }
        if zoneLost {
            cycleErrors.append("The iCloud zone is missing. It is created again and local data is uploaded again.")
            await recoverZone()
        }
        if !retry.isEmpty {
            engine.state.add(pendingRecordZoneChanges: retry)
        }
        await reconcilePending()
    }

    // MARK: Account and errors

    // Runs inside an engine callback: the engine must not be stopped or replaced from here, and a new engine reports
    // a sign-in before anything else, having dropped what was queued for it.
    private func handleAccountChange(_ change: CKSyncEngine.Event.AccountChange) async {
        switch change.changeType {
        case .signIn:
            note("account: sign in")
            status.availability = .available
            await recoverZone()
        case .signOut:
            note("account: sign out")
            Task { await self.signedOut() }
        case .switchAccounts:
            note("account: switched")
            Task { await self.pauseForAccountChange() }
        @unknown default:
            break
        }
    }

    private func signedOut() async {
        await tearDown()
        try? await bridge.setMeta(Meta.engineState, nil)
        try? await bridge.clearTracking()
        status.availability = .noAccount
        publish()
    }

    private func handleOperationError(_ error: Error) async {
        cycleErrors.append(SyncErrorText.describe(error))
        guard let ck = error as? CKError else { return }
        switch ck.code {
        case .changeTokenExpired:
            await recoverByUploadingEverything(reason: "the change token expired")
        case .zoneNotFound, .userDeletedZone:
            await recoverByUploadingEverything(reason: "the iCloud zone is gone")
        case .notAuthenticated:
            status.availability = .notAuthorized
            publish()
        default:
            break
        }
    }

    private func persist(_ serialization: CKSyncEngine.State.Serialization) async {
        do {
            let data = try JSONEncoder().encode(serialization)
            try await bridge.setMeta(Meta.engineState, String(decoding: data, as: UTF8.self))
        } catch {
            cycleErrors.append("Saving the sync state failed:\n" + SyncErrorText.describe(error))
        }
    }

    // MARK: Status

    private func finishCycle() async {
        status.isSyncing = false
        await refreshPendingCount()
        if !cycleErrors.isEmpty {
            var shown = Array(cycleErrors.prefix(Self.shownErrors))
            if cycleErrors.count > shown.count {
                shown.append("… and \(cycleErrors.count - shown.count) more.")
            }
            let text = shown.joined(separator: "\n\n")
            status.lastError = text
            try? await bridge.setMeta(Meta.lastError, text)
        } else if blocked.isEmpty {
            // Records that failed to send keep their error on screen until they went through.
            status.lastError = nil
            try? await bridge.setMeta(Meta.lastError, nil)
            if status.pendingCount == 0 {
                let now = Date()
                status.lastSyncedAt = now
                try? await bridge.setMeta(Meta.lastSyncedAt, String(now.timeIntervalSince1970))
            }
        }
        note("cycle done: \(cycleErrors.count) errors, \(status.pendingCount) pending")
        let clean = cycleErrors.isEmpty
        cycleErrors = []
        publish()
        if clean, !blocked.isEmpty {
            // Records that failed earlier get another try now that a whole cycle went through.
            blocked = []
            await reconcilePending()
        }
    }

    private func setLastError(_ text: String) async {
        status.lastError = text
        try? await bridge.setMeta(Meta.lastError, text)
        publish()
    }

    private func refreshPendingCount() async {
        if let names = try? await bridge.pending() {
            status.pendingCount = names.count
        }
        publish()
    }

    private func note(_ text: String) {
        Self.logger.notice("\(text, privacy: .public)")
        log.append(Date().formatted(date: .omitted, time: .standard) + "  " + text)
        if log.count > Self.logLimit {
            log.removeFirst(log.count - Self.logLimit)
        }
        status.log = log
    }

    private static func name(of event: CKSyncEngine.Event) -> String {
        switch event {
        case .stateUpdate: return "state update"
        case .accountChange: return "account change"
        case .fetchedDatabaseChanges: return "fetched database changes"
        case .fetchedRecordZoneChanges(let changes):
            return "fetched \(changes.modifications.count) records, \(changes.deletions.count) deletions"
        case .sentDatabaseChanges: return "sent database changes"
        case .sentRecordZoneChanges: return "sent record changes"
        case .willFetchChanges: return "will fetch"
        case .willFetchRecordZoneChanges: return "will fetch zone"
        case .didFetchRecordZoneChanges: return "did fetch zone"
        case .didFetchChanges: return "did fetch"
        case .willSendChanges: return "will send"
        case .didSendChanges: return "did send"
        @unknown default: return "unknown event"
        }
    }

    private func publish() {
        for continuation in listeners.values {
            continuation.yield(status)
        }
    }

    private func removeListener(_ id: UUID) {
        listeners[id] = nil
    }
}
