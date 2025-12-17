package com.airgradient.android.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.airgradient.android.FeatureFlags
import com.airgradient.android.R
import com.airgradient.android.domain.models.auth.AuthState
import com.airgradient.android.ui.auth.ViewModels.AuthenticationEvent
import com.airgradient.android.ui.auth.ViewModels.AuthenticationViewModel
import com.airgradient.android.ui.auth.Views.AuthenticationSheet
import com.airgradient.android.ui.community.Views.CommunityScreen
import com.airgradient.android.ui.map.Views.OSMMapScreen
import com.airgradient.android.ui.locationdetail.Views.LocationDetailScreen
import com.airgradient.android.ui.mylocations.Views.MyLocationsScreen
import com.airgradient.android.ui.mymonitors.ViewModels.MonitorDetailViewModel
import com.airgradient.android.ui.mymonitors.ViewModels.PlaceSelectorViewModel
import com.airgradient.android.ui.mymonitors.Views.MonitorDetailRoute
import com.airgradient.android.ui.provisioning.wifible.Views.WifiBleProvisioningScreen
import com.airgradient.android.ui.settings.Views.SettingsScreen
import com.airgradient.android.ui.theme.md_theme_light_primary
import com.airgradient.android.ui.theme.md_theme_light_outline
import com.airgradient.android.ui.theme.md_theme_light_surfaceVariant

sealed class AGMapScreen(val route: String) {
    object Map : AGMapScreen("map") {
        const val SELECTED_LOCATION_ARG = "selectedLocationId"
        const val ROUTE_WITH_ARG = "map/{$SELECTED_LOCATION_ARG}"

        fun routeWithLocation(locationId: Int?): String {
            val targetId = locationId?.takeIf { it > 0 }
            return targetId?.let { "$route/$it" } ?: route
        }

        fun navRoute(): String = ROUTE_WITH_ARG
    }
    object MyLocations : AGMapScreen("my_locations")
    object MyMonitors : AGMapScreen("my_monitors")
    object MonitorDetail : AGMapScreen("monitor_detail") {
        const val PLACE_ID_ARG = "placeId"
        const val LOCATION_ID_ARG = "locationId"
        const val ROUTE_WITH_ARGS = "monitor_detail/{$PLACE_ID_ARG}/{$LOCATION_ID_ARG}"

        fun createRoute(placeId: Int, locationId: Int) = "monitor_detail/$placeId/$locationId"

        fun navRoute(): String = ROUTE_WITH_ARGS
    }
    object LocationDetail : AGMapScreen("location_detail/{locationId}") {
        const val ROUTE_PREFIX = "location_detail"

        fun createRoute(locationId: Int) = "$ROUTE_PREFIX/$locationId"
    }
    object Settings : AGMapScreen("settings")
    object Community : AGMapScreen("community")
    object WifiBleProvisioning : AGMapScreen("wifi_ble_provisioning")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AGMapApp(
    selectedLocationId: Int? = null,
    onSelectedLocationConsumed: () -> Unit = {},
    hasBookmarks: Boolean = false
) {
    val authenticationViewModel: AuthenticationViewModel = hiltViewModel()
    val authState by authenticationViewModel.authState.collectAsStateWithLifecycle()
    val isAuthenticated = authState is AuthState.Authenticated

    val navController = rememberNavController()
    var showAuthSheet by remember { mutableStateOf(false) }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val initialStartDestination = remember(hasBookmarks) {
        if (hasBookmarks) AGMapScreen.MyLocations.route else AGMapScreen.Map.route
    }

    LaunchedEffect(selectedLocationId) {
        selectedLocationId?.let { locationId ->
            navController.popBackStack(AGMapScreen.Map.navRoute(), inclusive = true)
            navController.popBackStack(AGMapScreen.Map.route, inclusive = true)
            navController.navigate(AGMapScreen.Map.routeWithLocation(locationId)) {
                launchSingleTop = true
            }
            onSelectedLocationConsumed()
        }
    }

    LaunchedEffect(authState, currentRoute) {
        val isMonitorRoute = currentRoute == AGMapScreen.MyMonitors.route ||
            currentRoute?.startsWith(AGMapScreen.MonitorDetail.route) == true
        if (authState is AuthState.SignedOut && isMonitorRoute) {
            navController.navigate(AGMapScreen.MyLocations.route) {
                popUpTo(navController.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(Unit) {
        authenticationViewModel.events.collect { event ->
            when (event) {
                AuthenticationEvent.SignedIn,
                AuthenticationEvent.SignedOut -> showAuthSheet = false
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                val isMyLocationsSelected = currentRoute == AGMapScreen.MyLocations.route ||
                    currentRoute == AGMapScreen.MyMonitors.route ||
                    currentRoute?.startsWith(AGMapScreen.MonitorDetail.route) == true ||
                    currentRoute?.startsWith(AGMapScreen.LocationDetail.ROUTE_PREFIX) == true
                val isMapSelected = currentRoute?.startsWith(AGMapScreen.Map.route) == true
                val isCommunitySelected = currentRoute == AGMapScreen.Community.route

                NavigationBarItem(
                    icon = {
                        Icon(
                            painter = painterResource(
                                id = if (isMyLocationsSelected) {
                                    R.drawable.nav_monitors_selected
                                } else {
                                    R.drawable.nav_monitors
                                }
                            ),
                            contentDescription = null,
                            tint = Color.Unspecified
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(id = R.string.nav_my_locations),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    selected = isMyLocationsSelected,
                    onClick = {
                        navController.navigate(AGMapScreen.MyLocations.route) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                            restoreState = false
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = md_theme_light_primary,
                        selectedTextColor = md_theme_light_primary,
                        indicatorColor = md_theme_light_surfaceVariant,
                        unselectedIconColor = md_theme_light_outline,
                        unselectedTextColor = md_theme_light_outline
                    )
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            painter = painterResource(
                                id = if (isMapSelected) {
                                    R.drawable.nav_map_selected
                                } else {
                                    R.drawable.nav_map
                                }
                            ),
                            contentDescription = null,
                            tint = Color.Unspecified
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(id = R.string.nav_map),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    selected = isMapSelected,
                    onClick = {
                        navController.navigate(AGMapScreen.Map.route) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = md_theme_light_primary,
                        selectedTextColor = md_theme_light_primary,
                        indicatorColor = md_theme_light_surfaceVariant,
                        unselectedIconColor = md_theme_light_outline,
                        unselectedTextColor = md_theme_light_outline
                    )
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            painter = painterResource(
                                id = if (isCommunitySelected) {
                                    R.drawable.nav_community_selected
                                } else {
                                    R.drawable.nav_community
                                }
                            ),
                            contentDescription = null,
                            tint = Color.Unspecified
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(id = R.string.nav_community),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    selected = isCommunitySelected,
                    onClick = {
                        navController.navigate(AGMapScreen.Community.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = md_theme_light_primary,
                        selectedTextColor = md_theme_light_primary,
                        indicatorColor = md_theme_light_surfaceVariant,
                        unselectedIconColor = md_theme_light_outline,
                        unselectedTextColor = md_theme_light_outline
                    )
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = initialStartDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            composable(AGMapScreen.Map.route) {
                OSMMapScreen(selectedLocationId = null)
            }

            composable(
                route = AGMapScreen.Map.navRoute(),
                arguments = listOf(
                    navArgument(AGMapScreen.Map.SELECTED_LOCATION_ARG) {
                        type = NavType.IntType
                        defaultValue = -1
                    }
                )
            ) { backStackEntry ->
                val selectedLocationId = backStackEntry.arguments
                    ?.getInt(AGMapScreen.Map.SELECTED_LOCATION_ARG)
                    ?.takeIf { it > 0 }
                OSMMapScreen(
                    selectedLocationId = selectedLocationId
                )
            }

            composable(AGMapScreen.MyLocations.route) {
                MyLocationsScreen(
                    onNavigateToLocationDetail = { locationId ->
                        navController.navigate(AGMapScreen.LocationDetail.createRoute(locationId)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToMonitorDetail = { placeId, locationId ->
                        navController.navigate(AGMapScreen.MonitorDetail.createRoute(placeId, locationId)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(AGMapScreen.Settings.route)
                    },
                    onExploreMap = {
                        navController.navigate(AGMapScreen.Map.route) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    },
                    isAuthenticated = isAuthenticated
                )
            }

            composable(AGMapScreen.MyMonitors.route) {
                LaunchedEffect(Unit) {
                    navController.navigate(AGMapScreen.MyLocations.route) {
                        popUpTo(navController.graph.findStartDestination().id)
                        launchSingleTop = true
                    }
                }
            }

            composable(
                route = AGMapScreen.MonitorDetail.navRoute(),
                arguments = listOf(
                    navArgument(AGMapScreen.MonitorDetail.PLACE_ID_ARG) { type = NavType.IntType },
                    navArgument(AGMapScreen.MonitorDetail.LOCATION_ID_ARG) { type = NavType.IntType }
                )
            ) { backStackEntry ->
                if (!isAuthenticated) {
                    LaunchedEffect(Unit) {
                        showAuthSheet = true
                        navController.navigate(AGMapScreen.MyLocations.route) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    }
                    return@composable
                }

                val placeId = backStackEntry.arguments?.getInt(AGMapScreen.MonitorDetail.PLACE_ID_ARG) ?: return@composable
                val locationId = backStackEntry.arguments?.getInt(AGMapScreen.MonitorDetail.LOCATION_ID_ARG) ?: return@composable

                val parentEntry = remember(backStackEntry) {
                    runCatching {
                        navController.getBackStackEntry(AGMapScreen.MyLocations.route)
                    }.getOrNull()
                }
                val parentViewModel: PlaceSelectorViewModel = parentEntry?.let { hiltViewModel(it) } ?: hiltViewModel()
                val parentState by parentViewModel.uiState.collectAsStateWithLifecycle()
                val summary = parentState.monitors.firstOrNull {
                    it.locationId == locationId && it.placeId == placeId
                }

                val detailViewModel: MonitorDetailViewModel = hiltViewModel()
                LaunchedEffect(summary, placeId) {
                    summary?.let { detailViewModel.setInitialData(placeId, it) }
                }
                val detailUiState by detailViewModel.uiState.collectAsStateWithLifecycle()

                MonitorDetailRoute(
                    uiState = detailUiState,
                    fallbackSummary = summary,
                    displayUnit = parentState.aqiDisplayUnit,
                    onBack = { navController.popBackStack() },
                    onSelectMetric = detailViewModel::selectMetric,
                    onSelectRange = detailViewModel::selectTimeRange,
                    onRetry = detailViewModel::retry
                )
            }

            composable(
                route = AGMapScreen.LocationDetail.route,
                arguments = listOf(navArgument("locationId") { type = NavType.IntType })
            ) { backStackEntry ->
                val locationId = backStackEntry.arguments?.getInt("locationId") ?: 0
                LocationDetailScreen(
                    locationId = locationId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(AGMapScreen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onRequestSignIn = { showAuthSheet = true },
                    onOpenBleProvisioning = {
                        if (FeatureFlags.WIFI_PROVISIONING_VIA_BLE_ENABLED) {
                            navController.navigate(AGMapScreen.WifiBleProvisioning.route)
                        }
                    }
                )
            }

            composable(AGMapScreen.Community.route) {
                CommunityScreen()
            }
            composable(AGMapScreen.WifiBleProvisioning.route) {
                if (FeatureFlags.WIFI_PROVISIONING_VIA_BLE_ENABLED) {
                    WifiBleProvisioningScreen(
                        onClose = { navController.popBackStack() },
                        onNavigateToMyMonitors = {
                            navController.navigate(AGMapScreen.MyLocations.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                } else {
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                    }
                }
            }
        }
    }

    if (showAuthSheet) {
        AuthenticationSheet(
            onDismissRequest = { showAuthSheet = false },
            viewModel = authenticationViewModel
        )
    }
}
