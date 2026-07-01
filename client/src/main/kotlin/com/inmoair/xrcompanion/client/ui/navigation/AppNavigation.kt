package com.inmoair.xrcompanion.client.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.inmoair.xrcompanion.client.data.DeviceRepository
import com.inmoair.xrcompanion.client.ui.screen.*
import com.inmoair.xrcompanion.client.ui.theme.AccentBlue
import com.inmoair.xrcompanion.client.ui.theme.SurfaceDark
import com.inmoair.xrcompanion.client.ui.theme.TextSecondary
import com.inmoair.xrcompanion.client.ui.viewmodel.AppManagerViewModel
import com.inmoair.xrcompanion.client.ui.viewmodel.ControlViewModel
import com.inmoair.xrcompanion.client.ui.viewmodel.DashboardViewModel
import com.inmoair.xrcompanion.client.ui.viewmodel.FileManagerViewModel

object Routes {
    const val DASHBOARD   = "dashboard"
    const val CONTROL     = "control"
    const val APP_MANAGER = "app_manager"
    const val FILE_MANAGER = "file_manager"
    const val SETTINGS    = "settings"
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun AppNavigation(
    navController: NavHostController,
    deviceRepository: DeviceRepository,
) {
    val bottomItems = listOf(
        BottomDestination(Routes.DASHBOARD, "Home", Icons.Default.Home),
        BottomDestination(Routes.CONTROL, "Control mode", Icons.Default.SettingsRemote),
        BottomDestination(Routes.FILE_MANAGER, "File transfer", Icons.Default.Folder),
        BottomDestination(Routes.SETTINGS, "Settings", Icons.Default.Settings),
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = SurfaceDark) {
                bottomItems.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(Routes.DASHBOARD) { saveState = true }
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentBlue,
                            selectedTextColor = AccentBlue,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = SurfaceDark,
                        ),
                    )
                }
            }
        },
        containerColor = com.inmoair.xrcompanion.client.ui.theme.DarkBackground,
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(innerPadding),
        ) {

        composable(Routes.DASHBOARD) {
            val vm: DashboardViewModel = hiltViewModel()
            val uiState by vm.uiState.collectAsState()
            DashboardScreen(
                uiState         = uiState,
                viewModel       = vm,
                onNavigateControl  = { navController.navigate(Routes.CONTROL) },
                onNavigateApps     = { navController.navigate(Routes.APP_MANAGER) },
                onNavigateFiles    = { navController.navigate(Routes.FILE_MANAGER) },
                onNavigateSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.CONTROL) {
            val vm: ControlViewModel = hiltViewModel()
            val uiState by vm.uiState.collectAsState()
            ControlScreen(
                uiState   = uiState,
                viewModel = vm,
                onBack    = { navController.popBackStack() },
            )
        }

        composable(Routes.APP_MANAGER) {
            val vm: AppManagerViewModel = hiltViewModel()
            val uiState by vm.uiState.collectAsState()
            AppManagerScreen(
                uiState   = uiState,
                viewModel = vm,
                onBack    = { navController.popBackStack() },
            )
        }

        composable(Routes.FILE_MANAGER) {
            val vm: FileManagerViewModel = hiltViewModel()
            val uiState by vm.uiState.collectAsState()
            FileManagerScreen(
                uiState = uiState,
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            ClientSettingsScreen(
                deviceRepository = deviceRepository,
                onBack = { navController.popBackStack() },
            )
        }
    }
    }
}
