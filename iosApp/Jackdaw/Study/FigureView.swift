import SwiftUI
import WebKit

struct FigureView: View {
    let figure: FigureInfo

    var body: some View {
        FigureWebView(html: figure.html)
            .aspectRatio(CGFloat(min(max(figure.aspectRatio, 0.2), 5)), contentMode: .fit)
            .frame(maxWidth: .infinity, maxHeight: 240)
            .padding(12)
            .frame(maxWidth: .infinity)
            .card(radius: 24)
            .accessibilityElement()
            .accessibilityLabel(figure.alt ?? L10n.tr("study.figure"))
            .accessibilityAddTraits(.isImage)
    }
}

private struct FigureWebView: UIViewRepresentable {
    let html: String

    final class Coordinator {
        var loaded: String?
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.defaultWebpagePreferences.allowsContentJavaScript = false
        let view = WKWebView(frame: .zero, configuration: configuration)
        view.isOpaque = false
        view.backgroundColor = .clear
        view.scrollView.backgroundColor = .clear
        view.scrollView.isScrollEnabled = false
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ view: WKWebView, context: Context) {
        guard context.coordinator.loaded != html else { return }
        context.coordinator.loaded = html
        view.loadHTMLString(html, baseURL: nil)
    }
}
