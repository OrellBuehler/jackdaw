package ch.orellbuehler.jackdaw.ui.theme

import androidx.compose.ui.graphics.Color
import ch.orellbuehler.jackdaw.format.TokenKind
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodeColorsTest {
    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    @Test
    fun everyTokenColourIsReadableOnItsSurfaceInBothThemes() {
        for ((name, colors) in listOf("light" to LightCodeColors, "dark" to DarkCodeColors)) {
            for (kind in TokenKind.entries) {
                val ratio = contrast(colors.of(kind), colors.surface)
                assertTrue(ratio >= 4.5, "$name $kind has a contrast of $ratio")
            }
        }
    }

    @Test
    fun theKindsHaveDistinctColours() {
        for (colors in listOf(LightCodeColors, DarkCodeColors)) {
            assertEquals(
                TokenKind.entries.size,
                TokenKind.entries
                    .map { colors.of(it) }
                    .toSet()
                    .size,
            )
        }
    }
}
