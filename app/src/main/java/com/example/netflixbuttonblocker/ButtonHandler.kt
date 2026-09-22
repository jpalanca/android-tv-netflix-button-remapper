package com.example.netflixbuttonblocker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object ButtonHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Called from AccessibilityService.onKeyEvent and MainActivity.onKeyDown.
     * @return true if the event was consumed (blocked / remapped).
     */
    fun handleNetflixPress(context: Context): Boolean {
        scope.launch {
            val prefs = context.applicationContext.dataStoreSnapshot()
            if (!prefs.enabled) return@launch
            // Target already foreground (e.g. watching Plex): dismiss the
            // press entirely — no toast, no relaunch.
            if (!prefs.blockOnly && shouldSuppressRemap(
                    prefs.targetPackage,
                    CachedSettings.foregroundPackage
                )
            ) return@launch
            val appCtx = context.applicationContext
            if (prefs.showToast) {
                val msg = if (prefs.blockOnly || prefs.targetPackage.isEmpty()) {
                    appCtx.getString(R.string.toast_blocked)
                } else {
                    appCtx.getString(R.string.toast_remapped, prefs.targetLabel)
                }
                Toast.makeText(appCtx, msg, Toast.LENGTH_SHORT).show()
            }
            if (!prefs.blockOnly && prefs.targetPackage.isNotEmpty()) {
                launchTarget(context.applicationContext, prefs.targetPackage)
            }
        }
        // The return value only matters to MainActivity's focused-window path;
        // the AccessibilityService consumes based on CachedSettings + the
        // snapshot check above. All defaults are OFF: nothing happens until
        // the user enables blocking and grants accessibility access.
        return true
    }

    private fun launchTarget(context: Context, packageName: String) {
        try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } ?: return
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private data class Snapshot(
        val enabled: Boolean,
        val blockOnly: Boolean,
        val showToast: Boolean,
        val targetPackage: String,
        val targetLabel: String,
    )

    private suspend fun Context.dataStoreSnapshot(): Snapshot {
        val data = dataStore.data.first()
        val enabled = data[Prefs.KEY_ENABLED] ?: false
        val blockOnly = data[Prefs.KEY_BLOCK_ONLY] ?: true
        val showToast = data[Prefs.KEY_SHOW_TOAST] ?: false
        val targetPackage = data[Prefs.KEY_TARGET_PACKAGE].orEmpty()
        val targetLabel = if (targetPackage.isEmpty()) {
            ""
        } else try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    targetPackage,
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(targetPackage, 0)
            }
            packageManager.getApplicationLabel(appInfo)?.toString() ?: targetPackage
        } catch (_: Exception) {
            targetPackage
        }
        return Snapshot(enabled, blockOnly, showToast, targetPackage, targetLabel)
    }
}

data class LaunchableApp(val label: String, val packageName: String)

fun queryLaunchableApps(context: Context): List<LaunchableApp> {
    val pm = context.packageManager
    val leanback = queryCategory(pm, Intent.CATEGORY_LEANBACK_LAUNCHER)
    val launcher = queryCategory(pm, Intent.CATEGORY_LAUNCHER)

    return (leanback + launcher)
        .distinctBy { it.packageName }
        .filter { it.packageName != context.packageName }
        .sortedBy { it.label.lowercase() }
}

private fun queryCategory(pm: PackageManager, category: String): List<LaunchableApp> {
    val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(category) }
    val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(intent, 0)
    }
    return infos.mapNotNull {
        val pkg = it.activityInfo?.packageName ?: return@mapNotNull null
        LaunchableApp(it.loadLabel(pm)?.toString() ?: pkg, pkg)
    }
}
