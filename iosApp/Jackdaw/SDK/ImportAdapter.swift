@preconcurrency import shared
import Foundation

// SKIE `onEnum(of:)` cases are the lowerCamelCased names of the nested sealed subclasses
// (ImportResult.Success -> .success). Nested Kotlin types are never named in Swift on purpose.
enum ImportAdapter {
    static func outcome(_ result: ImportResult) -> ImportOutcome {
        switch onEnum(of: result) {
        case .success(let ok):
            return .success(
                ImportDoneInfo(
                    packId: ok.packId,
                    title: ok.title,
                    itemCount: Int(ok.itemCount),
                    skippedCount: Int(ok.skippedCount),
                    isUpdate: ok.isUpdate,
                    counts: counts(ok.counts),
                    warnings: warnings(ok.warnings)
                )
            )
        case .failure(let bad):
            return .failure(issue(bad.error))
        default:
            return .failure(ImportIssue(kind: .other, message: ""))
        }
    }

    static func previewOutcome(_ preview: ImportPreview) -> PreviewOutcome {
        switch onEnum(of: preview) {
        case .valid(let ok):
            return .valid(
                ImportPreviewInfo(
                    packId: ok.packId,
                    title: ok.title,
                    blurb: ok.descriptionText,
                    icon: ok.icon,
                    colorHex: ok.color,
                    language: ok.language,
                    revision: Int(ok.revision),
                    itemCount: Int(ok.itemCount),
                    sectionCount: Int(ok.sectionCount),
                    typeCounts: Mapping.typeCounts(ok.typeCounts),
                    skippedCount: Int(ok.skippedCount),
                    warnings: warnings(ok.warnings),
                    isUpdate: ok.isUpdate,
                    installedTitle: ok.installedTitle,
                    installedRevision: Int(ok.installedRevision),
                    isDowngrade: ok.isDowngrade,
                    counts: counts(ok.counts),
                    overwritesEdits: ok.overwritesEdits
                )
            )
        case .invalid(let bad):
            return .invalid(issue(bad.error))
        default:
            return .invalid(ImportIssue(kind: .other, message: ""))
        }
    }

    static func counts(_ c: ImportCounts) -> ImportCountsInfo {
        ImportCountsInfo(
            added: Int(c.addedItems),
            kept: Int(c.keptItems),
            reset: Int(c.resetItems),
            removed: Int(c.removedItems)
        )
    }

    static func warnings(_ list: [ImportWarning]) -> [ImportWarningInfo] {
        list.enumerated().map { index, warning in
            ImportWarningInfo(
                id: index,
                kind: warningKind(warning.code),
                message: warning.message,
                itemId: warning.itemId,
                itemIndex: Int(warning.itemIndex),
                detail: warning.detail,
                count: Int(warning.count)
            )
        }
    }

    static func issue(_ error: ImportError) -> ImportIssue {
        ImportIssue(kind: issueKind(error.code), message: error.message)
    }

    static func issueKind(_ code: ImportErrorCode) -> ImportIssueKind {
        switch Mapping.normalized(code) {
        case "notjson", "notanobject", "notapack": return .notAPack
        case "versiontoonew": return .versionTooNew
        case "versioninvalid", "idinvalid", "titleinvalid": return .invalidPack
        case "itemsmissing", "novaliditems": return .noItems
        case "toolarge", "toomanyitems": return .tooLarge
        case "revisiondowngrade": return .revisionDowngrade
        case "unknownsample": return .unknownSample
        case "storageerror": return .storage
        case "iteminvalid": return .itemInvalid
        case "packmissing": return .packMissing
        default: return .other
        }
    }

    static func warningKind(_ code: ImportWarningCode) -> ImportWarningKind {
        switch Mapping.normalized(code) {
        case "itemunknowntype": return .unknownType
        case "morewarnings": return .more
        default: return .other
        }
    }
}
