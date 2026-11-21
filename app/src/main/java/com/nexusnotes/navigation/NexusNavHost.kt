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
import com.nexusnotes.feature.vault.VaultScreen

private const val ANIM_MS = 220

@Composable
fun NexusNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val navigate: (String) -> Unit = { route -> navController.navigate(route) { launchSingleTop = true } }
    val back: () -> Unit = { navController.navigateUp() }

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
        composable(NexusDestination.AllNotes.route) { AllNotesScreen(onNavigate = navigate, onBack = back) }
        composable(
            route = NexusDestination.NoteEditor.route,
            arguments = listOf(
                navArgument(ARG_NOTE_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            NoteEditorScreen(noteId = entry.arguments?.getString(ARG_NOTE_ID), onBack = back)
        }
        composable(NexusDestination.Favorites.route) { FavoritesScreen(onBack = back) }
        composable(NexusDestination.Archive.route) { ArchiveScreen(onBack = back) }
        composable(NexusDestination.Trash.route) { TrashScreen(onBack = back) }

        // Organization
        composable(
            route = NexusDestination.FolderDetail.route,
            arguments = listOf(navArgument(ARG_FOLDER_ID) { type = NavType.StringType }),
        ) { entry ->
            FolderDetailScreen(
                folderId = entry.arguments?.getString(ARG_FOLDER_ID).orEmpty(),
                onNavigate = navigate,
                onBack = back,
            )
        }
        composable(NexusDestination.Tags.route) { TagsScreen(onBack = back) }
        composable(NexusDestination.Search.route) { SearchScreen(onBack = back) }

        // Prompts
        composable(
            route = NexusDestination.PromptEditor.route,
            arguments = listOf(
                navArgument(ARG_PROMPT_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            PromptEditorScreen(
                promptId = entry.arguments?.getString(ARG_PROMPT_ID),
                onNavigate = navigate,
                onBack = back,
            )
        }
        composable(
            route = NexusDestination.PromptRunner.route,
            arguments = listOf(
                navArgument(ARG_PROMPT_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { PromptRunnerScreen(onBack = back) }

        // Security, data and app info
        composable(NexusDestination.Vault.route) { VaultScreen(onBack = back) }
        composable(NexusDestination.Backup.route) { BackupScreen(onBack = back) }
        composable(NexusDestination.About.route) { AboutScreen(onBack = back) }
    }
}
