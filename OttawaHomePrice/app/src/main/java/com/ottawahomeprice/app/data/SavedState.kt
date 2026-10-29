package com.ottawahomeprice.app.data

import android.content.SharedPreferences
import com.ottawahomeprice.app.geo.Place
import com.ottawahomeprice.core.LatLon
import com.ottawahomeprice.core.Month
import com.ottawahomeprice.core.PropertyType
import org.json.JSONArray
import org.json.JSONObject

/** What the main screen shows (place, month, type, chart options) and recent searches, kept between launches. */
class SavedState(private val prefs: SharedPreferences) {

    var place: Place?
        get() = prefs.getString(KEY_PLACE, null)?.let { runCatching { placeFrom(JSONObject(it)) }.getOrNull() }
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_PLACE) else putString(KEY_PLACE, json(value).toString()) }.apply()

    /** The chosen month, or null for "now". */
    var month: Month?
        get() = prefs.getInt(KEY_MONTH, -1).takeIf { it >= 0 }?.let { Month(it) }
        set(value) = prefs.edit().putInt(KEY_MONTH, value?.index ?: -1).apply()

    var type: PropertyType
        get() = prefs.getString(KEY_TYPE, null)?.let { runCatching { PropertyType.parse(it) }.getOrNull() } ?: PropertyType.ALL
        set(value) = prefs.edit().putString(KEY_TYPE, value.key).apply()

    var inflationAdjusted: Boolean
        get() = prefs.getBoolean(KEY_REAL, false)
        set(value) = prefs.edit().putBoolean(KEY_REAL, value).apply()

    var logScale: Boolean
        get() = prefs.getBoolean(KEY_LOG, true)
        set(value) = prefs.edit().putBoolean(KEY_LOG, value).apply()

    /** Most recent first, at most [MAX_RECENTS]. */
    val recents: List<Place>
        get() {
            val text = prefs.getString(KEY_RECENTS, null) ?: return emptyList()
            return runCatching {
                val array = JSONArray(text)
                (0 until array.length()).mapNotNull { runCatching { placeFrom(array.getJSONObject(it)) }.getOrNull() }
            }.getOrDefault(emptyList())
        }

    fun remember(place: Place) {
        val list = listOf(place) + recents.filter { it.title != place.title }
        val array = JSONArray()
        list.take(MAX_RECENTS).forEach { array.put(json(it)) }
        prefs.edit().putString(KEY_RECENTS, array.toString()).apply()
    }

    private fun json(place: Place) = JSONObject()
        .put("title", place.title)
        .put("detail", place.detail)
        .put("lat", place.location.lat)
        .put("lon", place.location.lon)
        .put("source", place.source.name)

    private fun placeFrom(o: JSONObject) = Place(
        title = o.getString("title"),
        detail = o.optString("detail"),
        location = LatLon(o.getDouble("lat"), o.getDouble("lon")),
        source = runCatching { Place.Source.valueOf(o.getString("source")) }.getOrDefault(Place.Source.SEARCH),
    )

    companion object {
        const val MAX_RECENTS = 8
        private const val KEY_PLACE = "place"
        private const val KEY_MONTH = "month"
        private const val KEY_TYPE = "type"
        private const val KEY_REAL = "inflation_adjusted"
        private const val KEY_LOG = "log_scale"
        private const val KEY_RECENTS = "recents"
    }
}
