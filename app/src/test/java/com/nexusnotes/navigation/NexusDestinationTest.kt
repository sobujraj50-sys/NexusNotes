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
        assertEquals(17, NexusDestinations.all.size)
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
    fun folderDetailRouteBuilder() {
        assertEquals("folders/unfiled", NexusDestination.FolderDetail.createRoute(UNFILED_FOLDER_ID))
    }

    @Test
    fun promptRouteBuilders() {
        assertEquals("prompts/editor", NexusDestination.PromptEditor.createRoute())
        assertEquals("prompts/run?promptId=p1", NexusDestination.PromptRunner.createRoute("p1"))
    }
}
