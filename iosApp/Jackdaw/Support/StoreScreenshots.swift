import Foundation

struct StoreScreenshot: Sendable {
    let screen: String
    let packId: String
    let sectionId: String?
    let filter: PracticeKind

    #if DEBUG
    static let requested: StoreScreenshot? = {
        let defaults = UserDefaults.standard
        guard let screen = defaults.string(forKey: "store_screenshot") else { return nil }
        let pack = defaults.string(forKey: "store_screenshot_pack") ?? ""
        return StoreScreenshot(
            screen: screen,
            packId: "com.github.orellbuehler.jackdaw.examples." + pack,
            sectionId: defaults.string(forKey: "store_screenshot_section"),
            filter: defaults.string(forKey: "store_screenshot_filter") == "flashcards" ? .flashcards : .quiz
        )
    }()
    #else
    static let requested: StoreScreenshot? = nil
    #endif
}
