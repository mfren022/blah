package com.topplecorp.game.gfx

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.topplecorp.core.Cell
import com.topplecorp.core.Material
import kotlin.math.ceil
import kotlin.math.sin

/** Visual state of a piece. Animations mutate this independently from the simulation. */
class VisualPiece(
    val id: Int,
    val material: Material,
    var cells: List<Cell>,
    var hp: Int,
    val maxHp: Int,
) {
    /** Extra pixel offset upward (used while falling). */
    var lift = 0f
    var fallVelocity = 0f
    var shake = 0f
    var flash = 0f
    var hidden = false
    val variant get() = id
    val cellSet = HashSet<Cell>()
    init { rebuild() }

    fun rebuild() { cellSet.clear(); cellSet.addAll(cells) }

    fun has(x: Int, y: Int) = cellSet.contains(Cell(x, y))

    val minX get() = cells.minOf { it.x }
    val maxX get() = cells.maxOf { it.x }
    val minY get() = cells.minOf { it.y }
    val maxY get() = cells.maxOf { it.y }
}

/**
 * Draws a tower of pieces as extruded pixel blocks: front faces carry the material texture while
 * top and right faces are shaded to give an oblique 3D look.
 */
class TowerRenderer(val cell: Int) {
    val depth = (cell / 4).coerceAtLeast(3)
    val textures = Textures(cell)
    private val path = Path()
    private val paint = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }

    /** Screen x of column [x]. */
    fun sx(originX: Float, x: Int) = originX + x * cell

    /** Screen y of the top of row [y], given the screen y of the ground line. */
    fun sy(groundY: Float, y: Int) = groundY - (y + 1) * cell

    private fun quad(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, color: Int) {
        path.reset()
        path.moveTo(x0, y0); path.lineTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.close()
        paint.color = color
        c.drawPath(path, paint)
    }

    private fun offset(p: VisualPiece, time: Float): Pair<Float, Float> {
        val sh = if (p.shake > 0f) sin(time * 90f) * p.shake * 2f else 0f
        return sh to -p.lift
    }

    fun draw(c: Canvas, pieces: Collection<VisualPiece>, originX: Float, groundY: Float, time: Float) {
        // Pass 1: depth faces (behind the front plane).
        for (p in pieces) {
            if (p.hidden) continue
            val base = Textures.shadeVariant(p.material, p.variant)
            val top = Pal.tint(base, 0.28f)
            val side = Pal.shade(base, 0.62f)
            val (ox, oy) = offset(p, time)
            for (cc in p.cells) {
                val x = sx(originX, cc.x) + ox
                val y = sy(groundY, cc.y) + oy
                val d = depth.toFloat()
                val cs = cell.toFloat()
                if (!p.has(cc.x, cc.y + 1)) {
                    quad(c, x, y, x + cs, y, x + cs + d, y - d, x + d, y - d, if (p.material == Material.GLASS) Pal.alpha(top, 0.7f) else top)
                    quad(c, x + d, y - d, x + cs + d, y - d, x + cs + d, y - d + 1, x + d, y - d + 1, Pal.tint(top, 0.3f))
                }
                if (!p.has(cc.x + 1, cc.y)) {
                    quad(c, x + cs, y, x + cs + d, y - d, x + cs + d, y + cs - d, x + cs, y + cs, if (p.material == Material.GLASS) Pal.alpha(side, 0.7f) else side)
                }
            }
        }
        // Pass 2: front faces.
        for (p in pieces) {
            if (p.hidden) continue
            drawFront(c, p, originX, groundY, time)
        }
    }

    private fun drawFront(c: Canvas, p: VisualPiece, originX: Float, groundY: Float, time: Float) {
        val (ox, oy) = offset(p, time)
        val tex = textures.get(p.material, p.variant)
        val base = Textures.shadeVariant(p.material, p.variant)
        val edge = Pal.shade(base, 0.38f)
        val hi = Pal.tint(base, 0.45f)
        val cs = cell.toFloat()
        val stage = if (p.hp < p.maxHp && p.maxHp > 0) ceil((1f - p.hp.toFloat() / p.maxHp) * 3f).toInt().coerceIn(1, 3) else 0
        for (cc in p.cells) {
            val x = (sx(originX, cc.x) + ox).toInt().toFloat()
            val y = (sy(groundY, cc.y) + oy).toInt().toFloat()
            Draw.bitmap(c, tex, x, y)
            // Outline where the piece ends, light bevel on top/left.
            if (!p.has(cc.x, cc.y + 1)) { Draw.rect(c, x, y, cs, 1f, edge); Draw.rect(c, x, y + 1, cs, 1f, hi) }
            if (!p.has(cc.x, cc.y - 1)) Draw.rect(c, x, y + cs - 1, cs, 1f, edge)
            if (!p.has(cc.x - 1, cc.y)) { Draw.rect(c, x, y, 1f, cs, edge); Draw.rect(c, x + 1, y + 1, 1f, cs - 2, hi) }
            if (!p.has(cc.x + 1, cc.y)) Draw.rect(c, x + cs - 1, y, 1f, cs, edge)
            if (stage > 0) Draw.bitmap(c, textures.cracks(stage, cc.x * 7 + cc.y * 13 + p.id), x, y)
            if (p.flash > 0f) Draw.rect(c, x, y, cs, cs, Pal.alpha(Pal.WHITE, p.flash * 0.8f))
        }
        // Special icon in the middle of the piece.
        Sprites.specials[p.material]?.let { icon ->
            val cx = (p.minX + p.maxX + 1) / 2f
            val cy = (p.minY + p.maxY + 1) / 2f
            val sc = if (cs >= 20 && (p.maxX > p.minX || p.maxY > p.minY)) 2f else 1f
            val bob = if (p.material == Material.GOLD) (sin(time * 4f) * 1f) else 0f
            val ix = originX + cx * cs - icon.width * sc / 2f + ox
            val iy = groundY - cy * cs - icon.height * sc / 2f + oy + bob
            Draw.bitmap(c, icon, ix.toInt().toFloat(), iy.toInt().toFloat(), sc)
        }
    }

    /** Soft shadow the tower casts on the street (to the right, matching the light direction). */
    fun drawShadow(c: Canvas, pieces: Collection<VisualPiece>, originX: Float, groundY: Float) {
        val cols = HashSet<Int>()
        pieces.forEach { p -> if (!p.hidden) p.cells.forEach { if (it.y <= 1) cols += it.x } }
        for (x in cols) {
            val sxv = sx(originX, x) + depth
            Draw.rect(c, sxv, groundY, cell.toFloat(), 4f, Pal.alpha(Pal.INK, 0.22f))
        }
    }
}
