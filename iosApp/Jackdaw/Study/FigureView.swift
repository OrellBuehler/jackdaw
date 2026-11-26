import SwiftUI
import WebKit

struct FigureView: View {
    let figure: FigureInfo
    @State private var zoomed = false

    private static let mapMaxHeight: CGFloat = 320

    private var ratio: CGFloat { CGFloat(min(max(figure.aspectRatio, 0.2), 5)) }

    var body: some View {
        picture
            .overlay {
                Color.clear
                    .contentShape(Rectangle())
                    .onTapGesture { zoomed = true }
            }
            .accessibilityElement()
            .accessibilityLabel(figure.alt ?? L10n.tr(figure.isMap ? "study.map" : "study.figure"))
            .accessibilityHint(L10n.tr(figure.isMap ? "study.map.open" : "study.figure.open"))
            .accessibilityAddTraits([.isImage, .isButton])
            .accessibilityAction { zoomed = true }
            .fullScreenCover(isPresented: $zoomed) {
                FigureZoomView(figure: figure)
            }
    }

    @ViewBuilder
    private var picture: some View {
        if figure.isMap {
            FigureWebView(html: figure.html)
                .aspectRatio(ratio, contentMode: .fit)
                .frame(maxWidth: Self.mapMaxHeight * ratio)
                .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                .card(radius: 24)
                .frame(maxWidth: .infinity)
        } else {
            FigureWebView(html: figure.html)
                .aspectRatio(ratio, contentMode: .fit)
                .frame(maxWidth: .infinity, maxHeight: 240)
                .padding(12)
                .frame(maxWidth: .infinity)
                .card(radius: 24)
        }
    }
}

private struct FigureZoomView: View {
    let figure: FigureInfo
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack(alignment: .topTrailing) {
            FigureWebView(html: figure.html, interactive: true)
                .accessibilityLabel(figure.alt ?? L10n.tr(figure.isMap ? "study.map" : "study.figure"))
            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 32))
                    .symbolRenderingMode(.hierarchical)
                    .foregroundStyle(Brand.textSecondary)
                    .frame(width: 44, height: 44)
            }
            .padding(8)
            .accessibilityLabel(L10n.tr("common.close"))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Brand.surface.ignoresSafeArea())
    }
}

private struct FigureWebView: UIViewRepresentable {
    let html: String
    var interactive = false

    @MainActor
    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var loaded: String?

        @objc func doubleTapped(_ gesture: UITapGestureRecognizer) {
            guard let view = gesture.view as? WKWebView else { return }
            let scroll = view.scrollView
            if scroll.zoomScale > scroll.minimumZoomScale + 0.01 {
                scroll.setZoomScale(scroll.minimumZoomScale, animated: true)
                return
            }
            let scale = min(scroll.maximumZoomScale, scroll.minimumZoomScale * 3)
            let point = gesture.location(in: view)
            let size = CGSize(width: view.bounds.width / scale, height: view.bounds.height / scale)
            let target = CGRect(x: point.x - size.width / 2, y: point.y - size.height / 2, width: size.width, height: size.height)
            scroll.zoom(to: target, animated: true)
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }
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
        if interactive {
            view.scrollView.contentInsetAdjustmentBehavior = .never
            let doubleTap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.doubleTapped(_:)))
            doubleTap.numberOfTapsRequired = 2
            doubleTap.delegate = context.coordinator
            view.addGestureRecognizer(doubleTap)
        } else {
            view.scrollView.isScrollEnabled = false
            view.isUserInteractionEnabled = false
        }
        return view
    }

    func updateUIView(_ view: WKWebView, context: Context) {
        guard context.coordinator.loaded != html else { return }
        context.coordinator.loaded = html
        view.loadHTMLString(html, baseURL: nil)
    }
}
