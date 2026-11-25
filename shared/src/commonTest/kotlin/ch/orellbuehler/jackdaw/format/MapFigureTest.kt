package ch.orellbuehler.jackdaw.format

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapFigureTest {
    private fun pin(
        lat: Double,
        lng: Double,
        label: String? = null,
    ) = MapPin(lat, lng, label)

    private fun spec(
        vararg pins: MapPin,
        line: Boolean = false,
    ) = MapSpec(pins.toList(), line, null)

    private val paris = pin(48.8566, 2.3522, "Paris")
    private val sydney = pin(-33.8688, 151.2093, "Sydney")

    @Test
    fun mercatorValues() {
        assertEquals(0.0, MapFigure.mercatorY(0.0), 1e-9)
        assertEquals(50.4984, MapFigure.mercatorY(45.0), 1e-3)
        assertEquals(75.4565, MapFigure.mercatorY(60.0), 1e-3)
        assertEquals(-75.4565, MapFigure.mercatorY(-60.0), 1e-3)
        assertEquals(180.0, MapFigure.mercatorY(MapFigure.MAX_LATITUDE), 1e-6)
        assertEquals(180.0, MapFigure.mercatorY(90.0), 1e-6)
        assertEquals(-180.0, MapFigure.mercatorY(-90.0), 1e-6)
    }

    @Test
    fun longitudesAreNormalized() {
        assertEquals(-170.0, MapFigure.normalizeLongitude(190.0), 1e-9)
        assertEquals(170.0, MapFigure.normalizeLongitude(-190.0), 1e-9)
        assertEquals(-180.0, MapFigure.normalizeLongitude(180.0), 1e-9)
        assertEquals(-180.0, MapFigure.normalizeLongitude(-180.0), 1e-9)
        assertEquals(0.0, MapFigure.normalizeLongitude(360.0), 1e-9)
        assertEquals(2.35, MapFigure.normalizeLongitude(2.35), 1e-9)
    }

    @Test
    fun aSinglePinShowsARegionAndIsCentered() {
        val view = MapFigure.view(listOf(paris))
        assertEquals(MapFigure.MIN_WIDTH_DEGREES, view.width, 1e-9)
        assertEquals(25.0, view.height, 1e-9)
        assertEquals(paris.lng, view.west + view.width / 2, 1e-9)
        assertEquals(MapFigure.mercatorY(paris.lat), view.south + view.height / 2, 1e-9)
        assertEquals(paris.lng, view.xs[0], 1e-9)
    }

    @Test
    fun farApartPinsAreAllVisibleWithMargin() {
        val view = MapFigure.view(listOf(paris, sydney, pin(40.7, -74.0), pin(-33.9, 18.4)))
        for (i in view.xs.indices) {
            val x = (view.xs[i] - view.west) / view.width
            val y = (view.ys[i] - view.south) / view.height
            assertTrue(x > 0.05 && x < 0.95, "x of pin $i is $x")
            assertTrue(y > 0.05 && y < 0.95, "y of pin $i is $y")
        }
        assertTrue(view.width <= 360.0)
        assertTrue(view.width > 200.0)
    }

    @Test
    fun theWholeWorldIsTheLargestView() {
        val view = MapFigure.view(listOf(pin(0.0, -135.0), pin(0.0, -45.0), pin(0.0, 45.0), pin(0.0, 135.0)))
        assertEquals(360.0, view.width, 1e-9)
        val wide = MapFigure.view(listOf(pin(80.0, 0.0), pin(-56.0, 10.0)))
        assertTrue(wide.height > wide.width / 1.6)
        assertTrue(wide.height <= 360.0)
    }

    @Test
    fun pinsOnBothSidesOfTheAntimeridianAreFramedTheShortWay() {
        val view = MapFigure.view(listOf(pin(-18.14, 178.44, "Suva"), pin(-13.83, -171.76, "Apia")))
        assertEquals(MapFigure.MIN_WIDTH_DEGREES, view.width, 1e-9)
        assertEquals(178.44, view.xs[0], 1e-9)
        assertEquals(188.24, view.xs[1], 1e-9)
        assertEquals(183.34, view.west + view.width / 2, 1e-9)
        val reversed = MapFigure.view(listOf(pin(-13.83, -171.76), pin(-18.14, 178.44)))
        assertEquals(view.west, reversed.west, 1e-9)
        val far = MapFigure.view(listOf(pin(35.68, 139.65), pin(21.3, -157.86)))
        assertEquals(139.65, far.xs[0], 1e-9)
        assertEquals(202.14, far.xs[1], 1e-9)
        assertTrue(far.width < 100.0)
    }

    @Test
    fun polarPinsAreClampedAndStayInTheView() {
        val view = MapFigure.view(listOf(pin(90.0, 0.0), pin(78.2, 15.6)))
        assertEquals(180.0, view.ys[0], 1e-6)
        assertTrue(view.south + view.height > view.ys[0])
        val figure = MapFigure.render(spec(pin(90.0, 0.0, "North Pole"), pin(-90.0, 0.0, "South Pole")))
        val centers =
            Regex("<circle cx='(-?\\d+)' cy='(-?\\d+)'")
                .findAll(figure.svg)
                .map {
                    it.groupValues[1].toInt() to
                        it.groupValues[2].toInt()
                }.toList()
        assertEquals(2, centers.size)
        val height = (MapFigure.WIDTH / figure.aspectRatio).toInt()
        assertTrue(centers.all { (x, y) -> x in 0..MapFigure.WIDTH && y in 0..height })
    }

    @Test
    fun polygonsAreClippedToTheView() {
        fun square(
            from: Double,
            to: Double,
        ) = MapFigure.Polygon(doubleArrayOf(from, to, to, from), doubleArrayOf(from, from, to, to))

        fun area(p: MapFigure.Polygon): Double {
            var sum = 0.0
            for (i in 0 until p.size) {
                val j = (i + 1) % p.size
                sum += p.xs[i] * p.ys[j] - p.xs[j] * p.ys[i]
            }
            return abs(sum) / 2
        }
        assertEquals(25.0, area(MapFigure.clip(square(-10.0, 10.0), 0.0, 0.0, 5.0, 5.0)), 1e-9)
        assertEquals(100.0, area(MapFigure.clip(square(1.0, 11.0), 0.0, 0.0, 20.0, 20.0)), 1e-9)
        assertEquals(0, MapFigure.clip(square(10.0, 20.0), 0.0, 0.0, 5.0, 5.0).size)
        val clipped = MapFigure.clip(square(-10.0, 10.0), 0.0, 0.0, 5.0, 5.0)
        assertTrue(clipped.xs.all { it in 0.0..5.0 } && clipped.ys.all { it in 0.0..5.0 })
    }

    @Test
    fun theWorldDataIsDecoded() {
        val countries = WorldData.countries
        assertTrue(countries.size > 200, "countries: ${countries.size}")
        val points = countries.sumOf { country -> country.rings.sumOf { it.size / 2 } }
        assertTrue(points > 20_000, "points: $points")
        for (country in countries) {
            for (ring in country.rings) {
                for (i in 0 until ring.size / 2) {
                    assertTrue(ring[2 * i] in country.minX..country.maxX && ring[2 * i + 1] in country.minY..country.maxY)
                }
            }
        }
    }

    private fun onLand(
        lat: Double,
        lng: Double,
    ): Boolean {
        val x = (lng * WorldData.UNIT).toInt()
        val y = (MapFigure.mercatorY(lat) * WorldData.UNIT).toInt()
        return WorldData.countries.any { country ->
            x in country.minX..country.maxX &&
                y in country.minY..country.maxY &&
                country.rings.count { insideRing(it, x, y) } % 2 == 1
        }
    }

    private fun insideRing(
        ring: IntArray,
        x: Int,
        y: Int,
    ): Boolean {
        var inside = false
        val n = ring.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = ring[2 * i]
            val yi = ring[2 * i + 1]
            val xj = ring[2 * j]
            val yj = ring[2 * j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi).toLong() * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    @Test
    fun knownPlacesLieOnLandAndTheOceansDoNot() {
        val cities =
            mapOf(
                "Paris" to (48.86 to 2.35),
                "Berlin" to (52.52 to 13.4),
                "Nairobi" to (-1.29 to 36.82),
                "Moscow" to (55.75 to 37.62),
                "Denver" to (39.74 to -104.99),
                "Delhi" to (28.61 to 77.21),
                "Canberra" to (-35.28 to 149.13),
                "Brasilia" to (-15.8 to -47.9),
                "Beijing" to (39.9 to 116.4),
                "Anchorage" to (61.2 to -149.9),
            )
        for ((name, place) in cities) assertTrue(onLand(place.first, place.second), name)
        for ((lat, lng) in listOf(0.0 to -30.0, -40.0 to -120.0, 30.0 to -40.0, -30.0 to 80.0)) assertFalse(onLand(lat, lng), "$lat,$lng")
    }

    @Test
    fun aSinglePinIsDrawnInTheMiddleOfTheView() {
        val figure = MapFigure.render(spec(paris))
        assertTrue(figure.svg.startsWith("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 2000 1250'>"))
        assertEquals(1.6, figure.aspectRatio, 1e-9)
        assertTrue("<circle cx='1000' cy='625' r='22'" in figure.svg)
        assertTrue(">Paris</text>" in figure.svg)
        assertTrue("<path fill='currentColor'" in figure.svg)
        assertFalse("<polyline" in figure.svg)
        assertTrue(figure.svg.endsWith("</svg>"))
        assertNotNull(FigureRules.check(figure.svg))
    }

    @Test
    fun linesAndDirectionMarksAreDrawnBetweenThePins() {
        val figure = MapFigure.render(spec(paris, sydney, line = true))
        assertEquals(1, Regex("<polyline").findAll(figure.svg).count())
        assertEquals(1, Regex("<path d='M").findAll(figure.svg).count())
        assertEquals(2, Regex("<circle").findAll(figure.svg).count())
        val single = MapFigure.render(spec(paris, line = true))
        assertFalse("<polyline" in single.svg)
        assertNotNull(FigureRules.check(figure.svg))
    }

    @Test
    fun mapsStayWellUnderTheFigureSizeLimit() {
        val world =
            spec(paris, sydney, pin(40.7, -74.0, "New York"), pin(-33.9, 18.4, "Cape Town"), pin(64.1, -21.9, "Reykjavik"), line = true)
        val worldSvg = MapFigure.render(world).svg
        assertTrue(worldSvg.length < 150_000, "world: ${worldSvg.length}")
        val regional = MapFigure.render(spec(pin(50.68, 4.41, "Waterloo"))).svg
        assertTrue(regional.length < 60_000, "regional: ${regional.length}")
        val empty = MapFigure.render(spec(pin(-45.0, -140.0, "Pacific"))).svg
        assertFalse("fill-rule" in empty)
        assertTrue(FigureRules.check(empty) != null)
    }

    @Test
    fun landIsDrawnOnBothSidesOfTheAntimeridian() {
        val figure = MapFigure.render(spec(pin(-18.14, 178.44, "Suva"), pin(-13.83, -171.76, "Apia")))
        assertTrue("fill-rule='evenodd'" in figure.svg)
        val circles = Regex("<circle cx='(\\d+)'").findAll(figure.svg).map { it.groupValues[1].toInt() }.toList()
        assertEquals(2, circles.size)
        assertTrue(circles[1] > circles[0])
        assertTrue(circles[1] - circles[0] < 1000)
    }

    @Test
    fun labelsDoNotCoverTheirPinNorEachOther() {
        val figure = MapFigure.render(spec(paris, pin(51.5, -0.12, "London"), pin(50.85, 4.35, "Brussels"), pin(52.52, 13.4, "Berlin")))
        val pins =
            Regex("<circle cx='(-?\\d+)' cy='(-?\\d+)'")
                .findAll(figure.svg)
                .map {
                    it.groupValues[1].toDouble() to
                        it.groupValues[2].toDouble()
                }.toList()
        val labels =
            Regex("<text x='(-?\\d+)' y='(-?\\d+)'[^>]*>([^<]*)</text>")
                .findAll(figure.svg)
                .map { Triple(it.groupValues[1].toDouble(), it.groupValues[2].toDouble(), it.groupValues[3]) }
                .toList()
        assertEquals(4, labels.size)
        val rects =
            labels.map { (x, baseline, text) ->
                val width = text.length * 70 * 0.6
                doubleArrayOf(x, baseline - 70 * 0.85, x + width, baseline + 70 * 0.15)
            }
        for (r in rects) {
            for ((px, py) in pins) assertFalse(px + 22 > r[0] && px - 22 < r[2] && py + 22 > r[1] && py - 22 < r[3], "label covers a pin")
            assertTrue(r[0] >= 0 && r[2] <= MapFigure.WIDTH && r[1] >= 0 && r[3] <= 1250)
        }
        for (i in rects.indices) {
            for (j in i + 1 until rects.size) {
                val a = rects[i]
                val b = rects[j]
                assertFalse(a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3], "labels $i and $j overlap")
            }
        }
    }

    @Test
    fun aLabelNearTheRightEdgeGoesToTheLeftOfItsPin() {
        val place = MapFigure.placeLabel(1950.0, 600.0, 300.0, 2000.0, 1250.0, emptyList())
        assertTrue(place.rect[2] < 1950.0)
        val room = MapFigure.placeLabel(500.0, 600.0, 300.0, 2000.0, 1250.0, emptyList())
        assertTrue(room.x > 500.0)
        val blocked = MapFigure.placeLabel(500.0, 600.0, 300.0, 2000.0, 1250.0, listOf(doubleArrayOf(530.0, 560.0, 900.0, 640.0)))
        assertTrue(blocked.rect[2] < 500.0)
    }

    @Test
    fun labelsAreCleanedShortenedAndEscaped() {
        assertEquals("Battle of Hastings", MapFigure.displayLabel("  Battle\n of \t Hastings  "))
        assertEquals("A".repeat(29) + "…", MapFigure.displayLabel("A".repeat(40)))
        assertEquals("A".repeat(30), MapFigure.displayLabel("A".repeat(30)))
        assertEquals("a b", MapFigure.displayLabel("a\u0000b\uD800"))
        assertEquals("😀 x", MapFigure.displayLabel("😀 x"))
        val figure = MapFigure.render(spec(pin(10.0, 10.0, "<b onload=x> & \"q\"")))
        assertTrue("&lt;b onload=x&gt; &amp; \"q\"</text>" in figure.svg)
        assertFalse("<b" in figure.svg)
    }

    @Test
    fun theFigureCarriesTheAltText() {
        val figure = MapFigure.render(MapSpec(listOf(paris), false, "A pin in Paris."))
        assertEquals("A pin in Paris.", figure.alt)
        assertNull(MapFigure.render(spec(paris)).alt)
    }
}
