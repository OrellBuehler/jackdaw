import Foundation

enum RemoteText {
    static let maxBytes = 28 * 1024 * 1024

    static func fetch(_ url: URL) async -> String? {
        guard url.scheme == "https" else { return nil }
        let request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { return nil }
            guard data.count <= maxBytes else { return nil }
            return String(data: data, encoding: .utf8)
        } catch {
            return nil
        }
    }
}
