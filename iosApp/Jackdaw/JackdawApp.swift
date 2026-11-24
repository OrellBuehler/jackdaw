import SwiftUI

@main
struct JackdawApp: App {
    @State private var model = AppModel()

    init() {
        Task { await CloudSync.shared.start() }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
        }
    }
}
