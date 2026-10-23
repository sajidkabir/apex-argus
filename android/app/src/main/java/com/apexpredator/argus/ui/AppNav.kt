package com.apexpredator.argus.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.apexpredator.argus.ui.screens.AirportsScreen
import com.apexpredator.argus.ui.screens.CamsScreen
import com.apexpredator.argus.ui.screens.ChatScreen
import com.apexpredator.argus.ui.screens.HistoryScreen
import com.apexpredator.argus.ui.screens.HomeScreen
import com.apexpredator.argus.ui.screens.NearMeScreen
import com.apexpredator.argus.ui.screens.RadarScreen
import com.apexpredator.argus.ui.screens.ResultScreen
import com.apexpredator.argus.ui.screens.SkyScreen
import com.apexpredator.argus.ui.screens.SpotterScreen

// Smooth tab transitions: every destination slides a quarter width
// and fades, so switching tabs feels like one continuous surface.
private val tabEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn() }
private val tabExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut() }
private val tabPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn() }
private val tabPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { slideOutHorizontally(targetOffsetX = { it / 4 }) + fadeOut() }

// Chat bubble drawn with Canvas: the app has no material-icons-extended
// dependency, so the sixth tab draws its own icon (rounded bubble plus
// three dots).
@Composable
private fun ChatBubbleIcon() {
    val bubble = MaterialTheme.colorScheme.onSurfaceVariant
    val dots = MaterialTheme.colorScheme.surface
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = bubble,
            topLeft = Offset(w * 0.08f, h * 0.10f),
            size = Size(w * 0.84f, h * 0.62f),
            cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
        )
        val tail = Path().apply {
            moveTo(w * 0.30f, h * 0.70f)
            lineTo(w * 0.22f, h * 0.95f)
            lineTo(w * 0.46f, h * 0.70f)
            close()
        }
        drawPath(path = tail, color = bubble)
        val cy = h * 0.41f
        val r = w * 0.045f
        drawCircle(color = dots, radius = r, center = Offset(w * 0.30f, cy))
        drawCircle(color = dots, radius = r, center = Offset(w * 0.50f, cy))
        drawCircle(color = dots, radius = r, center = Offset(w * 0.70f, cy))
    }
}

private data class Tab(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit,
)

private val TABS = listOf(
    Tab("radar", "Radar", { Icon(Icons.Filled.Place, contentDescription = "Radar") }),
    Tab("spotter", "Spotter", { Icon(Icons.Filled.Search, contentDescription = "Spotter") }),
    Tab("sky", "Sky", { Icon(Icons.Filled.Star, contentDescription = "Sky") }),
    Tab("airports", "Airports", { Icon(Icons.Filled.LocationOn, contentDescription = "Airports") }),
    Tab("tools", "Tools", { Icon(Icons.Filled.Home, contentDescription = "Tools") }),
    Tab("chat", "Chat", { ChatBubbleIcon() }),
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
                            icon = { tab.icon() },
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
            composable("tools",
                enterTransition = tabEnter,
                exitTransition = tabExit,
                popEnterTransition = tabPopEnter,
                popExitTransition = tabPopExit) {
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
            composable("radar",
                enterTransition = tabEnter,
                exitTransition = tabExit,
                popEnterTransition = tabPopEnter,
                popExitTransition = tabPopExit) {
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
            composable("spotter",
                enterTransition = tabEnter,
                exitTransition = tabExit,
                popEnterTransition = tabPopEnter,
                popExitTransition = tabPopExit) {
                SpotterScreen(
                    vm = spotterVm,
                    onOpenNearMe = { navController.navigate("nearme") }
                )
            }
            composable("nearme",
                enterTransition = tabEnter,
                exitTransition = tabExit,
                popEnterTransition = tabPopEnter,
                popExitTransition = tabPopExit) {
                NearMeScreen(
                    vm = spotterVm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("sky",
                enterTransition = tabEnter,
                exitTransition = tabExit,
                popEnterTransition = tabPopEnter,
                popExitTransition = tabPopExit) {
                SkyScreen(vm = skyVm)
            }
            composable("airports",
                enterTransition = tabEnter,
                exitTransition = tabExit,
                popEnterTransition = tabPopEnter,
                popExitTransition = tabPopExit) {
                AirportsScreen(
                    radarVm = radarVm,
                    onOpenRadar = { goTab("radar") }
                )
            }
            composable("chat",
                enterTransition = tabEnter,
                exitTransition = tabExit,
                popEnterTransition = tabPopEnter,
                popExitTransition = tabPopExit) {
                ChatScreen()
            }
            composable("cams",
                enterTransition = tabEnter,
                exitTransition = tabExit,
                popEnterTransition = tabPopEnter,
                popExitTransition = tabPopExit) {
                CamsScreen(onBack = { navController.popBackStack() })
            }
            composable("result",
                enterTransition = tabEnter,
                exitTransition = tabExit,
                popEnterTransition = tabPopEnter,
                popExitTransition = tabPopExit) {
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
            composable("history",
                enterTransition = tabEnter,
                exitTransition = tabExit,
                popEnterTransition = tabPopEnter,
                popExitTransition = tabPopExit) {
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
