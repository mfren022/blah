package com.ultimatesudoku.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ultimatesudoku.core.Difficulty
import com.ultimatesudoku.core.GameMode
import com.ultimatesudoku.core.Grid
import com.ultimatesudoku.core.SolvedBoard

/**
 * Every completed board, in any mode: its starting combination, solution and solve time. Kept in
 * SQLite rather than SharedPreferences because it grows without bound and prefs are rewritten on
 * every save. Calls touch the disk, so make them off the main thread.
 */
class History(context: Context) {

    private val helper = object : SQLiteOpenHelper(context, "history.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    completed_at INTEGER NOT NULL,
                    mode TEXT NOT NULL,
                    difficulty TEXT NOT NULL,
                    tag TEXT NOT NULL,
                    givens TEXT NOT NULL,
                    solution TEXT NOT NULL,
                    time_ms INTEGER NOT NULL,
                    mistakes INTEGER NOT NULL,
                    hints INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX solved_by_date ON $TABLE (completed_at)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    /** Stores [board] and returns it with its new id. */
    fun add(board: SolvedBoard): SolvedBoard {
        val values = ContentValues().apply {
            put("completed_at", board.completedAt)
            put("mode", board.mode.name)
            put("difficulty", board.difficulty.name)
            put("tag", board.tag)
            put("givens", Grid.format(board.givens, '0'))
            put("solution", Grid.format(board.solution, '0'))
            put("time_ms", board.timeMs)
            put("mistakes", board.mistakes)
            put("hints", board.hints)
        }
        return board.withId(helper.writableDatabase.insertOrThrow(TABLE, null, values))
    }

    /** All boards, newest first, optionally from one mode only. */
    fun all(mode: GameMode? = null): List<SolvedBoard> {
        val where = if (mode == null) null else "mode = ?"
        val args = if (mode == null) null else arrayOf(mode.name)
        return helper.readableDatabase.query(TABLE, null, where, args, null, null, "completed_at DESC, _id DESC")
            .use { cursor ->
                val boards = ArrayList<SolvedBoard>(cursor.count)
                // Skip unreadable rows rather than stopping at them.
                while (cursor.moveToNext()) read(cursor)?.let(boards::add)
                boards
            }
    }

    fun get(id: Long): SolvedBoard? =
        helper.readableDatabase.query(TABLE, null, "_id = ?", arrayOf(id.toString()), null, null, null)
            .use { if (it.moveToFirst()) read(it) else null }

    fun count(): Int = DatabaseUtils.queryNumEntries(helper.readableDatabase, TABLE).toInt()

    class Summary(val count: Int, val totalMs: Long)

    fun summary(): Summary =
        helper.readableDatabase.rawQuery("SELECT COUNT(*), TOTAL(time_ms) FROM $TABLE", null).use {
            it.moveToFirst()
            Summary(it.getInt(0), it.getDouble(1).toLong())
        }

    fun delete(id: Long) {
        helper.writableDatabase.delete(TABLE, "_id = ?", arrayOf(id.toString()))
    }

    fun clear() {
        helper.writableDatabase.delete(TABLE, null, null)
    }

    /** Closes the connection; the next call reopens it. The app keeps it open for its lifetime. */
    fun close() {
        helper.close()
    }

    /** A row as a board, or null if it can't be read (for example a mode from a newer version). */
    private fun read(c: Cursor): SolvedBoard? = runCatching {
        SolvedBoard(
            completedAt = c.getLong(c.getColumnIndexOrThrow("completed_at")),
            mode = GameMode.valueOf(c.getString(c.getColumnIndexOrThrow("mode"))),
            difficulty = Difficulty.valueOf(c.getString(c.getColumnIndexOrThrow("difficulty"))),
            givens = Grid.parse(c.getString(c.getColumnIndexOrThrow("givens")))!!,
            solution = Grid.parse(c.getString(c.getColumnIndexOrThrow("solution")))!!,
            timeMs = c.getLong(c.getColumnIndexOrThrow("time_ms")),
            mistakes = c.getInt(c.getColumnIndexOrThrow("mistakes")),
            hints = c.getInt(c.getColumnIndexOrThrow("hints")),
            tag = c.getString(c.getColumnIndexOrThrow("tag")),
            id = c.getLong(c.getColumnIndexOrThrow("_id")),
        )
    }.getOrNull()

    private companion object {
        const val TABLE = "solved"
    }
}
