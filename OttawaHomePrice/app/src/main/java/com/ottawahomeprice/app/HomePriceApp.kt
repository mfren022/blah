package com.ottawahomeprice.app

import android.app.Application
import android.content.Context
import com.ottawahomeprice.app.data.SavedState
import com.ottawahomeprice.app.geo.AddressLookup
import com.ottawahomeprice.app.geo.DeviceGeocoder
import com.ottawahomeprice.app.geo.Nominatim
import com.ottawahomeprice.core.Estimator
import com.ottawahomeprice.core.OttawaData
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class HomePriceApp : Application() {

    /** The estimator, built from the CSV files in assets/data. Parsing takes a few milliseconds. */
    val estimator: Estimator by lazy {
        Estimator(OttawaData.parse { name -> assets.open("data/$name").bufferedReader().use { it.readText() } })
    }

    val state: SavedState by lazy { SavedState(getSharedPreferences("state", Context.MODE_PRIVATE)) }

    private var lookupInstance: AddressLookup? = null

    /** Address searches: offline matches first, then the phone's geocoder, then OpenStreetMap. Tests swap in their own. */
    var lookup: AddressLookup
        get() = lookupInstance ?: defaultLookup().also { lookupInstance = it }
        internal set(value) { lookupInstance = value }

    private fun defaultLookup(): AddressLookup {
        val around = estimator.data.boundary.bounds.expandedBy(0.02)
        return AddressLookup(estimator, listOf(DeviceGeocoder(this, around), Nominatim(around)))
    }

    /** Address lookups, which may wait on the network, one at a time. */
    val lookups: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "lookups").apply { isDaemon = true }
    }

    /** Computing the map's shading, kept apart so a slow lookup doesn't hold it up. */
    val drawing: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "drawing").apply { isDaemon = true }
    }
}
