package com.ultimatesudoku.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.ultimatesudoku.core.Candidate
import com.ultimatesudoku.core.Game
import com.ultimatesudoku.core.Grid
import com.ultimatesudoku.core.Step
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The 9x9 grid. It draws whatever it is given (clues, entered digits, notes, mistakes) plus the
 * selection highlights and an optional hint overlay, and reports taps on cells.
 */
class BoardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    fun interface OnCellTapListener {
        fun onCellTapped(cell: Int)
    }

    var onCellTap: OnCellTapListener? = null

    private val palette = Palette(context)
    private val fonts = Fonts.get(context)

    private var givens = IntArray(81)
    private var values = IntArray(81)
    private var notes = IntArray(81)
    private var wrong = BooleanArray(81)
    private var conflicts = BooleanArray(81)

    var selected = -1
        set(value) {
            field = value
            describeSelection()
            invalidate()
        }
    var highlightPeers = true
    var highlightSame = true

    /** Hides the digits, e.g. while the game is paused. */
    var paused = false
        set(value) {
            field = value
            invalidate()
        }

    /** Short text shown over the board, e.g. "Generating". */
    var message: String? = null
        set(value) {
            field = value
            invalidate()
        }

    private var step: Step? = null
    private var stepCandidates: IntArray? = null
    private var stepCells = BooleanArray(81)
    private var stepUnits = BooleanArray(81)
    private var focusCell = -1
    private var focusDigit = 0
    private var focusIsMistake = false

    private val flashCells = BooleanArray(81)
    private var flashAlpha = 0f
    private var flashAnimator: ValueAnimator? = null

    private val fill = Paint()
    private val thin = Paint().apply { style = Paint.Style.STROKE }
    private val thick = Paint().apply { style = Paint.Style.STROKE }
    private val outline = Paint().apply { style = Paint.Style.STROKE }
    private val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val messagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()

    private val edges = FloatArray(10)
    private var lastTouched = -1

    fun setContent(givens: IntArray, values: IntArray, notes: IntArray, wrong: BooleanArray) {
        this.givens = givens
        this.values = values
        this.notes = notes
        this.wrong = wrong
        conflicts = Grid.conflicts(values)
        describeSelection()
        invalidate()
    }

    /** Shows [game]'s grid, marking wrong digits only when [checkMistakes] is on. */
    fun showGame(game: Game, checkMistakes: Boolean) {
        setContent(game.givens, game.values, game.notes, BooleanArray(81) { checkMistakes && game.isWrong(it) })
    }

    /** Tells screen readers which cell is selected and what it holds. */
    private fun describeSelection() {
        val cell = selected
        contentDescription = if (cell !in 0 until 81) {
            "Sudoku grid"
        } else {
            val content = when {
                values[cell] != 0 -> values[cell].toString()
                notes[cell] != 0 -> "notes ${Grid.digitList(notes[cell])}"
                else -> "empty"
            }
            "Row ${Grid.row(cell) + 1}, column ${Grid.col(cell) + 1}: $content"
        }
    }

    /** Overlays a solving step: its units, pattern cells, candidates and eliminations. */
    fun showStep(step: Step, candidates: IntArray) {
        clearHint()
        this.step = step
        stepCandidates = candidates
        for (c in step.cells) stepCells[c] = true
        for (u in step.units) for (c in Grid.UNITS[u]) stepUnits[c] = true
        step.placements.firstOrNull()?.let {
            focusCell = it.cell
            focusDigit = it.digit
        }
        invalidate()
    }

    /** Points at one cell: a wrong digit, or a digit about to be revealed. */
    fun showFocus(cell: Int, digit: Int, mistake: Boolean) {
        clearHint()
        focusCell = cell
        focusDigit = digit
        focusIsMistake = mistake
        invalidate()
    }

    fun clearHint() {
        step = null
        stepCandidates = null
        stepCells.fill(false)
        stepUnits.fill(false)
        focusCell = -1
        focusDigit = 0
        focusIsMistake = false
        invalidate()
    }

    /** Briefly lights up [cells], e.g. a row the player just completed. */
    fun flash(cells: Collection<Int>) {
        if (cells.isEmpty()) return
        flashCells.fill(false)
        for (c in cells) flashCells[c] = true
        flashAnimator?.cancel()
        flashAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 650
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                flashAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val size = when {
            MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED -> h
            MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED -> w
            else -> min(w, h)
        }
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val border = outerWidth()
        val span = min(w, h) - 2 * border
        for (i in 0..9) edges[i] = (border + span * i / 9f).roundToInt().toFloat()
    }

    private fun outerWidth() = dp(2.5f).roundToInt().toFloat()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val cell = cellAt(event.x, event.y)
                if (cell >= 0 && cell != lastTouched) {
                    lastTouched = cell
                    onCellTap?.onCellTapped(cell)
                }
            }
            MotionEvent.ACTION_UP -> {
                lastTouched = -1
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> lastTouched = -1
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun cellAt(x: Float, y: Float): Int {
        if (x < edges[0] || y < edges[0] || x >= edges[9] || y >= edges[9]) return -1
        var c = 0
        while (c < 8 && x >= edges[c + 1]) c++
        var r = 0
        while (r < 8 && y >= edges[r + 1]) r++
        return Grid.cell(r, c)
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        fill.color = palette.board
        canvas.drawRect(0f, 0f, size, size, fill)

        if (!paused) drawBackgrounds(canvas)
        drawLines(canvas, size)
        if (!paused) {
            drawFocusOutline(canvas)
            for (cell in 0 until 81) drawCell(canvas, cell)
        }
        message?.let { drawMessage(canvas, size, it) }
    }

    private fun cellRect(cell: Int): RectF {
        val r = Grid.row(cell)
        val c = Grid.col(cell)
        rect.set(edges[c], edges[r], edges[c + 1], edges[r + 1])
        return rect
    }

    private fun drawBackgrounds(canvas: Canvas) {
        val hinting = step != null || focusCell >= 0
        val sel = selected
        val selValue = if (sel >= 0) values[sel] else 0
        for (cell in 0 until 81) {
            val color = when {
                hinting && cell == focusCell && focusIsMistake -> palette.errorSoft
                hinting && stepCells[cell] -> palette.accentMid
                hinting && stepUnits[cell] -> palette.accentSoft
                hinting -> Color.TRANSPARENT
                cell == sel -> if (wrong[cell]) palette.error else palette.accent
                conflicts[cell] && givens[cell] == 0 -> palette.errorSoft
                highlightSame && selValue != 0 && values[cell] == selValue -> palette.accentMid
                highlightPeers && sel >= 0 && Grid.sees(cell, sel) -> palette.accentSoft
                else -> Color.TRANSPARENT
            }
            if (color != Color.TRANSPARENT) {
                fill.color = color
                canvas.drawRect(cellRect(cell), fill)
            }
            if (flashAlpha > 0f && flashCells[cell]) {
                fill.color = palette.accentMid
                fill.alpha = (flashAlpha * 255).toInt()
                canvas.drawRect(cellRect(cell), fill)
                fill.alpha = 255
            }
        }
    }

    private fun drawLines(canvas: Canvas, size: Float) {
        thin.color = palette.line
        thin.strokeWidth = maxOf(1f, dp(1f))
        thick.color = palette.lineStrong
        thick.strokeWidth = dp(2f).roundToInt().toFloat()
        for (i in 1..8) {
            if (i % 3 == 0) continue
            canvas.drawLine(edges[i], edges[0], edges[i], edges[9], thin)
            canvas.drawLine(edges[0], edges[i], edges[9], edges[i], thin)
        }
        for (i in intArrayOf(3, 6)) {
            canvas.drawLine(edges[i], edges[0], edges[i], edges[9], thick)
            canvas.drawLine(edges[0], edges[i], edges[9], edges[i], thick)
        }
        val border = outerWidth()
        outline.color = palette.lineStrong
        outline.strokeWidth = border
        canvas.drawRect(border / 2, border / 2, size - border / 2, size - border / 2, outline)
    }

    private fun drawFocusOutline(canvas: Canvas) {
        val cell = focusCell
        if (cell < 0) return
        val r = cellRect(cell)
        val w = dp(2.5f)
        outline.color = if (focusIsMistake) palette.error else palette.accent
        outline.strokeWidth = w
        canvas.drawRect(r.left + w / 2, r.top + w / 2, r.right - w / 2, r.bottom - w / 2, outline)
    }

    private fun drawCell(canvas: Canvas, cell: Int) {
        val r = cellRect(cell)
        val cx = r.centerX()
        val cy = r.centerY()
        val cellSize = r.width()
        val isSelected = cell == selected && step == null && focusCell < 0
        val value = values[cell]

        if (value != 0) {
            digitPaint.textSize = cellSize * 0.62f
            digitPaint.typeface = if (givens[cell] != 0) fonts.medium else fonts.regular
            digitPaint.color = when {
                isSelected -> palette.onAccent
                wrong[cell] -> palette.error
                givens[cell] != 0 -> palette.ink
                else -> palette.userDigit
            }
            drawCentered(canvas, value.toString(), cx, cy, digitPaint)
            return
        }

        if (cell == focusCell && focusDigit != 0 && !focusIsMistake) {
            // The digit a hint is about to place, shown as a ghost.
            digitPaint.textSize = cellSize * 0.62f
            digitPaint.typeface = fonts.regular
            digitPaint.color = palette.userDigit
            digitPaint.alpha = 150
            drawCentered(canvas, focusDigit.toString(), cx, cy, digitPaint)
            digitPaint.alpha = 255
            return
        }

        val currentStep = step
        val candidates = stepCandidates
        val inStep = currentStep != null && candidates != null &&
            (stepCells[cell] || currentStep.eliminations.any { it.cell == cell })
        val mask = if (inStep) candidates!![cell] else notes[cell]
        if (mask == 0) return

        val third = cellSize / 3f
        notePaint.textSize = cellSize * 0.26f
        val hinting = currentStep != null || focusCell >= 0
        val selValue = if (selected >= 0 && !hinting) values[selected] else 0
        for (d in 1..9) {
            if (!Grid.has(mask, d)) continue
            val nx = r.left + third * ((d - 1) % 3 + 0.5f)
            val ny = r.top + third * ((d - 1) / 3 + 0.5f)
            val candidate = Candidate(cell, d)
            val eliminated = inStep && currentStep!!.eliminations.contains(candidate)
            val highlighted = inStep && currentStep!!.highlights.contains(candidate)
            val alternate = inStep && currentStep!!.altHighlights.contains(candidate)
            notePaint.typeface = fonts.regular
            notePaint.color = palette.inkMuted
            when {
                eliminated -> notePaint.color = palette.error
                highlighted -> {
                    fill.color = palette.accent
                    val half = third * 0.42f
                    canvas.drawRect(nx - half, ny - half, nx + half, ny + half, fill)
                    notePaint.color = palette.onAccent
                    notePaint.typeface = fonts.medium
                }
                alternate -> {
                    outline.color = palette.accent
                    outline.strokeWidth = dp(1.5f)
                    val half = third * 0.42f
                    canvas.drawRect(nx - half, ny - half, nx + half, ny + half, outline)
                    notePaint.color = palette.accent
                    notePaint.typeface = fonts.medium
                }
                isSelected -> notePaint.color = palette.onAccent
                highlightSame && selValue == d -> {
                    notePaint.color = palette.accent
                    notePaint.typeface = fonts.bold
                }
            }
            drawCentered(canvas, d.toString(), nx, ny, notePaint)
            if (eliminated) {
                outline.color = palette.error
                outline.strokeWidth = dp(1.5f)
                val half = third * 0.36f
                canvas.drawLine(nx - half, ny + half, nx + half, ny - half, outline)
            }
        }
    }

    private fun drawMessage(canvas: Canvas, size: Float, text: String) {
        messagePaint.typeface = fonts.medium
        messagePaint.textSize = sp(15f)
        messagePaint.letterSpacing = 0.12f
        messagePaint.color = palette.ink
        val textWidth = messagePaint.measureText(text)
        val padH = dp(20f)
        val padV = dp(14f)
        val fm = messagePaint.fontMetrics
        val boxH = (fm.descent - fm.ascent) + 2 * padV
        fill.color = palette.bg
        val left = size / 2 - textWidth / 2 - padH
        val top = size / 2 - boxH / 2
        canvas.drawRect(left, top, size - left, top + boxH, fill)
        outline.color = palette.lineStrong
        outline.strokeWidth = dp(1.5f)
        canvas.drawRect(left, top, size - left, top + boxH, outline)
        drawCentered(canvas, text, size / 2, size / 2, messagePaint)
    }

    private fun drawCentered(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        val fm = paint.fontMetrics
        canvas.drawText(text, x, y - (fm.ascent + fm.descent) / 2, paint)
    }
}
