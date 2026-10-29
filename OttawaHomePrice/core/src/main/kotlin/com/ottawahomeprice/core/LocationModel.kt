package com.ottawahomeprice.core

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sqrt

/** How prices at one spot compare with Ottawa's average, and which neighbourhoods that comes from. */
data class LocationEstimate(
    val point: LatLon,
    /** Average home price here divided by the Ottawa-wide average. */
    val factor: Double,
    /** One standard deviation of log(factor). */
    val sigma: Double,
    /** The closest neighbourhood centre, and how far it is. */
    val nearest: Neighbourhood,
    val nearestKm: Double,
    /** The neighbourhood the point is most inside: closest relative to each one's size. */
    val area: Neighbourhood,
    val areaKm: Double,
    /** The neighbourhoods that shape the estimate, with their share of the weight (largest first). */
    val contributors: List<Pair<Neighbourhood, Double>>,
) {
    /** "Westboro", or "near Westboro" when the point is beyond the neighbourhood's usual extent. */
    val areaLabel: String
        get() = if (areaKm <= area.radiusKm * 1.25) area.name else "near ${area.name}"
}

/**
 * Relative price levels across the city, blended from neighbourhood anchors with a distance
 * kernel. Each anchor has a radius (its area's extent): within it the anchor dominates, a couple
 * of radii away its pull fades, and a long thin tail makes the nearest anchors win far from all
 * of them. Factors are averaged as logs, so a 2x and a 0.5x neighbour blend to 1x.
 */
class LocationModel(private val places: List<Neighbourhood>) {
    private val projection = LocalProjection.OTTAWA
    private val xs = DoubleArray(places.size) { projection.x(places[it].location.lon) }
    private val ys = DoubleArray(places.size) { projection.y(places[it].location.lat) }
    private val logFactors = DoubleArray(places.size) { ln(places[it].factor) }
    private val inverseRadii = DoubleArray(places.size) { 1 / places[it].radiusKm }
    private val weights = DoubleArray(places.size) { places[it].confidence.weight }

    init {
        require(places.isNotEmpty()) { "No neighbourhoods" }
        places.forEach { require(it.factor > 0 && it.radiusKm > 0) { "Bad neighbourhood ${it.name}" } }
    }

    private fun kernel(r: Double): Double {
        val r2 = r * r
        return exp(-0.5 * r2) + TAIL / (1 + r2 * r2)
    }

    /** Just the factor, for drawing the map's shading. */
    fun factorAt(lat: Double, lon: Double): Double {
        val px = projection.x(lon)
        val py = projection.y(lat)
        var sum = 0.0
        var total = 0.0
        for (i in places.indices) {
            val dx = xs[i] - px
            val dy = ys[i] - py
            val w = weights[i] * kernel(sqrt(dx * dx + dy * dy) * inverseRadii[i])
            sum += w * logFactors[i]
            total += w
        }
        return exp(sum / total)
    }

    fun estimate(point: LatLon): LocationEstimate {
        val px = projection.x(point.lon)
        val py = projection.y(point.lat)
        val w = DoubleArray(places.size)
        var total = 0.0
        var nearest = 0
        var nearestKm = Double.MAX_VALUE
        var area = 0
        var closestInRadii = Double.MAX_VALUE
        val distances = DoubleArray(places.size)
        for (i in places.indices) {
            val dx = xs[i] - px
            val dy = ys[i] - py
            val d = sqrt(dx * dx + dy * dy)
            val r = d * inverseRadii[i]
            distances[i] = d
            w[i] = weights[i] * kernel(r)
            total += w[i]
            if (d < nearestKm) { nearestKm = d; nearest = i }
            if (r < closestInRadii) { closestInRadii = r; area = i }
        }
        var mean = 0.0
        for (i in places.indices) mean += w[i] * logFactors[i]
        mean /= total

        var spread = 0.0
        var anchorSigma = 0.0
        for (i in places.indices) {
            val d = logFactors[i] - mean
            spread += w[i] * d * d
            anchorSigma += w[i] * places[i].confidence.sigma
        }
        spread = sqrt(spread / total)
        anchorSigma /= total
        // Further from every anchor than its radius: less is known about this spot.
        val coverage = min(0.2, 0.04 * maxOf(0.0, closestInRadii - 1))
        val sigma = sqrt(anchorSigma * anchorSigma + spread * spread) + coverage

        val contributors = places.indices
            .map { places[it] to w[it] / total }
            .filter { it.second >= 0.05 }
            .sortedByDescending { it.second }
            .take(4)

        return LocationEstimate(point, exp(mean), sigma, places[nearest], nearestKm, places[area], distances[area], contributors)
    }

    private companion object {
        /** Weight of the long-range tail relative to the Gaussian core. */
        const val TAIL = 0.002
    }
}
