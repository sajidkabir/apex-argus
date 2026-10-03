package com.apexpredator.argus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.apexpredator.argus.ui.screens.HistoryScreen
import com.apexpredator.argus.ui.screens.HomeScreen
import com.apexpredator.argus.ui.screens.ResultScreen

@Composable
fun AppNav(vm: MainViewModel = viewModel()) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            val res = vm.result
            // One-shot navigation when a decode completes.
            LaunchedEffect(res) {
                if (res != null) {
                    navController.navigate("result") { launchSingleTop = true }
                }
            }
            HomeScreen(vm = vm, onOpenHistory = { navController.navigate("history") })
        }
        composable("result") {
            val current = vm.result
            // System back also clears the result so returning to home
            // does not bounce straight back here.
            BackHandler {
                vm.backToHome()
                navController.popBackStack("home", inclusive = false)
            }
            if (current != null) {
                ResultScreen(
                    result = current,
                    onBack = {
                        vm.backToHome()
                        navController.popBackStack("home", inclusive = false)
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
