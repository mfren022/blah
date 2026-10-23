package com.ultimatesudoku.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A card growing into a whole screen, drawn over the screen it starts from. A sheet in the background
 * colour grows from the card's bounds to fill the overlay while the card's contents fade from it, and
 * the card's board grows from its preview to the spot where the next screen draws its own board. The
 * last frame matches the next screen's first, so that screen can open with no window animation.
 */
class GrowOverlay(context: Context) : ViewGroup(context) {

    /** The board in flight. Show the same game on it as on the preview. */
    val board = BoardView(context).apply { isEnabled = false }

    /** True while growing; false once grown or while shrinking back. */
    var isGrowing = false
        private set

    private val palette = Palette(context)
    private val sheetFrom = Rect()
    private val boardFrom = Rect()
    private val boardTo = Rect()
    private val fill = Paint()
    private val edge = Paint().apply { style = Paint.Style.STROKE }
    private val fade = Paint(Paint.FILTER_BITMAP_FLAG)
    private var cardImage: Bitmap? = null
    private var animator: ValueAnimator? = null

    /** Linear progress from the card (0) to the full screen (1). */
    private var fraction = 0f

    init {
        setWillNotDraw(false)
        // Swallows touches, so nothing underneath can be tapped mid-flight.
        isClickable = true
        // A moment of motion, not something to read out.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(board)
    }

    /**
     * Grows from [card] to the whole overlay, and its board from [preview] to [target] (in this
     * overlay's coordinates), then calls [onGrown]. Add the overlay to the view holding the card
     * first, and hide [preview]: the board in flight stands in for it.
     */
    fun grow(card: View, preview: View, target: Rect, onGrown: () -> Unit) {
        val parent = parent as View
        sheetFrom.set(boundsWithin(parent, card))
        boardFrom.set(boundsWithin(parent, preview))
        boardTo.set(target)
        // The card's text and buttons, to fade out over the sheet as it starts to grow.
        cardImage = Bitmap.createBitmap(card.width, card.height, Bitmap.Config.ARGB_8888).also { card.draw(Canvas(it)) }
        isGrowing = true
        animateTo(1f, GROW_MS) {
            isGrowing = false
            onGrown()
        }
    }

    /** Shrinks back into the card from wherever the growth has got to, then calls [onShrunk]. */
    fun shrink(onShrunk: () -> Unit) {
        isGrowing = false
        animateTo(0f, (GROW_MS * fraction).toLong(), onShrunk)
    }

    /** Stops where it is, without calling back. */
    fun stop() {
        cancelAnimator()
        isGrowing = false
    }

    private fun cancelAnimator() {
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
    }

    private fun animateTo(to: Float, duration: Long, onEnd: () -> Unit) {
        cancelAnimator()
        animator = ValueAnimator.ofFloat(fraction, to).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            addUpdateListener {
                fraction = it.animatedValue as Float
                placeBoard()
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animator = null
                    onEnd()
                }
            })
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) = placeBoard()

    /** Lays the board out at its size for this moment, so it redraws crisply rather than scaling. */
    private fun placeBoard() {
        val e = EASE.getInterpolation(fraction)
        val size = lerp(boardFrom.width(), boardTo.width(), e)
        val left = lerp(boardFrom.left, boardTo.left, e)
        val top = lerp(boardFrom.top, boardTo.top, e)
        val spec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        board.measure(spec, spec)
        board.layout(left, top, left + size, top + size)
    }

    override fun onDraw(canvas: Canvas) {
        val e = EASE.getInterpolation(fraction)
        // Dim the screen being left a little, so the sheet stands out as it grows over it.
        canvas.drawColor(Color.argb((SCRIM * e * 255).roundToInt(), 0, 0, 0))
        val left = lerp(sheetFrom.left, 0, e).toFloat()
        val top = lerp(sheetFrom.top, 0, e).toFloat()
        val right = lerp(sheetFrom.right, width, e).toFloat()
        val bottom = lerp(sheetFrom.bottom, height, e).toFloat()
        fill.color = palette.bg
        canvas.drawRect(left, top, right, bottom, fill)
        val image = cardImage
        val shown = 1f - min(1f, fraction / FADE_OUT)
        if (image != null && shown > 0f) {
            fade.alpha = (255 * shown).roundToInt()
            canvas.drawBitmap(image, sheetFrom.left.toFloat(), sheetFrom.top.toFloat(), fade)
        }
        // The card's 1dp outline, fading as it nears the screen's edges.
        val stroke = max(1f, dp(1f))
        edge.strokeWidth = stroke
        edge.color = palette.line
        edge.alpha = (255 * (1f - e)).roundToInt()
        canvas.drawRect(left + stroke / 2, top + stroke / 2, right - stroke / 2, bottom - stroke / 2, edge)
    }

    private fun lerp(from: Int, to: Int, f: Float): Int = (from + (to - from) * f).roundToInt()

    private companion object {
        const val GROW_MS = 420L

        /** Share of the growth over which the card's contents fade away. */
        const val FADE_OUT = 0.25f

        /** Darkness of the dimmed screen once fully covered. */
        const val SCRIM = 0.12f

        /** Material's standard easing: quick to leave, gentle to land. */
        val EASE = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    }
}
