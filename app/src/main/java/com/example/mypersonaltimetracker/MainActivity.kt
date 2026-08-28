package com.example.mypersonaltimetracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mypersonaltimetracker.reminder.ReminderScheduler
import com.example.mypersonaltimetracker.ui.common.appViewModel
import com.example.mypersonaltimetracker.ui.day.DayContent
import com.example.mypersonaltimetracker.ui.day.DayViewModel
import com.example.mypersonaltimetracker.ui.history.HistoryScreen
import com.example.mypersonaltimetracker.ui.history.HistoryViewModel
import com.example.mypersonaltimetracker.ui.month.MonthScreen
import com.example.mypersonaltimetracker.ui.month.MonthViewModel
import com.example.mypersonaltimetracker.ui.settings.SettingsScreen
import com.example.mypersonaltimetracker.ui.settings.SettingsViewModel
import com.example.mypersonaltimetracker.ui.theme.TimeTrackerTheme
import com.example.mypersonaltimetracker.ui.week.WeekHistoryScreen
import com.example.mypersonaltimetracker.ui.week.WeekHistoryViewModel
import com.example.mypersonaltimetracker.ui.week.WeekScreen
import com.example.mypersonaltimetracker.ui.week.WeekViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Ensure reminders are (re)scheduled according to current settings at app start.
        lifecycleScope.launch { ReminderScheduler.reschedule(this@MainActivity) }
        setContent {
            TimeTrackerTheme {
                AppRoot()
            }
        }
    }
}

private data class Tab(val route: String, val label: String)

private val tabs = listOf(
    Tab("today", "Hôm nay"),
    Tab("history", "Lịch sử"),
    Tab("week", "Tuần"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = remember { App.get(context) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val title = when {
        currentRoute == "today" -> "Hôm nay"
        currentRoute == "history" -> "Lịch sử"
        currentRoute == "week" -> "Tuần"
        currentRoute == "settings" -> "Cài đặt"
        currentRoute == "weekHistory" -> "Các tuần trước"
        currentRoute == "months" -> "Theo tháng"
        currentRoute?.startsWith("day/") == true ->
            backStackEntry?.arguments?.getString("date") ?: "Chi tiết ngày"
        else -> "Time Tracker"
    }
    val showBack = currentRoute == "settings" || currentRoute == "weekHistory" ||
        currentRoute == "months" || currentRoute?.startsWith("day/") == true
    val showBottomBar = currentRoute in tabs.map { it.route }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                        }
                    }
                },
                actions = {
                    if (currentRoute != "settings") {
                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(Icons.Default.Settings, contentDescription = "Cài đặt")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    when (tab.route) {
                                        "today" -> Icons.Default.Home
                                        "history" -> Icons.Default.DateRange
                                        else -> Icons.Default.Star
                                    },
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "today",
            modifier = Modifier.padding(padding),
        ) {
            composable("today") {
                val vm: DayViewModel = appViewModel(key = "today") {
                    DayViewModel(app, LocalDate.now())
                }
                DayContent(viewModel = vm)
            }
            composable("history") {
                val vm: HistoryViewModel = appViewModel { HistoryViewModel(app) }
                HistoryScreen(
                    viewModel = vm,
                    onDayClick = { date -> navController.navigate("day/$date") },
                    onShowMonths = { navController.navigate("months") },
                )
            }
            composable("week") {
                val vm: WeekViewModel = appViewModel { WeekViewModel(app) }
                WeekScreen(viewModel = vm, onShowHistory = { navController.navigate("weekHistory") })
            }
            composable("weekHistory") {
                val vm: WeekHistoryViewModel = appViewModel { WeekHistoryViewModel(app) }
                WeekHistoryScreen(viewModel = vm)
            }
            composable("months") {
                val vm: MonthViewModel = appViewModel { MonthViewModel(app) }
                MonthScreen(viewModel = vm)
            }
            composable("settings") {
                val vm: SettingsViewModel = appViewModel { SettingsViewModel(app) }
                SettingsScreen(viewModel = vm, snackbarHostState = snackbarHostState)
            }
            composable(
                route = "day/{date}",
                arguments = listOf(navArgument("date") { type = NavType.StringType }),
            ) { entry ->
                val date = LocalDate.parse(entry.arguments?.getString("date"))
                val vm: DayViewModel = appViewModel(key = "day-$date") { DayViewModel(app, date) }
                DayContent(viewModel = vm)
            }
        }
    }
}
