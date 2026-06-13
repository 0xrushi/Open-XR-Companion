package com.inmoair.xrcompanion.client.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.inmoair.xrcompanion.client.data.DeviceRepository
import com.inmoair.xrcompanion.client.ui.screen.*
import com.inmoair.xrcompanion.client.ui.viewmodel.AppManagerViewModel
import com.inmoair.xrcompanion.client.ui.viewmodel.ControlViewModel
import com.inmoair.xrcompanion.client.ui.viewmodel.DashboardViewModel

object Routes {
    const val DASHBOARD   = "dashboard"
    const val CONTROL     = "control"
    const val APP_MANAGER = "app_manager"
    const val SETTINGS    = "settings"
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    deviceRepository: DeviceRepository,
) {
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {

        composable(Routes.DASHBOARD) {
            val vm: DashboardViewModel = hiltViewModel()
            val uiState by vm.uiState.collectAsState()
            DashboardScreen(
                uiState         = uiState,
                viewModel       = vm,
                onNavigateControl  = { navController.navigate(Routes.CONTROL) },
                onNavigateApps     = { navController.navigate(Routes.APP_MANAGER) },
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

        composable(Routes.SETTINGS) {
            ClientSettingsScreen(
                deviceRepository = deviceRepository,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
