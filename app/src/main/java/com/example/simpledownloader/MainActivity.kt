package com.example.simpledownloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.simpledownloader.data.local.AppDatabase
import com.example.simpledownloader.data.preferences.DownloadSettingsManager
import com.example.simpledownloader.data.repository.VideoRepositoryImpl
import com.example.simpledownloader.domain.usecase.AnalyzeUrlUseCase
import com.example.simpledownloader.domain.usecase.DownloadVideoUseCase
import com.example.simpledownloader.navigation.Screen
import com.example.simpledownloader.ui.screens.history.HistoryScreen
import com.example.simpledownloader.ui.screens.history.HistoryViewModel
import com.example.simpledownloader.ui.screens.main.MainScreen
import com.example.simpledownloader.ui.screens.main.MainViewModel
import com.example.simpledownloader.ui.components.CustomFloatingTabBar
import com.example.simpledownloader.ui.theme.SimpleDownloaderTheme
import timber.log.Timber

class MainActivity : ComponentActivity() {
    
    // Simple dependency injection (will be replaced with proper DI later)
    private lateinit var database: AppDatabase
    private lateinit var settingsManager: DownloadSettingsManager
    private lateinit var mainViewModel: MainViewModel
    private lateinit var historyViewModel: HistoryViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Timber.d("MainActivity created")
        
        // Initialize database
        database = AppDatabase.getInstance(this)
        
        // Initialize settings manager
        settingsManager = DownloadSettingsManager(this)
        
        // Initialize Repository
        val repository = VideoRepositoryImpl(database.downloadDao(), applicationContext)
        val analyzeUrlUseCase = AnalyzeUrlUseCase(repository)
        val downloadVideoUseCase = DownloadVideoUseCase(repository)
        
        // Initialize ViewModels
        mainViewModel = MainViewModel(analyzeUrlUseCase, downloadVideoUseCase, settingsManager)
        historyViewModel = HistoryViewModel(
            downloadDao = database.downloadDao(),
            settingsManager = settingsManager,
            context = applicationContext
        )

        
        enableEdgeToEdge()
        
        setContent {
            SimpleDownloaderTheme {
                SimpleDownloaderApp(
                    mainViewModel = mainViewModel,
                    historyViewModel = historyViewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleDownloaderApp(
    mainViewModel: MainViewModel,
    historyViewModel: HistoryViewModel
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Screen.Home.route) {
                    MainScreen(viewModel = mainViewModel)
                }
                
                composable(Screen.History.route) {
                    HistoryScreen(viewModel = historyViewModel)
                }
            }

            CustomFloatingTabBar(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            popUpTo(Screen.Home.route) {
                                if (route == Screen.Home.route) inclusive = true
                            }
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}