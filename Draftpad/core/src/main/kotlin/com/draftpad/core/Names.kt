package com.draftpad.core

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Ids, file-safe names and repository paths. */
object Names {
    const val DEFAULT_FOLDER = "drafts"

    /** "Café menu, v2!" becomes "cafe-menu-v2". Never empty. */
    fun slug(title: String, maxLength: Int = 48): String {
        val ascii = Normalizer.normalize(title, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        val slug = ascii.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return slug.take(maxLength).trimEnd('-').ifEmpty { "sketch" }
    }

    /** A new draft id from the time, such as "20261025-140312", with "-2", "-3"... if [taken]. */
    fun newId(now: Long, zone: ZoneId, taken: (String) -> Boolean): String {
        val base = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).withZone(zone).format(Instant.ofEpochMilli(now))
        if (!taken(base)) return base
        var n = 2
        while (taken("$base-$n")) n++
        return "$base-$n"
    }

    /**
     * The repository folder a draft is first pushed to, "<folder>/<date>-<slug>", such as
     * "drafts/2026-10-25-castle-keep". Later pushes reuse [Draft.remotePath].
     */
    fun remoteFolder(folder: String, draft: Draft, zone: ZoneId): String {
        val date = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT).withZone(zone)
            .format(Instant.ofEpochMilli(draft.created))
        return "${cleanFolder(folder)}/$date-${slug(draft.title)}"
    }

    /** A repository folder as typed ("/sketches/ideas/") made safe ("sketches/ideas"). */
    fun cleanFolder(input: String): String {
        val parts = input.split('/', '\\').map { it.trim() }.filter { it.isNotEmpty() && it != "." && it != ".." }
        return parts.joinToString("/").ifEmpty { DEFAULT_FOLDER }
    }
}
