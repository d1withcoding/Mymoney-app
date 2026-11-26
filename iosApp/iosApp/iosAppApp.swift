import SwiftUI

@main
struct iosAppApp: App {
    // Set by the pennywise://add deep link. Back Tap can't reach an app
    // directly, so the user points Settings > Accessibility > Touch > Back Tap
    // at a Shortcut that opens this URL.
    @State private var showQuickAdd = false

    var body: some Scene {
        WindowGroup {
            MainTabView(showQuickAdd: $showQuickAdd)
                .onOpenURL { url in
                    if url.scheme == "pennywise" {
                        showQuickAdd = (url.host == "add")
                        return
                    }
                }
        }
    }
}
