@preconcurrency import shared
import Foundation

// The only Swift that talks to `JackdawSync`. Everything it hands out is a plain Sendable value.
final class SyncBridge: @unchecked Sendable {
    private let sync: JackdawSync

    init(sync: JackdawSync) {
        self.sync = sync
    }

    var packFormat: String {
        String(sync.packFormatVersion)
    }

    func enableTracking() {
        sync.enableTracking()
    }

    func watchLocalChanges(deliver: @Sendable () async -> Void) async {
        for await _ in sync.observeLocalChanges() {
            await deliver()
        }
    }

    func kind(of recordName: String) -> SyncKind? {
        switch sync.kindOf(recordName: recordName) {
        case "pack": return .pack
        case "review": return .review
        case "setting": return .setting
        default: return nil
        }
    }

    func pending() async throws -> [String] {
        try await sync.pendingRecords()
    }

    func packRecord(_ name: String) async throws -> PackWire? {
        guard let r = try await sync.packRecord(recordName: name) else { return nil }
        return PackWire(
            recordName: r.recordName,
            packId: r.packId,
            source: r.source,
            importedAt: r.importedAt,
            contentAt: r.contentAt,
            resetAt: r.resetAt,
            deletedAt: r.deletedAt,
            version: r.version
        )
    }

    func reviewRecord(_ name: String) async throws -> ReviewWire? {
        guard let r = try await sync.reviewRecord(recordName: name) else { return nil }
        return ReviewWire(
            recordName: r.recordName,
            id: r.id,
            ts: r.ts,
            day: r.day,
            packId: r.packId,
            itemId: r.itemId,
            facet: r.facet,
            exercise: r.exercise,
            grade: r.grade,
            correct: r.correct,
            practice: r.practice,
            reschedule: r.reschedule,
            itemType: r.itemType,
            version: r.version
        )
    }

    func settingRecord(_ name: String) async throws -> SettingWire? {
        guard let r = try await sync.settingRecord(recordName: name) else { return nil }
        return SettingWire(recordName: r.recordName, name: r.name, value: r.value, updatedAt: r.updatedAt, version: r.version)
    }

    func markSent(_ name: String, version: Int64, systemFields: String?) async throws {
        try await sync.markSent(recordName: name, version: version, systemFields: systemFields)
    }

    func discard(_ name: String) async throws {
        try await sync.discard(recordName: name)
    }

    func systemFields(_ name: String) async throws -> String? {
        try await sync.systemFields(recordName: name)
    }

    func setSystemFields(_ name: String, _ value: String?) async throws {
        try await sync.setSystemFields(recordName: name, value: value)
    }

    func apply(packs: [PackWire], reviews: [ReviewWire], settings: [SettingWire]) async throws -> ApplyOutcome {
        let kotlinPacks = packs.map {
            SyncPackRecord(
                recordName: $0.recordName,
                packId: $0.packId,
                source: $0.source,
                importedAt: $0.importedAt,
                contentAt: $0.contentAt,
                resetAt: $0.resetAt,
                deletedAt: $0.deletedAt,
                version: $0.version
            )
        }
        let kotlinReviews = reviews.map {
            SyncReviewRecord(
                recordName: $0.recordName,
                id: $0.id,
                ts: $0.ts,
                day: $0.day,
                packId: $0.packId,
                itemId: $0.itemId,
                facet: $0.facet,
                exercise: $0.exercise,
                grade: $0.grade,
                correct: $0.correct,
                practice: $0.practice,
                reschedule: $0.reschedule,
                itemType: $0.itemType,
                version: $0.version
            )
        }
        let kotlinSettings = settings.map {
            SyncSettingRecord(recordName: $0.recordName, name: $0.name, value: $0.value, updatedAt: $0.updatedAt, version: $0.version)
        }
        let result = try await sync.apply(packs: kotlinPacks, reviews: kotlinReviews, settings: kotlinSettings)
        return ApplyOutcome(needsUpload: result.needsUpload, notes: result.notes)
    }

    func queueEverything() async throws {
        try await sync.queueEverything()
    }

    func clearTracking() async throws {
        try await sync.clearTracking()
    }

    func meta(_ name: String) async throws -> String? {
        try await sync.meta(name: name)
    }

    func setMeta(_ name: String, _ value: String?) async throws {
        try await sync.setMeta(name: name, value: value)
    }
}
