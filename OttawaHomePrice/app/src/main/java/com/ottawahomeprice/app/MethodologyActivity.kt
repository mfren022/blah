package com.ottawahomeprice.app

import android.app.Activity
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ottawahomeprice.app.ui.dp
import com.ottawahomeprice.app.ui.fitSystemBars
import com.ottawahomeprice.app.ui.styleSystemBars
import com.ottawahomeprice.core.AnnualFigure
import com.ottawahomeprice.core.Format
import com.ottawahomeprice.core.Month

/** How the estimate is made, what it rests on, and where every figure comes from. */
class MethodologyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_methodology)
        styleSystemBars()
        fitSystemBars(findViewById<ScrollView>(R.id.root))
        findViewById<android.view.View>(R.id.back).setOnClickListener { finish() }

        val estimator = (application as HomePriceApp).estimator
        val data = estimator.data
        val index = estimator.index
        val mls = data.annual.filter { it.kind == AnnualFigure.Kind.MLS }
        val census = data.annual.filter { it.kind == AnnualFigure.Kind.CENSUS }
        val firstData = Month.containing(index.knotTimes.first())
        val growth = Format.decimals(data.metaDouble("projection_growth") * 100, 1)
        val inflation = Format.decimals(data.metaDouble("future_inflation") * 100, 0)
        val sections = findViewById<LinearLayout>(R.id.sections)

        fun section(title: String, body: String, links: Boolean = false) {
            sections.addView(TextView(this).apply {
                text = title
                setTextColor(getColor(R.color.ink))
                textSize = 17f
                typeface = resources.getFont(R.font.roboto_medium)
                setPadding(0, dp(18), 0, dp(6))
            })
            sections.addView(TextView(this).apply {
                text = body
                setTextColor(getColor(R.color.ink_muted))
                textSize = 14f
                setLineSpacing(0f, 1.15f)
                if (links) {
                    Linkify.addLinks(this, Linkify.WEB_URLS)
                    movementMethod = LinkMovementMethod.getInstance()
                    setLinkTextColor(getColor(R.color.accent))
                }
            })
        }

        section(
            "What the number means",
            "The estimate is the average sale price of homes like those around the spot you picked, in the " +
                "month you picked. It describes the area, not a particular house: a large detached house and " +
                "a small condo on the same street can differ by several times. It is not an appraisal.",
        )
        section(
            "1. Ottawa's average, month by month",
            "The backbone is the average price of all homes sold through the MLS® system in Ottawa: one " +
                "figure per year from ${mls.first().year} to ${mls.last().year} (Ottawa Real Estate Board), " +
                "placed at mid-year and joined by a smooth curve that never overshoots between years. " +
                (if (index.lastDataMonth.year > mls.last().year) "Monthly figures carry it to ${index.lastDataMonth.label()}: " +
                    "the last point is carried forward at the year-over-year change of the latest months, " +
                    "which keeps the spring peak and winter lull out of it. " else "") +
                (if (census.isNotEmpty()) "Before that, census dwelling values from ${census.joinToString(", ") { it.year.toString() }} " +
                    "extend it to ${firstData.year}. " else "") +
                "Earlier months are carried back with Canada's consumer price index, assuming home prices " +
                "kept pace with inflation (low confidence). Months after ${index.lastDataMonth.label()} are " +
                "projected at $growth% a year, a long-run assumption, not a market forecast.",
        )
        val sourced = data.neighbourhoods.count { it.sources.isNotEmpty() }
        val estimated = data.neighbourhoods.size - sourced
        section(
            "2. How the spot compares with the city",
            "${data.neighbourhoods.size} neighbourhoods, communities and villages each carry a price level " +
                "relative to the city average (1.5× means homes there average 50% more). " +
                when {
                    estimated == 0 -> "Each comes from a cited source: census dwelling values or MLS® neighbourhood sales. "
                    sourced == 0 -> "These levels are approximate estimates from general knowledge of Ottawa's housing " +
                        "market (typical values by area around 2021–2025); they have not yet been checked " +
                        "against census or sales figures, so treat differences of 10–20% between nearby areas loosely. "
                    else -> "$sourced come from a cited source (census dwelling values or MLS® neighbourhood sales); " +
                        "the other $estimated are approximate estimates from general knowledge of Ottawa's market. "
                } +
                "The level at any spot blends the nearby ones, weighted by distance relative to each area's " +
                "size and by how reliable its figure is. The map shows this blend. Area levels are held fixed " +
                "over time, so they don't capture neighbourhoods that have risen or fallen relative to the " +
                "city; areas built up recently show what similar homes cost in earlier years, not homes " +
                "that stood there.",
        )
        section(
            "3. Type of home",
            "Choosing a type multiplies by how that type's typical price compares with all homes in Ottawa: " +
                data.types.values.filter { it.ratio != 1.0 }.joinToString("; ") { "${it.label} ${Format.times(it.ratio)}" } +
                ". These are city-wide ratios; the mix in a given area can differ.",
        )
        section(
            "How sure it is",
            "The likely range is about two chances in three. It combines the uncertainty of the city " +
                "average for that month (a few percent in the sales era, much more for back-cast and " +
                "projected months), of the area's level (more where neighbourhoods disagree or are far " +
                "away) and of the type ratio.",
        )
        section(
            "Inflation",
            "‘After inflation’ restates prices in ${estimator.referenceMonth.label()} dollars using " +
                "Statistics Canada's consumer price index; future months assume $inflation% inflation a year.",
        )
        section(
            "Data sources",
            data.sources.values.filter { it.id != "provisional" || data.sources.size == 1 }
                .joinToString("\n\n") { s -> s.title + (if (s.url.isNotEmpty()) "\n" + s.url else "") + (if (s.note.isNotEmpty()) "\n" + s.note else "") },
            links = true,
        )
        section(
            "Privacy",
            "Everything is calculated on your phone. Only an address you search for leaves it: it goes to " +
                "your phone's geocoding service (usually Google) or, if that finds nothing, to " +
                "OpenStreetMap's Nominatim. Postal codes, neighbourhood names and map taps are handled " +
                "offline. Recent searches are kept on the phone.",
        )
        section(
            "Data as of",
            "Sales data through ${data.dataAsOf.label()}. The figures live in the app's data files " +
                "(assets/data/*.csv), so new months can be added without changing the code.",
        )
    }
}
