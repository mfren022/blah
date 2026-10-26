package com.ultimatesudoku.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.ultimatesudoku.app.R
import com.ultimatesudoku.core.LevelMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A chapter's levels as a node graph, like a metro map: square stops in rows and lanes, joined by
 * right-angled paths. The entrance is at the top and the chapter's final level, drawn larger with a
 * star, at the bottom. Finished levels are filled, and the paths on from them drawn bright; levels
 * ready to play are filled blue and ringed; the rest are drawn locked.
 *
 * In the [Style.ISLANDS] style the map lies over a landscape drawn as one isometric grid of diamond
 * tiles in just two shades: water a little darker than the page, and land much darker, an island
 * around each level. The grid fades into the page above and below the levels.
 */
class LevelMapView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    enum class Style { METRO, ISLANDS }

    fun interface OnLevelTapListener {
        fun onLevelTapped(level: LevelMap.Level)
    }

    var onLevelTap: OnLevelTapListener? = null

    var map: LevelMap? = null
        set(value) {
            field = value
            landscape = null
            requestLayout()
            invalidate()
        }

    var style = Style.METRO
        set(value) {
            field = value
            landscape = null
            requestLayout()
            invalidate()
        }

    /** Numbers of the levels ready to play next. */
    var open: Set<Int> = emptySet()
        set(value) {
            field = value
            invalidate()
        }

    /** Numbers of the levels finished. */
    var finished: Set<Int> = emptySet()
        set(value) {
            field = value
            invalidate()
        }

    private val palette = Palette(context)
    private val ready = context.getColor(R.color.story_level_ready)
    private val readyPressed = context.getColor(R.color.story_level_ready_pressed)
    private val fonts = Fonts.get(context)
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
        strokeJoin = Paint.Join.MITER
    }
    private val fill = Paint()
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val route = Path()
    private val diamond = Path()
    private val finalMark = context.getDrawable(R.drawable.ic_star)!!.mutate()

    private val stop = dp(44f)
    private val finalStop = dp(60f)
    private val ring = dp(7f)
    private val rowGap = dp(76f)

    /** Room above and below the levels for the landscape to fade into the page, and how far it fades. */
    private val margin = dp(72f)
    private val fade = dp(64f)

    /** The level under the finger, by number, or 0. */
    private var pressed = 0

    /** The landscape's tiles, worked out once for the map and the view's size. */
    private var landscape: Landscape? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val rows = map?.rows ?: 0
        var content = if (rows == 0) 0f else 2 * ring + finalStop + (rows - 1) * rowGap
        if (rows > 0 && style == Style.ISLANDS) content += 2 * margin
        val height = paddingTop + paddingBottom + content.roundToInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        landscape = null
    }

    /** Where level [number] is drawn: its centre, in this view's pixels. */
    fun center(number: Int): PointF? {
        val level = map?.levels?.firstOrNull { it.number == number } ?: return null
        return PointF(x(level.lane), y(level.row))
    }

    /** A point inside level [number]'s stop, near its top left corner and clear of its number. */
    fun fillPoint(number: Int): PointF? {
        val map = map ?: return null
        val level = map.levels.firstOrNull { it.number == number } ?: return null
        val inset = half(map, level) - dp(4f)
        return PointF(x(level.lane) - inset, y(level.row) - inset)
    }

    private fun x(lane: Int): Float {
        val inner = width - paddingLeft - paddingRight
        val spread = min(inner * 0.3f, dp(116f))
        return paddingLeft + inner / 2f + (lane - 1) * spread
    }

    private fun y(row: Int): Float =
        paddingTop + (if (style == Style.ISLANDS) margin else 0f) + ring + finalStop / 2 + row * rowGap

    private fun half(map: LevelMap, level: LevelMap.Level) = (if (level == map.final) finalStop else stop) / 2

    override fun onDraw(canvas: Canvas) {
        val map = map ?: return
        if (style == Style.ISLANDS) drawLandscape(canvas, map)
        // Paths run centre to centre under the stops: down, across halfway between the rows, down.
        // Those on from a finished level are walked, and drawn bright.
        track.color = palette.ink
        for (path in map.paths) {
            val walked = path.from in finished
            track.strokeWidth = dp(if (walked) 3f else 2f)
            track.alpha = if (walked) 210 else 70
            val from = map.level(path.from)
            val to = map.level(path.to)
            val mid = (y(from.row) + y(to.row)) / 2
            route.reset()
            route.moveTo(x(from.lane), y(from.row))
            route.lineTo(x(from.lane), mid)
            route.lineTo(x(to.lane), mid)
            route.lineTo(x(to.lane), y(to.row))
            canvas.drawPath(route, track)
        }
        for (level in map.levels) drawStop(canvas, map, level)
    }

    private fun drawStop(canvas: Canvas, map: LevelMap, level: LevelMap.Level) {
        val cx = x(level.lane)
        val cy = y(level.row)
        val h = half(map, level)
        val isOpen = level.number in open
        val isFinished = level.number in finished
        val ink: Int
        if (isFinished) {
            fill.color = if (pressed == level.number) palette.accentPressed else palette.accent
            canvas.drawRect(cx - h, cy - h, cx + h, cy + h, fill)
            ink = palette.onAccent
        } else if (isOpen) {
            // Blue, and ringed, where to go next.
            edge.color = palette.ink
            edge.alpha = 110
            edge.strokeWidth = dp(1.5f)
            canvas.drawRect(cx - h - ring, cy - h - ring, cx + h + ring, cy + h + ring, edge)
            fill.color = if (pressed == level.number) readyPressed else ready
            canvas.drawRect(cx - h, cy - h, cx + h, cy + h, fill)
            ink = palette.ink
        } else {
            // Opaque first, so the paths stop at the edge.
            fill.color = palette.bg
            canvas.drawRect(cx - h, cy - h, cx + h, cy + h, fill)
            fill.color = if (pressed == level.number) palette.surfacePressed else palette.surface
            canvas.drawRect(cx - h, cy - h, cx + h, cy + h, fill)
            edge.color = palette.ink
            edge.alpha = 90
            edge.strokeWidth = dp(1.5f)
            val inset = edge.strokeWidth / 2
            canvas.drawRect(cx - h + inset, cy - h + inset, cx + h - inset, cy + h - inset, edge)
            ink = palette.inkMuted
        }
        if (level == map.final) {
            val size = (h * 0.9f).roundToInt()
            finalMark.setBounds((cx - size / 2f).roundToInt(), (cy - size / 2f).roundToInt(), (cx + size / 2f).roundToInt(), (cy + size / 2f).roundToInt())
            finalMark.setTint(ink)
            finalMark.draw(canvas)
        } else {
            label.typeface = fonts.medium
            label.textSize = sp(15f)
            label.color = ink
            val fm = label.fontMetrics
            canvas.drawText(level.number.toString(), cx, cy - (fm.ascent + fm.descent) / 2, label)
        }
    }

    // ---- Landscape ----------------------------------------------------------------------------

    /** The landscape's tiles: each one's centre, whether it's land, and how far it has faded. */
    private class Landscape(val xs: FloatArray, val ys: FloatArray, val land: BooleanArray, val alpha: IntArray)

    private fun drawLandscape(canvas: Canvas, map: LevelMap) {
        val tiles = landscape ?: buildLandscape(map).also { landscape = it }
        // Diamonds twice as wide as tall, with a hairline of the page between neighbours.
        val gap = max(1f, dp(1f))
        val hw = dp(TILE_DP) / 2 - gap * 1.1f
        val hh = dp(TILE_DP) / 4 - gap * 0.55f
        diamond.reset()
        diamond.moveTo(0f, -hh)
        diamond.lineTo(hw, 0f)
        diamond.lineTo(0f, hh)
        diamond.lineTo(-hw, 0f)
        diamond.close()
        // The only two shades: water a little darker than the page, land much darker.
        val water = mix(palette.bg, Color.BLACK, WATER_SHADE)
        val land = mix(palette.bg, Color.BLACK, LAND_SHADE)
        for (k in tiles.xs.indices) {
            if (tiles.alpha[k] == 0) continue
            tilePaint.color = if (tiles.land[k]) land else water
            tilePaint.alpha = tiles.alpha[k]
            canvas.save()
            canvas.translate(tiles.xs[k], tiles.ys[k])
            canvas.drawPath(diamond, tilePaint)
            canvas.restore()
        }
    }

    /**
     * Tiles sit on an isometric lattice: tile (i, j) is drawn at the view's centre column plus (i - j)
     * half tile widths across, and (i + j) half tile heights down. An island is round on the ground, so
     * on screen an ellipse twice as wide as tall, its shore roughened by noise.
     */
    private fun buildLandscape(map: LevelMap): Landscape {
        val tw = dp(TILE_DP)
        val th = tw / 2
        val cx = width / 2f
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        val land = ArrayList<Boolean>()
        val alpha = ArrayList<Int>()
        val aMax = ceil(width / tw).toInt() + 2
        val bMax = ceil(height / (th / 2)).toInt() + 2
        for (b in -2..bMax) {
            for (a in -aMax..aMax) {
                if ((a + b) and 1 != 0) continue
                val x = cx + a * tw / 2
                val y = b * th / 2
                if (x < -tw || x > width + tw || y < -th || y > height + th) continue
                val i = (a + b) / 2
                val j = (b - a) / 2
                val wobble = (smooth(i, j) - 0.5f) * 2 * dp(COAST_WOBBLE_DP)
                val isLand = map.levels.any { level ->
                    val size = if (level == map.final) FINAL_ISLAND_DP else ISLAND_DP
                    val radius = dp(size) * (0.9f + 0.2f * hash(level.number, 0, SALT + 1))
                    hypot(x - x(level.lane), (y - y(level.row)) * 2) + wobble < radius
                }
                // Faded in from the top and out to the bottom, gone a tile short of each edge.
                var f = ((min(y, height - y) - th) / fade).coerceIn(0f, 1f)
                f = f * f * (3 - 2 * f)
                xs += x
                ys += y
                land += isLand
                alpha += (f * 255).roundToInt()
            }
        }
        return Landscape(xs.toFloatArray(), ys.toFloatArray(), land.toBooleanArray(), alpha.toIntArray())
    }

    private fun levelAt(x: Float, y: Float): LevelMap.Level? {
        val map = map ?: return null
        val reach = dp(6f)
        return map.levels.firstOrNull {
            val h = half(map, it) + reach
            abs(x - x(it.lane)) <= h && abs(y - y(it.row)) <= h
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = levelAt(event.x, event.y)?.number ?: 0
                invalidate()
                // Elsewhere, leave the touch to the scroll view.
                return pressed != 0
            }
            MotionEvent.ACTION_UP -> {
                val level = levelAt(event.x, event.y)
                if (level != null && level.number == pressed) {
                    performClick()
                    onLevelTap?.onLevelTapped(level)
                }
                pressed = 0
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = 0
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private companion object {
        /** A tile's width; it's half as tall. */
        const val TILE_DP = 16f

        /** An island's radius on the ground, around a level and around the final level. */
        const val ISLAND_DP = 56f
        const val FINAL_ISLAND_DP = 74f

        /** How far noise pushes a shore in or out. */
        const val COAST_WOBBLE_DP = 10f

        /** How much darker than the page the water is, and the land. */
        const val WATER_SHADE = 0.14f
        const val LAND_SHADE = 0.55f

        const val SALT = 7

        /** A repeatable random number in 0..1 for a tile. */
        fun hash(i: Int, j: Int, salt: Int): Float {
            var h = i * 374761393 + j * 668265263 + salt * 1442695041
            h = (h xor (h ushr 13)) * 1274126177
            h = h xor (h ushr 16)
            return (h and 0xFFFF) / 65535f
        }

        /** Noise that changes smoothly from tile to tile, over cells of two tiles. */
        fun smooth(i: Int, j: Int): Float {
            val fi = i / 2f
            val fj = j / 2f
            val i0 = floor(fi).toInt()
            val j0 = floor(fj).toInt()
            var ti = fi - i0
            var tj = fj - j0
            ti = ti * ti * (3 - 2 * ti)
            tj = tj * tj * (3 - 2 * tj)
            val top = hash(i0, j0, SALT) * (1 - ti) + hash(i0 + 1, j0, SALT) * ti
            val bottom = hash(i0, j0 + 1, SALT) * (1 - ti) + hash(i0 + 1, j0 + 1, SALT) * ti
            return top * (1 - tj) + bottom * tj
        }

        /** [a] blended towards [b] by [t], channel by channel. */
        fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).roundToInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).roundToInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).roundToInt(),
        )
    }
}
