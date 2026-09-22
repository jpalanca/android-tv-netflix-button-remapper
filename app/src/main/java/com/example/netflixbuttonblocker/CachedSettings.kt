package com.example.netflixbuttonblocker

import android.content.Context
import kotlinx.coroutines.flow.first

/** In-memory mirror of DataStore for the synchronous onKeyEvent path. */
object CachedSettings {
    // Optimistic default: after a reboot the process restarts before the
    // async DataStore refresh lands. A stray swallowed press (if the user
    // had disabled blocking) self-corrects on refresh; a stray Netflix
    // launch (if enabled) would defeat the app's purpose. Err toward block.
    @Volatile var enabled: Boolean = true

    /** Package of the currently foreground app (via window-state events). */
    @Volatile var foregroundPackage: String = ""

    suspend fun refresh(context: Context) {
        try {
            enabled = Prefs.isEnabledFlow(context).first()
        } catch (_: Exception) {
        }
    }
}
