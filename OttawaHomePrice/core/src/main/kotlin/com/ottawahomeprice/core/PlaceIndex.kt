package com.ottawahomeprice.core

import java.text.Normalizer
import java.util.Locale

object Text {
    private val marks = Regex("\\p{M}+")
    private val apostrophes = Regex("[’'`]")
    private val separators = Regex("[^a-z0-9]+")

    /** Lower case, accents and punctuation dropped: "Orléans" and "orleans" match, "Morgan's" is "morgans". */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(marks, "")
            .lowercase(Locale.ROOT)
            .replace(apostrophes, "")
            .replace(separators, " ")
            .trim()
}

/** A Canadian postal code or its first three characters (the Forward Sortation Area). */
data class PostalCode(val fsa: String, val full: String?) {
    companion object {
        // Canada Post never uses D, F, I, O, Q or U, nor W or Z as the first letter.
        private val pattern = Regex(
            "(?<![A-Za-z0-9])([ABCEGHJ-NPRSTVXY]\\d[ABCEGHJ-NPRSTV-Z])(?:[ -]?(\\d[ABCEGHJ-NPRSTV-Z]\\d))?(?![A-Za-z0-9])",
            RegexOption.IGNORE_CASE,
        )

        /** The last postal code (or bare FSA) in [text], which is where addresses put it. */
        fun find(text: String): PostalCode? {
            val match = pattern.findAll(text).lastOrNull() ?: return null
            val fsa = match.groupValues[1].uppercase()
            val ldu = match.groupValues[2].uppercase().takeIf { it.isNotEmpty() }
            return PostalCode(fsa, ldu?.let { "$fsa $it" })
        }

        /** Whether [text] is nothing but a postal code or FSA. */
        fun isOnlyPostal(text: String): Boolean {
            val trimmed = text.trim()
            val match = pattern.find(trimmed) ?: return false
            return match.range.first == 0 && match.range.last == trimmed.length - 1
        }
    }
}

/** Offline search over neighbourhood names and postal areas: instant suggestions, and a fallback without a network. */
class PlaceIndex(private val neighbourhoods: List<Neighbourhood>, postalAreas: List<PostalArea>) {

    enum class Kind { NEIGHBOURHOOD, POSTAL_AREA }

    data class Match(val label: String, val detail: String, val location: LatLon, val kind: Kind, val score: Double)

    private class Entry(val place: Neighbourhood, val names: List<String>)

    private val entries = neighbourhoods.map { n -> Entry(n, (listOf(n.name) + n.aliases).map(Text::normalize).distinct()) }
    private val postal = postalAreas.associateBy { it.fsa.uppercase() }

    fun postalArea(fsa: String): PostalArea? = postal[fsa.uppercase()]

    /** The postal area for a postal code anywhere in [query], if it is an Ottawa one. */
    fun postalMatch(query: String): Match? {
        val code = PostalCode.find(query) ?: return null
        val area = postal[code.fsa] ?: return null
        return Match(code.full ?: code.fsa, "Postal area ${area.fsa}: ${area.area}", area.location, Kind.POSTAL_AREA, 100.0)
    }

    /** Best neighbourhood and postal-area matches for what the user has typed so far. */
    fun search(query: String, limit: Int = 6): List<Match> {
        val q = Text.normalize(query)
        if (q.length < 2) return emptyList()
        val results = ArrayList<Match>()
        postalMatch(query)?.let { results += it }
        val queryWords = q.split(' ')
        for (entry in entries) {
            var best = 0.0
            for (name in entry.names) best = maxOf(best, score(q, queryWords, name))
            if (best > 0) {
                val n = entry.place
                results += Match(n.name, "Neighbourhood", n.location, Kind.NEIGHBOURHOOD, best)
            }
        }
        return results.sortedWith(compareByDescending<Match> { it.score }.thenBy { it.label }).take(limit)
    }

    /** A neighbourhood named anywhere in a full address ("12 Main St, Old Ottawa East"), longest name first. */
    fun neighbourhoodIn(query: String): Neighbourhood? {
        val padded = " ${Text.normalize(query)} "
        var best: Neighbourhood? = null
        var bestLength = 0
        for (entry in entries) for (name in entry.names) {
            if (name.length >= 4 && name.length > bestLength && padded.contains(" $name ")) {
                best = entry.place
                bestLength = name.length
            }
        }
        return best
    }

    private fun score(q: String, queryWords: List<String>, name: String): Double = when {
        name == q -> 100.0
        name.startsWith(q) -> 80.0 - (name.length - q.length) * 0.2
        name.split(' ').any { it.startsWith(q) } -> 60.0 - (name.length - q.length) * 0.1
        queryWords.size > 1 && queryWords.all { w -> name.split(' ').any { it.startsWith(w) } } -> 55.0
        name.length >= 4 && " $q ".contains(" $name ") -> 45.0
        else -> 0.0
    }
}
