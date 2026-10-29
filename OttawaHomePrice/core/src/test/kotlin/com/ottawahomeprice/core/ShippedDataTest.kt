package com.ottawahomeprice.core

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks on the data the app ships (app/src/main/assets/data), so a bad edit fails the build. */
class ShippedDataTest {
    private val data = TestData.shipped
    private val estimator = Estimator(data)

    private val inside = mapOf(
        "Parliament Hill" to LatLon(45.4236, -75.7009),
        "Kanata" to LatLon(45.3088, -75.8987),
        "Orléans" to LatLon(45.4685, -75.5155),
        "Barrhaven" to LatLon(45.2736, -75.7368),
        "Manotick" to LatLon(45.2260, -75.6833),
        "Stittsville" to LatLon(45.2590, -75.9220),
        "Carp" to LatLon(45.3486, -76.0388),
        "Constance Bay" to LatLon(45.4985, -76.0875),
        "Fitzroy Harbour" to LatLon(45.4720, -76.2100),
        "Kinburn" to LatLon(45.3830, -76.1880),
        "Munster" to LatLon(45.1700, -75.9100),
        "North Gower" to LatLon(45.1300, -75.7170),
        "Richmond" to LatLon(45.1910, -75.8350),
        "Osgoode" to LatLon(45.1480, -75.5990),
        "Metcalfe" to LatLon(45.2300, -75.4660),
        "Greely" to LatLon(45.2630, -75.5590),
        "Vars" to LatLon(45.3530, -75.3530),
        "Navan" to LatLon(45.4200, -75.4260),
        "Cumberland" to LatLon(45.5170, -75.4050),
        "Carlsbad Springs" to LatLon(45.3650, -75.4560),
        "Dunrobin" to LatLon(45.4500, -75.9660),
        "Airport" to LatLon(45.3225, -75.6692),
    )

    private val outside = mapOf(
        "Gatineau" to LatLon(45.4765, -75.7013),
        "Hull" to LatLon(45.4300, -75.7200),
        "Aylmer" to LatLon(45.3960, -75.8440),
        "Arnprior" to LatLon(45.4350, -76.3550),
        "Pakenham" to LatLon(45.3370, -76.2880),
        "Almonte" to LatLon(45.2270, -76.1940),
        "Carleton Place" to LatLon(45.1400, -76.1410),
        "Kemptville" to LatLon(45.0160, -75.6380),
        "Russell" to LatLon(45.2570, -75.3590),
        "Embrun" to LatLon(45.2730, -75.2740),
        "Rockland" to LatLon(45.5460, -75.2930),
        "Winchester" to LatLon(45.0920, -75.3530),
    )

    @Test
    fun boundaryHasTheCityInsideAndNeighboursOutside() {
        for ((name, p) in inside) assertTrue("$name should be in Ottawa", data.boundary.contains(p))
        for ((name, p) in outside) assertFalse("$name should be outside Ottawa", data.boundary.contains(p))
        // The amalgamated city is about 2,790 km².
        assertEquals(2790.0, abs(data.boundary.signedAreaKm2()), 300.0)
    }

    @Test
    fun theRiverbankCountsAndTheOtherSideDoesnt() {
        // Neighbourhood centres a little past the simplified river boundary are still Ottawa.
        for (n in data.neighbourhoods) assertTrue("${n.name}", estimator.inCity(n.location))
        for ((name, p) in inside) assertTrue(name, estimator.inCity(p))
        for ((name, p) in outside) assertFalse(name, estimator.inCity(p))
        // Across the river from downtown, Rockcliffe and Westboro: Quebec.
        for (p in listOf(LatLon(45.4600, -75.6900), LatLon(45.4380, -75.7250), LatLon(45.4150, -75.7700), LatLon(45.4300, -75.7400))) {
            assertFalse("$p", estimator.inCity(p))
        }
        // At Cumberland the river is only about 650 m wide (the ferry landings): Masson-Angers and
        // the mouth of the Lièvre are Quebec, the Cumberland landing is Ottawa.
        assertFalse(data.boundary.contains(LatLon(45.5267, -75.4098)))
        assertFalse(estimator.inCity(LatLon(45.5320, -75.4098)))
        assertFalse(estimator.inCity(LatLon(45.5300, -75.4200)))
        assertFalse(estimator.inCity(LatLon(45.5236, -75.4306)))
        assertTrue(estimator.inCity(LatLon(45.5207, -75.4125)))
    }

    @Test
    fun annualSeriesIsContinuous() {
        val mls = data.annual.filter { it.kind == AnnualFigure.Kind.MLS }
        val years = mls.map { it.year }
        assertEquals("one figure per year", years.toSet().size, years.size)
        assertEquals("no gaps", (years.first()..years.last()).toList(), years)
        assertTrue("starts by 1960", years.first() <= 1960)
        assertTrue("runs to at least 2025", years.last() >= 2025)
        for (i in 1 until mls.size) {
            val change = mls[i].price / mls[i - 1].price - 1
            assertTrue("${mls[i].year}: implausible change ${change * 100}%", change > -0.2 && change < 0.4)
        }
        val sourced = data.annual + emptyList()
        for (a in sourced) assertTrue("${a.year}: unknown source ${a.source}", a.source in data.sources)
    }

    @Test
    fun monthlyFiguresAreOrderedAndSourced() {
        val months = data.monthly.map { it.month }
        assertEquals(months.sorted(), months)
        assertEquals(months.toSet().size, months.size)
        for (m in data.monthly) {
            assertTrue("${m.month}: unknown source ${m.source}", m.source in data.sources)
            assertTrue("${m.month}: ${m.price}", m.price in 100_000.0..3_000_000.0)
        }
        assertTrue(data.dataAsOf >= (months.lastOrNull() ?: data.dataAsOf))
    }

    @Test
    fun cpiCoversEveryYear() {
        val years = data.cpi.keys.sorted()
        assertEquals(1914, years.first())
        assertEquals((1914..years.last()).toList(), years)
        assertTrue(years.last() >= 2025)
        assertEquals(100.0, data.cpi.getValue(2002), 0.6)
        for (i in 1 until years.size) {
            val change = data.cpi.getValue(years[i]) / data.cpi.getValue(years[i - 1]) - 1
            assertTrue("CPI ${years[i]}: ${change * 100}%", change > -0.12 && change < 0.20)
        }
    }

    @Test
    fun neighbourhoodsAreInOttawaAndSane() {
        val names = data.neighbourhoods.map { Text.normalize(it.name) }
        assertEquals("duplicate names", names.toSet().size, names.size)
        assertTrue("need city-wide coverage", data.neighbourhoods.size >= 120)
        for (n in data.neighbourhoods) {
            assertTrue("${n.name} is outside the boundary", estimator.inOttawa(n.location, toleranceKm = 0.3))
            assertTrue("${n.name}: factor ${n.factor}", n.factor in 0.35..4.5)
            assertTrue("${n.name}: radius ${n.radiusKm}", n.radiusKm in 0.2..8.0)
            for (s in n.sources) assertTrue("${n.name}: unknown source $s", s in data.sources)
        }
        val aliasOwners = HashMap<String, String>()
        for (n in data.neighbourhoods) for (a in n.aliases.map(Text::normalize)) {
            val owner = aliasOwners.put(a, n.name)
            assertTrue("alias '$a' used by ${n.name} and $owner", owner == null || owner == n.name)
            val other = data.neighbourhoods.firstOrNull { it !== n && Text.normalize(it.name) == a }
            assertTrue("alias '$a' of ${n.name} is another neighbourhood's name", other == null)
        }
    }

    @Test
    fun everyPopulatedAreaIsNearAnAnchor() {
        // Every postal area centre, and every village checked above, is within reach of an anchor.
        for (area in data.postalAreas) {
            val e = estimator.locate(area.location)
            assertTrue("${area.fsa} is ${e.nearestKm} km from ${e.nearest.name}", e.nearestKm < 6.0)
        }
        for ((name, p) in inside) {
            val e = estimator.locate(p)
            assertTrue("$name is ${e.nearestKm} km from ${e.nearest.name}", e.nearestKm < 5.0)
        }
    }

    @Test
    fun postalAreasAreOttawaFsas() {
        val fsas = data.postalAreas.map { it.fsa }
        assertEquals(fsas.toSet().size, fsas.size)
        for (a in data.postalAreas) {
            assertTrue(a.fsa, Regex("K[0-9][A-Z]").matches(a.fsa))
            assertTrue("${a.fsa} outside Ottawa", estimator.inOttawa(a.location, toleranceKm = 1.0))
        }
        for (fsa in listOf("K1S", "K1Y", "K2J", "K2K", "K1C", "K4A", "K2S", "K4M", "K1P", "K1N")) {
            assertTrue("missing $fsa", fsa in fsas)
        }
    }

    @Test
    fun typesAreOrdered() {
        val ratio = { t: PropertyType -> estimator.typeRatio(t) }
        assertTrue(ratio(PropertyType.DETACHED) > 1.0)
        assertTrue(ratio(PropertyType.DETACHED) > ratio(PropertyType.SEMI))
        assertTrue(ratio(PropertyType.SEMI) > ratio(PropertyType.TOWNHOUSE) - 0.05)
        assertTrue(ratio(PropertyType.TOWNHOUSE) > ratio(PropertyType.CONDO))
        assertTrue(ratio(PropertyType.CONDO) < 1.0)
        for (t in PropertyType.entries) assertTrue("missing $t", t in data.types)
    }

    @Test
    fun metaIsConsistent() {
        assertTrue(data.firstMonth < data.dataAsOf)
        assertTrue(data.dataAsOf < data.lastMonth)
        assertEquals(data.dataAsOf, estimator.index.lastDataMonth)
    }

    @Test
    fun estimatesAreInTheRightBallpark() {
        val now = data.dataAsOf
        val city = estimator.index.price(now)
        assertTrue("city average $city", city in 500_000.0..900_000.0)
        val p1980 = estimator.index.price(Month.of(1980, 7))
        assertTrue("1980 $p1980", p1980 in 45_000.0..90_000.0)
        val p1956 = estimator.index.price(Month.of(1956, 7))
        assertTrue("1956 $p1956", p1956 in 8_000.0..25_000.0)
        val p1920 = estimator.index.price(Month.of(1920, 7))
        assertTrue("1920 $p1920", p1920 in 1_500.0..15_000.0)

        fun factorOf(name: String) = estimator.locate(data.neighbourhoods.first { it.name == name }.location).factor
        assertTrue(factorOf("Rockcliffe Park") > factorOf("Glebe"))
        assertTrue(factorOf("Glebe") > factorOf("Vanier"))
        assertTrue(factorOf("Westboro") > factorOf("Centretown"))
    }

    @Test
    fun cityAveragesNearOneOverPopulatedAreas() {
        // Weighted by where the postal areas are, relative prices average to roughly 1.
        val factors = data.postalAreas.map { estimator.locate(it.location).factor }
        val geometric = kotlin.math.exp(factors.sumOf { kotlin.math.ln(it) } / factors.size)
        assertTrue("mean factor $geometric", geometric in 0.8..1.25)
    }
}
