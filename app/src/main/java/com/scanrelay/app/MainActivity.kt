package com.scanrelay.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.scanrelay.app.data.model.AppMode
import com.scanrelay.app.ui.mode_select.ModeSelectScreen
import com.scanrelay.app.ui.pairing.PairScreen
import com.scanrelay.app.ui.receiver.HistoryScreen
import com.scanrelay.app.ui.receiver.ReceiverScreen
import com.scanrelay.app.ui.receiver.ReceiverViewModel
import com.scanrelay.app.ui.scanner.ScannerScreen
import com.scanrelay.app.ui.scanner.ScannerViewModel
import com.scanrelay.app.ui.settings.AboutScreen
import com.scanrelay.app.ui.settings.OverlayPermissionGuide
import com.scanrelay.app.ui.settings.SettingsScreen
import com.scanrelay.app.ui.theme.ScanRelayTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as ScanRelayApp

        setContent {
            ScanRelayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ScanRelayNavigation(app)
                }
            }
        }
    }
}

@Composable
fun ScanRelayNavigation(app: ScanRelayApp) {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val appMode by app.preferences.appModeFlow.collectAsState(initial = AppMode.UNSET)

    // Shared receiver view model
    val receiverViewModel: ReceiverViewModel = viewModel()
    val scannerViewModel: ScannerViewModel = viewModel()

    val startDestination = when (appMode) {
        AppMode.UNSET -> "mode_select"
        AppMode.SCANNER -> "scanner"
        AppMode.RECEIVER -> "receiver"
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("mode_select") {
            ModeSelectScreen(
                onModeSelected = { selectedMode ->
                    coroutineScope.launch {
                        app.preferences.setAppMode(selectedMode)
                        val nextDest = if (selectedMode == AppMode.SCANNER) "scanner" else "receiver"
                        navController.navigate(nextDest) {
                            popUpTo("mode_select") { inclusive = true }
                        }
                    }
                }
            )
        }

        composable("scanner") {
            ScannerScreen(
                onNavigateToPairing = { navController.navigate("pairing") },
                onNavigateToSettings = { navController.navigate("settings") },
                viewModel = scannerViewModel
            )
        }

        composable("receiver") {
            ReceiverScreen(
                onNavigateToPairing = { navController.navigate("pairing") },
                onNavigateToHistory = { navController.navigate("history") },
                onNavigateToSettings = { navController.navigate("settings") },
                viewModel = receiverViewModel
            )
        }

        composable("pairing") {
            PairScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("history") {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = receiverViewModel
            )
        }

        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToOverlayGuide = { navController.navigate("overlay_guide") },
                onNavigateToAbout = { navController.navigate("about") }
            )
        }

        composable("overlay_guide") {
            OverlayPermissionGuide(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("about") {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
