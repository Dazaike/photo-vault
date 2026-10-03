package com.dazaike.photovault.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun VaultNavHost() {
    val navController = rememberNavController()
    val viewModel: VaultViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = "grid",
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it / 4 },
                animationSpec = tween(280, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(280))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 4 },
                animationSpec = tween(280, easing = FastOutSlowInEasing),
            ) + fadeOut(animationSpec = tween(280))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 4 },
                animationSpec = tween(280, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(280))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it / 4 },
                animationSpec = tween(280, easing = FastOutSlowInEasing),
            ) + fadeOut(animationSpec = tween(280))
        },
    ) {
        composable("grid") {
            VaultGridScreen(
                viewModel = viewModel,
                onOpenViewer = { itemId -> navController.navigate("viewer/$itemId") },
                onOpenTrash = { navController.navigate("trash") },
                onOpenAlbums = { navController.navigate("albums") },
                onOpenAlbum = { albumId -> navController.navigate("album/$albumId") },
            )
        }
        composable(
            "viewer/{itemId}",
            arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            enterTransition = { scaleIn(initialScale = 0.88f, animationSpec = tween(260)) + fadeIn(tween(260)) },
            exitTransition = { scaleOut(targetScale = 0.88f, animationSpec = tween(220)) + fadeOut(tween(220)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { scaleOut(targetScale = 0.88f, animationSpec = tween(220)) + fadeOut(tween(220)) },
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
            val items by viewModel.items.collectAsState()
            PhotoViewerScreen(
                viewModel = viewModel,
                items = items,
                targetItemId = itemId,
                onBack = { navController.popBackStack() },
            )
        }
        composable("trash") {
            TrashScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("albums") {
            AlbumsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenAlbum = { albumId -> navController.navigate("album/$albumId") },
            )
        }
        composable(
            "album/{albumId}",
            arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getString("albumId") ?: return@composable
            AlbumDetailScreen(
                viewModel = viewModel,
                albumId = albumId,
                onBack = { navController.popBackStack() },
                onOpenViewer = { itemId -> navController.navigate("albumViewer/$albumId/$itemId") },
            )
        }
        composable(
            "albumViewer/{albumId}/{itemId}",
            arguments = listOf(
                navArgument("albumId") { type = NavType.StringType },
                navArgument("itemId") { type = NavType.StringType },
            ),
            enterTransition = { scaleIn(initialScale = 0.88f, animationSpec = tween(260)) + fadeIn(tween(260)) },
            exitTransition = { scaleOut(targetScale = 0.88f, animationSpec = tween(220)) + fadeOut(tween(220)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { scaleOut(targetScale = 0.88f, animationSpec = tween(220)) + fadeOut(tween(220)) },
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getString("albumId") ?: return@composable
            val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
            val items by viewModel.itemsInAlbum(albumId).collectAsState(initial = emptyList())
            PhotoViewerScreen(
                viewModel = viewModel,
                items = items,
                targetItemId = itemId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
