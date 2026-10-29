package com.ottawahomeprice.core

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sqrt

/** What an estimate for a given month rests on, from strongest to weakest. */
enum class Basis(val title: String, val detail: String) {
    RECENT("Recent sales", "Based on the latest monthly MLS® sales figures."),
    ANNUAL("Annual sales averages", "Interpolated between yearly MLS® average sale prices."),
    CENSUS("Census dwelling values", "Interpolated from census dwelling values; fewer data points."),
    BACKCAST("Inflation back-cast", "Before the earliest Ottawa price data: carried back with the consumer price index."),
    PROJECTION("Projection", "After the latest data: grown at an assumed long-run rate. Not a forecast of the market."),
}

/** The Canada consumer price index, interpolated monthly, for back-casting and today's-dollar figures. */
class Cpi(annual: Map<Int, Double>, latestMonth: Month?, latestValue: Double?, private val futureInflation: Double) {
    private val curve: Pchip
    private val lastTime: Double
    private val lastLog: Double

    init {
        require(annual.size >= 2) { "cpi.csv needs at least two years" }
        val knots = annual.entries.sortedBy { it.key }.map { it.key + 0.5 to ln(it.value) }.toMutableList()
        if (latestMonth != null && latestValue != null && latestMonth.time > knots.last().first + 1.0 / 24) {
            knots += latestMonth.time to ln(latestValue)
        }
        curve = Pchip(knots.map { it.first }.toDoubleArray(), knots.map { it.second }.toDoubleArray())
        lastTime = knots.last().first
        lastLog = knots.last().second
    }

    /** Index value at fractional year [time]; flat before the first year, grown at the assumed rate after the last. */
    fun at(time: Double): Double =
        if (time > lastTime) exp(lastLog + ln(1 + futureInflation) * (time - lastTime)) else exp(curve.value(time))

    fun at(month: Month): Double = at(month.time)
}

/**
 * Ottawa's average home price month by month: interpolated (monotone cubic, on log prices) through
 * yearly averages placed at mid-year and, past the last full year, the recent monthly sales; carried
 * back with the CPI before the first figure, and grown at an assumed rate after the last.
 *
 * Each month also gets a log-scale uncertainty that grows with distance from the data.
 */
class CityIndex(data: OttawaData) {

    private class Knot(val time: Double, val logPrice: Double, val basis: Basis)

    val cpi = Cpi(
        data.cpi,
        data.meta["cpi_latest_month"]?.let { Month.parse(it) },
        data.meta["cpi_latest"]?.toDoubleOrNull(),
        data.metaDouble("future_inflation"),
    )

    private val projectionGrowth = data.metaDouble("projection_growth")
    private val projectionSigma = data.metaDouble("projection_sigma")
    private val backcastRealGrowth = data.metaDouble("backcast_real_growth")
    private val backcastSigmaPerYear = data.metaDouble("backcast_sigma_per_year")
    private val sigmaAnnual = data.metaDouble("sigma_annual")
    private val sigmaRecent = data.metaDouble("sigma_recent")
    private val sigmaCensus = data.metaDouble("sigma_census")

    private val knots: List<Knot> = buildKnots(data)
    private val curve = Pchip(knots.map { it.time }.toDoubleArray(), knots.map { it.logPrice }.toDoubleArray())
    private val first = knots.first()
    private val last = knots.last()

    /** The last month whose middle is at or before the last data point; later months are projections. */
    val lastDataMonth: Month = Month(Math.floor(last.time * 12 - 0.5 + 1e-9).toInt())

    /** Fractional years of the knots and their prices, for tests and the methodology screen. */
    val knotTimes: List<Double> get() = knots.map { it.time }
    val knotPrices: List<Double> get() = knots.map { exp(it.logPrice) }

    private fun buildKnots(data: OttawaData): List<Knot> {
        val knots = data.annual.map {
            Knot(it.year + 0.5, ln(it.price), if (it.kind == AnnualFigure.Kind.CENSUS) Basis.CENSUS else Basis.ANNUAL)
        }.toMutableList()
        require(knots.isNotEmpty()) { "annual.csv is empty" }
        val lastYear = data.annual.last { it.kind == AnnualFigure.Kind.MLS }.year
        val lastYearTime = lastYear + 0.5

        // Months after the last full year. A trailing twelve-month average (free of the spring
        // peak) sits at its centre, or failing a full year, the plain average of the months there
        // are. The latest month's level is then the last point carried forward at the
        // year-over-year change of the latest months, which is also free of seasonality.
        val byMonth = data.monthly.associateBy { it.month }
        val latest = data.monthly.lastOrNull()?.month
        // Months past the middle of the last full year, which is where its average sits (a
        // December released with the year-end figures counts too).
        if (latest != null && latest.time > lastYearTime + 1.0 / 24) {
            val trailing = (0 until 12).mapNotNull { byMonth[latest - it] }
            val sinceLastYear = data.monthly.filter { it.month.year > lastYear }
            if (trailing.size == 12) {
                val center = (latest.time + (latest - 11).time) / 2
                if (center > knots.last().time + 0.25) knots += Knot(center, ln(weightedAverage(trailing)), Basis.RECENT)
            } else if (sinceLastYear.size >= 3) {
                // Leans a little seasonal, but better than nothing.
                val center = (sinceLastYear.first().month.time + sinceLastYear.last().month.time) / 2
                if (center > knots.last().time + 0.25) knots += Knot(center, ln(weightedAverage(sinceLastYear)), Basis.RECENT)
            }
            val pairs = (0 until 3).mapNotNull { k ->
                val now = byMonth[latest - k]
                val before = byMonth[latest - k - 12]
                if (now != null && before != null) now to before else null
            }
            if (pairs.isNotEmpty() && pairs.first().first.month == latest) {
                val yoy = weightedAverage(pairs.map { it.first }) / weightedAverage(pairs.map { it.second })
                val lastKnot = knots.last()
                knots += Knot(latest.time, lastKnot.logPrice + ln(yoy) * (latest.time - lastKnot.time), Basis.RECENT)
            }
        }
        return knots
    }

    private fun weightedAverage(months: List<MonthlyFigure>): Double {
        val units = months.map { it.units }
        return if (units.all { it != null && it > 0 }) {
            months.indices.sumOf { months[it].price * units[it]!! } / units.sumOf { it!! }
        } else {
            months.sumOf { it.price } / months.size
        }
    }

    /** Natural log of the average price at fractional year [time]. */
    fun logPrice(time: Double): Double = when {
        time < first.time ->
            first.logPrice + ln(cpi.at(time) / cpi.at(first.time)) + backcastRealGrowth * (time - first.time)
        time > last.time -> last.logPrice + ln(1 + projectionGrowth) * (time - last.time)
        else -> curve.value(time)
    }

    fun price(month: Month): Double = exp(logPrice(month.time))

    fun basis(month: Month): Basis {
        val t = month.time
        if (t < first.time) return Basis.BACKCAST
        if (t > last.time) return Basis.PROJECTION
        val i = curve.segment(t)
        val a = knots[i].basis
        val b = knots[min(i + 1, knots.size - 1)].basis
        // Months after the last full year rest on the recent monthly figures; otherwise the
        // weaker of the two knots around this month.
        if (b == Basis.RECENT) return Basis.RECENT
        return if (a.ordinal >= b.ordinal) a else b
    }

    /** One standard deviation of the log price: about the 68% range of what the true average could be. */
    fun sigma(month: Month): Double {
        val t = month.time
        return when (basis(month)) {
            Basis.RECENT -> sigmaRecent
            Basis.ANNUAL -> sigmaAnnual
            Basis.CENSUS -> sigmaCensus
            Basis.BACKCAST -> {
                val start = if (first.basis == Basis.CENSUS) sigmaCensus else sigmaAnnual
                min(0.6, start + backcastSigmaPerYear * (first.time - t))
            }
            Basis.PROJECTION -> min(0.8, sigmaRecent + projectionSigma * sqrt(t - last.time))
        }
    }

    /** [price] in dollars of [reference]. */
    fun inDollarsOf(price: Double, month: Month, reference: Month): Double = price * cpi.at(reference) / cpi.at(month)
}
