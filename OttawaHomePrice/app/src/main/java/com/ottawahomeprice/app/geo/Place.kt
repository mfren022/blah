package com.ottawahomeprice.app.geo

import com.ottawahomeprice.core.LatLon

/** A spot the user picked: a found address, a postal area, a neighbourhood or a point on the map. */
data class Place(val title: String, val detail: String, val location: LatLon, val source: Source) {
    enum class Source {
        /** An address found by the phone's geocoder or OpenStreetMap. */
        SEARCH,
        /** The centre of a postal Forward Sortation Area (offline). */
        POSTAL_AREA,
        /** The centre of a neighbourhood (offline). */
        NEIGHBOURHOOD,
        /** Tapped on the map. */
        MAP,
    }
}
