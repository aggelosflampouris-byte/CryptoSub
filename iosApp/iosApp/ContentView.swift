import SwiftUI
import Shared  // The compiled KMP framework

/// Root SwiftUI view. Hosts the shared Compose Multiplatform UI via
/// ComposeUIViewController wrapped in a UIViewControllerRepresentable.
struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}

/// Bridges the KMP shared App composable into SwiftUI.
struct ComposeView: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {
        return Main_iosKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
