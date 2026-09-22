package com.example.netflixbuttonblocker

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Fallback when this app has focus (e.g. testing in settings screen).
    // Synchronous gate: when disabled the press must fall through to Netflix.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && isNetflixButton(keyCode, event.scanCode) &&
            isFreshDownPress(event.action, event.isCanceled) && event.repeatCount == 0
        ) {
            if (!CachedSettings.enabled) return super.onKeyDown(keyCode, event)
            lifecycleScope.launch {
                val blocking = try {
                    Prefs.isEnabledFlow(this@MainActivity).first()
                } catch (_: Exception) {
                    true
                }
                if (blocking) ButtonHandler.handleNetflixPress(applicationContext)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { CachedSettings.refresh(applicationContext) }
        setContent {
            // SettingsScreen applies its own color scheme; no wrapper needed.
            SettingsScreen(
                    onOpenAccessibilitySettings = { openAccessibilitySettings() },
                    onToggleService = { enabled ->
                        if (enabled) BlockerForegroundService.start(this)
                        else BlockerForegroundService.stop(this)
                    }
                )
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { CachedSettings.refresh(applicationContext) }
    }

    /**
     * Opens the accessibility screen via a fallback chain. On this Shield
     * build the platform intent resolves to a framework stub package that
     * launches to nothing, so stub handlers are skipped; generic
     * ACTION_SETTINGS opens the real Leanback settings.
     */
    private fun openAccessibilitySettings() {
        val candidates = listOf(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).setPackage("com.android.tv.settings"),
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).setPackage("com.android.settings"),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            try {
                val resolved = intent.resolveActivity(packageManager)
                if (!isUsableSettingsHandler(resolved?.packageName)) continue
                startActivity(intent)
                Toast.makeText(
                    this,
                    getString(R.string.access_hint),
                    Toast.LENGTH_LONG
                ).show()
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        Toast.makeText(
            this,
            getString(R.string.access_hint),
            Toast.LENGTH_LONG
        ).show()
    }
}
