package com.ottawahomeprice.core

import java.io.File

/** Small synthetic data sets for engine tests, and the data the app ships. */
object TestData {
    val META = """
        key,value
        data_as_of,2026-09
        first_month,1914-01
        last_month,2060-12
        cpi_latest_month,2026-09
        cpi_latest,168.0
        future_inflation,0.02
        projection_growth,0.035
        projection_sigma,0.065
        backcast_real_growth,0.0
        backcast_sigma_per_year,0.008
        sigma_annual,0.04
        sigma_recent,0.045
        sigma_census,0.12
        sigma_type,0.08
    """.trimIndent()

    val BASE = mapOf(
        "annual.csv" to """
            year,avg_price,kind,units,source,note
            1951,10000,census,,s,
            1960,15000,mls,,s,
            1970,25000,mls,,s,
            1980,60000,mls,,s,
            1990,140000,mls,,s,
            2000,160000,mls,,s,
            2010,330000,mls,,s,
            2020,530000,mls,,s,
            2025,650000,mls,,s,
        """.trimIndent(),
        "monthly.csv" to "month,avg_price,units,source,note",
        "cpi.csv" to """
            year,cpi,source
            1914,6.0,s
            1951,12.0,s
            1960,15.0,s
            1980,44.0,s
            2000,95.0,s
            2025,165.0,s
        """.trimIndent(),
        "neighbourhoods.csv" to """
            name,aliases,lat,lon,radius_km,factor,confidence,basis,sources
            Glebe,The Glebe,45.4010,-75.6900,0.8,1.8,high,b,s
            Westboro,Westboro Village|Westboro Beach,45.3920,-75.7560,0.9,1.5,medium,b,s
            Vanier,Eastview,45.4370,-75.6620,0.9,0.75,medium,b,s
            Orléans,Orleans,45.4690,-75.5150,3.0,1.0,low,b,s
            Old Ottawa East,,45.4100,-75.6750,0.8,1.4,medium,b,s
        """.trimIndent(),
        "postal_areas.csv" to """
            fsa,lat,lon,area
            K1S,45.3990,-75.6880,Glebe; Old Ottawa South
            K1L,45.4380,-75.6600,Vanier
        """.trimIndent(),
        "boundary.csv" to """
            lat,lon
            45.60,-76.40
            45.60,-75.20
            44.90,-75.20
            44.90,-76.40
        """.trimIndent(),
        "map.csv" to """
            feature,kind,lat,lon
            Ottawa River,river,45.47,-76.27
            Ottawa River,river,45.43,-75.70
            Parliament Hill,label,45.4236,-75.7009
        """.trimIndent(),
        "types.csv" to """
            type,label,ratio,basis
            all,All homes,1.0,b
            detached,Detached house,1.2,b
            condo,Condo apartment,0.6,b
        """.trimIndent(),
        "sources.csv" to "id,title,url,note\ns,Synthetic,,",
        "meta.csv" to META,
    )

    fun data(vararg overrides: Pair<String, String>): OttawaData {
        val files = BASE + overrides.toMap()
        return OttawaData.parse { files.getValue(it) }
    }

    /** The app's data/ folder (set by the build), or null when run some other way. */
    val shippedDir: File by lazy {
        val dir = System.getProperty("data.dir")?.let(::File)
            ?: listOf("../app/src/main/assets/data", "app/src/main/assets/data", "../../app/src/main/assets/data")
                .map(::File).firstOrNull { it.isDirectory }
        checkNotNull(dir?.takeIf { it.isDirectory }) { "Can't find the app's data folder; set -Ddata.dir" }
    }

    val shipped: OttawaData by lazy { OttawaData.parse { File(shippedDir, it).readText() } }
}
