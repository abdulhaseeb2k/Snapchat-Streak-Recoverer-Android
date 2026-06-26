package com.snapstreakrecoverer.ssr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.snapstreakrecoverer.ssr.data.RecoveryDatabase
import com.snapstreakrecoverer.ssr.ui.screens.FriendScreen
import com.snapstreakrecoverer.ssr.ui.screens.ProfileScreen
import com.snapstreakrecoverer.ssr.ui.screens.RecoveryScreen
import com.snapstreakrecoverer.ssr.ui.screens.SettingsScreen
import com.snapstreakrecoverer.ssr.ui.theme.SSRTheme
import com.snapstreakrecoverer.ssr.ui.theme.ThemeManager
import com.snapstreakrecoverer.ssr.ui.theme.ThemeSelection
import com.snapstreakrecoverer.ssr.ui.viewmodel.FriendViewModel
import com.snapstreakrecoverer.ssr.ui.viewmodel.ProfileViewModel
import com.snapstreakrecoverer.ssr.ui.viewmodel.RecoveryViewModel
import com.snapstreakrecoverer.ssr.ui.viewmodel.SettingsViewModel
import com.snapstreakrecoverer.ssr.ui.viewmodel.ViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val database = RecoveryDatabase.getDatabase(this)
        val dao = database.recoveryDao()
        val themeManager = ThemeManager(this)
        val viewModelFactory = ViewModelFactory(dao, themeManager)

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
            val themeSelection by settingsViewModel.themeSelection.collectAsState()
            
            SSRTheme(
                darkTheme = when (themeSelection) {
                    ThemeSelection.LIGHT -> false
                    ThemeSelection.DARK -> true
                    ThemeSelection.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                }
            ) {
                AppNavigation(viewModelFactory)
            }
        }
    }
}

@Composable
fun AppNavigation(viewModelFactory: ViewModelFactory) {
    val navController = rememberNavController()
    
    NavHost(navController = navController, startDestination = "profiles") {
        composable("profiles") {
            val viewModel: ProfileViewModel = viewModel(factory = viewModelFactory)
            ProfileScreen(
                viewModel = viewModel,
                onProfileSelected = { profile ->
                    navController.navigate("friends/${profile.id}")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                }
            )
        }
        composable("settings") {
            val settingsViewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "friends/{profileId}",
            arguments = listOf(navArgument("profileId") { type = NavType.IntType })
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getInt("profileId") ?: return@composable
            val viewModel: FriendViewModel = viewModel(factory = viewModelFactory)
            val profileViewModel: ProfileViewModel = viewModel(factory = viewModelFactory)
            val profiles by profileViewModel.allProfiles.collectAsState()
            val profile = profiles.find { it.id == profileId } ?: return@composable

            FriendScreen(
                profile = profile,
                viewModel = viewModel,
                onRecover = {
                    navController.navigate("recovery/${profileId}")
                }
            )
        }
        composable(
            route = "recovery/{profileId}",
            arguments = listOf(navArgument("profileId") { type = NavType.IntType })
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getInt("profileId") ?: return@composable
            val recoveryViewModel: RecoveryViewModel = viewModel(factory = viewModelFactory)

            LaunchedEffect(profileId) {
                recoveryViewModel.load(profileId)
            }

            RecoveryScreen(
                viewModel = recoveryViewModel,
                onComplete = {
                    navController.popBackStack("profiles", inclusive = false)
                }
            )
        }
    }
}
