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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A chapter's levels as a node graph, like a metro map: square stops in rows and lanes, joined by
 * right-angled paths. The entrance is at the top and the chapter's final level, drawn larger with a
 * star, at the bottom. Finished levels are filled, and the paths on from them drawn bright; levels
 * ready to play are filled blue and ringed; the rest are drawn locked.
 *
 * In the [Style.ISLANDS] style the map lies over a landscape drawn as one isometric grid of diamond
 * tiles in just two shades: land a little darker than the page, and water much darker. A river winds
 * down it in wide bends around the levels in the middle lane, one side then the other, and swells
 * into a lake with an islet wherever it runs on down the middle between them; the rows are further
 * apart where it crosses from lane to lane. Every level stands on land. The grid fades into the page
 * above and below the levels.
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
     * rows are further apart, by varied amounts, and much further where the river crosses from lane to
     * lane between them: where a level in the middle lane is above or below rows with the middle free.
     */
    private fun lines(map: LevelMap): FloatArray {
        val lines = FloatArray(map.rows)
        var y = paddingTop + (if (style == Style.ISLANDS) margin else 0f) + ring + finalStop / 2
        for (row in lines.indices) {
            lines[row] = y
            y += if (style == Style.METRO) {
                rowGap
            } else if (middle(map, row) != middle(map, row + 1)) {
                rowGap * (1.5625f + 0.375f * hash(row, 21, SALT))
            } else {
                rowGap * (1 + 0.15f * hash(row, 21, SALT))
            }
        }
        return lines
    }

    /** Whether there's a level in the middle lane in [row]. */
    private fun middle(map: LevelMap, row: Int) = map.levels.any { it.row == row && it.lane == 1 }

    /** Levels sit on their row's line, in lanes across the view. */
    private fun layout(map: LevelMap): Layout {
        layout?.let { return it }
        val lines = lines(map)
        val inner = width - paddingLeft - paddingRight
        val spots = map.levels.associate { it.number to PointF(paddingLeft + inner / 2f + (it.lane - 1) * spread(), lines[it.row]) }
        return Layout(lines, spots).also { layout = it }
    }

    /** How far apart the lanes are. */
    private fun spread() = min((width - paddingLeft - paddingRight) * 0.3f, dp(116f))

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
     * The land is all one but for the river. Its course comes in from the side of the view beside the
     * first level in the middle lane, bends wide around that level and every other one in the lane, one
     * side then the other, and goes out at the side beside the last; or, where the sides of the view are
     * far off, in at the top and out at the bottom. Tiles near the course are water, and more of them
     * where it runs on down the middle between bends, where it swells into a lake, lopsided, with an
     * islet. The shores are smoothed, and last, the ground under and around every level is land.
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

        // The river's course, through points around each level in the middle lane in turn.
        val mids = map.levels.filter { it.lane == 1 }.sortedBy { it.row }
        val bend = 1.4f * spread()
        val firstSide = if (hash(0, 64, SALT) < 0.5f) 1 else -1
        val beyond = width / 2f + 2 * tw
        val far = beyond > 2.2f * bend
        val points = ArrayList<PointF>()
        for ((q, level) in mids.withIndex()) {
            val spot = layout.spots.getValue(level.number)
            val up = if (level.row > 0) layout.lines[level.row] - layout.lines[level.row - 1] else 0f
            val down = if (level.row + 1 < map.rows) layout.lines[level.row + 1] - layout.lines[level.row] else 0f
            val side = if (q % 2 == 0) firstSide else -firstSide
            if (q == 0) points += if (far) PointF(spot.x + side * 2.2f * bend, -3 * th) else PointF(cx + side * beyond, spot.y)
            for (degrees in (if (q == 0) 0 else -90)..(if (q == mids.lastIndex) 0 else 90) step 30) {
                val angle = degrees * PI.toFloat() / 180
                val reach = (if (degrees < 0) up else down) * 0.55f
                points += PointF(spot.x + side * bend * cos(angle), spot.y + reach * sin(angle))
            }
            if (q == mids.lastIndex) points += if (far) PointF(spot.x + side * 2.2f * bend, height + 3 * th) else PointF(cx + side * beyond, spot.y)
        }
        // Smoothed into a flowing line, a dozen steps from each point to the next.
        val course = ArrayList<PointF>()
        for (s in 0 until points.size - 1) {
            for (t in 0 until 12) {
                course += catmullRom(points[max(0, s - 1)], points[s], points[s + 1], points[min(points.size - 1, s + 2)], t / 12f)
            }
        }
        points.lastOrNull()?.let { course += it }

        // Between bends, where the river runs on down the middle lane, it swells into a lake, further
        // to one side than the other, and the other side the next time.
        val firstLean = if (hash(0, 65, SALT) < 0.5f) 1 else -1
        val lakes = (0 until mids.size - 1).map { q ->
            Lake(layout.spots.getValue(mids[q].number).y, layout.spots.getValue(mids[q + 1].number).y, if (q % 2 == 0) firstLean else -firstLean)
        }
        val water = BooleanArray(n)
        val river = dp(RIVER_DP)
        val swell = dp(LAKE_DP)
        if (course.isNotEmpty()) {
            for (k in 0 until n) {
                var best = Float.MAX_VALUE
                var along = 0
                for ((s, p) in course.withIndex()) {
                    val d = ground(k, p.x, p.y)
                    if (d < best) {
                        best = d
                        along = s
                    }
                }
                val p = course[along]
                var reach = river * (0.9f + 0.25f * sin(along.toFloat() / course.size * 8 * PI.toFloat() + SALT))
                for (lake in lakes) {
                    if (p.y <= lake.top || p.y >= lake.bottom) continue
                    val t = (p.y - lake.top) / (lake.bottom - lake.top)
                    val toward = if (xs[k] > p.x) 1 else -1
                    reach += sin(PI.toFloat() * t).pow(0.8f) * swell * (1 + LEAN * (if (toward == lake.lean) 1 else -1))
                }
                if (best < reach) water[k] = true
            }
        }
        // An islet in each lake, clear of the levels.
        for ((q, lake) in lakes.withIndex()) {
            val middle = (lake.top + lake.bottom) / 2
            val islet = (0 until n)
                .filter { water[it] && abs(ys[it] - middle) < (lake.bottom - lake.top) * 0.22f && clear[it] > rim + 1.2f * tw }
                .minByOrNull { hash(iis[it], js[it], 70 + q) } ?: continue
            for (k in 0 until n) if (ground(k, xs[islet], ys[islet]) < 1.2f * tw) water[k] = false
        }

        // Shores smoothed: a tile with most of its eight neighbours the other way round goes with them.
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

        // Every level stands on land.
        for (k in 0 until n) if (clear[k] < rim) water[k] = false

        // Faded in from the top and out to the bottom, gone a tile short of each edge.
        val alpha = IntArray(n) { k ->
            var f = ((min(ys[k], height - ys[k]) - th) / fade).coerceIn(0f, 1f)
            f = f * f * (3 - 2 * f)
            (f * 255).roundToInt()
        }
        return Landscape(xs.toFloatArray(), ys.toFloatArray(), water, alpha)
    }

    /** A lake in the river's course, from [top] to [bottom], swelling further towards [lean]: 1 right, -1 left. */
    private class Lake(val top: Float, val bottom: Float, val lean: Int)

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
        const val TILE_DP = 28f

        /** How far the land around a level reaches past its stop, on the ground. */
        const val RIM_DP = 24f

        /** How far the river reaches either side of its course, on the ground, give or take a little. */
        const val RIVER_DP = 30f

        /** How much further a lake reaches at its widest, and how lopsided it is. */
        const val LAKE_DP = 80f
        const val LEAN = 0.55f

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

        /** The point [t] of the way from [p1] to [p2] on a Catmull-Rom spline through all four. */
        fun catmullRom(p0: PointF, p1: PointF, p2: PointF, p3: PointF, t: Float): PointF {
            fun at(a: Float, b: Float, c: Float, d: Float) =
                0.5f * (2 * b + (c - a) * t + (2 * a - 5 * b + 4 * c - d) * t * t + (3 * b - a - 3 * c + d) * t * t * t)
            return PointF(at(p0.x, p1.x, p2.x, p3.x), at(p0.y, p1.y, p2.y, p3.y))
        }

        /** [a] blended towards [b] by [t], channel by channel. */
        fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).roundToInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).roundToInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).roundToInt(),
        )
    }
}
