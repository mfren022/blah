package com.ottawahomeprice.app.geo

import com.ottawahomeprice.core.Bounds
import com.ottawahomeprice.core.Format
import com.ottawahomeprice.core.LatLon
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONException

/**
 * OpenStreetMap's Nominatim search, used when the phone has no geocoder or it finds nothing.
 * Its usage policy asks for an identifying User-Agent and at most one request a second; the app
 * sends one request per search the user makes.
 */
class Nominatim(private val bounds: Bounds, private val userAgent: String = USER_AGENT) : GeocodingProvider {
    override val name = "OpenStreetMap"

    override fun search(query: String): List<GeocodedAddress> {
        val viewbox = listOf(bounds.west, bounds.north, bounds.east, bounds.south).joinToString(",") { Format.decimals(it, 4) }
        val url = URL(
            "https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=5" +
                "&countrycodes=ca&bounded=1&viewbox=$viewbox&q=" + URLEncoder.encode(query, "UTF-8"),
        )
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept-Language", "en-CA,en;q=0.9,fr-CA;q=0.6")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("OpenStreetMap answered $code")
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            return parse(text)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val USER_AGENT = "OttawaHomePrice/1.0 (Android app; https://github.com/mfren022/blah)"

        /** Reads Nominatim's jsonv2 search response. */
        fun parse(json: String): List<GeocodedAddress> {
            val array = try {
                JSONArray(json)
            } catch (e: JSONException) {
                throw IOException("Unexpected answer from OpenStreetMap", e)
            }
            val out = ArrayList<GeocodedAddress>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val lat = item.optString("lat").toDoubleOrNull() ?: continue
                val lon = item.optString("lon").toDoubleOrNull() ?: continue
                val address = item.optJSONObject("address")
                fun part(key: String) = address?.optString(key)?.takeIf { it.isNotBlank() }
                val locality = part("city") ?: part("town") ?: part("village") ?: part("municipality")
                val province = part("state")
                val street = listOfNotNull(part("house_number"), part("road")).joinToString(" ").takeIf { it.isNotBlank() }
                val label = if (street != null) {
                    listOfNotNull(street, part("suburb") ?: part("neighbourhood"), locality, listOfNotNull(province, part("postcode")).joinToString(" ").takeIf { it.isNotBlank() })
                        .joinToString(", ")
                } else {
                    item.optString("display_name")
                }
                out += GeocodedAddress(label, LatLon(lat, lon), locality, province)
            }
            return out
        }
    }
}
