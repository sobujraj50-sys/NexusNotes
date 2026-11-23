package com.nexusnotes.domain

import com.nexusnotes.domain.FolderTree.MoveCheck
import com.nexusnotes.domain.model.Folder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderTreeTest {

    private fun f(id: String, parent: String? = null, name: String = id) = Folder(id, name, parent, null, 0)

    // work ─ projects ─ app
    //      └ meetings
    // home
    private val tree = listOf(
        f("home", name = "Home"),
        f("work", name = "Work"),
        f("projects", "work", "Projects"),
        f("meetings", "work", "meetings"),
        f("app", "projects", "App"),
    )

    @Test
    fun flattenOrdersParentsBeforeChildrenAlphabetically() {
        val nodes = FolderTree.flatten(tree, mapOf("app" to 3))
        assertEquals(listOf("home", "work", "meetings", "projects", "app"), nodes.map { it.folder.id })
        assertEquals(listOf(0, 0, 1, 1, 2), nodes.map { it.depth })
        assertEquals(3, nodes.last().noteCount)
    }

    @Test
    fun orphansAreStillShown() {
        val nodes = FolderTree.flatten(listOf(f("a", "missing"), f("b", "a")))
        assertEquals(listOf("a", "b"), nodes.map { it.folder.id })
    }

    @Test
    fun corruptCycleInDataDoesNotHang() {
        val nodes = FolderTree.flatten(listOf(f("a", "b"), f("b", "a")))
        assertEquals(2, nodes.size)
    }

    @Test
    fun subtree() {
        assertEquals(setOf("work", "projects", "meetings", "app"), FolderTree.subtreeIds(tree, "work").toSet())
        assertEquals(listOf("app"), FolderTree.subtreeIds(tree, "app"))
    }

    @Test
    fun depth() {
        assertEquals(0, FolderTree.depthOf(tree, "work"))
        assertEquals(2, FolderTree.depthOf(tree, "app"))
        assertEquals(-1, FolderTree.depthOf(tree, null))
    }

    @Test
    fun movesThatWouldCreateLoopsAreBlocked() {
        assertEquals(MoveCheck.IntoItself, FolderTree.checkMove(tree, "work", "work"))
        assertEquals(MoveCheck.IntoOwnSubfolder, FolderTree.checkMove(tree, "work", "app"))
        assertEquals(MoveCheck.IntoOwnSubfolder, FolderTree.checkMove(tree, "work", "projects"))
    }

    @Test
    fun validMoves() {
        assertEquals(MoveCheck.Ok, FolderTree.checkMove(tree, "app", "home"))
        assertEquals(MoveCheck.Ok, FolderTree.checkMove(tree, "projects", null))
        assertEquals(MoveCheck.Ok, FolderTree.checkMove(tree, "home", "meetings"))
    }

    @Test
    fun depthLimit() {
        val chain = (0 until FolderTree.MAX_DEPTH).map { i -> f("d$i", if (i == 0) null else "d${i - 1}") }
        assertFalse(FolderTree.canCreateUnder(chain, "d${FolderTree.MAX_DEPTH - 1}"))
        assertTrue(FolderTree.canCreateUnder(chain, "d0"))
        val withExtra = chain + f("x") + f("y", "x")
        assertEquals(MoveCheck.TooDeep, FolderTree.checkMove(withExtra, "x", "d${FolderTree.MAX_DEPTH - 2}"))
    }

    @Test
    fun cleanName() {
        assertEquals("My  Notes".replace("  ", " "), FolderTree.cleanName("  My \n Notes "))
        assertNull(FolderTree.cleanName("   "))
        assertEquals(FolderTree.MAX_NAME_LENGTH, FolderTree.cleanName("x".repeat(200))!!.length)
        assertEquals("কাজ", FolderTree.cleanName(" কাজ "))
    }
}
