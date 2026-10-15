package com.topplecorp.game.ui

import android.graphics.Canvas
import com.topplecorp.game.audio.Sound

/** Reusable screen furniture built on [Ui]. */
object UiKit {
    const val HEADER_H = 44f

    /** Round back button in the top-left corner of sub-screens. */
    fun backButton(onClick: () -> Unit) =
        Button(10f, 10f, 26f, 26f, icon = Icon.BACK, sound = Sound.BACK, onClick = onClick).apply {
            radius = 13f
            iconSize = 14f
        }

    /** Gold counter pill, right-aligned at [right], vertically centred on [cy]. */
    fun goldPill(c: Canvas, right: Float, cy: Float, gold: Int) {
        val text = formatGold(gold)
        val w = Ui.width(text, 9f, Ui.Weight.BOLD) + 26f
        val x = right - w
        Ui.panel(c, x, cy - 10f, w, 20f, 10f)
        Ui.coin(c, x + 10f, cy, 5f)
        Ui.text(c, text, x + 19f, cy, 9f, Ui.TEXT, weight = Ui.Weight.BOLD)
    }

    fun formatGold(v: Int): String {
        val s = v.toString()
        val sb = StringBuilder()
        for ((i, ch) in s.withIndex()) {
            if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
            sb.append(ch)
        }
        return sb.toString()
    }

    /** Screen title shown next to the back button. */
    fun header(c: Canvas, w: Float, title: String) {
        Ui.roundRect(c, -10f, -10f, w + 20f, HEADER_H + 10f, 0f, Ui.BG_SOFT)
        Ui.text(c, title, w / 2f, 23f, 14f, Ui.TEXT, Ui.Align.CENTER, Ui.Weight.BOLD)
    }
}
