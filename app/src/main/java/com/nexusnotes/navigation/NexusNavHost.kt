package com.nexusnotes.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nexusnotes.feature.backup.BackupScreen
import com.nexusnotes.feature.editor.NoteEditorScreen
import com.nexusnotes.feature.folders.FolderDetailScreen
import com.nexusnotes.feature.folders.FoldersScreen
import com.nexusnotes.feature.home.HomeScreen
import com.nexusnotes.feature.notes.AllNotesScreen
import com.nexusnotes.feature.notes.ArchiveScreen
import com.nexusnotes.feature.notes.FavoritesScreen
import com.nexusnotes.feature.notes.TrashScreen
import com.nexusnotes.feature.prompts.PromptEditorScreen
import com.nexusnotes.feature.prompts.PromptLibraryScreen
import com.nexusnotes.feature.prompts.PromptRunnerScreen
import com.nexusnotes.feature.search.SearchScreen
import com.nexusnotes.feature.settings.AboutScreen
import com.nexusnotes.feature.settings.SettingsScreen
import com.nexusnotes.feature.tags.TagsScreen
import com.nexusnotes.feature.vault.VaultEditorScreen
import com.nexusnotes.feature.vault.VaultScreen

private const val ANIM_MS = 220

private fun optionalString(name: String) = navArgument(name) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}

@Composable
fun NexusNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val navigate: (String) -> Unit = { route -> navController.navigate(route) { launchSingleTop = true } }
    val back: () -> Unit = { navController.navigateUp() }
    val openNote: (String) -> Unit = { id -> navigate(NexusDestination.NoteEditor.createRoute(id)) }

    NavHost(
        navController = navController,
        startDestination = NexusDestination.Home.route,
        modifier = modifier,
        enterTransition = { fadeIn(tween(ANIM_MS)) + slideInHorizontally(tween(ANIM_MS)) { it / 12 } },
        exitTransition = { fadeOut(tween(ANIM_MS)) },
        popEnterTransition = { fadeIn(tween(ANIM_MS)) },
        popExitTransition = { fadeOut(tween(ANIM_MS)) + slideOutHorizontally(tween(ANIM_MS)) { it / 12 } },
    ) {
        // Top level
        composable(NexusDestination.Home.route) {
            HomeScreen(
                onNavigate = navigate,
                onOpenPrompts = { navController.navigateToTopLevel(NexusDestination.PromptLibrary.route) },
            )
        }
        composable(NexusDestination.Folders.route) { FoldersScreen(onNavigate = navigate) }
        composable(NexusDestination.PromptLibrary.route) { PromptLibraryScreen(onNavigate = navigate) }
        composable(NexusDestination.Settings.route) { SettingsScreen(onNavigate = navigate) }

        // Notes
        composable(NexusDestination.AllNotes.route) {
            AllNotesScreen(onOpenNote = openNote, onNavigate = navigate, onBack = back)
        }
        composable(
            route = NexusDestination.NoteEditor.route,
            arguments = listOf(optionalString(ARG_NOTE_ID), optionalString(ARG_FOLDER_ID)),
        ) {
            NoteEditorScreen(
                onBack = back,
                onOpenVault = { navigate(NexusDestination.Vault.route) },
                onOpenNote = { id ->
                    // Replace the current editor with the duplicate.
                    navController.navigate(NexusDestination.NoteEditor.createRoute(id)) {
                        popUpTo(NexusDestination.NoteEditor.route) { inclusive = true }
                    }
                },
            )
        }
        composable(NexusDestination.Favorites.route) { FavoritesScreen(onOpenNote = openNote, onBack = back) }
        composable(NexusDestination.Archive.route) { ArchiveScreen(onOpenNote = openNote, onBack = back) }
        composable(NexusDestination.Trash.route) { TrashScreen(onOpenNote = openNote, onBack = back) }

        // Organization
        composable(
            route = NexusDestination.FolderDetail.route,
            arguments = listOf(navArgument(ARG_FOLDER_ID) { type = NavType.StringType }),
        ) { entry ->
            FolderDetailScreen(
                folderId = entry.arguments?.getString(ARG_FOLDER_ID).orEmpty(),
                onOpenNote = openNote,
                onNewNote = { folderId -> navigate(NexusDestination.NoteEditor.createRoute(folderId = folderId)) },
                onNavigate = navigate,
                onBack = back,
            )
        }
        composable(NexusDestination.Tags.route) {
            TagsScreen(onOpenTag = { tagId -> navigate(NexusDestination.Search.createRoute(tagId = tagId)) }, onBack = back)
        }
        composable(
            route = NexusDestination.Search.route,
            arguments = listOf(optionalString(ARG_TAG_ID), optionalString(ARG_FOLDER_ID)),
        ) {
            SearchScreen(
                onOpenNote = openNote,
                onOpenPrompt = { id -> navigate(NexusDestination.PromptEditor.createRoute(id)) },
                onBack = back,
            )
        }

        // Prompts
        composable(
            route = NexusDestination.PromptEditor.route,
            arguments = listOf(optionalString(ARG_PROMPT_ID), optionalString(ARG_CATEGORY)),
        ) {
            PromptEditorScreen(
                onBack = back,
                onOpenPrompt = { id ->
                    navController.navigate(NexusDestination.PromptEditor.createRoute(id)) {
                        popUpTo(NexusDestination.PromptEditor.route) { inclusive = true }
                    }
                },
                onRun = { id -> navigate(NexusDestination.PromptRunner.createRoute(id)) },
            )
        }
        composable(
            route = NexusDestination.PromptRunner.route,
            arguments = listOf(optionalString(ARG_PROMPT_ID)),
        ) { PromptRunnerScreen(onBack = back) }

        // Security, data and app info
        composable(NexusDestination.Vault.route) {
            VaultScreen(onBack = back, onOpenItem = { id -> navigate(NexusDestination.VaultEditor.createRoute(id)) })
        }
        composable(
            route = NexusDestination.VaultEditor.route,
            arguments = listOf(optionalString(ARG_VAULT_ITEM_ID)),
        ) {
            VaultEditorScreen(
                onBack = back,
                onOpenNote = { noteId ->
                    // The vault note no longer exists: replace it with the normal note.
                    navController.navigate(NexusDestination.NoteEditor.createRoute(noteId)) {
                        popUpTo(NexusDestination.VaultEditor.route) { inclusive = true }
                    }
                },
            )
        }
        composable(NexusDestination.Backup.route) { BackupScreen(onBack = back) }
        composable(NexusDestination.About.route) { AboutScreen(onBack = back) }
    }
}
