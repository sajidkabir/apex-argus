package com.apexpredator.argus.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.apexpredator.argus.ui.screens.AirportsScreen
import com.apexpredator.argus.ui.screens.CamsScreen
import com.apexpredator.argus.ui.screens.HistoryScreen
import com.apexpredator.argus.ui.screens.HomeScreen
import com.apexpredator.argus.ui.screens.NearMeScreen
import com.apexpredator.argus.ui.screens.RadarScreen
import com.apexpredator.argus.ui.screens.ResultScreen
import com.apexpredator.argus.ui.screens.SkyScreen
import com.apexpredator.argus.ui.screens.SpotterScreen

private data class Tab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val TABS = listOf(
    Tab("radar", "Radar", Icons.Filled.Place),
    Tab("spotter", "Spotter", Icons.Filled.Search),
    Tab("sky", "Sky", Icons.Filled.Star),
    Tab("airports", "Airports", Icons.Filled.LocationOn),
    Tab("tools", "Tools", Icons.Filled.Home),
)

private val TAB_ROUTES = TABS.map { it.route }.toSet()

@Composable
fun AppNav(vm: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    // Activity-scoped so the Airports tab can aim the radar and open an
    // airport's board on the shared radar state.
    val activity = LocalContext.current as ComponentActivity
    val radarVm: RadarViewModel = viewModel(viewModelStoreOwner = activity)
    val spotterVm: SpotterViewModel = viewModel(viewModelStoreOwner = activity)
    val skyVm: SkyViewModel = viewModel(viewModelStoreOwner = activity)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    fun goTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in TAB_ROUTES) {
                NavigationBar {
                    for (tab in TABS) {
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { goTab(tab.route) },
                            icon = {
                                Icon(tab.icon, contentDescription = tab.label)
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { scaffoldPadding ->
        NavHost(
            navController = navController,
            startDestination = "tools",
            modifier = Modifier.padding(scaffoldPadding)
        ) {
            composable("tools") {
                val res = vm.result
                // One-shot navigation when a decode completes.
                LaunchedEffect(res) {
                    if (res != null) {
                        navController.navigate("result") { launchSingleTop = true }
                    }
                }
                HomeScreen(
                    vm = vm,
                    onOpenHistory = { navController.navigate("history") },
                    onOpenRadar = { goTab("radar") },
                    onOpenSpotter = { goTab("spotter") },
                    onOpenNearMe = { navController.navigate("nearme") },
                    onOpenSky = { goTab("sky") },
                    onOpenAirports = { goTab("airports") },
                    onOpenCams = { navController.navigate("cams") }
                )
            }
            composable("radar") {
                RadarScreen(
                    vm = radarVm,
                    onBack = null,
                    onDecodeMetar = { icao ->
                        vm.icaoInput = icao
                        vm.liveKind = com.apexpredator.argus.data.ReportKind.METAR
                        vm.fetchLive()
                        goTab("tools")
                    }
                )
            }
            composable("spotter") {
                SpotterScreen(
                    vm = spotterVm,
                    onOpenNearMe = { navController.navigate("nearme") }
                )
            }
            composable("nearme") {
                NearMeScreen(
                    vm = spotterVm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("sky") {
                SkyScreen(vm = skyVm)
            }
            composable("airports") {
                AirportsScreen(
                    radarVm = radarVm,
                    onOpenRadar = { goTab("radar") }
                )
            }
            composable("cams") {
                CamsScreen(onBack = { navController.popBackStack() })
            }
            composable("result") {
                val current = vm.result
                // System back also clears the result so returning to
                // tools does not bounce straight back here.
                BackHandler {
                    vm.backToHome()
                    navController.popBackStack("tools", inclusive = false)
                }
                if (current != null) {
                    ResultScreen(
                        result = current,
                        onBack = {
                            vm.backToHome()
                            navController.popBackStack("tools", inclusive = false)
                        }
                    )
                }
            }
            composable("history") {
                val entries by vm.historyEntries.collectAsState()
                HistoryScreen(
                    entries = entries,
                    onOpen = { entry ->
                        vm.openHistory(entry)
                        navController.navigate("result") { launchSingleTop = true }
                    },
                    onClear = { vm.clearHistory() },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
