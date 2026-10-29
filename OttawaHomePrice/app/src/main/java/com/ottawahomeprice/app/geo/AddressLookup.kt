package com.ottawahomeprice.app.geo

import com.ottawahomeprice.core.Estimator
import com.ottawahomeprice.core.Format
import com.ottawahomeprice.core.LatLon
import com.ottawahomeprice.core.PlaceIndex
import com.ottawahomeprice.core.PostalCode
import com.ottawahomeprice.core.Text
import java.io.IOException

/** An address search service. [search] blocks, so call it off the main thread. */
interface GeocodingProvider {
    val name: String

    /** Matches for [query], or an empty list. Throws [IOException] when the service can't be reached. */
    @Throws(IOException::class)
    fun search(query: String): List<GeocodedAddress>
}

data class GeocodedAddress(
    /** One line, e.g. "123 Bank St, Ottawa, ON K1P 5N7". */
    val label: String,
    val location: LatLon,
    /** City or town, when the service gives one. */
    val locality: String?,
    /** Province, when the service gives one. */
    val province: String?,
)

sealed class LookupResult {
    data class Found(val places: List<Place>) : LookupResult()
    data class OutsideOttawa(val label: String) : LookupResult()
    data class NotFound(val message: String) : LookupResult()
}

/**
 * Turns what the user typed into places in Ottawa. Postal areas and neighbourhood names resolve
 * offline at once; anything else goes to the online [providers] in turn, and their results are
 * kept only if they fall inside the city. Without a network, a postal code or neighbourhood named
 * in the address still gives an approximate place.
 */
class AddressLookup(private val estimator: Estimator, private val providers: List<GeocodingProvider>) {
    private val places: PlaceIndex = estimator.places

    fun lookup(rawQuery: String): LookupResult {
        val query = rawQuery.trim()
        if (query.length < 2) return LookupResult.NotFound("Type an address, a postal code or a neighbourhood.")

        val postal = PostalCode.find(query)
        val postalOnly = PostalCode.isOnlyPostal(query)
        if (postalOnly && postal != null) {
            val area = places.postalArea(postal.fsa)
            // Eastern Ontario codes start with K; any other letter is another region.
            if (area == null && !postal.fsa.startsWith("K")) return LookupResult.OutsideOttawa(postal.full ?: postal.fsa)
            // A bare FSA is an area anyway; a full postal code is worth asking the geocoder about.
            if (area != null && postal.full == null) return LookupResult.Found(listOf(postalPlace(postal.fsa, area.area, area.location)))
        }

        // An exact neighbourhood name needs no network.
        if (!postalOnly) {
            val exact = places.search(query, limit = 1).firstOrNull()
            if (exact != null && exact.score >= 100 && exact.kind == PlaceIndex.Kind.NEIGHBOURHOOD) {
                return LookupResult.Found(listOf(neighbourhoodPlace(exact.label, exact.location, approximate = false)))
            }
        }

        // A bare postal code goes out in its standard form ("K1S 5B6").
        val online = if (postalOnly && postal?.full != null) postal.full else withCity(query)
        var unreachable = false
        var outside: String? = null
        for (provider in providers) {
            val results = try {
                provider.search(online)
            } catch (e: IOException) {
                unreachable = true
                continue
            } catch (e: RuntimeException) {
                // A device geocoder can throw IllegalStateException and the like; try the next one.
                unreachable = true
                continue
            }
            val inside = results.filter { isOttawa(it) }
            if (inside.isNotEmpty()) return LookupResult.Found(dedupe(inside).map { searchPlace(it) })
            if (results.isNotEmpty() && outside == null) outside = results.first().label
        }

        // Offline fallbacks: the postal code or a neighbourhood named in the address.
        if (postal != null) {
            val area = places.postalArea(postal.fsa)
            if (area != null) {
                return LookupResult.Found(listOf(postalPlace(postal.full ?: postal.fsa, area.area, area.location, approximate = !postalOnly)))
            }
        }
        places.neighbourhoodIn(query)?.let {
            return LookupResult.Found(listOf(neighbourhoodPlace(it.name, it.location, approximate = true)))
        }
        outside?.let { return LookupResult.OutsideOttawa(it) }
        if (unreachable) {
            return LookupResult.NotFound(
                "Couldn't reach the address search. Check your connection, or search a postal code or " +
                    "neighbourhood, or tap the map.",
            )
        }
        val suggestions = places.search(query, limit = 3)
        return LookupResult.NotFound(
            if (suggestions.isEmpty()) "No Ottawa address matches “$query”. Check the spelling, add the street type (St, Ave, Rd), or tap the map."
            else "No exact match for “$query”. Did you mean ${suggestions.joinToString(", ") { it.label }}?",
        )
    }

    private fun isOttawa(address: GeocodedAddress): Boolean {
        val province = address.province?.let(Text::normalize)
        if (province != null && (province.startsWith("quebec") || province == "qc")) return false
        return estimator.inOttawa(address.location, toleranceKm = 0.5)
    }

    /** Adds the city when the query names none, so "100 Main St" means Ottawa's. */
    private fun withCity(query: String): String {
        val q = " ${Text.normalize(query)} "
        val namesPlace = listOf(" ottawa ", " ontario ", " on ", " quebec ", " qc ", " gatineau ", " canada ").any { q.contains(it) }
        return if (namesPlace || PostalCode.find(query) != null) query else "$query, Ottawa, ON"
    }

    private fun dedupe(list: List<GeocodedAddress>): List<GeocodedAddress> {
        val out = ArrayList<GeocodedAddress>()
        for (a in list) {
            if (out.none { com.ottawahomeprice.core.Geo.distanceKm(it.location, a.location) < 0.1 }) out += a
        }
        return out.take(5)
    }

    private fun searchPlace(a: GeocodedAddress): Place {
        val label = a.label.removeSuffix(", Canada").removeSuffix(", Canada ").trim()
        val comma = label.indexOf(',')
        val title = if (comma > 0) label.substring(0, comma) else label
        val detail = if (comma > 0) label.substring(comma + 1).trim() else ""
        return Place(title, detail, a.location, Place.Source.SEARCH)
    }

    private fun postalPlace(code: String, area: String, location: LatLon, approximate: Boolean = false) = Place(
        title = code,
        detail = (if (approximate) "Approximate: centre of postal area " else "Centre of postal area ") + code.take(3) + " ($area)",
        location = location,
        source = Place.Source.POSTAL_AREA,
    )

    private fun neighbourhoodPlace(name: String, location: LatLon, approximate: Boolean) = Place(
        title = name,
        detail = if (approximate) "Approximate: centre of $name (the address itself wasn't found)" else "Centre of the neighbourhood",
        location = location,
        source = Place.Source.NEIGHBOURHOOD,
    )

    /** Names a point tapped on the map. */
    fun mapPlace(point: LatLon): Place {
        val area = estimator.locate(point).areaLabel
        return Place(
            title = if (area.startsWith("near ")) "Map point $area" else "Map point in $area",
            detail = Format.decimals(point.lat, 5) + ", " + Format.decimals(point.lon, 5),
            location = point,
            source = Place.Source.MAP,
        )
    }
}
