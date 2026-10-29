package com.ottawahomeprice.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double) {
    init {
        require(lat in -90.0..90.0 && lon in -180.0..180.0) { "Not a coordinate: $lat, $lon" }
    }

    override fun toString(): String = String.format(java.util.Locale.ROOT, "%.5f, %.5f", lat, lon)
}

object Geo {
    private const val EARTH_RADIUS_KM = 6371.0088

    /** Great-circle distance in kilometres. */
    fun distanceKm(a: LatLon, b: LatLon): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(h)))
    }
}

/**
 * Flat x/y kilometres around a reference latitude: exact enough over a city (under 0.1% across
 * Ottawa) and much cheaper than great-circle distances in the inner loops.
 */
class LocalProjection(referenceLat: Double) {
    private val kmPerDegLat = 111.132
    private val kmPerDegLon = 111.320 * cos(Math.toRadians(referenceLat))

    fun x(lon: Double): Double = lon * kmPerDegLon
    fun y(lat: Double): Double = lat * kmPerDegLat
    fun lon(x: Double): Double = x / kmPerDegLon
    fun lat(y: Double): Double = y / kmPerDegLat

    fun distanceKm(a: LatLon, b: LatLon): Double = hypot(x(a.lon) - x(b.lon), y(a.lat) - y(b.lat))

    companion object {
        /** Centred on Ottawa. */
        val OTTAWA = LocalProjection(45.30)
    }
}

data class Bounds(val south: Double, val west: Double, val north: Double, val east: Double) {
    fun contains(p: LatLon): Boolean = p.lat in south..north && p.lon in west..east
    val center: LatLon get() = LatLon((south + north) / 2, (west + east) / 2)

    fun expandedBy(fraction: Double): Bounds {
        val dLat = (north - south) * fraction
        val dLon = (east - west) * fraction
        return Bounds(south - dLat, west - dLon, north + dLat, east + dLon)
    }

    companion object {
        fun of(points: Iterable<LatLon>): Bounds {
            var s = Double.MAX_VALUE; var w = Double.MAX_VALUE
            var n = -Double.MAX_VALUE; var e = -Double.MAX_VALUE
            for (p in points) {
                s = min(s, p.lat); n = max(n, p.lat); w = min(w, p.lon); e = max(e, p.lon)
            }
            require(s <= n) { "No points" }
            return Bounds(s, w, n, e)
        }
    }
}

/** A simple polygon (the city boundary), not closed: the last vertex joins the first. */
class Polygon(val points: List<LatLon>) {
    init {
        require(points.size >= 3) { "A polygon needs at least 3 vertices" }
    }

    val bounds: Bounds = Bounds.of(points)
    private val projection = LocalProjection.OTTAWA
    private val xs = DoubleArray(points.size) { projection.x(points[it].lon) }
    private val ys = DoubleArray(points.size) { projection.y(points[it].lat) }

    /** Even-odd ray casting. */
    fun contains(p: LatLon): Boolean {
        if (!bounds.contains(p)) return false
        val px = projection.x(p.lon)
        val py = projection.y(p.lat)
        var inside = false
        var j = points.size - 1
        for (i in points.indices) {
            val yi = ys[i]; val yj = ys[j]
            if ((yi > py) != (yj > py)) {
                val crossX = xs[i] + (py - yi) / (yj - yi) * (xs[j] - xs[i])
                if (px < crossX) inside = !inside
            }
            j = i
        }
        return inside
    }

    /** Distance in km from [p] to the nearest point of the boundary line (0 on it). */
    fun distanceToEdgeKm(p: LatLon): Double {
        val px = projection.x(p.lon)
        val py = projection.y(p.lat)
        var best = Double.MAX_VALUE
        var j = points.size - 1
        for (i in points.indices) {
            best = min(best, segmentDistance(px, py, xs[j], ys[j], xs[i], ys[i]))
            j = i
        }
        return best
    }

    /** Signed area in km² (positive when the vertices run counter-clockwise on a map). */
    fun signedAreaKm2(): Double {
        var sum = 0.0
        var j = points.size - 1
        for (i in points.indices) {
            sum += xs[j] * ys[i] - xs[i] * ys[j]
            j = i
        }
        return sum / 2
    }

    private fun segmentDistance(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val lengthSq = dx * dx + dy * dy
        val t = if (lengthSq == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / lengthSq).coerceIn(0.0, 1.0)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}
