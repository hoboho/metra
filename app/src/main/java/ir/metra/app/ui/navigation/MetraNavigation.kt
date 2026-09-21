package ir.metra.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ir.metra.app.R
import ir.metra.app.feature.ledger.LedgerScreen
import ir.metra.app.feature.ledger.LedgerEditorScreen
import ir.metra.app.feature.dashboard.DashboardScreen
import ir.metra.app.feature.expenses.ExpenseEditorScreen
import ir.metra.app.feature.projects.ProjectEditorScreen
import ir.metra.app.feature.projects.ProjectsScreen
import ir.metra.app.feature.reports.ReportsScreen
import ir.metra.app.feature.settings.BackupScreen
import ir.metra.app.feature.settings.DatabaseInfoScreen
import ir.metra.app.feature.settings.PaymentRulesScreen
import ir.metra.app.feature.settings.SettingsScreen
import ir.metra.app.feature.statistics.StatisticsScreen
import ir.metra.app.feature.statistics.YearlyOverviewScreen
import ir.metra.app.feature.work.WorkEditorScreen
import ir.metra.app.feature.work.WorkLogScreen
import kotlinx.serialization.Serializable

// ------------------------------------------------------------------- routes
// Type-safe destinations: each route is a serializable object/class, so
// arguments are checked at compile time instead of being parsed from strings.

@Serializable
data object DashboardRoute

@Serializable
data object WorkLogRoute

@Serializable
data object ReportsRoute

@Serializable
data object LedgerRoute

@Serializable
data class LedgerEditorArgs(val entryId: Long = 0L)

@Serializable
data object StatisticsRoute

@Serializable
data object SettingsRoute

@Serializable
data object ProjectsRoute

/** Project editor; `projectId == 0` means "create a new project". */
@Serializable
data class ProjectEditorArgs(val projectId: Long = 0L)

@Serializable
data class WorkEditorArgs(
    val workRecordId: Long = 0L,
    val epochDay: Long? = null,
    /** True when opened from the "log today" shortcut. */
    val todayShortcut: Boolean = false,
)

@Serializable
data class ExpenseEditorArgs(val workRecordId: Long, val expenseId: Long = 0L)

@Serializable
data object PaymentRulesRoute

@Serializable
data object DatabaseInfoRoute

@Serializable
data object BackupRoute

@Serializable
data object YearlyOverviewRoute

/** Bottom navigation items. */
enum class TopLevelDestination(
    val route: Any,
    val labelRes: Int,
    val icon: ImageVector,
    val contentDescriptionRes: Int,
) {
    Dashboard(DashboardRoute, R.string.nav_dashboard, Icons.Filled.Home, R.string.nav_dashboard),
    WorkLog(WorkLogRoute, R.string.nav_worklog, Icons.Filled.ListAlt, R.string.nav_worklog),
    Reports(ReportsRoute, R.string.nav_reports, Icons.Filled.InsertDriveFile, R.string.nav_reports),
    Ledger(LedgerRoute, R.string.nav_ledger, Icons.Filled.Receipt, R.string.nav_ledger),
    Statistics(StatisticsRoute, R.string.nav_statistics, Icons.Filled.BarChart, R.string.nav_statistics),
    Settings(SettingsRoute, R.string.nav_settings, Icons.Filled.Settings, R.string.nav_settings),
}

@Composable
fun MetraNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    onNavigateToTopLevel: (TopLevelDestination) -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = DashboardRoute,
        modifier = modifier,
    ) {
        composable<DashboardRoute> {
            DashboardScreen(
                onOpenWorkEditor = { navController.navigate(WorkEditorArgs(todayShortcut = true)) },
                onOpenWorkLog = { onNavigateToTopLevel(TopLevelDestination.WorkLog) },
                onOpenReports = { onNavigateToTopLevel(TopLevelDestination.Reports) },
                onOpenStatistics = { onNavigateToTopLevel(TopLevelDestination.Statistics) },
            )
        }
        composable<WorkLogRoute> {
            WorkLogScreen(
                onOpenRecord = { id -> navController.navigate(WorkEditorArgs(workRecordId = id)) },
                onOpenProjects = { navController.navigate(ProjectsRoute) },
            )
        }
        composable<ReportsRoute> {
            ReportsScreen(onOpenProjects = { navController.navigate(ProjectsRoute) })
        }
        composable<LedgerRoute> {
            LedgerScreen(
                onAddEntry = { navController.navigate(LedgerEditorArgs()) },
                onEditEntry = { id -> navController.navigate(LedgerEditorArgs(entryId = id)) },
            )
        }
        composable<LedgerEditorArgs> {
            LedgerEditorScreen(onDone = { navController.popBackStack() })
        }
        composable<StatisticsRoute> {
            StatisticsScreen(
                onOpenYearlyOverview = { navController.navigate(YearlyOverviewRoute) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onOpenProjects = { navController.navigate(ProjectsRoute) },
                onOpenPaymentRules = { navController.navigate(PaymentRulesRoute) },
                onOpenDatabaseInfo = { navController.navigate(DatabaseInfoRoute) },
                onOpenBackup = { navController.navigate(BackupRoute) },
            )
        }
        composable<ProjectsRoute> {
            ProjectsScreen(
                onEditProject = { id -> navController.navigate(ProjectEditorArgs(projectId = id)) },
                onNewProject = { navController.navigate(ProjectEditorArgs(projectId = 0L)) },
            )
        }
        composable<ProjectEditorArgs> { entry ->
            val args = entry.toRoute<ProjectEditorArgs>()
            ProjectEditorScreen(projectId = args.projectId, onDone = { navController.popBackStack() })
        }
        composable<WorkEditorArgs> { entry ->
            val args = entry.toRoute<WorkEditorArgs>()
            WorkEditorScreen(
                workRecordId = args.workRecordId,
                epochDay = args.epochDay,
                todayShortcut = args.todayShortcut,
                onDone = { navController.popBackStack() },
                onOpenExpenseEditor = { recordId, expenseId ->
                    navController.navigate(ExpenseEditorArgs(workRecordId = recordId, expenseId = expenseId))
                },
                onOpenProjects = { navController.navigate(ProjectsRoute) },
            )
        }
        composable<ExpenseEditorArgs> { entry ->
            val args = entry.toRoute<ExpenseEditorArgs>()
            ExpenseEditorScreen(
                workRecordId = args.workRecordId,
                expenseId = args.expenseId,
                onDone = { navController.popBackStack() },
            )
        }
        composable<PaymentRulesRoute> { PaymentRulesScreen() }
        composable<DatabaseInfoRoute> { DatabaseInfoScreen() }
        composable<BackupRoute> { BackupScreen() }
        composable<YearlyOverviewRoute> { YearlyOverviewScreen() }
    }
}

/** Root scaffold: bottom bar plus the ever-present "add workday" action. */
@Composable
fun MetraApp(
    navController: NavHostController = rememberNavController(),
    onAddWorkday: () -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // The FAB is only useful on the screens that list or summarise work.
    val showFab = currentDestination?.let { destination ->
        listOf(
            DashboardRoute::class,
            WorkLogRoute::class,
            StatisticsRoute::class,
            ReportsRoute::class,
        ).any { route -> destination.hasRoute(route) }
    } ?: false

    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentDestination != null && TopLevelDestination.entries.any { top ->
                    currentDestination.hasRoute(top.route::class)
                }
            ) {
                NavigationBar(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    windowInsets = NavigationBarDefaults.windowInsets,
                ) {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination.hasRoute(destination.route::class)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = stringResource(destination.contentDescriptionRes),
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(
                    onClick = onAddWorkday,
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    shape = androidx.compose.material3.FloatingActionButtonDefaults.shape,
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.dashboard_quick_add),
                    )
                }
            }
        },
    ) { innerPadding ->
        MetraNavHost(
            navController = navController,
            onNavigateToTopLevel = { destination ->
                navController.navigate(destination.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier.padding(innerPadding),
        )
    }
}
