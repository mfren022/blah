package com.draftpad.core

/**
 * Undo and redo over snapshots of a sketch's elements. Snapshots share their (immutable) elements,
 * so each one costs a list, not a copy of the drawing.
 */
class History(initial: List<Element> = emptyList(), private val limit: Int = 200) {
    var current: List<Element> = initial
        private set

    private val past = ArrayDeque<List<Element>>()
    private val future = ArrayDeque<List<Element>>()

    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    /** Makes [next] the current state. Returns false, recording nothing, if nothing changed. */
    fun commit(next: List<Element>): Boolean {
        if (next == current) return false
        past.addLast(current)
        if (past.size > limit) past.removeFirst()
        future.clear()
        current = next
        return true
    }

    fun undo(): Boolean {
        val previous = past.removeLastOrNull() ?: return false
        future.addLast(current)
        current = previous
        return true
    }

    fun redo(): Boolean {
        val next = future.removeLastOrNull() ?: return false
        past.addLast(current)
        current = next
        return true
    }

    /** Starts over from [elements] with nothing to undo, as when another draft is opened. */
    fun reset(elements: List<Element>) {
        past.clear()
        future.clear()
        current = elements
    }
}
