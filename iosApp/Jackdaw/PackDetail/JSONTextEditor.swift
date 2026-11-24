import SwiftUI
import UIKit

struct JSONTextEditor: UIViewRepresentable {
    @Binding var text: String

    func makeCoordinator() -> Coordinator {
        Coordinator(text: $text)
    }

    func makeUIView(context: Context) -> UITextView {
        let view = UITextView()
        view.delegate = context.coordinator
        view.font = UIFontMetrics(forTextStyle: .footnote).scaledFont(
            for: UIFont.monospacedSystemFont(ofSize: 13, weight: .regular)
        )
        view.adjustsFontForContentSizeCategory = true
        view.textColor = .label
        view.backgroundColor = .clear
        view.textContainerInset = UIEdgeInsets(top: 12, left: 10, bottom: 12, right: 10)
        view.autocorrectionType = .no
        view.autocapitalizationType = .none
        view.spellCheckingType = .no
        view.smartQuotesType = .no
        view.smartDashesType = .no
        view.smartInsertDeleteType = .no
        view.text = text
        return view
    }

    func updateUIView(_ uiView: UITextView, context: Context) {
        context.coordinator.text = $text
        if uiView.text != text {
            uiView.text = text
        }
    }

    @MainActor
    final class Coordinator: NSObject, UITextViewDelegate {
        var text: Binding<String>

        init(text: Binding<String>) {
            self.text = text
        }

        func textViewDidChange(_ textView: UITextView) {
            text.wrappedValue = textView.text
        }
    }
}
