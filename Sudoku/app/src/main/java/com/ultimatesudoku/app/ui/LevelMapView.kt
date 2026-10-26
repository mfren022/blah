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
 * A chapter's levels as a node graph, the entrance at the top and the chapter's final level, larger and
 * marked with a star, at the bottom. Finished levels are white and the paths on from them drawn bright;
 * levels ready to play are blue and ringed; the rest are drawn locked. In one of two [Style]s:
 *
 * - [Style.METRO], like a metro map: square stops in rows and lanes, joined by right-angled paths.
 * - [Style.ISLANDS], a pond seen in isometric view, drawn entirely as one grid of shaded diamond
 *   tiles: each level is a block of tiles on a dark island, lit from the top left, in lighter water
 *   with shallows around the shores, and each path is a ford of paler tiles from island to island.
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
            terrain = null
            requestLayout()
            invalidate()
        }

    var style = Style.METRO
        set(value) {
            field = value
            terrain = null
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

    /** How far an island level's platform stands above the tiles around it. */
    private val rise = dp(3f)

    /** The level under the finger, by number, or 0. */
    private var pressed = 0

    /** The island grid, worked out once for the map and the view's size. */
    private var terrain: Terrain? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rows = map?.rows ?: 0
        val content = when {
            rows == 0 -> 0f
            style == Style.ISLANDS -> {
                val th = tileWidth(width) / 2
                2 * MARGIN_HALF_TILES * th + (rows - 1) * ROW_STEP * th / 2
            }
            else -> 2 * ring + finalStop + (rows - 1) * rowGap
        }
        val height = paddingTop + paddingBottom + content.roundToInt()
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        terrain = null
    }

    /** Where level [number] is drawn: its centre, in this view's pixels. */
    fun center(number: Int): PointF? {
        val level = map?.levels?.firstOrNull { it.number == number } ?: return null
        return PointF(x(level), y(level))
    }

    /** A point inside level [number]'s stop or block, clear of its number. */
    fun fillPoint(number: Int): PointF? {
        val map = map ?: return null
        val level = map.levels.firstOrNull { it.number == number } ?: return null
        return if (style == Style.ISLANDS) {
            // On the platform's top, where the tile left of its centre would be.
            PointF(x(level) - tileWidth(width), y(level) - rise)
        } else {
            val inset = half(map, level) - dp(4f)
            PointF(x(level) - inset, y(level) - inset)
        }
    }

    private fun x(level: LevelMap.Level): Float = if (style == Style.ISLANDS) {
        width / 2f + (level.lane - 1) * LANE_STEP * tileWidth(width) / 2
    } else {
        val inner = width - paddingLeft - paddingRight
        val spread = min(inner * 0.3f, dp(116f))
        paddingLeft + inner / 2f + (level.lane - 1) * spread
    }

    private fun y(level: LevelMap.Level): Float = if (style == Style.ISLANDS) {
        val th = tileWidth(width) / 2
        paddingTop + MARGIN_HALF_TILES * th + level.row * ROW_STEP * th / 2
    } else {
        paddingTop + ring + finalStop / 2 + level.row * rowGap
    }

    private fun half(map: LevelMap, level: LevelMap.Level) = (if (level == map.final) finalStop else stop) / 2

    /** The width of an island tile, about an eighteenth of the view's. */
    private fun tileWidth(width: Int) = (width / 18f).coerceIn(dp(16f), dp(26f))

    override fun onDraw(canvas: Canvas) {
        val map = map ?: return
        if (style == Style.ISLANDS) drawIslands(canvas, map) else drawMetro(canvas, map)
    }

    // ---- Metro --------------------------------------------------------------------------------

    private fun drawMetro(canvas: Canvas, map: LevelMap) {
        // Paths run centre to centre under the stops: down, across halfway between the rows, down.
        // Those on from a finished level are walked, and drawn bright.
        track.color = palette.ink
        for (path in map.paths) {
            val walked = path.from in finished
            track.strokeWidth = dp(if (walked) 3f else 2f)
            track.alpha = if (walked) 210 else 70
            val from = map.level(path.from)
            val to = map.level(path.to)
            val mid = (y(from) + y(to)) / 2
            route.reset()
            route.moveTo(x(from), y(from))
            route.lineTo(x(from), mid)
            route.lineTo(x(to), mid)
            route.lineTo(x(to), y(to))
            canvas.drawPath(route, track)
        }
        for (level in map.levels) drawStop(canvas, map, level)
    }

    private fun drawStop(canvas: Canvas, map: LevelMap, level: LevelMap.Level) {
        val cx = x(level)
        val cy = y(level)
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
        drawLabel(canvas, map, level, cx, cy, h * 0.9f, ink)
    }

    private fun drawLabel(canvas: Canvas, map: LevelMap, level: LevelMap.Level, cx: Float, cy: Float, starSize: Float, ink: Int) {
        if (level == map.final) {
            val size = starSize.roundToInt()
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

    // ---- Islands ------------------------------------------------------------------------------

    /**
     * The island grid. Tiles sit on an isometric lattice: tile (i, j) is drawn at the view's centre
     * column plus (i - j) half tile widths across and (i + j) half tile heights down from the entrance
     * row. Distances on the ground are measured in i and j, so islands are round on the ground and
     * ellipses on screen.
     */
    private class Terrain(
        val tileWidth: Float,
        val xs: FloatArray,
        val ys: FloatArray,
        /** Water or land, shaded, before levels and paths. */
        val base: IntArray,
        val land: BooleanArray,
        val fade: FloatArray,
        /** For a tile in a level's block, the level's number; else 0. */
        val block: IntArray,
        /** For a tile in the ring around a block, the level's number; else 0. */
        val halo: IntArray,
        /** For a tile on a path, 1 + the path's index in the map; else 0. */
        val path: IntArray,
    )

    private fun drawIslands(canvas: Canvas, map: LevelMap) {
        val t = terrain ?: buildTerrain(map).also { terrain = it }
        val tw = t.tileWidth
        val th = tw / 2
        // A gap between neighbours, so the tiles read as a grid.
        val gap = max(1f, dp(1.2f))
        diamond.reset()
        diamond.moveTo(0f, -(th / 2 - gap * 0.55f))
        diamond.lineTo(tw / 2 - gap * 1.1f, 0f)
        diamond.lineTo(0f, th / 2 - gap * 0.55f)
        diamond.lineTo(-(tw / 2 - gap * 1.1f), 0f)
        diamond.close()

        val bg = palette.bg
        for (k in t.xs.indices) {
            // Each level's block is drawn whole, below.
            if (t.block[k] != 0) continue
            val base = t.base[k]
            val color = when {
                t.halo[k] != 0 && t.halo[k] in open -> mix(base, ready, 0.6f)
                t.path[k] != 0 -> {
                    // Fords across the water, trails over land: bright once walked.
                    val walked = map.paths[t.path[k] - 1].from in finished
                    if (t.land[k]) mix(base, Color.WHITE, if (walked) 0.40f else 0.14f)
                    else mix(bg, Color.WHITE, if (walked) 0.55f else 0.26f)
                }
                else -> base
            }
            tilePaint.color = color
            tilePaint.alpha = (t.fade[k] * 255).roundToInt()
            canvas.save()
            canvas.translate(t.xs[k], t.ys[k])
            canvas.drawPath(diamond, tilePaint)
            canvas.restore()
        }
        for (level in map.levels) {
            drawBlock(canvas, level, if (level == map.final) 5 else 3, tw, gap)
            val ink = when (level.number) {
                in finished -> palette.onAccent
                in open -> palette.ink
                else -> palette.inkMuted
            }
            drawLabel(canvas, map, level, x(level), y(level) - rise, th * 2.2f, ink)
        }
    }

    /**
     * A level's square of [size] by [size] tiles, raised as a platform: its tiles in the level's colour,
     * the joins between them only a shade darker so its number reads clearly, and its two near sides in
     * shadow.
     */
    private fun drawBlock(canvas: Canvas, level: LevelMap.Level, size: Int, tw: Float, gap: Float) {
        val pressedHere = pressed == level.number
        val locked = mix(landLit(palette.bg), Color.WHITE, 0.12f)
        val top = when (level.number) {
            in finished -> if (pressedHere) palette.accentPressed else palette.accent
            in open -> if (pressedHere) readyPressed else ready
            else -> if (pressedHere) mix(locked, Color.WHITE, 0.12f) else locked
        }
        val cx = x(level)
        val cy = y(level)
        val hw = size * tw / 2 - gap * 1.1f
        val hh = size * tw / 4 - gap * 0.55f
        // The sides first, then the top over them, lifted by the rise.
        tilePaint.alpha = 255
        tilePaint.color = mix(top, Color.BLACK, 0.35f)
        route.reset()
        route.moveTo(cx - hw, cy - rise)
        route.lineTo(cx, cy + hh - rise)
        route.lineTo(cx, cy + hh)
        route.lineTo(cx - hw, cy)
        route.close()
        canvas.drawPath(route, tilePaint)
        tilePaint.color = mix(top, Color.BLACK, 0.5f)
        route.reset()
        route.moveTo(cx, cy + hh - rise)
        route.lineTo(cx + hw, cy - rise)
        route.lineTo(cx + hw, cy)
        route.lineTo(cx, cy + hh)
        route.close()
        canvas.drawPath(route, tilePaint)
        tilePaint.color = mix(top, Color.BLACK, 0.14f)
        route.reset()
        route.moveTo(cx, cy - hh - rise)
        route.lineTo(cx + hw, cy - rise)
        route.lineTo(cx, cy + hh - rise)
        route.lineTo(cx - hw, cy - rise)
        route.close()
        canvas.drawPath(route, tilePaint)
        tilePaint.color = top
        val r = size / 2
        for (di in -r..r) {
            for (dj in -r..r) {
                canvas.save()
                canvas.translate(cx + (di - dj) * tw / 2, cy - rise + (di + dj) * tw / 4)
                canvas.drawPath(diamond, tilePaint)
                canvas.restore()
            }
        }
    }

    private fun buildTerrain(map: LevelMap): Terrain {
        val tw = tileWidth(width)
        val th = tw / 2
        val cx = width / 2f
        val top = paddingTop + MARGIN_HALF_TILES * th

        // Every lattice point whose tile shows in the view.
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        val iis = ArrayList<Int>()
        val js = ArrayList<Int>()
        val index = HashMap<Long, Int>()
        val aMax = ceil(width / (tw / 2)).toInt() + 2
        val bMin = floor((-th - top) / (th / 2)).toInt() - 1
        val bMax = ceil((height + th - top) / (th / 2)).toInt() + 1
        for (b in bMin..bMax) {
            for (a in -aMax..aMax) {
                if ((a + b) and 1 != 0) continue
                val x = cx + a * tw / 2
                val y = top + b * th / 2
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

        // Each level's spot on the ground.
        val levelI = map.levels.map { (((it.lane - 1) * LANE_STEP) + it.row * ROW_STEP) / 2 }
        val levelJ = map.levels.map { (it.row * ROW_STEP - (it.lane - 1) * LANE_STEP) / 2 }

        // Land: a round island around each level, its shore roughened by noise. Its height falls
        // from 1 at the level to 0 at the shore.
        val elevation = FloatArray(n)
        val land = BooleanArray(n)
        for (k in 0 until n) {
            var best = -9f
            val wobble = (smooth(iis[k], js[k]) - 0.5f) * COAST_WOBBLE
            map.levels.forEachIndexed { l, level ->
                val radius = if (level == map.final) 4.4f else 3.0f + 0.6f * hash(level.number, 0, SALT + 1)
                val d = hypot((iis[k] - levelI[l]).toFloat(), (js[k] - levelJ[l]).toFloat())
                best = max(best, 1 - (d + wobble) / radius)
            }
            elevation[k] = best
            land[k] = best > 0
        }

        // Water's depth: how many steps it is from the nearest land.
        val depth = IntArray(n) { if (land[it]) 0 else Int.MAX_VALUE }
        val queue = ArrayDeque<Int>()
        for (k in 0 until n) if (land[k]) queue.add(k)
        while (queue.isNotEmpty()) {
            val k = queue.removeFirst()
            for ((di, dj) in NEIGHBOURS) {
                val m = index[key(iis[k] + di, js[k] + dj)] ?: continue
                if (depth[m] == Int.MAX_VALUE) {
                    depth[m] = depth[k] + 1
                    queue.add(m)
                }
            }
        }

        // Each level's block of tiles, larger for the final one, and the ring around it.
        val block = IntArray(n)
        val halo = IntArray(n)
        map.levels.forEachIndexed { l, level ->
            val r = if (level == map.final) 2 else 1
            for (di in -r - 1..r + 1) {
                for (dj in -r - 1..r + 1) {
                    val m = index[key(levelI[l] + di, levelJ[l] + dj)] ?: continue
                    if (max(abs(di), abs(dj)) <= r) block[m] = level.number
                    else if (halo[m] == 0) halo[m] = level.number
                }
            }
        }
        for (k in 0 until n) if (block[k] != 0) halo[k] = 0

        // Paths: the tiles along the straight line from one level's centre to the next.
        val path = IntArray(n)
        map.paths.forEachIndexed { p, link ->
            val from = map.level(link.from)
            val to = map.level(link.to)
            val ax = x(from)
            val ay = y(from)
            val bx = x(to)
            val by = y(to)
            for (k in 0 until n) {
                if (block[k] != 0 || path[k] != 0) continue
                if (segmentDistance(xs[k], ys[k], ax, ay, bx, by) < tw * PATH_WIDTH) path[k] = p + 1
            }
        }

        // Shading: land lit from the top left, darker on the far slopes; water paler in the shallows.
        val bg = palette.bg
        val landBase = mix(mix(bg, Color.BLACK, 0.62f), TEAL_DARK, 0.5f)
        val landLit = landLit(bg)
        val base = IntArray(n)
        val fade = FloatArray(n)
        for (k in 0 until n) {
            base[k] = if (land[k]) {
                val h = elevation[k]
                val toLight = index[key(iis[k] - 1, js[k])]?.let { elevation[it] } ?: h
                val light = h - toLight
                var c = mix(landBase, landLit, (0.10f + h * 0.25f + light * 0.9f).coerceIn(0f, 0.75f))
                if (light < 0) c = mix(c, Color.BLACK, min(0.55f, -light * 0.9f))
                c
            } else {
                val c = when (val d = depth[k]) {
                    1 -> mix(bg, Color.WHITE, 0.15f)
                    2 -> mix(bg, Color.WHITE, 0.11f)
                    else -> mix(bg, Color.WHITE, max(0f, 0.08f - 0.012f * (min(d, 12) - 3)))
                }
                val ripple = (hash(iis[k], js[k], 99) - 0.5f) * 0.08f
                if (ripple > 0) mix(c, Color.WHITE, ripple) else mix(c, Color.BLACK, -ripple)
            }
            // Fading out towards the edges, into the page.
            fade[k] = min(
                min(xs[k] / (2.5f * tw), (width - xs[k]) / (2.5f * tw)),
                min(ys[k] / (3 * th), (height - ys[k]) / (3 * th)),
            ).coerceIn(0f, 1f)
        }
        return Terrain(tw, xs.toFloatArray(), ys.toFloatArray(), base, land, fade, block, halo, path)
    }

    private fun landLit(bg: Int) = mix(mix(bg, Color.WHITE, 0.10f), TEAL_LIT, 0.5f)

    private fun levelAt(x: Float, y: Float): LevelMap.Level? {
        val map = map ?: return null
        val reach = dp(6f)
        return map.levels.firstOrNull {
            val dx = abs(x - x(it))
            val dy = abs(y - y(it))
            if (style == Style.ISLANDS) {
                // Within the level's diamond of tiles, give or take.
                val size = if (it == map.final) 5 else 3
                val tw = tileWidth(width)
                dx / (size * tw / 2 + reach) + dy / (size * tw / 4 + reach) <= 1
            } else {
                val h = half(map, it) + reach
                dx <= h && dy <= h
            }
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
        /** Half tile widths between lanes, and half tile heights between rows: even, so levels sit on tiles. */
        const val LANE_STEP = 8
        const val ROW_STEP = 14

        /** Half tile heights above the entrance and below the final level. */
        const val MARGIN_HALF_TILES = 6

        /** How far noise pushes a shore in or out, in tiles. */
        const val COAST_WOBBLE = 2.4f

        /** How close to a path's line a tile's centre must be to be part of it, in tile widths. */
        const val PATH_WIDTH = 0.42f

        const val SALT = 7
        val TEAL_DARK = Color.rgb(0x0B, 0x2A, 0x3C)
        val TEAL_LIT = Color.rgb(0x23, 0x55, 0x66)
        val NEIGHBOURS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

        fun key(i: Int, j: Int): Long = (i.toLong() shl 32) or (j.toLong() and 0xFFFFFFFFL)

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

        fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
            val vx = bx - ax
            val vy = by - ay
            val t = (((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy)).coerceIn(0f, 1f)
            return hypot(px - ax - t * vx, py - ay - t * vy)
        }

        /** [a] blended towards [b] by [t], channel by channel. */
        fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).roundToInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).roundToInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).roundToInt(),
        )
    }
}
