package com.nexusnotes.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHistoryTest {

    @Test
    fun startsWithNothingToUndoOrRedo() {
        val h = EditHistory("a")
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
        assertNull(h.undo())
        assertNull(h.redo())
    }

    @Test
    fun fastTypingIsOneUndoStep() {
        val h = EditHistory("", coalesceMillis = 1_000)
        h.record("H", now = 0)
        h.record("He", now = 200)
        h.record("Hel", now = 400)
        assertEquals("", h.undo())
        assertFalse(h.canUndo)
    }

    @Test
    fun pauseStartsANewUndoStep() {
        val h = EditHistory("", coalesceMillis = 1_000)
        h.record("Hello", now = 0)
        h.record("Hello world", now = 5_000)
        assertEquals("Hello", h.undo())
        assertEquals("", h.undo())
        assertNull(h.undo())
    }

    @Test
    fun redoRestoresUndoneState() {
        val h = EditHistory("", coalesceMillis = 0)
        h.record("a", now = 0)
        h.record("ab", now = 10)
        assertEquals("a", h.undo())
        assertTrue(h.canRedo)
        assertEquals("ab", h.redo())
        assertFalse(h.canRedo)
    }

    @Test
    fun newEditAfterUndoClearsRedo() {
        val h = EditHistory("", coalesceMillis = 0)
        h.record("a", now = 0)
        h.undo()
        h.record("x", now = 10)
        assertFalse(h.canRedo)
        assertNull(h.redo())
    }

    @Test
    fun editAfterUndoStartsNewStepEvenIfFast() {
        val h = EditHistory("", coalesceMillis = 10_000)
        h.record("a", now = 0)
        h.undo()
        h.record("b", now = 1)
        assertEquals("", h.undo())
    }

    @Test
    fun identicalValueIsIgnored() {
        val h = EditHistory("same", coalesceMillis = 0)
        h.record("same", now = 0)
        assertFalse(h.canUndo)
    }

    @Test
    fun capacityDropsOldestSteps() {
        val h = EditHistory(0, capacity = 3, coalesceMillis = 0)
        for (i in 1..10) h.record(i, now = i * 100L)
        var undos = 0
        while (h.undo() != null) undos++
        assertEquals(3, undos)
        assertEquals(7, h.current)
    }

    @Test
    fun resetForgetsEverything() {
        val h = EditHistory("", coalesceMillis = 0)
        h.record("a", now = 0)
        h.reset("loaded")
        assertFalse(h.canUndo)
        assertEquals("loaded", h.current)
    }

    @Test
    fun clockGoingBackwardsStartsNewStep() {
        val h = EditHistory("", coalesceMillis = 1_000)
        h.record("a", now = 5_000)
        h.record("ab", now = 100)
        assertEquals("a", h.undo())
    }

    @Test
    fun worksWithEditorSnapshots() {
        val h = EditHistory(EditorSnapshot("", ""), coalesceMillis = 0)
        h.record(EditorSnapshot("Title", ""), now = 0)
        h.record(EditorSnapshot("Title", "Body"), now = 10)
        assertEquals(EditorSnapshot("Title", ""), h.undo())
    }
}
