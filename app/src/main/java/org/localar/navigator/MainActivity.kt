package org.localar.navigator

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import org.localar.navigator.navigation.NavigationViewModel
import org.localar.navigator.ui.SearchAndRouteScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val pinKitaColors = androidx.compose.material3.lightColorScheme(
                primary = Color(0xFF0F4C4C), // Deep Teal
                secondary = Color(0xFF2F6B45), // Forest Green
                tertiary = Color(0xFFD89B2B), // Amber
                background = Color(0xFFF7F4EC), // Warm Cream
                surface = Color(0xFFFFFDF8), // Surface Warm White
                onPrimary = Color.White,
                onSecondary = Color.White,
                onBackground = Color(0xFF16302C),
                onSurface = Color(0xFF16302C),
                surfaceVariant = Color(0xFFF0ECE1),
                onSurfaceVariant = Color(0xFF4E5B57)
            )

            MaterialTheme(colorScheme = pinKitaColors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navigationViewModel: NavigationViewModel = viewModel()

                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
                        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                        if (fineGranted || coarseGranted) {
                            navigationViewModel.startListeningGps()
                        }
                    }

                    LaunchedEffect(Unit) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.CAMERA
                            )
                        )
                    }

                    SearchAndRouteScreen(viewModel = navigationViewModel)
                }
            }
        }
    }
}
