package com.example.netflixbuttonblocker

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * No-root global hook for the Shield Netflix button.
 *
 * A plain foreground service CANNOT receive global key events on stock
 * Android — only the focused app sees them. AccessibilityService with
 * flagRequestFilterKeyEvents is the supported no-root path to observe and
 * consume KEYCODE 181 system-wide.
 */
class NetflixButtonAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Track the foreground app so a remap never relaunches the app the
        // user is already watching (e.g. mid-movie in Plex).
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.takeIf { it.isNotEmpty() }?.let {
                CachedSettings.foregroundPackage = it
            }
        }
    }
    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!isNetflixButton(event.keyCode, event.scanCode)) return false
        if (event.repeatCount != 0) return true // swallow repeats (any action)
        if (!isFreshDownPress(event.action, event.isCanceled)) return false

        // Must be synchronous: return true to consume before Netflix launches.
        // Default is blocking enabled; async DataStore check only re-emits
        // side effects (toast / remap). If disabled, we cannot "un-consume",
        // so check a fast cached flag first.
        if (!CachedSettings.enabled) {
            scope.launch {
                // Refresh cache in background; let this press fall through.
                CachedSettings.refresh(applicationContext)
            }
            return false
        }

        ButtonHandler.handleNetflixPress(applicationContext)
        scope.launch { CachedSettings.refresh(applicationContext) }
        return true
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch { CachedSettings.refresh(applicationContext) }
        BlockerForegroundService.start(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
