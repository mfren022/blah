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
import java.util.PriorityQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A chapter's levels as a node graph, like a metro map: square stops in rows and lanes, joined by
 * right-angled paths. The entrance is at the top and the chapter's final level, drawn larger with a
 * star, at the bottom. Finished levels are filled, and the paths on from them drawn bright; levels
 * ready to play are filled blue and ringed; the rest are drawn locked.
 *
 * In the [Style.ISLANDS] style the rows are spaced unevenly and the route sways from side to side,
 * and the map lies over a landscape drawn as one isometric grid of diamond tiles in just two shades:
 * land a little darker than the page, and water much darker. A wide river winds across it from one
 * side to the other, a big lake with islands lies low down and another high up, and a couple of ponds
 * are dotted about; every level stands on land, an island where it's in the water. The grid fades
 * into the page above and below the levels.
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
            layout = null
            landscape = null
            requestLayout()
            invalidate()
        }

    var style = Style.METRO
        set(value) {
            field = value
            layout = null
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

    /** Where the levels go, worked out once for the map, the style and the view's width. */
    private var layout: Layout? = null

    /** The landscape's tiles, worked out once for the map and the view's size. */
    private var landscape: Landscape? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var content = 0f
        map?.let { map ->
            // From the top of the first row's stops to the bottom of the final level's.
            content = lines(map).last() - paddingTop + finalStop / 2 + ring
            if (style == Style.ISLANDS) content += margin
        }
        val height = paddingTop + paddingBottom + content.roundToInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layout = null
        landscape = null
    }

    /** Where level [number] is drawn: its centre, in this view's pixels. */
    fun center(number: Int): PointF? {
        val map = map ?: return null
        return layout(map).spots[number]?.let { PointF(it.x, it.y) }
    }

    /** A point inside level [number]'s stop, near its top left corner and clear of its number. */
    fun fillPoint(number: Int): PointF? {
        val map = map ?: return null
        val level = map.levels.firstOrNull { it.number == number } ?: return null
        val spot = layout(map).spots.getValue(number)
        val inset = half(map, level) - dp(4f)
        return PointF(spot.x - inset, spot.y - inset)
    }

    /** Each row's line, top to bottom, and each level's centre by number. */
    private class Layout(val lines: FloatArray, val spots: Map<Int, PointF>)

    /**
     * The line each row of levels sits on, from the top of the view. In the [Style.ISLANDS] style the
     * rows are a varied distance apart.
     */
    private fun lines(map: LevelMap): FloatArray {
        val lines = FloatArray(map.rows)
        var y = paddingTop + (if (style == Style.ISLANDS) margin else 0f) + ring + finalStop / 2
        for (row in lines.indices) {
            lines[row] = y
            y += if (style == Style.ISLANDS) rowGap * (0.88f + 0.5f * hash(row, 21, SALT)) else rowGap
        }
        return lines
    }

    /**
     * Levels sit on their row's line, in lanes across the view. In the [Style.ISLANDS] style the route
     * sways from side to side down the rows, and each level is nudged off its place a little, though
     * one straight on from a level above it in its lane keeps to the same line down, and the entrance
     * and final level stay on their rows' lines.
     */
    private fun layout(map: LevelMap): Layout {
        layout?.let { return it }
        val lines = lines(map)
        val spots = HashMap<Int, PointF>()
        val inner = width - paddingLeft - paddingRight
        val spread = min(inner * 0.3f, dp(116f))
        val phase = hash(0, 22, SALT) * 6.28f
        // Row by row, so a level's neighbour above is placed before it.
        for (level in map.levels.sortedBy { it.row }) {
            val x = paddingLeft + inner / 2f + (level.lane - 1) * spread
            val y = lines[level.row]
            if (style == Style.METRO) {
                spots[level.number] = PointF(x, y)
                continue
            }
            val end = level == map.entrance || level == map.final
            val dy = if (end) 0f else (hash(level.number, 24, SALT) - 0.5f) * 2 * dp(NUDGE_Y_DP)
            val above = map.paths.firstOrNull { it.to == level.number && map.level(it.from).lane == level.lane }
            val across = if (above != null) {
                spots.getValue(above.from).x
            } else {
                val sway = dp(SWAY_DP) * sin(level.row * 1.3f + phase)
                val dx = if (end) 0f else (hash(level.number, 23, SALT) - 0.5f) * 2 * dp(NUDGE_X_DP)
                x + sway + dx
            }
            spots[level.number] = PointF(across, y + dy)
        }
        return Layout(lines, spots).also { layout = it }
    }

    private fun half(map: LevelMap, level: LevelMap.Level) = (if (level == map.final) finalStop else stop) / 2

    override fun onDraw(canvas: Canvas) {
        val map = map ?: return
        val layout = layout(map)
        if (style == Style.ISLANDS) drawLandscape(canvas, map, layout)
        // Paths run centre to centre under the stops: down, across halfway between the rows' lines,
        // down. Those on from a finished level are walked, and drawn bright.
        track.color = palette.ink
        for (path in map.paths) {
            val walked = path.from in finished
            track.strokeWidth = dp(if (walked) 3f else 2f)
            track.alpha = if (walked) 210 else 70
            val from = layout.spots.getValue(path.from)
            val to = layout.spots.getValue(path.to)
            val mid = (layout.lines[map.level(path.from).row] + layout.lines[map.level(path.to).row]) / 2
            route.reset()
            route.moveTo(from.x, from.y)
            route.lineTo(from.x, mid)
            route.lineTo(to.x, mid)
            route.lineTo(to.x, to.y)
            canvas.drawPath(route, track)
        }
        for (level in map.levels) drawStop(canvas, map, level, layout.spots.getValue(level.number))
    }

    private fun drawStop(canvas: Canvas, map: LevelMap, level: LevelMap.Level, spot: PointF) {
        val cx = spot.x
        val cy = spot.y
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

    /** The landscape's tiles: each one's centre, whether it's water, and how far it has faded. */
    private class Landscape(val xs: FloatArray, val ys: FloatArray, val water: BooleanArray, val alpha: IntArray)

    private fun drawLandscape(canvas: Canvas, map: LevelMap, layout: Layout) {
        val tiles = landscape ?: buildLandscape(map, layout).also { landscape = it }
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
        // The only two shades: land a little darker than the page, water much darker.
        val land = mix(palette.bg, Color.BLACK, LAND_SHADE)
        val water = mix(palette.bg, Color.BLACK, WATER_SHADE)
        for (k in tiles.xs.indices) {
            if (tiles.alpha[k] == 0) continue
            tilePaint.color = if (tiles.water[k]) water else land
            tilePaint.alpha = tiles.alpha[k]
            canvas.save()
            canvas.translate(tiles.xs[k], tiles.ys[k])
            canvas.drawPath(diamond, tilePaint)
            canvas.restore()
        }
    }

    /**
     * Tiles sit on an isometric lattice: tile (i, j) is drawn at the view's centre column plus (i - j)
     * half tile widths across, and (i + j) half tile heights down. Distances on the ground are measured
     * with screen heights doubled, so round things there are ellipses twice as wide as tall on screen.
     *
     * The land is all one to begin with. Lakes are laid on it, their shores wavering with the angle
     * around them and smoothed, and islets set in the big one; then a river is found across the view,
     * the cheapest way from one side to the other around the levels through ground made rough by
     * noise, so that it winds, and widened, and its banks smoothed; last, the ground under and around
     * every level is land.
     */
    private fun buildLandscape(map: LevelMap, layout: Layout): Landscape {
        val tw = dp(TILE_DP)
        val th = tw / 2
        val cx = width / 2f

        // Every lattice point whose tile shows in the view, or lies just past its sides.
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        val iis = ArrayList<Int>()
        val js = ArrayList<Int>()
        val index = HashMap<Long, Int>()
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
                index[key(i, j)] = xs.size
                xs += x
                ys += y
                iis += i
                js += j
            }
        }
        val n = xs.size
        fun neighbour(k: Int, d: Int): Int = index[key(iis[k] + AROUND_I[d], js[k] + AROUND_J[d])] ?: -1
        fun ground(k: Int, x: Float, y: Float) = hypot(xs[k] - x, (ys[k] - y) * 2)

        // How far each tile is from the nearest level's stop, on the ground.
        val clear = FloatArray(n) { k ->
            var best = Float.MAX_VALUE
            for (level in map.levels) {
                val spot = layout.spots.getValue(level.number)
                val h = half(map, level)
                val dx = max(0f, abs(xs[k] - spot.x) - h)
                val dy = max(0f, abs(ys[k] - spot.y) - h)
                best = min(best, hypot(dx, dy * 2))
            }
            best
        }
        val rim = dp(RIM_DP)

        // Lakes: a big one low down, another high up on the other side, and a couple of small ponds.
        val water = BooleanArray(n)
        val top = paddingTop + margin
        val span = height - paddingBottom - margin - top
        val unit = min(width.toFloat(), span * 0.75f)
        fun lake(lx: Float, ly: Float, radius: Float, salt: Int, lobes: Int) {
            for (k in 0 until n) {
                val angle = atan2((ys[k] - ly) * 2, xs[k] - lx) / (2 * PI.toFloat()) + 0.5f
                val r = radius * (0.7f + 0.6f * wave(angle * lobes, salt, lobes)) +
                    (smooth(iis[k], js[k], salt + 1, 3f) - 0.5f) * 0.3f * radius
                if (ground(k, lx, ly) < r) water[k] = true
            }
        }
        val bigX = 0.34f * width
        val bigY = top + 0.70f * span
        val bigR = 0.46f * unit
        lake(bigX, bigY, bigR, SALT * 13, 5)
        lake(0.80f * width, top + 0.18f * span, 0.30f * unit, SALT * 17, 5)
        for (p in 0 until PONDS) {
            val px = (0.1f + 0.8f * hash(p, 31, SALT)) * width
            val py = top + (0.15f + 0.7f * hash(p, 32, SALT)) * span
            lake(px, py, 0.09f * unit, SALT * 19 + p, 3)
        }

        // Shores smoothed: a tile with most of its eight neighbours the other way round goes with them.
        fun smoothShores() {
            val was = water.copyOf()
            for (k in 0 until n) {
                var wet = 0
                var around = 0
                for (d in 0 until 8) {
                    val m = neighbour(k, d)
                    if (m < 0) continue
                    around++
                    if (was[m]) wet++
                }
                if (wet * 2 > around + 2) water[k] = true else if (wet * 2 < around - 2) water[k] = false
            }
        }
        smoothShores()
        smoothShores()

        // Islets in the big lake, clear of its shores and of the levels.
        val islets = ArrayList<Int>()
        val sites = (0 until n)
            .filter { water[it] && ground(it, bigX, bigY) < bigR * 0.55f && clear[it] > rim + 1.5f * tw }
            .sortedBy { hash(iis[it], js[it], SALT * 23) }
        for (c in sites) {
            if (islets.size >= ISLETS) break
            if (islets.any { ground(c, xs[it], ys[it]) < 3 * tw }) continue
            islets += c
            val r = tw * (0.9f + 0.6f * hash(iis[c], js[c], 25))
            for (k in 0 until n) if (ground(k, xs[c], ys[c]) < r) water[k] = false
        }

        // The river: the cheapest way across, from a point on the left side to anywhere on the right.
        // Ground near a level costs far more, so it goes around them; noise makes it wind.
        val river = dp(RIVER_DP)
        val keepOff = rim + river * 0.8f
        val cost = FloatArray(n) { k ->
            var c = 1 + 5 * smooth(iis[k], js[k], SALT * 29, 5f)
            if (clear[k] < keepOff) c += 40 * (1 - clear[k] / keepOff)
            c
        }
        val startY = top + (0.15f + 0.7f * hash(1, 41, SALT)) * span
        val source = (0 until n).filter { xs[it] < 0 }.minBy { abs(ys[it] - startY) }
        val dist = FloatArray(n) { Float.MAX_VALUE }
        val previous = IntArray(n) { -1 }
        val queue = PriorityQueue<Pair<Float, Int>>(compareBy { it.first })
        dist[source] = 0f
        queue += 0f to source
        var mouth = -1
        while (queue.isNotEmpty()) {
            val (d, k) = queue.poll()!!
            if (d > dist[k]) continue
            if (xs[k] > width) {
                mouth = k
                break
            }
            for (e in 0 until 8) {
                val m = neighbour(k, e)
                if (m < 0) continue
                val next = d + ground(m, xs[k], ys[k]) / tw * cost[m]
                if (next < dist[m]) {
                    dist[m] = next
                    previous[m] = k
                    queue += next to m
                }
            }
        }
        val course = ArrayList<Int>()
        var k = mouth
        while (k >= 0) {
            course += k
            k = previous[k]
        }
        // Widened, more in some reaches than others.
        for (t in 0 until n) {
            var best = Float.MAX_VALUE
            var along = 0
            for ((s, c) in course.withIndex()) {
                val d = ground(t, xs[c], ys[c])
                if (d < best) {
                    best = d
                    along = s
                }
            }
            val reach = river * (0.75f + 0.5f * wave(along * 6f / course.size, SALT * 9))
            if (best < reach) water[t] = true
        }
        smoothShores()

        // Every level stands on land.
        for (t in 0 until n) if (clear[t] < rim) water[t] = false

        // Faded in from the top and out to the bottom, gone a tile short of each edge.
        val alpha = IntArray(n) { t ->
            var f = ((min(ys[t], height - ys[t]) - th) / fade).coerceIn(0f, 1f)
            f = f * f * (3 - 2 * f)
            (f * 255).roundToInt()
        }
        return Landscape(xs.toFloatArray(), ys.toFloatArray(), water, alpha)
    }

    private fun levelAt(x: Float, y: Float): LevelMap.Level? {
        val map = map ?: return null
        val layout = layout(map)
        val reach = dp(6f)
        return map.levels.firstOrNull {
            val spot = layout.spots.getValue(it.number)
            val h = half(map, it) + reach
            abs(x - spot.x) <= h && abs(y - spot.y) <= h
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
        const val TILE_DP = 24f

        /** In the islands style, how far the route sways from side to side, and each level is nudged. */
        const val SWAY_DP = 20f
        const val NUDGE_X_DP = 14f
        const val NUDGE_Y_DP = 9f

        /** How far the land around a level reaches past its stop, on the ground. */
        const val RIM_DP = 22f

        /** How far the river reaches either side of its course, on the ground, give or take a quarter. */
        const val RIVER_DP = 40f

        const val PONDS = 2
        const val ISLETS = 2

        /** How much darker than the page the land is, and the water. */
        const val LAND_SHADE = 0.14f
        const val WATER_SHADE = 0.55f

        const val SALT = 7

        /** The lattice steps to a tile's eight neighbours, round from the one down and to the right. */
        val AROUND_I = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
        val AROUND_J = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)

        fun key(i: Int, j: Int): Long = (i.toLong() shl 32) or (j.toLong() and 0xFFFFFFFFL)

        /** A repeatable random number in 0..1 for a tile. */
        fun hash(i: Int, j: Int, salt: Int): Float {
            var h = i * 374761393 + j * 668265263 + salt * 1442695041
            h = (h xor (h ushr 13)) * 1274126177
            h = h xor (h ushr 16)
            return (h and 0xFFFF) / 65535f
        }

        /** Noise that changes smoothly from tile to tile, over cells of [cell] tiles. */
        fun smooth(i: Int, j: Int, salt: Int, cell: Float): Float {
            val fi = i / cell
            val fj = j / cell
            val i0 = floor(fi).toInt()
            val j0 = floor(fj).toInt()
            var ti = fi - i0
            var tj = fj - j0
            ti = ti * ti * (3 - 2 * ti)
            tj = tj * tj * (3 - 2 * tj)
            val top = hash(i0, j0, salt) * (1 - ti) + hash(i0 + 1, j0, salt) * ti
            val bottom = hash(i0, j0 + 1, salt) * (1 - ti) + hash(i0 + 1, j0 + 1, salt) * ti
            return top * (1 - tj) + bottom * tj
        }

        /** Noise in one dimension, smooth along [t], and repeating every [period] if there is one. */
        fun wave(t: Float, salt: Int, period: Int = 0): Float {
            val i0 = floor(t).toInt()
            var f = t - i0
            f = f * f * (3 - 2 * f)
            fun at(i: Int) = hash(if (period > 0) Math.floorMod(i, period) else i, 0, salt)
            return at(i0) * (1 - f) + at(i0 + 1) * f
        }

        /** [a] blended towards [b] by [t], channel by channel. */
        fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).roundToInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).roundToInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).roundToInt(),
        )
    }
}
