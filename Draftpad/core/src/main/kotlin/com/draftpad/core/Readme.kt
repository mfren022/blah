package com.draftpad.core

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The README.md pushed next to a sketch. It is what Claude (or anyone browsing the repository) reads
 * first: the title, the notes, the picture, a summary of what is drawn and a guide to the other files.
 */
object Readme {
    const val IMAGE = "sketch.png"
    const val VECTOR = "sketch.svg"
    const val SOURCE = "draft.json"

    private val FILE_NOTES = linkedMapOf(
        IMAGE to "the sketch as an image",
        VECTOR to "the same drawing as vectors: exact shapes, colours and text positions in canvas units",
        SOURCE to "Draftpad's own file for the sketch (every element and its coordinates)",
    )

    /**
     * [frame] is the area the PNG and SVG show, [imageWidth] and [imageHeight] the PNG's size in pixels,
     * and [files] the names pushed next to the README.
     */
    fun write(
        draft: Draft,
        frame: Box,
        imageWidth: Int,
        imageHeight: Int,
        files: List<String>,
        metrics: TextMetrics = TextMetrics.Estimate,
    ): String {
        val title = draft.title.ifBlank { "Untitled sketch" }
        val sb = StringBuilder("# ").append(markdown(title)).append("\n\n")
        if (draft.notes.isNotBlank()) sb.append(draft.notes.trim()).append("\n\n")
        if (IMAGE in files) sb.append("![").append(markdown(title)).append("](").append(IMAGE).append(")\n\n")

        sb.append("- **Contents:** ").append(contents(draft)).append('\n')
        val labels = draft.elements.filterIsInstance<Label>().filter { it.text.isNotBlank() }
            .sortedWith(compareBy({ it.bounds(metrics).top.roundToInt() / 8 }, { it.x }))
        if (labels.isNotEmpty()) {
            sb.append("- **Text**, top to bottom:\n")
            for (l in labels) {
                val b = l.bounds(metrics)
                sb.append("  - \"").append(markdown(l.text.trim().replace('\n', ' '))).append("\" at (")
                    .append((b.left - frame.left).roundToInt()).append(", ").append((b.top - frame.top).roundToInt())
                    .append(")\n")
            }
        }
        sb.append("- **Size:** ").append(frame.width.roundToInt()).append(" × ").append(frame.height.roundToInt())
            .append(" canvas units (1 unit is 1dp on the phone)")
        if (IMAGE in files) sb.append("; `").append(IMAGE).append("` is ").append(imageWidth).append(" × ")
            .append(imageHeight).append(" px")
        sb.append('\n')
        sb.append("- **Updated:** ").append(time(draft.modified)).append("\n\n")

        sb.append("## Files\n\n")
        for (f in files) {
            val note = FILE_NOTES[f] ?: if (f == draft.background?.file) "the image the sketch is drawn over" else null
            sb.append("- `").append(f).append('`')
            if (note != null) sb.append(": ").append(note)
            sb.append('\n')
        }
        sb.append("\nPositions are measured from the top-left corner of the picture, in canvas units.\n")
        return sb.toString()
    }

    /** "12 pen strokes, 2 rectangles and 1 text label". */
    fun contents(draft: Draft): String {
        val counts = linkedMapOf<String, Int>()
        fun add(name: String) { counts[name] = (counts[name] ?: 0) + 1 }
        for (e in draft.elements) when (e) {
            is Stroke -> add("pen stroke")
            is Shape -> add(
                when (e.kind) {
                    ShapeKind.LINE -> "line"
                    ShapeKind.ARROW -> "arrow"
                    ShapeKind.RECT -> "rectangle"
                    ShapeKind.ELLIPSE -> "ellipse"
                },
            )
            is Label -> add("text label")
        }
        val parts = counts.map { (name, n) -> "$n $name" + if (n == 1) "" else "s" }
        val drawn = when (parts.size) {
            0 -> "nothing drawn yet"
            1 -> parts[0]
            else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
        }
        return if (draft.background != null) "$drawn, over an image" else drawn
    }

    private fun time(millis: Long): String =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'", Locale.ROOT).withZone(ZoneOffset.UTC)
            .format(Instant.ofEpochMilli(millis))

    /** Escapes the characters Markdown would read as formatting. */
    private fun markdown(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            if (ch in "\\`*_[]<>#|") sb.append('\\')
            sb.append(ch)
        }
        return sb.toString()
    }
}
