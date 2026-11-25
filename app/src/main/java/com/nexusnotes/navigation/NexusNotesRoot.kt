package com.nexusnotes.navigation

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nexusnotes.core.design.icons.NexusIcons
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

fun bottomNavTag(destination: TopLevelDestination): String = "bottom_nav_${destination.name.lowercase()}"

/** Root composable: bottom navigation (top-level screens only) + navigation host. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NexusNotesRoot(openRoutes: Flow<String> = emptyFlow()) {
    val navController = rememberNavController()
    LaunchedEffect(navController, openRoutes) {
        openRoutes.collect { route -> navController.navigate(route) { launchSingleTop = true } }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = currentDestination?.route?.let { it in NexusDestinations.topLevelRoutes } == true

    Scaffold(
        // Each screen's own Scaffold handles the status bar; this one only reserves the bottom bar.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    TopLevelDestination.entries.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == item.destination.route
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(item.destination.route) },
                            icon = { Icon(item.icon(), contentDescription = null) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                            modifier = Modifier.testTag(bottomNavTag(item)),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NexusNavHost(
            navController = navController,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        )
    }
}

private fun TopLevelDestination.icon(): ImageVector = when (this) {
    TopLevelDestination.HOME -> Icons.Filled.Home
    TopLevelDestination.FOLDERS -> NexusIcons.Folder
    TopLevelDestination.PROMPTS -> NexusIcons.Sparkle
    TopLevelDestination.SETTINGS -> Icons.Filled.Settings
}

/** Tab-style navigation: one copy of each tab, state saved and restored when switching. */
fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
