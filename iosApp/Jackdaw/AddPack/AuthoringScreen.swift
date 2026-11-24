import SwiftUI
import UIKit

struct AuthoringScreen: View {
    let onPaste: () -> Void
    @Environment(AppModel.self) private var model
    @State private var prompt = ""
    @State private var kinds = Set(ItemKind.allCases)
    @State private var figures = false
    @State private var copied = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Mascot(size: 88, happy: true)
                Text("ai.headline")
                    .font(.brand(.title2, weight: .bold))
                    .foregroundStyle(Brand.text)
                Text("ai.explanation")
                    .font(.brand(.body))
                    .foregroundStyle(Brand.textSecondary)
                configurator
                promptPreview
                actions
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20)
            .padding(.top, 8)
            .padding(.bottom, 32)
        }
        .background(Brand.background.ignoresSafeArea())
        .navigationTitle("ai.title")
        .navigationBarTitleDisplayMode(.inline)
        .sensoryFeedback(.success, trigger: copied)
        .onAppear { refresh() }
        .onChange(of: kinds) { refresh() }
        .onChange(of: figures) { refresh() }
    }

    private func refresh() {
        prompt = model.authoringPrompt(kinds: kinds, figures: figures)
        copied = false
    }

    private func toggle(_ kind: ItemKind) {
        if !kinds.contains(kind) {
            kinds.insert(kind)
        } else if kinds.count > 1 {
            kinds.remove(kind)
        }
    }

    private var configurator: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("ai.types.label")
                .font(.brand(.caption, weight: .bold))
                .foregroundStyle(Brand.textSecondary)
                .textCase(.uppercase)
            FlowLayout(spacing: 8, lineSpacing: 8) {
                ForEach(ItemKind.allCases, id: \.self) { kind in
                    let selected = kinds.contains(kind)
                    Button {
                        toggle(kind)
                    } label: {
                        Chip(text: L10n.tr(kind.labelKey), systemImage: kind.symbolName, filled: selected)
                            .padding(.vertical, 4)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(selected ? [.isSelected] : [])
                }
            }
            Toggle(isOn: $figures) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("ai.figures")
                        .font(.brand(.body, weight: .semibold))
                        .foregroundStyle(Brand.text)
                    Text("ai.figures.note")
                        .font(.brand(.caption))
                        .foregroundStyle(Brand.textSecondary)
                }
            }
            .tint(Brand.accent)
        }
    }

    private var promptPreview: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("ai.prompt.label")
                .font(.brand(.caption, weight: .bold))
                .foregroundStyle(Brand.textSecondary)
                .textCase(.uppercase)
            Text(prompt)
                .font(.system(.caption, design: .monospaced))
                .foregroundStyle(Brand.text)
                .lineLimit(9)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(14)
                .background(Brand.surfaceMuted, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
    }

    private var actions: some View {
        VStack(spacing: 12) {
            Button {
                UIPasteboard.general.string = prompt
                copied.toggle()
            } label: {
                Label(L10n.tr(copied ? "ai.copied" : "ai.copy"), systemImage: copied ? "checkmark" : "doc.on.doc")
            }
            .buttonStyle(BrandButtonStyle())
            .disabled(prompt.isEmpty)
            ShareLink(item: prompt) {
                Label("ai.share", systemImage: "square.and.arrow.up")
            }
            .buttonStyle(SoftButtonStyle())
            .disabled(prompt.isEmpty)
            Button {
                onPaste()
            } label: {
                Label("ai.paste", systemImage: "doc.on.clipboard")
            }
            .buttonStyle(SoftButtonStyle(tint: Brand.textSecondary))
        }
    }
}
