import Foundation
import Observation

@MainActor
@Observable
final class ImportFlow {
    enum Phase {
        case idle
        case loading
        case preview(ImportPreviewInfo)
        case importing(ImportPreviewInfo)
        case done(ImportDoneInfo)
        case failed(ImportIssue)
    }

    private(set) var phase: Phase = .idle

    private let client = SDKClient.shared
    private var text = ""
    private var sourceURL: String?

    var isFinished: Bool {
        if case .done = phase { return true }
        return false
    }

    func reset() {
        phase = .idle
        text = ""
        sourceURL = nil
    }

    func begin(text source: String, sourceURL link: String? = nil) async {
        text = source
        sourceURL = link
        phase = .loading
        let outcome = await client.previewImport(text: source)
        switch outcome {
        case .valid(let info):
            phase = .preview(info)
        case .invalid(let issue):
            phase = .failed(issue)
        }
    }

    func begin(url: URL) async {
        phase = .loading
        do {
            let loaded = try await PackFile.readText(from: url)
            await begin(text: loaded)
        } catch {
            phase = .failed(ImportIssue(kind: .unreadableFile, message: error.localizedDescription))
        }
    }

    func confirm(allowDowngrade: Bool) async {
        guard case .preview(let info) = phase else { return }
        phase = .importing(info)
        let outcome = await client.importPack(text: text, allowDowngrade: allowDowngrade, sourceURL: sourceURL)
        switch outcome {
        case .success(let done):
            phase = .done(done)
        case .failure(let issue):
            phase = .failed(issue)
        }
    }
}
