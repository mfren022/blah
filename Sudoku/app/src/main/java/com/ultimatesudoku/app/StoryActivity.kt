package com.ultimatesudoku.app

import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.ultimatesudoku.core.Technique

/**
 * Story mode placeholder. The chapter plan is real data so the mode can be built on it: each
 * chapter names the techniques its puzzles should teach, which the generator and LogicSolver can
 * already target and grade.
 */
class StoryActivity : BaseActivity() {

    class Chapter(val title: String, val lesson: String, val techniques: List<Technique>)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreen(R.layout.activity_story, getString(R.string.story_title))
        val list = findViewById<LinearLayout>(R.id.chapters)
        CHAPTERS.forEachIndexed { i, chapter ->
            val row = layoutInflater.inflate(R.layout.item_chapter, list, false)
            row.findViewById<TextView>(R.id.number).text = "%02d".format(i + 1)
            row.findViewById<TextView>(R.id.name).text = chapter.title
            row.findViewById<TextView>(R.id.lesson).text = chapter.lesson
            row.findViewById<ImageView>(R.id.state).setImageResource(if (isOpen(i)) R.drawable.ic_chevron else R.drawable.ic_lock)
            row.contentDescription =
                "Chapter ${i + 1}, ${chapter.title}: ${chapter.lesson}." + if (isOpen(i)) "" else " Locked."
            row.setOnClickListener { startActivity(ChapterActivity.open(this, i)) }
            list.addView(row)
        }
    }

    companion object {
        /** Until story mode is built, only the first district is open, and only its first level. */
        fun isOpen(index: Int) = index == 0

        val CHAPTERS = listOf(
            Chapter("The Old Quarter", "Full houses and hidden singles", listOf(Technique.FULL_HOUSE, Technique.HIDDEN_SINGLE)),
            Chapter("Market Street", "Naked singles", listOf(Technique.NAKED_SINGLE)),
            Chapter("The Harbour", "Pointing and claiming", listOf(Technique.POINTING, Technique.CLAIMING)),
            Chapter("Twin Bridges", "Naked and hidden pairs", listOf(Technique.NAKED_PAIR, Technique.HIDDEN_PAIR)),
            Chapter("The Observatory", "Triples and the X-Wing", listOf(Technique.NAKED_TRIPLE, Technique.HIDDEN_TRIPLE, Technique.X_WING)),
            Chapter("Skyline", "Skyscrapers and kites", listOf(Technique.SKYSCRAPER, Technique.TWO_STRING_KITE)),
            Chapter("The Gardens", "XY-Wings and XYZ-Wings", listOf(Technique.XY_WING, Technique.XYZ_WING, Technique.W_WING)),
            Chapter("Deep Water", "Swordfish and coloring", listOf(Technique.SWORDFISH, Technique.SIMPLE_COLORING)),
            Chapter("The Summit", "Chains and unique rectangles", listOf(Technique.XY_CHAIN, Technique.UNIQUE_RECTANGLE, Technique.JELLYFISH)),
        )
    }
}
