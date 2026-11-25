package ch.orellbuehler.jackdaw.format

internal class WorldCountry(
    val minX: Int,
    val minY: Int,
    val maxX: Int,
    val maxY: Int,
    private val encoded: String,
) {
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
