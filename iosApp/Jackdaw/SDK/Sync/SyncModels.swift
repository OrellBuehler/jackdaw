import Foundation

struct PackWire: Sendable {
    let recordName: String
    let packId: String
    let source: String?
    let importedAt: Int64
    let contentAt: Int64
    let resetAt: Int64
    let deletedAt: Int64
    let version: Int64
}

struct ReviewWire: Sendable {
    let recordName: String
    let id: String
    let ts: Int64
    let day: Int64
    let packId: String
    let itemId: String
    let facet: Int64
    let exercise: Int64
    let grade: Int64
    let correct: Bool
    let practice: Bool
    let reschedule: Bool
    let itemType: String
    let version: Int64
}

struct SettingWire: Sendable {
    let recordName: String
    let name: String
    let value: String
    let updatedAt: Int64
    let version: Int64
}

struct ApplyOutcome: Sendable {
    let needsUpload: [String]
    let notes: [String]
}

enum SyncKind: Sendable {
    case pack
    case review
    case setting
}

enum SyncAvailability: Sendable, Equatable {
    case unsupported
    case checking
    case available
    case noAccount
    case restricted
    case temporarilyUnavailable
    case couldNotDetermine
    case notAuthorized
    case accountChanged
}

struct SyncStatus: Sendable, Equatable {
    var isEnabled: Bool
    var availability: SyncAvailability
    var isSyncing: Bool
    var lastSyncedAt: Date?
    var lastError: String?
    var pendingCount: Int
    var log: [String] = []

    static let initial = SyncStatus(
        isEnabled: true,
        availability: .checking,
        isSyncing: false,
        lastSyncedAt: nil,
        lastError: nil,
        pendingCount: 0
    )
}
