package ch.orellbuehler.jackdaw.format

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

internal class MapView(
    val west: Double,
    val south: Double,
    val width: Double,
    val height: Double,
    val xs: DoubleArray,
    val ys: DoubleArray,
)

internal object MapFigure {
    const val WIDTH = 2000
    const val MIN_WIDTH_DEGREES = 40.0
    const val MAX_LATITUDE = 85.0511287798
    private const val ASPECT = 1.6
    private const val FILL = 0.7
    private const val WIDEN = 1.1
    private const val MIN_LAND_SHARE = 0.15
    private const val PIN_RADIUS = 22.0
    private const val FONT_SIZE = 70.0
    private const val LABEL_GAP = 14.0
    private const val LABEL_MARGIN = 14.0
    private const val MAX_LABEL = 30
    private const val LINE_CLEARANCE = 14.0
    private const val PIN_CLEARANCE = 25.0
    private const val LABEL_CLEARANCE = 6.0
    private const val ARROW_REACH = 36.0
    private const val ARROW_MIN_LENGTH = 200.0
    private const val COLLISION = 10_000.0
    private const val CLIP_MARGIN = 24.0
    private const val MAX_PATH = 150_000
    private const val ACCENT = "#E5484D"
    private val RINGS = doubleArrayOf(1.0, 2.2, 3.6)
    private val SIMPLIFY = doubleArrayOf(2.0, 4.0, 8.0, 16.0)

    fun mercatorY(lat: Double): Double {
        val clamped = lat.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        return ln(tan(PI / 4 + clamped * PI / 360.0)) * 180.0 / PI
    }

    fun normalizeLongitude(lng: Double): Double {
        val shifted = (lng + 180.0) % 360.0
        return (if (shifted < 0) shifted + 360.0 else shifted) - 180.0
    }

    fun view(pins: List<MapPin>): MapView {
        val lngs = pins.map { normalizeLongitude(it.lng) }.sorted()
        var start = lngs[0]
        var span = 0.0
        if (lngs.size > 1) {
            var widest = -1.0
            for (i in lngs.indices) {
                val next = if (i == lngs.size - 1) lngs[0] + 360.0 else lngs[i + 1]
                val gap = next - lngs[i]
                if (gap > widest) {
                    widest = gap
                    start = lngs[(i + 1) % lngs.size]
                }
            }
            span = 360.0 - widest
        }
        val xs = DoubleArray(pins.size) { start + positiveModulo(normalizeLongitude(pins[it].lng) - start, 360.0) }
        val ys = DoubleArray(pins.size) { mercatorY(pins[it].lat) }
        val lowest = ys.min()
        val highest = ys.max()
        val ySpan = highest - lowest
        val centerX = start + span / 2
        val centerY = (lowest + highest) / 2
        val minHeight = min(ySpan / 0.9, 360.0)
        var width = min(max(max(MIN_WIDTH_DEGREES, span / FILL), ySpan / FILL * ASPECT), 360.0)
        var height = max(width / ASPECT, minHeight)
        while (width < 360.0 && WorldData.landShare(centerX - width / 2, centerY - height / 2, width, height) < MIN_LAND_SHARE) {
            width = min(width * WIDEN, 360.0)
            height = max(width / ASPECT, minHeight)
        }
        val west = centerX - width / 2
        val south = centerY - height / 2
        return MapView(west, south, width, height, xs, ys)
    }

    fun render(spec: MapSpec): Figure {
        val view = view(spec.pins)
        val scale = WIDTH / view.width
        val height = floor(view.height * scale + 0.5).toInt()
        val px = DoubleArray(spec.pins.size) { (view.xs[it] - view.west) * scale }
        val py = DoubleArray(spec.pins.size) { (view.south + view.height - view.ys[it]) * scale }
        var land = ""
        for (tolerance in SIMPLIFY) {
            land = landPath(view, scale, height, tolerance)
            if (land.length <= MAX_PATH) break
        }
        val svg = StringBuilder()
        svg.append("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 $WIDTH $height'>")
        if (land.isNotEmpty()) {
            svg.append(
                "<path fill='currentColor' fill-opacity='.12' stroke='currentColor' stroke-opacity='.4' stroke-width='3' " +
                    "stroke-linejoin='round' fill-rule='evenodd' d='$land'/>",
            )
        }
        val lined = spec.line && spec.pins.size > 1
        val segments = ArrayList<DoubleArray>()
        val boxes = ArrayList<DoubleArray>()
        if (lined) {
            for (i in 0 until px.size - 1) segments += doubleArrayOf(px[i], py[i], px[i + 1], py[i + 1])
            for (arrow in arrows(px, py)) {
                boxes += doubleArrayOf(arrow[0] - ARROW_REACH, arrow[1] - ARROW_REACH, arrow[0] + ARROW_REACH, arrow[1] + ARROW_REACH)
            }
            appendLine(svg, px, py)
        }
        val circles = ArrayList<DoubleArray>()
        for (i in px.indices) {
            circles += doubleArrayOf(px[i], py[i], PIN_CLEARANCE)
            svg.append(
                "<circle cx='${round(
                    px[i],
                )}' cy='${round(py[i])}' r='${PIN_RADIUS.toInt()}' fill='$ACCENT' stroke='#fff' stroke-width='6'/>",
            )
        }
        for (i in px.indices) {
            val label = spec.pins[i].label?.let { displayLabel(it) } ?: continue
            val width = label.length * FONT_SIZE * 0.6
            val place = placeLabel(px[i], py[i], width, WIDTH.toDouble(), height.toDouble(), boxes, circles, segments)
            boxes += place.rect
            svg.append(
                "<text x='${round(place.x)}' y='${round(place.baseline)}' text-anchor='start' font-family='sans-serif' " +
                    "font-size='${FONT_SIZE.toInt()}' font-weight='600' fill='currentColor'>${escape(label)}</text>",
            )
        }
        svg.append("</svg>")
        return Figure(svg.toString(), spec.alt, WIDTH.toDouble() / height, isMap = true)
    }

    private fun arrows(
        px: DoubleArray,
        py: DoubleArray,
    ): List<DoubleArray> {
        val result = ArrayList<DoubleArray>()
        for (i in 0 until px.size - 1) {
            val dx = px[i + 1] - px[i]
            val dy = py[i + 1] - py[i]
            val length = sqrt(dx * dx + dy * dy)
            if (length < ARROW_MIN_LENGTH) continue
            result += doubleArrayOf((px[i] + px[i + 1]) / 2, (py[i] + py[i + 1]) / 2, dx / length, dy / length)
        }
        return result
    }

    private fun appendLine(
        svg: StringBuilder,
        px: DoubleArray,
        py: DoubleArray,
    ) {
        val points = px.indices.joinToString(" ") { "${round(px[it])},${round(py[it])}" }
        svg.append(
            "<polyline points='$points' fill='none' stroke='$ACCENT' stroke-opacity='.85' stroke-width='9' stroke-linejoin='round' stroke-linecap='round'/>",
        )
        for (arrow in arrows(px, py)) {
            val mx = arrow[0]
            val my = arrow[1]
            val ux = arrow[2]
            val uy = arrow[3]
            val tip = "${round(mx + ux * 34)} ${round(my + uy * 34)}"
            val left = "${round(mx - ux * 22 - uy * 26)} ${round(my - uy * 22 + ux * 26)}"
            val right = "${round(mx - ux * 22 + uy * 26)} ${round(my - uy * 22 - ux * 26)}"
            svg.append("<path d='M$tip L$left L$right Z' fill='$ACCENT' stroke='#fff' stroke-width='3' stroke-linejoin='round'/>")
        }
    }

    class Place(
        val x: Double,
        val baseline: Double,
        val rect: DoubleArray,
    )

    fun placeLabel(
        pinX: Double,
        pinY: Double,
        width: Double,
        viewWidth: Double,
        viewHeight: Double,
        boxes: List<DoubleArray>,
        circles: List<DoubleArray> = emptyList(),
        segments: List<DoubleArray> = emptyList(),
    ): Place {
        val half = FONT_SIZE / 2
        val reach = PIN_RADIUS + LABEL_GAP
        val offsets = ArrayList<DoubleArray>(24)
        for (ring in RINGS) {
            val side = reach * ring
            val diagonal = side * 0.7
            offsets += doubleArrayOf(side, 0.0)
            offsets += doubleArrayOf(-side - width, 0.0)
            offsets += doubleArrayOf(-width / 2, -side - half)
            offsets += doubleArrayOf(-width / 2, side + half)
            offsets += doubleArrayOf(diagonal, -diagonal - half)
            offsets += doubleArrayOf(-diagonal - width, -diagonal - half)
            offsets += doubleArrayOf(diagonal, diagonal + half)
            offsets += doubleArrayOf(-diagonal - width, diagonal + half)
        }
        val own = doubleArrayOf(pinX, pinY, PIN_CLEARANCE)
        val minX = LABEL_MARGIN
        val maxX = max(LABEL_MARGIN, viewWidth - LABEL_MARGIN - width)
        val minY = LABEL_MARGIN + half
        val maxY = max(minY, viewHeight - LABEL_MARGIN - half)
        var best: Place? = null
        var bestCost = 0.0
        for (index in offsets.indices) {
            val x = (pinX + offsets[index][0]).coerceIn(minX, maxX)
            val centerY = (pinY + offsets[index][1]).coerceIn(minY, maxY)
            val rect = doubleArrayOf(x, centerY - half, x + width, centerY + half)
            val moved = abs(x - (pinX + offsets[index][0])) + abs(centerY - (pinY + offsets[index][1]))
            var cost = index * 2.0 + moved * 0.5 + circleCost(own, rect)
            for (circle in circles) cost += circleCost(circle, rect)
            for (box in boxes) cost += boxCost(box, rect)
            for (segment in segments) cost += segmentCost(segment, rect)
            if (best == null || cost < bestCost) {
                best = Place(x, centerY + FONT_SIZE * 0.35, rect)
                bestCost = cost
            }
        }
        return best!!
    }

    private fun boxCost(
        box: DoubleArray,
        rect: DoubleArray,
    ): Double {
        val w = min(box[2], rect[2] + LABEL_CLEARANCE) - max(box[0], rect[0] - LABEL_CLEARANCE)
        val h = min(box[3], rect[3] + LABEL_CLEARANCE) - max(box[1], rect[1] - LABEL_CLEARANCE)
        return if (w > 0 && h > 0) COLLISION + w * h / 50 else 0.0
    }

    private fun circleCost(
        circle: DoubleArray,
        rect: DoubleArray,
    ): Double {
        val dx = circle[0] - circle[0].coerceIn(rect[0], rect[2])
        val dy = circle[1] - circle[1].coerceIn(rect[1], rect[3])
        val depth = circle[2] + LABEL_CLEARANCE - sqrt(dx * dx + dy * dy)
        return if (depth > 0) COLLISION + depth * 20 else 0.0
    }

    private fun segmentCost(
        segment: DoubleArray,
        rect: DoubleArray,
    ): Double {
        val left = rect[0] - LINE_CLEARANCE
        val top = rect[1] - LINE_CLEARANCE
        val right = rect[2] + LINE_CLEARANCE
        val bottom = rect[3] + LINE_CLEARANCE
        val dx = segment[2] - segment[0]
        val dy = segment[3] - segment[1]
        var enter = 0.0
        var leave = 1.0
        val p = doubleArrayOf(-dx, dx, -dy, dy)
        val q = doubleArrayOf(segment[0] - left, right - segment[0], segment[1] - top, bottom - segment[1])
        for (i in 0 until 4) {
            if (p[i] == 0.0) {
                if (q[i] < 0) return 0.0
            } else {
                val t = q[i] / p[i]
                if (p[i] < 0) enter = max(enter, t) else leave = min(leave, t)
            }
        }
        if (enter >= leave) return 0.0
        return COLLISION + (leave - enter) * sqrt(dx * dx + dy * dy)
    }

    fun displayLabel(label: String): String {
        val clean = StringBuilder()
        var i = 0
        val text = label.trimSpec()
        while (i < text.length) {
            val c = text[i]
            when {
                c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate() -> {
                    clean.append(c).append(text[i + 1])
                    i++
                }
                c.isHighSurrogate() || c.isLowSurrogate() || c == '\uFFFE' || c == '\uFFFF' -> {}
                isSpecWhitespace(c) || c.code < 0x20 || c.code == 0x7F -> if (clean.isNotEmpty() && clean.last() != ' ') clean.append(' ')
                else -> clean.append(c)
            }
            i++
        }
        var result = clean.toString().trimEnd()
        if (result.length > MAX_LABEL) {
            var end = MAX_LABEL - 1
            if (result[end - 1].isHighSurrogate()) end--
            result = result.substring(0, end).trimEnd() + "\u2026"
        }
        return result
    }

    private fun escape(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun positiveModulo(
        value: Double,
        modulus: Double,
    ): Double {
        val r = value % modulus
        return if (r < 0) r + modulus else r
    }

    private fun round(value: Double): Int = floor(value + 0.5).toInt()

    private fun landPath(
        view: MapView,
        scale: Double,
        height: Int,
        tolerance: Double,
    ): String {
        val out = StringBuilder()
        val east = view.west + view.width
        val north = view.south + view.height
        val margin = CLIP_MARGIN / scale
        val limit = tolerance * tolerance
        for (country in WorldData.countries) {
            val minY = country.minY / WorldData.UNIT
            val maxY = country.maxY / WorldData.UNIT
            if (maxY < view.south - margin || minY > north + margin) continue
            for (copy in -1..1) {
                val offset = copy * 360.0
                if (country.maxX / WorldData.UNIT + offset < view.west - margin ||
                    country.minX / WorldData.UNIT + offset > east + margin
                ) {
                    continue
                }
                for (ring in country.rings) emitRing(out, ring, offset, view, north, scale, height, limit)
            }
        }
        return out.toString()
    }

    private fun emitRing(
        out: StringBuilder,
        ring: IntArray,
        offset: Double,
        view: MapView,
        north: Double,
        scale: Double,
        height: Int,
        limit: Double,
    ) {
        val count = ring.size / 2
        val xs = DoubleArray(count)
        val ys = DoubleArray(count)
        var kept = 0
        for (i in 0 until count) {
            val x = (ring[2 * i] / WorldData.UNIT + offset - view.west) * scale
            val y = (north - ring[2 * i + 1] / WorldData.UNIT) * scale
            if (kept > 0) {
                val dx = x - xs[kept - 1]
                val dy = y - ys[kept - 1]
                if (dx * dx + dy * dy < limit) continue
            }
            xs[kept] = x
            ys[kept] = y
            kept++
        }
        if (kept < 3) return
        var minX = xs[0]
        var maxX = xs[0]
        var minY = ys[0]
        var maxY = ys[0]
        for (i in 1 until kept) {
            minX = min(minX, xs[i])
            maxX = max(maxX, xs[i])
            minY = min(minY, ys[i])
            maxY = max(maxY, ys[i])
        }
        val left = -CLIP_MARGIN
        val top = -CLIP_MARGIN
        val right = WIDTH + CLIP_MARGIN
        val bottom = height + CLIP_MARGIN
        if (maxX < left || minX > right || maxY < top || minY > bottom) return
        if (maxX - minX < 3.0 && maxY - minY < 3.0) return
        var polygon = Polygon(xs.copyOf(kept), ys.copyOf(kept))
        if (minX < left || maxX > right || minY < top || maxY > bottom) {
            polygon = clip(polygon, left, top, right, bottom)
            if (polygon.size < 3) return
        }
        var lastX = round(polygon.xs[0])
        var lastY = round(polygon.ys[0])
        out
            .append('M')
            .append(lastX)
            .append(' ')
            .append(lastY)
            .append('l')
        for (i in 1 until polygon.size) {
            val x = round(polygon.xs[i])
            val y = round(polygon.ys[i])
            if (x == lastX && y == lastY) continue
            out
                .append(x - lastX)
                .append(' ')
                .append(y - lastY)
                .append(' ')
            lastX = x
            lastY = y
        }
        if (out.last() == ' ') out.setLength(out.length - 1)
        out.append('z')
    }

    class Polygon(
        val xs: DoubleArray,
        val ys: DoubleArray,
    ) {
        val size: Int get() = xs.size
    }

    fun clip(
        polygon: Polygon,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
    ): Polygon {
        var current = polygon
        for (edge in 0 until 4) {
            if (current.size == 0) break
            val resultX = ArrayList<Double>(current.size + 4)
            val resultY = ArrayList<Double>(current.size + 4)
            for (i in 0 until current.size) {
                val ax = current.xs[(i + current.size - 1) % current.size]
                val ay = current.ys[(i + current.size - 1) % current.size]
                val bx = current.xs[i]
                val by = current.ys[i]
                val aIn = inside(edge, ax, ay, left, top, right, bottom)
                val bIn = inside(edge, bx, by, left, top, right, bottom)
                if (aIn != bIn) {
                    val (x, y) = intersect(edge, ax, ay, bx, by, left, top, right, bottom)
                    resultX += x
                    resultY += y
                }
                if (bIn) {
                    resultX += bx
                    resultY += by
                }
            }
            current = Polygon(resultX.toDoubleArray(), resultY.toDoubleArray())
        }
        return current
    }

    private fun inside(
        edge: Int,
        x: Double,
        y: Double,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
    ): Boolean =
        when (edge) {
            0 -> x >= left
            1 -> x <= right
            2 -> y >= top
            else -> y <= bottom
        }

    private fun intersect(
        edge: Int,
        ax: Double,
        ay: Double,
        bx: Double,
        by: Double,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
    ): Pair<Double, Double> {
        if (edge < 2) {
            val x = if (edge == 0) left else right
            val t = if (abs(bx - ax) < 1e-12) 0.0 else (x - ax) / (bx - ax)
            return x to ay + t * (by - ay)
        }
        val y = if (edge == 2) top else bottom
        val t = if (abs(by - ay) < 1e-12) 0.0 else (y - ay) / (by - ay)
        return ax + t * (bx - ax) to y
    }
}
