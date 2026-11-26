import Foundation
import SwiftUI

extension StyledText {
    var attributed: AttributedString {
        StyledText.attributed(spans)
    }

    static func attributed(_ spans: [StyledSpan]) -> AttributedString {
        var result = AttributedString()
        for span in spans {
            var part = AttributedString(span.text)
            var intent = InlinePresentationIntent()
            if span.bold { intent.insert(.stronglyEmphasized) }
            if span.italic { intent.insert(.emphasized) }
            if span.code { intent.insert(.code) }
            if !intent.isEmpty {
                part.inlinePresentationIntent = intent
            }
            result.append(part)
        }
        return result
    }
}

struct RichTextView: View {
    let text: StyledText
    var font: Font = .body
    var color: Color = Brand.text
    var alignment: TextAlignment = .leading

    private var frameAlignment: Alignment {
        switch alignment {
        case .center: return .center
        case .trailing: return .trailing
        default: return .leading
        }
    }

    var body: some View {
        if text.hasCode {
            VStack(spacing: 8) {
                ForEach(Array(text.blocks.enumerated()), id: \.offset) { _, block in
                    blockView(block)
                }
            }
        } else {
            paragraph(text.spans)
        }
    }

    @ViewBuilder
    private func blockView(_ block: StyledBlock) -> some View {
        switch block {
        case .paragraph(let spans):
            paragraph(spans)
                .frame(maxWidth: .infinity, alignment: frameAlignment)
        case .code(let lines):
            CodeBlockView(lines: lines)
        }
    }

    private func paragraph(_ spans: [StyledSpan]) -> some View {
        Text(StyledText.attributed(spans))
            .font(font)
            .foregroundStyle(color)
            .multilineTextAlignment(alignment)
            .fixedSize(horizontal: false, vertical: true)
    }
}
