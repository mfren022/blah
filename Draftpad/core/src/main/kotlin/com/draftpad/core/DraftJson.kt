package com.draftpad.core

/**
 * Draft (de)serialization: Draftpad's own file, also pushed to GitHub as draft.json. One element per
 * line, coordinates rounded to a tenth of a unit:
 *
 * {
 *   "format": "draftpad", "version": 1, "id": "20261025-140312", "title": "Castle", "notes": "...",
 *   "created": 1761400992000, "modified": 1761401102000,
 *   "remotePath": "drafts/2026-10-25-castle",
 *   "background": {"file": "background.jpg", "x": 0, "y": 0, "width": 360, "height": 780},
 *   "elements": [
 *     {"type": "stroke", "color": "#1F1F1F", "width": 4, "points": [10, 20, 14.5, 22]},
 *     {"type": "rect", "color": "#E53935", "width": 4, "x1": 0, "y1": 0, "x2": 100, "y2": 50},
 *     {"type": "text", "color": "#1F1F1F", "size": 20, "x": 12, "y": 40, "text": "Play"}
 *   ]
 * }
 *
 * Shape types are "line", "arrow", "rect" and "ellipse". Unknown types are skipped when reading.
 */
object DraftJson {
    const val FORMAT = "draftpad"
    const val VERSION = 1

    fun encode(d: Draft): String {
        val sb = StringBuilder("{\n")
        fun field(name: String, value: String) {
            sb.append("  ").append(Json.quote(name)).append(": ").append(value).append(",\n")
        }
        field("format", Json.quote(FORMAT))
        field("version", "$VERSION")
        field("id", Json.quote(d.id))
        field("title", Json.quote(d.title))
        field("notes", Json.quote(d.notes))
        field("created", "${d.created}")
        field("modified", "${d.modified}")
        d.remotePath?.let { field("remotePath", Json.quote(it)) }
        d.background?.let {
            field("background", Json.write(mapOf("file" to it.file, "x" to it.x, "y" to it.y, "width" to it.width, "height" to it.height)))
        }
        sb.append("  \"elements\": [")
        d.elements.forEachIndexed { i, e -> sb.append(if (i == 0) "\n    " else ",\n    ").append(Json.write(element(e))) }
        sb.append(if (d.elements.isEmpty()) "]\n}\n" else "\n  ]\n}\n")
        return sb.toString()
    }

    fun element(e: Element): Map<String, Any?> = when (e) {
        is Stroke -> mapOf("type" to "stroke", "color" to Colors.hex(e.color), "width" to e.width, "points" to e.points)
        is Shape -> mapOf(
            "type" to e.kind.id, "color" to Colors.hex(e.color), "width" to e.width,
            "x1" to e.x1, "y1" to e.y1, "x2" to e.x2, "y2" to e.y2,
        )
        is Label -> mapOf(
            "type" to "text", "color" to Colors.hex(e.color), "size" to e.size, "x" to e.x, "y" to e.y, "text" to e.text,
        )
    }

    fun decode(text: String): Draft {
        val root = Json.parse(text) as? Map<*, *> ?: throw IllegalArgumentException("Not a JSON object")
        require(root["format"] == FORMAT) { "Not a Draftpad draft" }
        val background = (root["background"] as? Map<*, *>)?.let {
            Background(it["file"] as? String ?: return@let null, f(it, "x"), f(it, "y"), f(it, "width"), f(it, "height"))
        }
        return Draft(
            id = root["id"] as? String ?: throw IllegalArgumentException("Draft has no id"),
            title = root["title"] as? String ?: "",
            notes = root["notes"] as? String ?: "",
            created = (root["created"] as? Number)?.toLong() ?: 0L,
            modified = (root["modified"] as? Number)?.toLong() ?: 0L,
            elements = (root["elements"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.let(::decodeElement) },
            background = background,
            remotePath = root["remotePath"] as? String,
        )
    }

    private fun f(m: Map<*, *>, key: String) = (m[key] as? Number)?.toFloat() ?: 0f

    private fun decodeElement(m: Map<*, *>): Element? {
        val color = (m["color"] as? String)?.let(Colors::parse) ?: Colors.parse("#1F1F1F")
        return when (val type = m["type"]) {
            "stroke" -> {
                val points = (m["points"] as? List<*>).orEmpty().map { (it as? Number)?.toFloat() ?: 0f }
                if (points.isEmpty() || points.size % 2 != 0) null
                else Stroke(points.toFloatArray(), color, f(m, "width"))
            }
            "text" -> Label(m["text"] as? String ?: "", f(m, "x"), f(m, "y"), f(m, "size"), color)
            else -> ShapeKind.entries.firstOrNull { it.id == type }?.let {
                Shape(it, f(m, "x1"), f(m, "y1"), f(m, "x2"), f(m, "y2"), color, f(m, "width"))
            }
        }
    }
}
