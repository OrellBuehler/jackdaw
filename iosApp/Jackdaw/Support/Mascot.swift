import SwiftUI

struct JackdawHead: Shape {
    func path(in rect: CGRect) -> Path {
        let scale = min(rect.width, rect.height) / 256
        return Path(ellipseIn: CGRect(x: rect.minX + 44 * scale, y: rect.minY + 62 * scale, width: 168 * scale, height: 168 * scale))
    }
}

struct JackdawBeak: Shape {
    func path(in rect: CGRect) -> Path {
        let scale = min(rect.width, rect.height) / 256
        func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            CGPoint(x: rect.minX + x * scale, y: rect.minY + y * scale)
        }
        var path = Path()
        path.move(to: point(112, 156))
        path.addLine(to: point(144, 154))
        path.addLine(to: point(129, 186))
        path.closeSubpath()
        return path
    }
}

struct HappyEye: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.addQuadCurve(
            to: CGPoint(x: rect.maxX, y: rect.maxY),
            control: CGPoint(x: rect.midX, y: 2 * rect.minY - rect.maxY)
        )
        return path
    }
}

struct Mascot: View {
    var size: CGFloat = 160
    var happy = false
    var animated = false
    @State private var lifted = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let s = size / 256
        let lift: CGFloat = lifted ? -12 * s : 0
        ZStack(alignment: .topLeading) {
            Brand.violet
                .frame(width: size, height: size)
            head(scale: s)
                .offset(y: lift)
            RoundedRectangle(cornerRadius: 26 * s, style: .continuous)
                .fill(Color(hex: 0xF6F3FF))
                .frame(width: 212 * s, height: 170 * s)
                .rotationEffect(.degrees(-5))
                .offset(x: 22 * s, y: 172 * s)
            beak(scale: s)
                .offset(y: lift)
        }
        .frame(width: size, height: size, alignment: .topLeading)
        .clipShape(Circle())
        .animation(
            animated && !reduceMotion ? Animation.easeInOut(duration: 1.1).repeatForever(autoreverses: true) : nil,
            value: lifted
        )
        .onAppear {
            if animated && !reduceMotion { lifted = true }
        }
        .accessibilityHidden(true)
    }

    private func head(scale s: CGFloat) -> some View {
        ZStack(alignment: .topLeading) {
            JackdawHead()
                .fill(Brand.ink)
                .frame(width: size, height: size)
            nape(scale: s)
            eye(centerX: 99, scale: s)
            eye(centerX: 157, scale: s)
        }
        .frame(width: size, height: size, alignment: .topLeading)
    }

    private func nape(scale s: CGFloat) -> some View {
        let lilac = Color(hex: 0xC9C4EC)
        return ZStack(alignment: .topLeading) {
            Circle()
                .fill(lilac)
                .frame(width: 140 * s, height: 140 * s)
                .offset(x: -48 * s, y: 96 * s)
            Circle()
                .fill(lilac)
                .frame(width: 140 * s, height: 140 * s)
                .offset(x: 164 * s, y: 96 * s)
        }
        .frame(width: size, height: size, alignment: .topLeading)
        .clipShape(JackdawHead())
    }

    private func beak(scale s: CGFloat) -> some View {
        ZStack {
            JackdawBeak()
                .fill(Brand.amber)
            JackdawBeak()
                .stroke(Brand.amber, style: StrokeStyle(lineWidth: 10 * s, lineJoin: .round))
        }
        .frame(width: size, height: size)
    }

    @ViewBuilder
    private func eye(centerX x: CGFloat, scale s: CGFloat) -> some View {
        if happy {
            HappyEye()
                .stroke(Brand.cream, style: StrokeStyle(lineWidth: 10 * s, lineCap: .round))
                .frame(width: 40 * s, height: 18 * s)
                .offset(x: (x - 20) * s, y: 112 * s)
        } else {
            Circle()
                .fill(Brand.cream)
                .frame(width: 50 * s, height: 50 * s)
                .offset(x: (x - 25) * s, y: 101 * s)
            Circle()
                .fill(Brand.ink)
                .frame(width: 22 * s, height: 22 * s)
                .offset(x: (x - 7) * s, y: 112 * s)
        }
    }
}
