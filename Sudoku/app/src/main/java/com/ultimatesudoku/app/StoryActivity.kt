package com.ultimatesudoku.app

import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.ultimatesudoku.core.Technique

/**
 * Story mode placeholder: Anna's dream, in nine chapters. The chapter plan is real data so the mode
 * can be built on it: each chapter names the techniques its puzzles should teach, which the generator
 * and LogicSolver can already target and grade.
 */
class StoryActivity : BaseActivity() {

    /** A chapter of the story: what it teaches, its part of Anna's dream, and its picture, with words for it. */
    class Chapter(
        val title: String,
        val lesson: String,
        val techniques: List<Technique>,
        val story: Int,
        val art: Int,
        val artDescription: Int,
    )

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
        /** Until story mode is built, only the first chapter is open, and only its first level. */
        fun isOpen(index: Int) = index == 0

        val CHAPTERS = listOf(
            Chapter(
                "The Pond", "Full houses and hidden singles", listOf(Technique.FULL_HOUSE, Technique.HIDDEN_SINGLE),
                R.string.story_pond, R.drawable.art_pond, R.string.art_pond,
            ),
            Chapter(
                "The Village", "Naked singles", listOf(Technique.NAKED_SINGLE),
                R.string.story_village, R.drawable.art_village, R.string.art_village,
            ),
            Chapter(
                "The Harbour", "Pointing and claiming", listOf(Technique.POINTING, Technique.CLAIMING),
                R.string.story_harbour, R.drawable.art_harbour, R.string.art_harbour,
            ),
            Chapter(
                "The Lighthouse", "Naked and hidden pairs", listOf(Technique.NAKED_PAIR, Technique.HIDDEN_PAIR),
                R.string.story_lighthouse, R.drawable.art_lighthouse, R.string.art_lighthouse,
            ),
            Chapter(
                "The Open Sea", "Triples and the X-Wing", listOf(Technique.NAKED_TRIPLE, Technique.HIDDEN_TRIPLE, Technique.X_WING),
                R.string.story_open_sea, R.drawable.art_open_sea, R.string.art_open_sea,
            ),
            Chapter(
                "The Kites", "Skyscrapers and kites", listOf(Technique.SKYSCRAPER, Technique.TWO_STRING_KITE),
                R.string.story_kites, R.drawable.art_kites, R.string.art_kites,
            ),
            Chapter(
                "The Stars", "XY-Wings and XYZ-Wings", listOf(Technique.XY_WING, Technique.XYZ_WING, Technique.W_WING),
                R.string.story_stars, R.drawable.art_stars, R.string.art_stars,
            ),
            Chapter(
                "The Deep", "Swordfish and coloring", listOf(Technique.SWORDFISH, Technique.SIMPLE_COLORING),
                R.string.story_deep, R.drawable.art_deep, R.string.art_deep,
            ),
            Chapter(
                "Daybreak", "Chains and unique rectangles", listOf(Technique.XY_CHAIN, Technique.UNIQUE_RECTANGLE, Technique.JELLYFISH),
                R.string.story_daybreak, R.drawable.art_daybreak, R.string.art_daybreak,
            ),
        )
    }
}
