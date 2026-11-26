import Foundation

struct StoreScreenshot: Sendable {
    let screen: String
    let packId: String
    let sectionId: String?

    #if DEBUG
    static let requested: StoreScreenshot? = {
        let defaults = UserDefaults.standard
        guard let screen = defaults.string(forKey: "store_screenshot") else { return nil }
        let pack = defaults.string(forKey: "store_screenshot_pack") ?? ""
        return StoreScreenshot(
            screen: screen,
            packId: "com.github.orellbuehler.jackdaw.examples." + pack,
            sectionId: defaults.string(forKey: "store_screenshot_section")
        )
    }()
    #else
    static let requested: StoreScreenshot? = nil
    #endif
}
