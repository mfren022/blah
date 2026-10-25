package com.draftpad.core

import kotlin.math.abs

/**
 * Writes a sketch as SVG, drawn the same way as on screen: round caps and joins, strokes smoothed by
 * [Geometry.traceStroke], arrow heads from [Geometry.arrowHead], text in a sans-serif face.
 */
object Svg {
    /**
     * The sketch as a standalone SVG showing [frame]. Coordinates are canvas units with the frame's
     * top-left corner as the origin. [backgroundHref] locates the background image: a file next to
     * the SVG, or a data: URI to embed it. Without one the background image is left out.
     */
    fun write(draft: Draft, frame: Box, backgroundHref: String? = null): String {
        val ox = frame.left
        val oy = frame.top
        fun x(v: Float) = Json.num(v - ox)
        fun y(v: Float) = Json.num(v - oy)
        val w = Json.num(frame.width)
        val h = Json.num(frame.height)

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$w\" height=\"$h\" viewBox=\"0 0 $w $h\">\n")
        if (draft.title.isNotBlank()) sb.append("<title>").append(escape(draft.title)).append("</title>\n")
        if (draft.notes.isNotBlank()) sb.append("<desc>").append(escape(draft.notes)).append("</desc>\n")
        sb.append("<!-- Drawn in Draftpad. Units are canvas units: 1 unit is 1dp on the phone at 100% zoom. -->\n")
        sb.append("<rect width=\"$w\" height=\"$h\" fill=\"#FFFFFF\"/>\n")
        val bg = draft.background
        if (bg != null && backgroundHref != null) {
            sb.append("<image href=\"").append(escape(backgroundHref)).append("\" x=\"").append(x(bg.x))
                .append("\" y=\"").append(y(bg.y)).append("\" width=\"").append(Json.num(bg.width))
                .append("\" height=\"").append(Json.num(bg.height)).append("\" preserveAspectRatio=\"none\"/>\n")
        }
        sb.append("<g fill=\"none\" stroke-linecap=\"round\" stroke-linejoin=\"round\">\n")
        for (e in draft.elements) {
            when (e) {
                is Stroke -> if (e.pointCount == 1) {
                    sb.append("<circle cx=\"").append(x(e.points[0])).append("\" cy=\"").append(y(e.points[1]))
                        .append("\" r=\"").append(Json.num(e.width / 2)).append("\" ").append(paint("fill", e.color))
                        .append("/>\n")
                } else {
                    sb.append("<path d=\"").append(pathData(e.points, ox, oy)).append("\" ")
                        .append(stroke(e.color, e.width)).append("/>\n")
                }
                is Shape -> shape(sb, e, ::x, ::y)
                is Label -> {
                    sb.append("<text font-family=\"sans-serif\" font-size=\"").append(Json.num(e.size)).append("\" ")
                        .append(paint("fill", e.color)).append(" xml:space=\"preserve\">")
                    e.lines.forEachIndexed { i, line ->
                        sb.append("<tspan x=\"").append(x(e.x)).append("\" y=\"").append(y(e.baseline(i))).append("\">")
                            .append(escape(line)).append("</tspan>")
                    }
                    sb.append("</text>\n")
                }
            }
        }
        sb.append("</g>\n</svg>\n")
        return sb.toString()
    }

    private fun shape(sb: StringBuilder, s: Shape, x: (Float) -> String, y: (Float) -> String) {
        val paint = stroke(s.color, s.width)
        when (s.kind) {
            ShapeKind.LINE -> sb.append("<line x1=\"").append(x(s.x1)).append("\" y1=\"").append(y(s.y1))
                .append("\" x2=\"").append(x(s.x2)).append("\" y2=\"").append(y(s.y2)).append("\" ").append(paint).append("/>\n")
            ShapeKind.ARROW -> {
                val head = Geometry.arrowHead(s)
                sb.append("<g data-kind=\"arrow\"><line x1=\"").append(x(s.x1)).append("\" y1=\"").append(y(s.y1))
                    .append("\" x2=\"").append(x(head.shaftX)).append("\" y2=\"").append(y(head.shaftY)).append("\" ")
                    .append(paint).append("/><polygon points=\"")
                    .append(x(head.tipX)).append(',').append(y(head.tipY)).append(' ')
                    .append(x(head.leftX)).append(',').append(y(head.leftY)).append(' ')
                    .append(x(head.rightX)).append(',').append(y(head.rightY)).append("\" ")
                    .append(paint("fill", s.color)).append("/></g>\n")
            }
            ShapeKind.RECT -> {
                val b = Box.around(s.x1, s.y1, s.x2, s.y2)
                sb.append("<rect x=\"").append(x(b.left)).append("\" y=\"").append(y(b.top))
                    .append("\" width=\"").append(Json.num(b.width)).append("\" height=\"").append(Json.num(b.height))
                    .append("\" ").append(paint).append("/>\n")
            }
            ShapeKind.ELLIPSE -> sb.append("<ellipse cx=\"").append(x((s.x1 + s.x2) / 2)).append("\" cy=\"")
                .append(y((s.y1 + s.y2) / 2)).append("\" rx=\"").append(Json.num(abs(s.x2 - s.x1) / 2))
                .append("\" ry=\"").append(Json.num(abs(s.y2 - s.y1) / 2)).append("\" ").append(paint).append("/>\n")
        }
    }

    /** SVG path data for a stroke, relative to (ox, oy). */
    fun pathData(points: FloatArray, ox: Float = 0f, oy: Float = 0f): String {
        val d = StringBuilder()
        Geometry.traceStroke(points, object : PathSink {
            override fun moveTo(x: Float, y: Float) {
                d.append('M').append(Json.num(x - ox)).append(' ').append(Json.num(y - oy))
            }

            override fun lineTo(x: Float, y: Float) {
                d.append(" L").append(Json.num(x - ox)).append(' ').append(Json.num(y - oy))
            }

            override fun quadTo(cx: Float, cy: Float, x: Float, y: Float) {
                d.append(" Q").append(Json.num(cx - ox)).append(' ').append(Json.num(cy - oy)).append(' ')
                    .append(Json.num(x - ox)).append(' ').append(Json.num(y - oy))
            }
        })
        return d.toString()
    }

    private fun stroke(color: Int, width: Float) = paint("stroke", color) + " stroke-width=\"" + Json.num(width) + "\""

    /** `fill="#RRGGBB"` or `stroke="#RRGGBB"`, plus an opacity for translucent colours. */
    private fun paint(attribute: String, color: Int): String {
        val rgb = Colors.hex(color or (0xFF shl 24))
        val alpha = color ushr 24
        val opacity = if (alpha == 0xFF) "" else " $attribute-opacity=\"${Json.num(alpha / 255f)}\""
        val none = if (attribute == "fill") " stroke=\"none\"" else ""
        return "$attribute=\"$rgb\"$opacity$none"
    }

    /** Escapes text for XML content and attributes, dropping characters XML 1.0 can't hold. */
    fun escape(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) when {
            ch == '&' -> sb.append("&amp;")
            ch == '<' -> sb.append("&lt;")
            ch == '>' -> sb.append("&gt;")
            ch == '"' -> sb.append("&quot;")
            ch == '\'' -> sb.append("&apos;")
            ch < ' ' && ch != '\n' && ch != '\t' && ch != '\r' -> {}
            ch == '￾' || ch == '￿' -> {}
            else -> sb.append(ch)
        }
        return sb.toString()
    }
}
