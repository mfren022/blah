package com.ottawahomeprice.app

import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.ottawahomeprice.app.geo.GeocodedAddress
import com.ottawahomeprice.app.geo.Place
import com.ottawahomeprice.app.ui.OttawaMapView
import com.ottawahomeprice.app.ui.PriceChartView
import com.ottawahomeprice.core.Format
import com.ottawahomeprice.core.LatLon
import com.ottawahomeprice.core.Month
import com.ottawahomeprice.core.PropertyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class MainScreenTest : ScreenTest() {

    private fun launch(): MainActivity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

    private fun MainActivity.text(id: Int): String = view<TextView>(id).text.toString()

    private fun MainActivity.searchFor(query: String) {
        val input = view<EditText>(R.id.search_input)
        input.requestFocus()
        input.setText(query)
        view<View>(R.id.search_go).performClick()
        waitFor { view<View>(R.id.search_go).isEnabled }
        idle()
    }

    private fun waitForMap(activity: MainActivity) = waitFor { activity.view<OttawaMapView>(R.id.map).shaded }

    @Test
    fun opensOnTheCityAverageForThisMonth() {
        val activity = launch()
        waitForMap(activity)
        assertNull(activity.currentPlace)
        assertEquals("All of Ottawa", activity.text(R.id.place_title))
        val now = activity.currentMonthShown
        assertTrue(activity.text(R.id.price_label).endsWith(now.label()))
        val city = app.estimator.index.price(now)
        assertEquals(Format.money(city), activity.text(R.id.price))
        assertEquals(now.label(), activity.text(R.id.month_label))
        capture(activity, "01_home")
        captureScroll(activity.view(R.id.root), "01_home_full")
    }

    @Test
    fun typingSuggestsNeighbourhoodsAndPostalAreasOffline() {
        val activity = launch()
        val input = activity.view<EditText>(R.id.search_input)
        input.requestFocus()
        input.setText("west")
        idle()
        val list = activity.view<LinearLayout>(R.id.suggestions)
        assertEquals(View.VISIBLE, list.visibility)
        assertTrue(list.childCount > 0)
        val first = list.getChildAt(0).findViewById<TextView>(R.id.suggestion_title).text.toString()
        assertTrue(first, first.startsWith("West"))
        capture(activity, "02_suggestions")
        list.getChildAt(0).performClick()
        idle()
        assertEquals(first, activity.currentPlace?.title)
        assertEquals(Place.Source.NEIGHBOURHOOD, activity.currentPlace?.source)
        assertEquals(View.GONE, list.visibility)
    }

    @Test
    fun postalAreaResolvesWithoutTheNetwork() {
        val fake = FakeGeocoder(emptyMap(), offline = true)
        useGeocoder(fake)
        val activity = launch()
        activity.searchFor("K1S")
        assertTrue("no network needed for a bare FSA", fake.queries.isEmpty())
        val place = activity.currentPlace!!
        assertEquals(Place.Source.POSTAL_AREA, place.source)
        assertEquals("K1S", place.title)
        val area = app.estimator.data.postalAreas.first { it.fsa == "K1S" }
        assertEquals(area.location, place.location)
        assertTrue(activity.text(R.id.comparison).contains("Ottawa average"))
        // Remembered for next time.
        assertEquals("K1S", app.state.recents.first().title)
        assertEquals(View.VISIBLE, activity.view<View>(R.id.recents_scroll).visibility)
    }

    @Test
    fun addressSearchUsesTheGeocoderAndKeepsOttawaResults() {
        val oldOttawaEast = LatLon(45.4085, -75.6765)
        val fake = FakeGeocoder(
            mapOf(
                "100 Main St" to listOf(
                    GeocodedAddress("100 Main St, Ottawa, ON K1S 1C2, Canada", oldOttawaEast, "Ottawa", "Ontario"),
                    // Also matched across the river: dropped.
                    GeocodedAddress("100 Rue Main, Gatineau, QC J8P 4R6, Canada", LatLon(45.4870, -75.6380), "Gatineau", "Québec"),
                ),
            ),
        )
        useGeocoder(fake)
        val activity = launch()
        waitForMap(activity)
        activity.searchFor("100 Main St")
        assertEquals(listOf("100 Main St, Ottawa, ON"), fake.queries)
        val place = activity.currentPlace!!
        assertEquals("100 Main St", place.title)
        assertEquals("Ottawa, ON K1S 1C2", place.detail)
        assertEquals(oldOttawaEast, place.location)
        assertEquals(View.GONE, activity.view<View>(R.id.search_status).visibility)

        val estimate = app.estimator.estimate(app.estimator.locate(oldOttawaEast), activity.currentMonthShown, PropertyType.ALL)
        assertEquals(Format.money(estimate.price), activity.text(R.id.price))
        assertTrue(activity.text(R.id.based_on).contains("Based on"))
        capture(activity, "03_address")
        captureScroll(activity.view(R.id.root), "03_address_full")
    }

    @Test
    fun addressesOutsideOttawaAreRefused() {
        val fake = FakeGeocoder(
            mapOf("25 Rue Laurier, Gatineau" to listOf(GeocodedAddress("25 Rue Laurier, Gatineau, QC", LatLon(45.4290, -75.7150), "Gatineau", "Québec"))),
        )
        useGeocoder(fake)
        val activity = launch()
        activity.searchFor("25 Rue Laurier, Gatineau")
        assertNull(activity.currentPlace)
        val status = activity.view<TextView>(R.id.search_status)
        assertEquals(View.VISIBLE, status.visibility)
        assertTrue(status.text.toString(), status.text.contains("outside the City of Ottawa"))
        // And postal codes from elsewhere, without asking anyone.
        activity.searchFor("M5V 3L9")
        assertTrue(status.text.toString().contains("outside the City of Ottawa"))
        capture(activity, "04_outside")
    }

    @Test
    fun withoutANetworkAnAddressFallsBackToItsPostalCodeOrNeighbourhood() {
        useGeocoder(FakeGeocoder(emptyMap(), offline = true))
        val activity = launch()
        activity.searchFor("12 Fifth Ave, Ottawa K1S 2M3")
        assertEquals(Place.Source.POSTAL_AREA, activity.currentPlace?.source)
        assertTrue(activity.currentPlace!!.detail.startsWith("Approximate"))

        val name = app.estimator.data.neighbourhoods.first { it.name.length >= 6 }.name
        activity.searchFor("123 Something Rd, $name")
        assertEquals(Place.Source.NEIGHBOURHOOD, activity.currentPlace?.source)
        assertEquals(name, activity.currentPlace?.title)

        activity.searchFor("123 Nowhere Rd")
        val status = activity.view<TextView>(R.id.search_status)
        assertTrue(status.text.toString(), status.text.contains("Couldn't reach"))
    }

    @Test
    fun movingThroughTime() {
        val activity = launch()
        val range = app.estimator.range
        val now = activity.currentMonthShown

        activity.view<View>(R.id.prev_year).performClick()
        assertEquals(now - 12, activity.currentMonthShown)
        activity.view<View>(R.id.next_month).performClick()
        assertEquals(now - 11, activity.currentMonthShown)

        // Quick dates.
        val quick = activity.view<LinearLayout>(R.id.quick_dates)
        quick.getChildAt(1).performClick()
        assertEquals(Month.of(1980, 6), activity.currentMonthShown)
        assertEquals("June 1980", activity.text(R.id.month_label))
        val price1980 = app.estimator.index.price(Month.of(1980, 6))
        assertEquals(Format.money(price1980), activity.text(R.id.price))
        assertTrue(activity.text(R.id.real_dollars).contains("dollars"))

        // The slider covers the whole range.
        val slider = activity.view<SeekBar>(R.id.month_slider)
        assertEquals(range.endInclusive - range.start, slider.max)
        assertEquals(Month.of(1980, 6) - range.start, slider.progress)

        // Can't go past the ends.
        repeat(200) { activity.view<View>(R.id.prev_year).performClick() }
        assertEquals(range.start, activity.currentMonthShown)
        assertFalse(activity.view<View>(R.id.prev_month).isEnabled)
        capture(activity, "05_1914")

        // Remembered across launches.
        val again = launch()
        assertEquals(range.start, again.currentMonthShown)
    }

    @Test
    fun draggingTheChartPicksAMonth() {
        val activity = launch()
        val chart = activity.view<PriceChartView>(R.id.chart)
        val target = Month.of(1990, 1)
        val x = chart.xOf(target) / chart.width
        drag(chart, 0.95f, x)
        val picked = activity.currentMonthShown
        assertTrue("picked $picked", kotlin.math.abs(picked - target) <= 2)
        assertEquals(picked.label(), activity.text(R.id.month_label))
        captureScroll(activity.view(R.id.root), "06_chart_1990")
    }

    @Test
    fun projectionAndBackcastAreLabelled() {
        val activity = launch()
        val quick = activity.view<LinearLayout>(R.id.quick_dates)
        quick.getChildAt(4).performClick() // +10 yrs
        assertTrue(activity.text(R.id.basis), activity.text(R.id.basis).startsWith("Projection"))
        assertTrue(activity.text(R.id.real_dollars).contains("if inflation runs"))
        quick.getChildAt(0).performClick() // 1950
        val basis = activity.text(R.id.basis)
        assertTrue(basis, basis == "Inflation back-cast" || basis == "Census dwelling values" || basis == "Annual sales averages")
    }

    @Test
    fun typesOfHome() {
        val activity = launch()
        activity.searchFor("K1S")
        val types = activity.view<LinearLayout>(R.id.types)
        assertEquals(PropertyType.entries.size, types.childCount)
        val all = activity.text(R.id.price)
        types.getChildAt(PropertyType.CONDO.ordinal).performClick()
        assertEquals(PropertyType.CONDO, activity.currentType)
        assertNotEquals(all, activity.text(R.id.price))
        assertTrue(activity.text(R.id.price_label).startsWith("Average condo apartment price"))
        assertTrue(types.getChildAt(PropertyType.CONDO.ordinal).isSelected)
        types.getChildAt(PropertyType.DETACHED.ordinal).performClick()
        assertEquals(PropertyType.DETACHED, app.state.type)
        capture(activity, "07_detached")
    }

    @Test
    fun chartOptions() {
        val activity = launch()
        activity.view<View>(R.id.toggle_real).performClick()
        assertTrue(app.state.inflationAdjusted)
        assertTrue(activity.view<View>(R.id.toggle_real).isSelected)
        activity.view<View>(R.id.toggle_log).performClick()
        assertFalse(app.state.logScale)
        captureScroll(activity.view(R.id.root), "08_real_linear")
    }

    @Test
    fun tappingTheMapEstimatesThere() {
        val activity = launch()
        waitForMap(activity)
        val map = activity.view<OttawaMapView>(R.id.map)
        // Somewhere in Kanata.
        val kanata = LatLon(45.3088, -75.8987)
        val (x, y) = map.screenOf(kanata)
        tap(map, x / map.width, y / map.height)
        idle(500) // a single tap is confirmed once a double tap is ruled out
        val place = activity.currentPlace!!
        assertEquals(Place.Source.MAP, place.source)
        assertTrue(place.title, place.title.startsWith("Map point"))
        assertTrue(Geo.close(kanata, place.location))
        // Map taps aren't added to recent searches.
        assertTrue(app.state.recents.isEmpty())

        // Gatineau is outside.
        val (gx, gy) = map.screenOf(LatLon(45.4900, -75.7300))
        tap(map, gx / map.width, gy / map.height)
        idle(500)
        assertEquals(place, activity.currentPlace)
        assertTrue(activity.text(R.id.search_status).contains("outside"))
        val scroll = activity.view<ScrollView>(R.id.root)
        captureScroll(scroll, "09_map_kanata")
    }

    @Test
    fun doubleTapZoomsTheMap() {
        val activity = launch()
        waitForMap(activity)
        val map = activity.view<OttawaMapView>(R.id.map)
        val downtown = LatLon(45.4150, -75.6950)
        val (x0, y0) = map.screenOf(downtown)
        tap(map, x0 / map.width, y0 / map.height)
        idle(50)
        tap(map, x0 / map.width, y0 / map.height)
        idle(500)
        val (x1, y1) = map.screenOf(downtown)
        // Zoomed about the tapped point: it stays put while the rest spreads out.
        assertEquals(x0, x1, 2f)
        assertEquals(y0, y1, 2f)
        val (kx, _) = map.screenOf(LatLon(45.3088, -75.8987))
        assertTrue(x0 - kx > 0)
        tap(map, x0 / map.width, y0 / map.height)
        idle(50)
        tap(map, x0 / map.width, y0 / map.height)
        idle(500)
        captureScroll(activity.view(R.id.root), "10_map_zoomed")
        map.resetView()
    }

    @Test
    fun sharesTheEstimateAsText() {
        useGeocoder(FakeGeocoder(emptyMap(), offline = true))
        val activity = launch()
        activity.searchFor("K1S")
        activity.view<View>(R.id.share).performClick()
        val chooser = shadowOf(activity).nextStartedActivity
        assertEquals(android.content.Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
        val text = send.getStringExtra(android.content.Intent.EXTRA_TEXT)!!
        assertTrue(text, text.startsWith("K1S\nAverage home price, "))
        assertTrue(text, text.contains(activity.text(R.id.price)))
        assertTrue(text, text.contains("not an appraisal"))
    }

    @Test
    fun restoresThePlaceOnRelaunch() {
        val activity = launch()
        activity.searchFor("K2J")
        val first = activity.currentPlace
        val again = launch()
        assertEquals(first, again.currentPlace)
        assertEquals(activity.text(R.id.price), again.text(R.id.price))
    }

    @Test
    fun methodologyScreen() {
        val activity = launch()
        activity.view<View>(R.id.info).performClick()
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(MethodologyActivity::class.java.name, intent.component?.className)
        val screen = Robolectric.buildActivity(MethodologyActivity::class.java, intent).setup().get()
        val sections = screen.findViewById<LinearLayout>(R.id.sections)
        assertTrue(sections.childCount >= 16)
        captureScroll(screen.findViewById(R.id.root), "11_methodology")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun darkTheme() {
        useGeocoder(FakeGeocoder(emptyMap(), offline = true))
        val activity = launch()
        activity.searchFor("K1Y")
        waitForMap(activity)
        captureScroll(activity.view(R.id.root), "12_dark")
    }

    private object Geo {
        fun close(a: LatLon, b: LatLon) = com.ottawahomeprice.core.Geo.distanceKm(a, b) < 0.5
    }
}
