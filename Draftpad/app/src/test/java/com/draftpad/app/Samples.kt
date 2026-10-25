package com.draftpad.app

import com.draftpad.core.Draft
import com.draftpad.core.Element
import com.draftpad.core.Label
import com.draftpad.core.Shape
import com.draftpad.core.ShapeKind
import com.draftpad.core.Stroke
import kotlin.math.cos
import kotlin.math.sin

/** Drafts for tests and screenshots. */
object Samples {
    private val ink = Pens.COLORS[0]
    private val grey = Pens.COLORS[1]
    private val red = Pens.COLORS[2]
    private val blue = Pens.COLORS[5]

    /** A wireframe of a level-select screen, using every kind of element. */
    fun levelSelect(id: String = "20261025-101500"): Draft {
        val e = mutableListOf<Element>()
        e += Shape(ShapeKind.RECT, 40f, 30f, 340f, 600f, ink, 4f)
        e += Label("Level select", 64f, 86f, 30f, ink)
        e += Shape(ShapeKind.LINE, 64f, 104f, 316f, 104f, grey, 2f)
        for (row in 0 until 3) for (col in 0 until 3) {
            val x = 64f + col * 88f
            val y = 130f + row * 96f
            e += Shape(ShapeKind.RECT, x, y, x + 72f, y + 72f, if (row == 2) grey else blue, 4f)
            e += Label("${row * 3 + col + 1}", x + 28f, y + 46f, 20f, if (row == 2) grey else blue)
        }
        e += Stroke(star(100f, 166f, 26f), Pens.COLORS[3], 4f)
        e += Shape(ShapeKind.ELLIPSE, 60f, 520f, 160f, 572f, red, 4f)
        e += Label("Back", 86f, 553f, 20f, ink)
        e += Label("Locked levels\nare greyed out", 196f, 470f, 14f, red)
        e += Shape(ShapeKind.ARROW, 230f, 446f, 196f, 404f, red, 4f)
        e += Stroke(wave(196f, 540f, 120f), Pens.COLORS[4], 2f)
        return Draft(
            id = id,
            title = "Level select",
            notes = "Three rows of levels. The bottom row is still locked.",
            created = 1761401700000,
            modified = 1761401700000,
            elements = e,
        )
    }

    /** A hand-drawn five-pointed star around (cx, cy). */
    fun star(cx: Float, cy: Float, r: Float): FloatArray {
        val points = ArrayList<Float>()
        for (i in 0..10) {
            val angle = -Math.PI / 2 + i * Math.PI / 5
            val radius = if (i % 2 == 0) r else r * 0.45f
            points += cx + (radius * cos(angle)).toFloat()
            points += cy + (radius * sin(angle)).toFloat()
        }
        return points.toFloatArray()
    }

    fun wave(x: Float, y: Float, length: Float): FloatArray {
        val points = ArrayList<Float>()
        var t = 0f
        while (t <= length) {
            points += x + t
            points += y + 8f * sin(t / 12f)
            t += 4f
        }
        return points.toFloatArray()
    }
}
