package ir.metra.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point.
 *
 * Deliberately thin: Hilt owns the object graph and every feature initialises
 * lazily. No analytics, no advertising SDK, no network client — the app has no
 * INTERNET permission and does not need one.
 */
@HiltAndroidApp
class MetraApplication : Application()
