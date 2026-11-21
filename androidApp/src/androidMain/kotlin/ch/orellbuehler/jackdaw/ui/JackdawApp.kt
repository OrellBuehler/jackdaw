package ch.orellbuehler.jackdaw.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ch.orellbuehler.jackdaw.JackdawApplication
import ch.orellbuehler.jackdaw.R
import ch.orellbuehler.jackdaw.store.ThemePreference
import ch.orellbuehler.jackdaw.ui.addpack.AddPackScreen
import ch.orellbuehler.jackdaw.ui.addpack.ImportEvent
import ch.orellbuehler.jackdaw.ui.addpack.ImportSheetHost
import ch.orellbuehler.jackdaw.ui.addpack.ImportViewModel
import ch.orellbuehler.jackdaw.ui.addpack.messageRes
import ch.orellbuehler.jackdaw.ui.components.LocalBottomBarInset
import ch.orellbuehler.jackdaw.ui.library.LibraryScreen
import ch.orellbuehler.jackdaw.ui.library.LibraryViewModel
import ch.orellbuehler.jackdaw.ui.nav.AddPackRoute
import ch.orellbuehler.jackdaw.ui.nav.ItemsRoute
import ch.orellbuehler.jackdaw.ui.nav.LibraryRoute
import ch.orellbuehler.jackdaw.ui.nav.OnboardingRoute
import ch.orellbuehler.jackdaw.ui.nav.PackRoute
import ch.orellbuehler.jackdaw.ui.nav.SettingsRoute
import ch.orellbuehler.jackdaw.ui.nav.StatsRoute
import ch.orellbuehler.jackdaw.ui.nav.StudyRoute
import ch.orellbuehler.jackdaw.ui.pack.ItemsScreen
import ch.orellbuehler.jackdaw.ui.pack.ItemsViewModel
import ch.orellbuehler.jackdaw.ui.pack.PackDetailScreen
import ch.orellbuehler.jackdaw.ui.pack.PackDetailViewModel
import ch.orellbuehler.jackdaw.ui.settings.AppViewModel
import ch.orellbuehler.jackdaw.ui.settings.OnboardingScreen
import ch.orellbuehler.jackdaw.ui.settings.SettingsScreen
import ch.orellbuehler.jackdaw.ui.settings.SettingsViewModel
import ch.orellbuehler.jackdaw.ui.stats.StatsScreen
import ch.orellbuehler.jackdaw.ui.stats.StatsViewModel
import ch.orellbuehler.jackdaw.ui.study.StudyScreen
import ch.orellbuehler.jackdaw.ui.study.StudyViewModel
import ch.orellbuehler.jackdaw.ui.theme.JackdawTheme
import ch.orellbuehler.jackdaw.ui.theme.isDark
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

private data class TopLevel(
    val route: Any,
    val label: Int,
    val selected: ImageVector,
    val unselected: ImageVector,
)

private val topLevels =
    listOf(
        TopLevel(LibraryRoute, R.string.nav_library, Icons.Rounded.LibraryBooks, Icons.Outlined.LibraryBooks),
        TopLevel(StatsRoute, R.string.nav_stats, Icons.Rounded.BarChart, Icons.Outlined.BarChart),
        TopLevel(SettingsRoute, R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings),
    )

private fun NavBackStackEntry?.isTopLevel(): Boolean {
    val destination = this?.destination ?: return false
    return topLevels.any { destination.hasRoute(it.route::class) }
}

@Composable
fun JackdawApp(
    appViewModel: AppViewModel,
    importViewModel: ImportViewModel,
) {
    val settings by appViewModel.settings.collectAsStateWithLifecycle()
    JackdawTheme(settings?.theme ?: ThemePreference.SYSTEM) {
        SystemBarsEffect()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val loaded = settings
            if (loaded != null) {
                AppNavigation(
                    startOnboarding = !loaded.onboardingSeen,
                    appViewModel = appViewModel,
                    importViewModel = importViewModel,
                )
            }
        }
    }
}

@Composable
private fun SystemBarsEffect() {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val dark = MaterialTheme.isDark
    val scrim =
        MaterialTheme.colorScheme.background
            .copy(alpha = 0.9f)
            .toArgb()
    DisposableEffect(dark) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(scrim, scrim) { dark },
        )
        onDispose { }
    }
}

@Composable
private fun AppNavigation(
    startOnboarding: Boolean,
    appViewModel: AppViewModel,
    importViewModel: ImportViewModel,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val app = context.applicationContext as JackdawApplication
    val sdk = app.sdk
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val showBar = backStack.isTopLevel()
    val snackbar = remember { SnackbarHostState() }
    val barInset = 80.dp + with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }

    val openText = stringResource(R.string.action_open)
    LaunchedEffect(Unit) {
        importViewModel.events.collect { event ->
            when (event) {
                is ImportEvent.Imported -> {
                    val result = event.result
                    val route = PackRoute(result.packId)
                    if (event.fromSample) {
                        val outcome =
                            snackbar.showSnackbar(
                                message =
                                    resources.getString(
                                        if (result.isUpdate) R.string.snackbar_updated else R.string.snackbar_added,
                                        result.title,
                                    ),
                                actionLabel = openText,
                                duration = SnackbarDuration.Short,
                            )
                        if (outcome == SnackbarResult.ActionPerformed) nav.navigate(route)
                    } else {
                        val onAddScreen = nav.currentBackStackEntry?.destination?.hasRoute<AddPackRoute>() == true
                        if (onAddScreen) nav.popBackStack()
                        nav.navigate(route)
                        snackbar.showSnackbar(
                            resources.getString(if (result.isUpdate) R.string.snackbar_updated else R.string.snackbar_added, result.title),
                        )
                    }
                }
                is ImportEvent.SampleFailed -> snackbar.showSnackbar(resources.getString(event.problem.messageRes()))
            }
        }
    }

    CompositionLocalProvider(LocalBottomBarInset provides barInset) {
        Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = nav,
                startDestination = if (startOnboarding) OnboardingRoute else LibraryRoute,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { enterFor(initialState, targetState) },
                exitTransition = { exitFor(initialState, targetState) },
                popEnterTransition = { popEnterFor(initialState, targetState) },
                popExitTransition = { popExitFor(initialState, targetState) },
            ) {
                composable<OnboardingRoute> {
                    OnboardingScreen(
                        importViewModel = importViewModel,
                        onFinish = {
                            appViewModel.finishOnboarding()
                            nav.navigate(LibraryRoute) { popUpTo(OnboardingRoute) { inclusive = true } }
                        },
                    )
                }
                composable<LibraryRoute> {
                    val vm = viewModel { LibraryViewModel(sdk) }
                    LibraryScreen(
                        viewModel = vm,
                        importViewModel = importViewModel,
                        onOpenPack = { nav.navigate(PackRoute(it)) },
                        onStudy = { nav.navigate(StudyRoute(it)) },
                        onAddPack = { nav.navigate(AddPackRoute) },
                    )
                }
                composable<StatsRoute> {
                    val firstDay = remember { firstDayOfWeek() }
                    val vm = viewModel { StatsViewModel(sdk, firstDay) }
                    StatsScreen(vm)
                }
                composable<SettingsRoute> {
                    val vm = viewModel { SettingsViewModel(sdk) }
                    SettingsScreen(vm)
                }
                composable<AddPackRoute> {
                    AddPackScreen(importViewModel = importViewModel, onBack = { nav.popBackStack() })
                }
                composable<PackRoute> { entry ->
                    val route = entry.toRoute<PackRoute>()
                    val vm = viewModel(key = "pack-${route.packId}") { PackDetailViewModel(sdk, route.packId) }
                    PackDetailScreen(
                        viewModel = vm,
                        onBack = { nav.popBackStack() },
                        onStudy = { mode, sectionId, filter ->
                            nav.navigate(StudyRoute(route.packId, mode.name, sectionId, filter.name))
                        },
                        onBrowse = { nav.navigate(ItemsRoute(route.packId)) },
                    )
                }
                composable<ItemsRoute> { entry ->
                    val route = entry.toRoute<ItemsRoute>()
                    val vm = viewModel(key = "items-${route.packId}") { ItemsViewModel(sdk, route.packId) }
                    ItemsScreen(vm, onBack = { nav.popBackStack() })
                }
                composable<StudyRoute> { entry ->
                    val route = entry.toRoute<StudyRoute>()
                    val vm = viewModel { StudyViewModel(sdk, route.packId, route.toConfig()) }
                    StudyScreen(vm, onExit = { nav.popBackStack() })
                }
            }
            AnimatedVisibility(
                visible = showBar,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(250)) { it } + fadeIn(tween(250)),
                exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
            ) {
                BottomBar(nav, backStack)
            }
            SnackbarHost(
                snackbar,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (showBar) barInset else 0.dp),
            )
            ImportSheetHost(importViewModel)
        }
    }
}

@Composable
private fun BottomBar(
    nav: NavController,
    backStack: NavBackStackEntry?,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        topLevels.forEach { item ->
            val selected = backStack?.destination?.hasRoute(item.route::class) == true
            val label = stringResource(item.label)
            NavigationBarItem(
                selected = selected,
                onClick = {
                    nav.navigate(item.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(if (selected) item.selected else item.unselected, contentDescription = null) },
                label = { Text(label, maxLines = 1) },
            )
        }
    }
}

private fun firstDayOfWeek(): DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

private val fadeSpec = tween<Float>(220)

private fun androidx.compose.animation.AnimatedContentTransitionScope<NavBackStackEntry>.enterFor(
    from: NavBackStackEntry,
    to: NavBackStackEntry,
): EnterTransition =
    if (from.isTopLevel() && to.isTopLevel()) {
        fadeIn(fadeSpec)
    } else {
        slideInHorizontally(tween(320)) { it / 6 } + fadeIn(tween(260))
    }

private fun androidx.compose.animation.AnimatedContentTransitionScope<NavBackStackEntry>.exitFor(
    from: NavBackStackEntry,
    to: NavBackStackEntry,
): ExitTransition =
    if (from.isTopLevel() && to.isTopLevel()) {
        fadeOut(fadeSpec)
    } else {
        slideOutHorizontally(tween(320)) { -it / 8 } + fadeOut(tween(200))
    }

private fun androidx.compose.animation.AnimatedContentTransitionScope<NavBackStackEntry>.popEnterFor(
    from: NavBackStackEntry,
    to: NavBackStackEntry,
): EnterTransition =
    if (from.isTopLevel() && to.isTopLevel()) {
        fadeIn(fadeSpec)
    } else {
        slideInHorizontally(tween(320)) { -it / 8 } + fadeIn(tween(260))
    }

private fun androidx.compose.animation.AnimatedContentTransitionScope<NavBackStackEntry>.popExitFor(
    from: NavBackStackEntry,
    to: NavBackStackEntry,
): ExitTransition =
    if (from.isTopLevel() && to.isTopLevel()) {
        fadeOut(fadeSpec)
    } else {
        slideOutHorizontally(tween(320)) { it / 6 } + fadeOut(tween(200))
    }
