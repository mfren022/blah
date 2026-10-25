package com.draftpad.app

import com.draftpad.core.ShapeKind

enum class Tool(val icon: Int, val label: Int, val shape: ShapeKind? = null) {
    PEN(R.drawable.ic_pen, R.string.tool_pen),
    LINE(R.drawable.ic_line, R.string.tool_line, ShapeKind.LINE),
    ARROW(R.drawable.ic_arrow, R.string.tool_arrow, ShapeKind.ARROW),
    RECT(R.drawable.ic_rect, R.string.tool_rect, ShapeKind.RECT),
    ELLIPSE(R.drawable.ic_ellipse, R.string.tool_ellipse, ShapeKind.ELLIPSE),
    TEXT(R.drawable.ic_text, R.string.tool_text),
    MOVE(R.drawable.ic_move, R.string.tool_move),
    ERASE(R.drawable.ic_eraser, R.string.tool_erase),
}

/** The pen colours and line widths on offer. Widths and text sizes are in canvas units (dp). */
object Pens {
    val COLORS = intArrayOf(
        0xFF1F1F22.toInt(), // black
        0xFF8E8E93.toInt(), // grey
        0xFFE53935.toInt(), // red
        0xFFFB8C00.toInt(), // orange
        0xFF43A047.toInt(), // green
        0xFF1E88E5.toInt(), // blue
        0xFF8E24AA.toInt(), // purple
    )
    val COLOR_NAMES = intArrayOf(
        R.string.color_black, R.string.color_gray, R.string.color_red, R.string.color_orange,
        R.string.color_green, R.string.color_blue, R.string.color_purple,
    )
    val WIDTHS = floatArrayOf(2f, 4f, 8f)
    val WIDTH_NAMES = intArrayOf(R.string.width_thin, R.string.width_medium, R.string.width_thick)

    /** Text size that goes with each width. */
    val TEXT_SIZES = floatArrayOf(14f, 20f, 30f)
}
