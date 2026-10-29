package com.ottawahomeprice.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.ottawahomeprice.app.geo.LookupResult
import com.ottawahomeprice.app.geo.Place
import com.ottawahomeprice.app.ui.OttawaMapView
import com.ottawahomeprice.app.ui.PriceChartView
import com.ottawahomeprice.app.ui.dp
import com.ottawahomeprice.app.ui.fitSystemBars
import com.ottawahomeprice.app.ui.styleSystemBars
import com.ottawahomeprice.app.ui.withAlpha
import com.ottawahomeprice.core.Basis
import com.ottawahomeprice.core.Estimator
import com.ottawahomeprice.core.Format
import com.ottawahomeprice.core.LocationEstimate
import com.ottawahomeprice.core.Month
import com.ottawahomeprice.core.PlaceIndex
import com.ottawahomeprice.core.PriceSeries
import com.ottawahomeprice.core.PropertyType
import java.time.YearMonth
import kotlin.math.roundToLong

/**
 * The one screen that matters: pick a place (search, a suggestion, a recent search or the map),
 * a month and a type of home, and see the estimated average price, how sure it is, and how it
 * moved over time.
 */
class MainActivity : Activity() {

    private val app: HomePriceApp get() = application as HomePriceApp
    private lateinit var estimator: Estimator

    private var place: Place? = null
    private var location: LocationEstimate? = null
    private var month = Month(0)
    private var type = PropertyType.ALL
    private var inflationAdjusted = false
    private var logScale = true
    private var series: PriceSeries? = null

    /** Ignores answers to searches that a newer search replaced. */
    private var searchGeneration = 0

    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var clear: ImageButton
    private lateinit var go: TextView
    private lateinit var status: TextView
    private lateinit var suggestions: LinearLayout
    private lateinit var recents: LinearLayout
    private lateinit var monthLabel: TextView
    private lateinit var slider: SeekBar
    private lateinit var chart: PriceChartView
    private lateinit var map: OttawaMapView
    private val typeButtons = LinkedHashMap<PropertyType, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        styleSystemBars()
        scroll = findViewById(R.id.root)
        fitSystemBars(scroll)
        estimator = app.estimator

        val state = app.state
        place = state.place?.takeIf { estimator.inOttawa(it.location, toleranceKm = 1.0) }
        location = place?.let { estimator.locate(it.location) }
        month = (state.month ?: currentMonth()).coerceIn(estimator.range)
        type = state.type
        inflationAdjusted = state.inflationAdjusted
        logScale = state.logScale

        setUpSearch()
        setUpTime()
        setUpTypes()
        setUpChart()
        setUpMap()
        findViewById<View>(R.id.share).setOnClickListener { share() }
        findViewById<View>(R.id.info).setOnClickListener { openMethodology() }
        findViewById<View>(R.id.how_link).setOnClickListener { openMethodology() }

        rebuildSeries()
        render()
    }

    /** This month on the phone's clock, within what the app offers. */
    private fun currentMonth(): Month {
        val now = YearMonth.now()
        return Month.of(now.year, now.monthValue).coerceIn(estimator.range)
    }

    private fun openMethodology() = startActivity(Intent(this, MethodologyActivity::class.java))

    /** The estimate as plain text, for messages and notes. */
    internal fun shareText(): String {
        val e = estimator.estimate(location, month, type)
        val p = place
        val what = if (type == PropertyType.ALL) "home" else (estimator.data.types[type]?.label ?: "home").lowercase()
        return buildString {
            append(if (p == null) "Ottawa" else p.title)
            if (p != null && p.detail.isNotEmpty() && p.source == Place.Source.SEARCH) append(", ").append(p.detail)
            append("\nAverage ").append(what).append(" price, ").append(month.label()).append(": ").append(Format.money(e.price))
            append("\nLikely between ").append(Format.money(e.low)).append(" and ").append(Format.money(e.high))
            if (p != null || type != PropertyType.ALL) {
                append("\n").append(Format.versusAverage(e.relative)).append(" (").append(Format.money(e.cityAverage)).append(")")
            }
            append("\nBasis: ").append(e.basis.title)
            append("\nAn estimate from ").append(getString(R.string.app_name)).append(", not an appraisal.")
        }
    }

    private fun share() {
        val send = Intent(Intent.ACTION_SEND)
        send.type = "text/plain"
        send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
        send.putExtra(Intent.EXTRA_TEXT, shareText())
        startActivity(Intent.createChooser(send, getString(R.string.share)))
    }

    // ---- Search ---------------------------------------------------------------------------------

    private fun setUpSearch() {
        input = findViewById(R.id.search_input)
        clear = findViewById(R.id.search_clear)
        go = findViewById(R.id.search_go)
        status = findViewById(R.id.search_status)
        suggestions = findViewById(R.id.suggestions)
        recents = findViewById(R.id.recents)

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                clear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                if (input.hasFocus()) showSuggestions(s?.toString().orEmpty())
            }
        })
        input.setOnEditorActionListener { _, actionId, event ->
            val enter = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                search(input.text.toString())
                true
            } else {
                false
            }
        }
        go.setOnClickListener { search(input.text.toString()) }
        clear.setOnClickListener {
            input.setText("")
            hideStatus()
            suggestions.visibility = View.GONE
        }
        refreshRecents()
    }

    private fun showSuggestions(query: String) {
        suggestions.removeAllViews()
        val matches = if (query.trim().length >= 2) estimator.places.search(query, limit = 5) else emptyList()
        if (matches.isEmpty()) {
            suggestions.visibility = View.GONE
            return
        }
        for (m in matches) {
            val place = Place(
                title = m.label,
                detail = if (m.kind == PlaceIndex.Kind.POSTAL_AREA) m.detail else "Centre of the neighbourhood",
                location = m.location,
                source = if (m.kind == PlaceIndex.Kind.POSTAL_AREA) Place.Source.POSTAL_AREA else Place.Source.NEIGHBOURHOOD,
            )
            suggestions.addView(suggestionRow(m.label, m.detail, R.drawable.ic_place) { choose(place) })
        }
        suggestions.visibility = View.VISIBLE
    }

    private fun suggestionRow(title: String, detail: String, icon: Int, onClick: () -> Unit): View {
        val row = layoutInflater.inflate(R.layout.item_suggestion, suggestions, false)
        row.findViewById<TextView>(R.id.suggestion_title).text = title
        row.findViewById<TextView>(R.id.suggestion_detail).apply {
            text = detail
            visibility = if (detail.isEmpty()) View.GONE else View.VISIBLE
        }
        row.findViewById<android.widget.ImageView>(R.id.suggestion_icon).setImageResource(icon)
        row.setOnClickListener { onClick() }
        return row
    }

    private fun search(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            showStatus(getString(R.string.search_hint) + "?", error = false)
            return
        }
        hideKeyboard()
        suggestions.visibility = View.GONE
        showStatus(getString(R.string.searching), error = false)
        go.isEnabled = false
        val generation = ++searchGeneration
        val lookup = app.lookup
        app.lookups.execute {
            val result = try {
                lookup.lookup(query)
            } catch (e: RuntimeException) {
                LookupResult.NotFound("Something went wrong looking that up. Try again, or tap the map.")
            }
            runOnUiThread {
                if (isDestroyed || generation != searchGeneration) return@runOnUiThread
                go.isEnabled = true
                onLookup(result)
            }
        }
    }

    private fun onLookup(result: LookupResult) {
        when (result) {
            is LookupResult.Found -> {
                hideStatus()
                choose(result.places.first())
                if (result.places.size > 1) {
                    suggestions.removeAllViews()
                    suggestions.addView(sectionLabel(getString(R.string.did_you_mean)))
                    for (p in result.places) {
                        suggestions.addView(suggestionRow(p.title, p.detail, R.drawable.ic_place) { choose(p) })
                    }
                    suggestions.visibility = View.VISIBLE
                }
            }
            is LookupResult.OutsideOttawa ->
                showStatus("“${result.label}” is outside the City of Ottawa. This app covers Ottawa only.", error = true)
            is LookupResult.NotFound -> showStatus(result.message, error = true)
        }
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(getColor(R.color.ink_muted))
        textSize = 12f
        setPadding(dp(4), dp(6), dp(4), dp(2))
    }

    private fun showStatus(text: String, error: Boolean) {
        status.text = text
        status.setTextColor(getColor(if (error) R.color.error else R.color.ink_muted))
        status.visibility = View.VISIBLE
    }

    private fun hideStatus() {
        status.visibility = View.GONE
    }

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()
    }

    private fun refreshRecents() {
        recents.removeAllViews()
        val list = app.state.recents
        findViewById<View>(R.id.recents_scroll).visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        for (p in list) {
            val chip = layoutInflater.inflate(R.layout.item_chip, recents, false) as TextView
            chip.text = p.title
            chip.setOnClickListener { choose(p) }
            recents.addView(chip)
        }
    }

    /** Makes [p] the current place and remembers it (unless it was just a tap on the map). */
    private fun choose(p: Place) {
        place = p
        location = estimator.locate(p.location)
        app.state.place = p
        if (p.source != Place.Source.MAP) {
            app.state.remember(p)
            refreshRecents()
        }
        suggestions.visibility = View.GONE
        hideStatus()
        if (input.hasFocus()) hideKeyboard()
        rebuildSeries()
        render()
    }

    // ---- Time -----------------------------------------------------------------------------------

    private fun setUpTime() {
        monthLabel = findViewById(R.id.month_label)
        slider = findViewById(R.id.month_slider)
        slider.max = estimator.range.endInclusive - estimator.range.start
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) setMonth(estimator.range.start + progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
        findViewById<View>(R.id.prev_year).setOnClickListener { setMonth(month - 12) }
        findViewById<View>(R.id.prev_month).setOnClickListener { setMonth(month - 1) }
        findViewById<View>(R.id.next_month).setOnClickListener { setMonth(month + 1) }
        findViewById<View>(R.id.next_year).setOnClickListener { setMonth(month + 12) }
        monthLabel.setOnClickListener { pickMonth() }

        val quick = findViewById<LinearLayout>(R.id.quick_dates)
        val now = currentMonth()
        val options = listOf(
            "1950" to Month.of(1950, 6),
            "1980" to Month.of(1980, 6),
            "2000" to Month.of(2000, 6),
            getString(R.string.now) to now,
            "+10 yrs" to now + 120,
        )
        options.forEachIndexed { i, (label, m) ->
            val button = layoutInflater.inflate(R.layout.item_quick, quick, false) as TextView
            button.text = label
            button.setOnClickListener { setMonth(m) }
            val params = button.layoutParams as LinearLayout.LayoutParams
            params.weight = 1f
            params.width = 0
            if (i > 0) params.marginStart = dp(6)
            quick.addView(button)
        }
    }

    private fun setMonth(m: Month) {
        val clamped = m.coerceIn(estimator.range)
        if (clamped == month) return
        month = clamped
        app.state.month = month
        render()
    }

    private fun pickMonth() {
        val first = estimator.range.start.year
        val last = estimator.range.endInclusive.year
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), 0)
        }
        val monthPicker = NumberPicker(this).apply {
            minValue = 1
            maxValue = 12
            displayedValues = Month.NAMES.toTypedArray()
            value = month.month
            wrapSelectorWheel = true
        }
        val yearPicker = NumberPicker(this).apply {
            minValue = first
            maxValue = last
            value = month.year
            wrapSelectorWheel = false
        }
        layout.addView(monthPicker, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        layout.addView(yearPicker, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        AlertDialog.Builder(this)
            .setTitle(R.string.pick_month)
            .setView(layout)
            .setPositiveButton(R.string.ok) { _, _ -> setMonth(Month.of(yearPicker.value, monthPicker.value)) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---- Type -----------------------------------------------------------------------------------

    private fun setUpTypes() {
        val row = findViewById<LinearLayout>(R.id.types)
        val labels = mapOf(
            PropertyType.ALL to "All",
            PropertyType.DETACHED to "Detached",
            PropertyType.SEMI to "Semi",
            PropertyType.TOWNHOUSE to "Town",
            PropertyType.CONDO to "Condo",
        )
        PropertyType.entries.forEachIndexed { i, t ->
            val button = layoutInflater.inflate(R.layout.item_quick, row, false) as TextView
            button.text = labels.getValue(t)
            button.contentDescription = estimator.data.types[t]?.label ?: labels.getValue(t)
            button.setOnClickListener {
                if (type != t) {
                    type = t
                    app.state.type = t
                    rebuildSeries()
                    render()
                }
            }
            val params = button.layoutParams as LinearLayout.LayoutParams
            params.weight = 1f
            params.width = 0
            if (i > 0) params.marginStart = dp(6)
            row.addView(button)
            typeButtons[t] = button
        }
    }

    // ---- Chart and map --------------------------------------------------------------------------

    private fun setUpChart() {
        chart = findViewById(R.id.chart)
        chart.onMonthPicked = { m -> setMonth(m) }
        val real = findViewById<TextView>(R.id.toggle_real)
        val log = findViewById<TextView>(R.id.toggle_log)
        real.setOnClickListener {
            inflationAdjusted = !inflationAdjusted
            app.state.inflationAdjusted = inflationAdjusted
            render()
        }
        log.setOnClickListener {
            logScale = !logScale
            app.state.logScale = logScale
            render()
        }
    }

    private fun setUpMap() {
        map = findViewById(R.id.map)
        map.setEstimator(estimator, app.drawing)
        map.onPointPicked = { point ->
            if (estimator.inOttawa(point)) {
                choose(app.lookup.mapPlace(point))
            } else {
                showStatus(getString(R.string.outside_ottawa), error = true)
                scroll.smoothScrollTo(0, 0)
            }
        }
    }

    private fun rebuildSeries() {
        series = estimator.series(location, type)
        val firstData = estimator.index.knotTimes.first().let { Month.containing(it) }
        chart.setSeries(series!!, firstData, estimator.index.lastDataMonth, showCity = location != null || type != PropertyType.ALL)
    }

    // ---- Rendering ------------------------------------------------------------------------------

    private fun render() {
        val e = estimator.estimate(location, month, type)
        val p = place
        val typeLabel = estimator.data.types[type]?.label ?: "All homes"

        findViewById<TextView>(R.id.place_title).text = p?.title ?: getString(R.string.city_wide)
        findViewById<TextView>(R.id.place_detail).text = when {
            p == null -> getString(R.string.city_wide_detail)
            // Neighbourhoods and map points already name their area in the title.
            p.source == Place.Source.SEARCH || p.source == Place.Source.POSTAL_AREA ->
                listOf(p.detail, location?.areaLabel?.let { "Area: $it" }).filter { !it.isNullOrEmpty() }.joinToString(" · ")
            else -> p.detail
        }

        val what = if (type == PropertyType.ALL) "home" else typeLabel.lowercase()
        findViewById<TextView>(R.id.price_label).text =
            (if (p == null) "Ottawa average $what price" else "Average $what price") + ", " + month.label()
        findViewById<TextView>(R.id.price).text = Format.money(e.price)
        findViewById<TextView>(R.id.price_range).text = "Likely between ${Format.money(e.low)} and ${Format.money(e.high)}"

        val basis = findViewById<TextView>(R.id.basis)
        val basisColor = getColor(basisColor(e.basis))
        basis.text = when (e.basis) {
            Basis.PROJECTION -> e.basis.title + " (+" + Format.decimals(estimator.data.metaDouble("projection_growth") * 100, 1) + "% a year)"
            else -> e.basis.title
        }
        basis.setTextColor(basisColor)
        (basis.background.mutate() as? GradientDrawable)?.setColor(withAlpha(basisColor, 0.12f))

        findViewById<TextView>(R.id.comparison).text = if (p == null && type == PropertyType.ALL) {
            "The average over every home sold in the city that month."
        } else {
            Format.versusAverage(e.relative) + " (" + Format.money(e.cityAverage) + ")"
        }

        val yearAgo = month - 12
        findViewById<TextView>(R.id.change).apply {
            if (yearAgo >= estimator.range.start) {
                val before = estimator.estimate(location, yearAgo, type)
                text = Format.signedPercent(e.price / before.price - 1) + " from a year earlier (" + Format.money(before.price) + ")"
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }

        findViewById<TextView>(R.id.real_dollars).apply {
            val ref = e.referenceMonth
            when {
                month < ref -> {
                    text = "About ${Format.money(e.inReferenceDollars)} in ${ref.label()} dollars"
                    visibility = View.VISIBLE
                }
                month > ref -> {
                    text = "About ${Format.money(e.inReferenceDollars)} in ${ref.label()} dollars, if inflation runs at " +
                        Format.decimals(estimator.data.metaDouble("future_inflation") * 100, 0) + "% a year"
                    visibility = View.VISIBLE
                }
                else -> visibility = View.GONE
            }
        }

        findViewById<TextView>(R.id.based_on).text = buildString {
            append(e.basis.detail)
            val loc = location
            if (loc != null) {
                append("\n")
                append(getString(R.string.based_on)).append(": ")
                append(loc.contributors.joinToString(" · ") { (n, share) -> "${n.name} ${(share * 100).roundToLong()}%" })
                append(". Area level ").append(Format.times(loc.factor))
                append(" the city average (±").append((loc.sigma * 100).roundToLong()).append("%).")
            }
        }

        monthLabel.text = month.label()
        monthLabel.contentDescription = "Month: ${month.label()}. Tap to pick a month."
        slider.progress = month - estimator.range.start
        findViewById<View>(R.id.prev_year).isEnabled = month > estimator.range.start
        findViewById<View>(R.id.prev_month).isEnabled = month > estimator.range.start
        findViewById<View>(R.id.next_month).isEnabled = month < estimator.range.endInclusive
        findViewById<View>(R.id.next_year).isEnabled = month < estimator.range.endInclusive

        for ((t, button) in typeButtons) {
            val selected = t == type
            button.isSelected = selected
            button.setTextColor(getColor(if (selected) R.color.on_accent else R.color.ink))
        }

        chart.setOptions(inflationAdjusted, logScale)
        chart.setSelected(month)
        styleToggle(findViewById(R.id.toggle_real), inflationAdjusted)
        styleToggle(findViewById(R.id.toggle_log), logScale)
        findViewById<TextView>(R.id.chart_legend).text = SpannableStringBuilder().apply {
            fun key(symbol: String, color: Int, label: String) {
                if (isNotEmpty()) append("    ")
                val start = length
                append(symbol)
                setSpan(ForegroundColorSpan(color), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                append(" ").append(label)
            }
            val accent = getColor(R.color.accent)
            key("━", accent, if (p == null && type == PropertyType.ALL) getString(R.string.legend_city) else getString(R.string.legend_estimate))
            if (p != null || type != PropertyType.ALL) key("╌", getColor(R.color.city_line), getString(R.string.legend_city))
            key("█", withAlpha(accent, 0.3f), getString(R.string.legend_range))
            if (inflationAdjusted) append("\nIn ${e.referenceMonth.label()} dollars.")
            append("\n").append(getString(R.string.history_hint))
        }
        map.setMarker(p?.location)
    }

    private fun styleToggle(view: TextView, on: Boolean) {
        view.isSelected = on
        view.setTextColor(getColor(if (on) R.color.on_accent else R.color.ink))
    }

    private fun basisColor(basis: Basis): Int = when (basis) {
        Basis.RECENT -> R.color.basis_recent
        Basis.ANNUAL -> R.color.basis_annual
        Basis.CENSUS -> R.color.basis_census
        Basis.BACKCAST -> R.color.basis_backcast
        Basis.PROJECTION -> R.color.basis_projection
    }

    // ---- For tests ------------------------------------------------------------------------------

    internal val currentPlace: Place? get() = place
    internal val currentMonthShown: Month get() = month
    internal val currentType: PropertyType get() = type
}
