package com.ottawahomeprice.core

/** How a figure was measured, from best to weakest evidence. */
enum class Confidence(val key: String, val weight: Double, val sigma: Double) {
    HIGH("high", 1.0, 0.10),
    MEDIUM("medium", 0.75, 0.15),
    LOW("low", 0.5, 0.22);

    companion object {
        fun parse(text: String): Confidence =
            entries.firstOrNull { it.key == text.trim().lowercase() } ?: throw IllegalArgumentException("Unknown confidence '$text'")
    }
}

/** One year's average sale price of all homes in Ottawa ([kind] "mls"), or a census dwelling value ("census"). */
data class AnnualFigure(val year: Int, val price: Double, val kind: Kind, val units: Int?, val source: String, val note: String) {
    enum class Kind { MLS, CENSUS }
}

/**
 * One month's average sale price over all homes sold (freehold and condo together) and how many
 * sold. Before September 2023 OREB published the two classes separately; their sales-weighted
 * mix is stored here.
 */
data class MonthlyFigure(val month: Month, val price: Double, val units: Int?, val source: String, val note: String)

/** A neighbourhood, community or village and how its home prices compare with Ottawa's average. */
data class Neighbourhood(
    val name: String,
    val aliases: List<String>,
    val location: LatLon,
    val radiusKm: Double,
    /** Average home price here divided by the Ottawa-wide average. */
    val factor: Double,
    val confidence: Confidence,
    val basis: String,
    val sources: List<String>,
)

/** A postal Forward Sortation Area (the first three characters of a postal code). */
data class PostalArea(val fsa: String, val location: LatLon, val area: String)

/** A line or label on the offline map. */
data class MapFeature(val name: String, val kind: Kind, val points: List<LatLon>) {
    enum class Kind(val key: String) { RIVER("river"), CANAL("canal"), HIGHWAY("highway"), LABEL("label"), LABEL_OUTSIDE("label_outside") }
}

data class Source(val id: String, val title: String, val url: String, val note: String)

data class TypeRatio(val type: PropertyType, val label: String, val ratio: Double, val basis: String)

/**
 * Everything the estimator knows, parsed from the CSV files in the app's assets (data/). The
 * engine only reads these files, so updating the data (a new month of sales, a new neighbourhood)
 * needs no code change.
 */
class OttawaData(
    val annual: List<AnnualFigure>,
    val monthly: List<MonthlyFigure>,
    val cpi: Map<Int, Double>,
    val neighbourhoods: List<Neighbourhood>,
    val postalAreas: List<PostalArea>,
    val boundary: Polygon,
    val mapFeatures: List<MapFeature>,
    val types: Map<PropertyType, TypeRatio>,
    val sources: Map<String, Source>,
    val meta: Map<String, String>,
) {
    fun metaDouble(key: String): Double =
        meta[key]?.toDoubleOrNull() ?: throw IllegalArgumentException("meta.csv: '$key' missing or not a number")

    fun metaMonth(key: String): Month =
        Month.parse(meta[key] ?: throw IllegalArgumentException("meta.csv: '$key' missing"))

    /** The month the price data runs to. */
    val dataAsOf: Month get() = metaMonth("data_as_of")

    val firstMonth: Month get() = metaMonth("first_month")
    val lastMonth: Month get() = metaMonth("last_month")

    companion object {
        val FILES = listOf(
            "annual.csv", "monthly.csv", "cpi.csv", "neighbourhoods.csv", "postal_areas.csv",
            "boundary.csv", "map.csv", "types.csv", "sources.csv", "meta.csv",
        )

        /** Parses the data files; [read] returns the text of a file by name (e.g. "annual.csv"). */
        fun parse(read: (String) -> String): OttawaData {
            fun table(name: String) = CsvTable.parse(name, read(name))

            val annual = table("annual.csv").rows.map {
                AnnualFigure(
                    year = it.int("year"),
                    price = it.double("avg_price"),
                    kind = when (it.str("kind")) {
                        "mls" -> AnnualFigure.Kind.MLS
                        "census" -> AnnualFigure.Kind.CENSUS
                        else -> throw IllegalArgumentException("annual.csv line ${it.line}: kind must be mls or census")
                    },
                    units = it.doubleOrNull("units")?.toInt(),
                    source = it.str("source"),
                    note = it.strOrNull("note") ?: "",
                )
            }.sortedBy { it.year }

            val monthly = table("monthly.csv").rows.map {
                MonthlyFigure(
                    month = Month.parse(it.str("month")),
                    price = it.double("avg_price"),
                    units = it.doubleOrNull("units")?.toInt(),
                    source = it.str("source"),
                    note = it.strOrNull("note") ?: "",
                )
            }.sortedBy { it.month }

            val cpi = table("cpi.csv").rows.associate { it.int("year") to it.double("cpi") }

            val neighbourhoods = table("neighbourhoods.csv").rows.map {
                Neighbourhood(
                    name = it.str("name"),
                    aliases = it.list("aliases"),
                    location = LatLon(it.double("lat"), it.double("lon")),
                    radiusKm = it.double("radius_km"),
                    factor = it.double("factor"),
                    confidence = Confidence.parse(it.str("confidence")),
                    basis = it.strOrNull("basis") ?: "",
                    sources = it.list("sources"),
                )
            }

            val postal = table("postal_areas.csv").rows.map {
                PostalArea(it.str("fsa").uppercase(), LatLon(it.double("lat"), it.double("lon")), it.str("area"))
            }

            val boundary = Polygon(table("boundary.csv").rows.map { LatLon(it.double("lat"), it.double("lon")) })

            val features = LinkedHashMap<Pair<String, MapFeature.Kind>, MutableList<LatLon>>()
            for (row in table("map.csv").rows) {
                val kind = MapFeature.Kind.entries.firstOrNull { it.key == row.str("kind") }
                    ?: throw IllegalArgumentException("map.csv line ${row.line}: unknown kind '${row.str("kind")}'")
                features.getOrPut(row.str("feature") to kind) { ArrayList() } += LatLon(row.double("lat"), row.double("lon"))
            }
            val mapFeatures = features.map { (key, points) -> MapFeature(key.first, key.second, points) }

            val types = table("types.csv").rows.associate {
                val type = PropertyType.parse(it.str("type"))
                type to TypeRatio(type, it.str("label"), it.double("ratio"), it.strOrNull("basis") ?: "")
            }

            val sources = table("sources.csv").rows.associate {
                it.str("id") to Source(it.str("id"), it.str("title"), it.strOrNull("url") ?: "", it.strOrNull("note") ?: "")
            }

            val meta = table("meta.csv").rows.associate { it.str("key") to it.str("value") }

            return OttawaData(annual, monthly, cpi, neighbourhoods, postal, boundary, mapFeatures, types, sources, meta)
        }
    }
}
