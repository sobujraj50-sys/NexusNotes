package com.nexusnotes.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusDestinationTest {

    @Test
    fun allRoutesAreUnique() {
        val routes = NexusDestinations.all.map { it.route }
        assertEquals("Duplicate route found: $routes", routes.size, routes.toSet().size)
    }

    @Test
    fun allDestinationsAreRegisteredAndNonNull() {
        // Guards against the sealed-class initialization-order bug (null entries).
        assertEquals(18, NexusDestinations.all.size)
        assertTrue(NexusDestinations.all.all { it.route.isNotBlank() && it.title.isNotBlank() })
    }

    @Test
    fun bottomNavigationHasExactlyTheFourPrimaryTabsInOrder() {
        assertEquals(
            listOf("home", "folders", "prompts", "settings"),
            TopLevelDestination.entries.map { it.destination.route },
        )
        assertEquals(setOf("home", "folders", "prompts", "settings"), NexusDestinations.topLevelRoutes)
    }

    @Test
    fun noteEditorRouteBuilder() {
        assertEquals("editor", NexusDestination.NoteEditor.createRoute())
        assertEquals("editor?noteId=abc-123", NexusDestination.NoteEditor.createRoute("abc-123"))
    }

    @Test
    fun editorRouteWithFolder() {
        assertEquals("editor?folderId=f1", NexusDestination.NoteEditor.createRoute(folderId = "f1"))
        assertEquals("editor?noteId=n1&folderId=f1", NexusDestination.NoteEditor.createRoute("n1", "f1"))
    }

    @Test
    fun searchRouteBuilder() {
        assertEquals("search", NexusDestination.Search.createRoute())
        assertEquals("search?tagId=t1", NexusDestination.Search.createRoute(tagId = "t1"))
        assertEquals("search?folderId=unfiled", NexusDestination.Search.createRoute(folderId = UNFILED_FOLDER_ID))
    }

    @Test
    fun folderDetailRouteBuilder() {
        assertEquals("folders/unfiled", NexusDestination.FolderDetail.createRoute(UNFILED_FOLDER_ID))
    }

    @Test
    fun promptRouteBuilders() {
        assertEquals("prompts/editor", NexusDestination.PromptEditor.createRoute())
        assertEquals("prompts/run?promptId=p1", NexusDestination.PromptRunner.createRoute("p1"))
    }

    @Test
    fun vaultEditorRoutes() {
        assertEquals("vault/editor", NexusDestination.VaultEditor.createRoute())
        assertEquals("vault/editor?vaultItemId=abc", NexusDestination.VaultEditor.createRoute("abc"))
    }
}
