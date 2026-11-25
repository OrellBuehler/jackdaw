package ch.orellbuehler.jackdaw.format

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal class WorldCountry(
    val minX: Int,
    val minY: Int,
    val maxX: Int,
    val maxY: Int,
    private val encoded: String,
) {
    val ringBoxes: List<DoubleArray> by lazy { rings.map { WorldData.ringBox(it) } }

    val rings: List<IntArray> by lazy { encoded.split(' ').filter { it.isNotEmpty() }.map { WorldData.decodeRing(it) } }
}

internal object WorldData {
    const val UNIT = 100.0
    private const val FINAL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef"
    private const val MORE = "ghijklmnopqrstuvwxyz0123456789-_"
    private val values =
        IntArray(128) { -1 }.also { table ->
            FINAL.forEachIndexed { i, c -> table[c.code] = i }
            MORE.forEachIndexed { i, c -> table[c.code] = 32 + i }
        }

    val countries: List<WorldCountry> by lazy { parse(GENERATED_WORLD_CHUNKS.joinToString("")) }

    fun parse(text: String): List<WorldCountry> {
        val result = ArrayList<WorldCountry>(256)
        for (line in text.lineSequence().drop(1)) {
            val space = line.indexOf(' ')
            if (space < 0) continue
            val box = line.substring(0, space).split(',').map { it.toInt() }
            result += WorldCountry(box[0], box[1], box[2], box[3], line.substring(space + 1))
        }
        return result
    }

    fun ringBox(ring: IntArray): DoubleArray {
        val n = ring.size / 2
        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        var twice = 0.0
        for (i in 0 until n) {
            val x = ring[2 * i] / UNIT
            val y = ring[2 * i + 1] / UNIT
            val j = (i + 1) % n
            twice += x * (ring[2 * j + 1] / UNIT) - (ring[2 * j] / UNIT) * y
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        return doubleArrayOf(minX, minY, maxX, maxY, abs(twice) / 2)
    }

    // Estimated share of the view (west, south, width, height in Mercator degrees) that is land: the area of every ring
    // times the part of its bounding box that lies in the view. Cheap and deterministic, not exact.
    fun landShare(
        west: Double,
        south: Double,
        width: Double,
        height: Double,
    ): Double {
        val east = west + width
        val north = south + height
        var land = 0.0
        for (country in countries) {
            if (country.maxY / UNIT < south || country.minY / UNIT > north) continue
            for (copy in -1..1) {
                val offset = copy * 360.0
                if (country.maxX / UNIT + offset < west || country.minX / UNIT + offset > east) continue
                for (box in country.ringBoxes) {
                    val w = min(box[2] + offset, east) - max(box[0] + offset, west)
                    val h = min(box[3], north) - max(box[1], south)
                    if (w <= 0.0 || h <= 0.0) continue
                    val boxArea = (box[2] - box[0]) * (box[3] - box[1])
                    land += if (boxArea <= 0.0) 0.0 else box[4] * min(1.0, w * h / boxArea)
                }
            }
        }
        return land / (width * height)
    }

    fun decodeRing(text: String): IntArray {
        val numbers = ArrayList<Int>(text.length / 2)
        var acc = 0
        var shift = 0
        for (c in text) {
            val value = if (c.code < 128) values[c.code] else -1
            if (value < 0) continue
            acc += (value and 31) shl shift
            if (value < 32) {
                numbers += (acc ushr 1) xor -(acc and 1)
                acc = 0
                shift = 0
            } else {
                shift += 5
            }
        }
        val count = numbers.size / 2
        val ring = IntArray(count * 2)
        var x = 0
        var y = 0
        for (i in 0 until count) {
            x += numbers[2 * i]
            y += numbers[2 * i + 1]
            ring[2 * i] = x
            ring[2 * i + 1] = y
        }
        return ring
    }
}
