package com.ottawahomeprice.core

import kotlin.math.exp
import kotlin.math.sqrt

enum class PropertyType(val key: String) {
    ALL("all"), DETACHED("detached"), SEMI("semi"), TOWNHOUSE("townhouse"), CONDO("condo");

    companion object {
        fun parse(text: String): PropertyType =
            entries.firstOrNull { it.key == text.trim().lowercase() } ?: throw IllegalArgumentException("Unknown property type '$text'")
    }
}

/** The estimate for one place, month and type of home. */
data class Estimate(
    val month: Month,
    val type: PropertyType,
    val price: Double,
    val low: Double,
    val high: Double,
    /** Ottawa's average for all homes that month. */
    val cityAverage: Double,
    /** Price here relative to the city average for the same month (location and type together). */
    val relative: Double,
    val basis: Basis,
    val sigma: Double,
    val location: LocationEstimate?,
    /** [price] in dollars of [referenceMonth]. */
    val inReferenceDollars: Double,
    val referenceMonth: Month,
)

/** Monthly estimates over a span, for the chart. */
class PriceSeries(
    val start: Month,
    val price: DoubleArray,
    val low: DoubleArray,
    val high: DoubleArray,
    val city: DoubleArray,
    /** CPI ratio that turns each month's dollars into [referenceMonth] dollars. */
    val toReference: DoubleArray,
    val referenceMonth: Month,
) {
    val size: Int get() = price.size
    fun month(i: Int): Month = start + i
    fun indexOf(month: Month): Int = (month - start).coerceIn(0, size - 1)
}

/**
 * Average home price at any spot in Ottawa in any month: the city-wide average for that month
 * ([CityIndex]) times how the spot compares with the city ([LocationModel]) times how the chosen
 * type of home compares with all homes.
 */
class Estimator(val data: OttawaData) {
    val index = CityIndex(data)
    val locations = LocationModel(data.neighbourhoods)
    val places = PlaceIndex(data.neighbourhoods, data.postalAreas)

    /** Months the app offers. */
    val range: ClosedRange<Month> = data.firstMonth..data.lastMonth

    /** Months are given in dollars of this month when adjusting for inflation. */
    val referenceMonth: Month get() = data.dataAsOf

    private val typeSigma = data.metaDouble("sigma_type")

    private companion object {
        /** How far outside the drawn boundary a point in a neighbourhood's area still counts. */
        const val EDGE_SLACK_KM = 0.6
    }

    fun locate(point: LatLon): LocationEstimate = locations.estimate(point)

    /** Whether [point] is inside the City of Ottawa, allowing [toleranceKm] beyond the drawn boundary. */
    fun inOttawa(point: LatLon, toleranceKm: Double = 0.0): Boolean =
        data.boundary.contains(point) || (toleranceKm > 0 && data.boundary.distanceToEdgeKm(point) <= toleranceKm)

    /**
     * Whether [point] belongs to the city, forgiving the simplified boundary where it runs down the
     * Ottawa River: a point up to [EDGE_SLACK_KM] outside it still counts when it lies in a
     * neighbourhood that itself sits on the boundary (Champlain Park, say). A big rural area well
     * inside the line (Cumberland) doesn't stretch it, so the Quebec shore opposite stays out.
     */
    fun inCity(point: LatLon): Boolean {
        if (data.boundary.contains(point)) return true
        if (data.boundary.distanceToEdgeKm(point) > EDGE_SLACK_KM) return false
        val around = locate(point)
        val centre = around.area.location
        val centreDepth = if (data.boundary.contains(centre)) data.boundary.distanceToEdgeKm(centre) else 0.0
        return around.areaKm <= around.area.radiusKm && centreDepth <= EDGE_SLACK_KM
    }

    fun typeRatio(type: PropertyType): Double = if (type == PropertyType.ALL) 1.0 else data.types[type]?.ratio ?: 1.0

    private fun sigmaFor(location: LocationEstimate?, type: PropertyType, timeSigma: Double): Double {
        val loc = location?.sigma ?: 0.0
        val t = if (type == PropertyType.ALL) 0.0 else typeSigma
        return sqrt(timeSigma * timeSigma + loc * loc + t * t)
    }

    fun estimate(location: LocationEstimate?, month: Month, type: PropertyType): Estimate {
        val city = index.price(month)
        val relative = (location?.factor ?: 1.0) * typeRatio(type)
        val price = city * relative
        val sigma = sigmaFor(location, type, index.sigma(month))
        return Estimate(
            month = month,
            type = type,
            price = price,
            low = price * exp(-sigma),
            high = price * exp(sigma),
            cityAverage = city,
            relative = relative,
            basis = index.basis(month),
            sigma = sigma,
            location = location,
            inReferenceDollars = index.inDollarsOf(price, month, referenceMonth),
            referenceMonth = referenceMonth,
        )
    }

    fun series(location: LocationEstimate?, type: PropertyType, from: Month = range.start, to: Month = range.endInclusive): PriceSeries {
        val n = to - from + 1
        require(n > 0) { "Empty range" }
        val relative = (location?.factor ?: 1.0) * typeRatio(type)
        val price = DoubleArray(n)
        val low = DoubleArray(n)
        val high = DoubleArray(n)
        val city = DoubleArray(n)
        val toReference = DoubleArray(n)
        val referenceCpi = index.cpi.at(referenceMonth)
        for (i in 0 until n) {
            val month = from + i
            val c = index.price(month)
            val p = c * relative
            val s = sigmaFor(location, type, index.sigma(month))
            city[i] = c
            price[i] = p
            low[i] = p * exp(-s)
            high[i] = p * exp(s)
            toReference[i] = referenceCpi / index.cpi.at(month)
        }
        return PriceSeries(from, price, low, high, city, toReference, referenceMonth)
    }
}
