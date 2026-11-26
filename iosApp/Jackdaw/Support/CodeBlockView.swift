import SwiftUI

enum CodePalette {
    static let surface = Color.adaptive(light: 0xEDEFF7, dark: 0x0C0B14)
    static let border = Color.adaptive(light: 0xD3D7E6, dark: 0x332F45)
    static let plain = Color.adaptive(light: 0x1F2330, dark: 0xE6E8F0)
    static let keyword = Color.adaptive(light: 0xA8205F, dark: 0xFF8FB8)
    static let string = Color.adaptive(light: 0x0B6B4B, dark: 0x7EE2B0)
    static let comment = Color.adaptive(light: 0x586071, dark: 0x9AA1B2)
    static let number = Color.adaptive(light: 0x0A57A6, dark: 0x82B8FF)
    static let typeName = Color.adaptive(light: 0x7436B0, dark: 0xC7A6FF)

    static func color(_ kind: CodeTokenKind) -> Color {
        switch kind {
        case .plain: return plain
        case .keyword: return keyword
        case .string: return string
        case .comment: return comment
        case .number: return number
        case .typeName: return typeName
        }
    }

    static var font: Font { Font.system(.footnote, design: .monospaced) }
}

enum CodeRendering {
    static func attributed(_ lines: [[CodeToken]]) -> AttributedString {
        var result = AttributedString()
        for (index, line) in lines.enumerated() {
            if index > 0 { result.append(AttributedString("\n")) }
            for token in line {
                result.append(piece(token))
            }
        }
        return result
    }

    static func piece(_ token: CodeToken, spaces: Bool = false) -> AttributedString {
        var part = AttributedString(spaces ? token.text.replacingOccurrences(of: " ", with: "\u{00A0}") : token.text)
        part.foregroundColor = CodePalette.color(token.kind)
        return part
    }
}

struct CodeSurface<Content: View>: View {
    private let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            content
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
        }
        .scrollBounceBehavior(.basedOnSize, axes: .horizontal)
        .background(CodePalette.surface, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .strokeBorder(CodePalette.border, lineWidth: 1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct CodeBlockView: View {
    let lines: [[CodeToken]]

    var body: some View {
        CodeSurface {
            Text(CodeRendering.attributed(lines))
                .font(CodePalette.font)
                .lineSpacing(2)
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: true, vertical: true)
        }
    }
}
