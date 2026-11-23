package com.nexusnotes.domain

/**
 * Undo/redo history for the editor.
 *
 * Typing is grouped: edits that arrive within [coalesceMillis] of the previous one
 * extend the same undo step, so one Undo removes a burst of typing, not one letter.
 * The history lives in memory only (it is not saved with the note).
 */
class EditHistory<T>(
    initial: T,
    private val capacity: Int = 100,
    private val coalesceMillis: Long = 1_000,
) {
    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()
    private var lastRecordAt: Long? = null

    var current: T = initial
        private set

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Record a new state produced by the user at time [now] (milliseconds). */
    fun record(value: T, now: Long) {
        if (value == current) return
        val last = lastRecordAt
        val startsNewStep = last == null || now - last > coalesceMillis || now < last
        if (startsNewStep) {
            undoStack.addLast(current)
            while (undoStack.size > capacity) undoStack.removeFirst()
        }
        current = value
        redoStack.clear()
        lastRecordAt = now
    }

    /** Returns the restored state, or null if there is nothing to undo. */
    fun undo(): T? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(current)
        current = undoStack.removeLast()
        lastRecordAt = null
        return current
    }

    /** Returns the restored state, or null if there is nothing to redo. */
    fun redo(): T? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(current)
        current = redoStack.removeLast()
        lastRecordAt = null
        return current
    }

    /** Forget all history and start again from [value]. */
    fun reset(value: T) {
        undoStack.clear()
        redoStack.clear()
        current = value
        lastRecordAt = null
    }
}

/** The part of a note the editor's undo/redo tracks. */
data class EditorSnapshot(val title: String, val body: String)
