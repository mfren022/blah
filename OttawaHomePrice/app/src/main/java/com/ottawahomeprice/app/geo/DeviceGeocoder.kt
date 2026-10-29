package com.ottawahomeprice.app.geo

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.ottawahomeprice.core.Bounds
import com.ottawahomeprice.core.LatLon
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The phone's own geocoder (on most phones backed by Google), limited to the area around Ottawa. */
class DeviceGeocoder(private val context: Context, private val bounds: Bounds) : GeocodingProvider {
    override val name = "Phone geocoder"

    override fun search(query: String): List<GeocodedAddress> {
        if (!Geocoder.isPresent()) throw IOException("This phone has no geocoder")
        val geocoder = Geocoder(context, Locale.CANADA)
        val addresses: List<Address> = if (Build.VERSION.SDK_INT >= 33) {
            val done = CountDownLatch(1)
            var found: List<Address> = emptyList()
            var error: String? = null
            geocoder.getFromLocationName(
                query, MAX_RESULTS, bounds.south, bounds.west, bounds.north, bounds.east,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        found = addresses
                        done.countDown()
                    }

                    override fun onError(errorMessage: String?) {
                        error = errorMessage ?: "Geocoder error"
                        done.countDown()
                    }
                },
            )
            if (!done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) throw IOException("The geocoder didn't answer")
            error?.let { throw IOException(it) }
            found
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocationName(query, MAX_RESULTS, bounds.south, bounds.west, bounds.north, bounds.east).orEmpty()
        }
        return addresses.filter { it.hasLatitude() && it.hasLongitude() }.map { a ->
            val line = if (a.maxAddressLineIndex >= 0) a.getAddressLine(0) else null
            GeocodedAddress(
                label = line ?: listOfNotNull(a.featureName, a.locality, a.adminArea).joinToString(", "),
                location = LatLon(a.latitude, a.longitude),
                locality = a.locality ?: a.subAdminArea,
                province = a.adminArea,
            )
        }
    }

    private companion object {
        const val MAX_RESULTS = 5
        const val TIMEOUT_SECONDS = 12L
    }
}
