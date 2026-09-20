package ir.metra.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.notification.ReminderNotifier
import ir.metra.app.core.notification.ReminderScheduler
import ir.metra.app.data.preferences.ThemeMode
import ir.metra.app.data.preferences.UserPreferences
import ir.metra.app.data.preferences.UserPreferencesRepository
import ir.metra.app.feature.onboarding.OnboardingScreen
import ir.metra.app.ui.navigation.MetraApp
import ir.metra.app.ui.navigation.WorkEditorArgs
import ir.metra.app.ui.theme.MetraTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The single activity.
 *
 * Responsibilities are kept deliberately small: choose the theme, enforce the
 * optional app lock, route the onboarding flow, and host the navigation graph.
 * Everything else lives in feature screens and ViewModels.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var preferencesRepository: UserPreferencesRepository

    @Inject lateinit var reminderScheduler: ReminderScheduler

    @Inject lateinit var reminderNotifier: ReminderNotifier

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        ensureReminderChannel()

        val requestedWorkEditor =
            intent?.getBooleanExtra(EXTRA_OPEN_WORK_EDITOR, false) ?: false

        setContent {
            val preferences by preferencesRepository.preferences
                .collectAsStateWithLifecycle(initialValue = UserPreferences.DEFAULT)
            val darkTheme = when (preferences.themeMode) {
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            MetraTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MetraRoot(
                        activity = this@MainActivity,
                        preferences = preferences,
                        requestedWorkEditor = requestedWorkEditor,
                    )
                }
            }
        }

    }

    private fun ensureReminderChannel() {
        lifecycleScope.launch {
            val prefs = preferencesRepository.preferences.first()
            if (prefs.reminderEnabled) {
                reminderNotifier.ensureChannel()
                reminderScheduler.schedule(prefs.reminderMinuteOfDay)
            }
        }
    }

    companion object {
        /** Set by the reminder notification to land directly on the editor. */
        const val EXTRA_OPEN_WORK_EDITOR = "ir.metra.app.extra.OPEN_WORK_EDITOR"

        fun openWorkEditor(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_WORK_EDITOR, true)
            }
    }
}

/**
 * Chooses between onboarding, the app lock and the main app.
 *
 * The order matters: onboarding runs first (nothing to lock yet), then the lock
 * gate, then the app itself.
 */
@Composable
private fun MetraRoot(
    activity: FragmentActivity,
    preferences: UserPreferences,
    requestedWorkEditor: Boolean,
) {
    val navController = rememberNavController()
    if (!preferences.onboardingCompleted) {
        OnboardingScreen()
        return
    }
    // Consumed once so recomposition cannot re-trigger the navigation.
    var consumedShortcut by remember { mutableStateOf(false) }
    LaunchedEffect(requestedWorkEditor) {
        if (requestedWorkEditor && !consumedShortcut) {
            consumedShortcut = true
            navController.navigate(WorkEditorArgs(todayShortcut = true))
        }
    }
    MetraApp(
        navController = navController,
        onAddWorkday = { navController.navigate(WorkEditorArgs(todayShortcut = true)) },
    )
}
