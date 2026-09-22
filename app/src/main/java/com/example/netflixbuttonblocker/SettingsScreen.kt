package com.example.netflixbuttonblocker

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Scheme = darkColorScheme(
    background = Color(0xFF101014),
    surface = Color(0xFF101014),
    surfaceContainer = Color(0xFF1B1B21),
    surfaceContainerHigh = Color(0xFF2B2B33),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFD6D3DC),
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color.White
)

/**
 * Single-layer settings screen: one scrollable Surface, flat sections
 * separated by dividers — no dialogs, no elevated floating cards.
 * Every row is one D-pad focus target with a full-row highlight, per
 * Android TV guidance (focus must be visible at 10-foot distance).
 */
@Composable
fun SettingsScreen(
    onOpenAccessibilitySettings: () -> Unit,
    onToggleService: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val enabled by Prefs.isEnabledFlow(ctx).collectAsState(initial = false)
    val blockOnly by Prefs.blockOnlyFlow(ctx).collectAsState(initial = true)
    val showToast by Prefs.showToastFlow(ctx).collectAsState(initial = false)
    val targetPkg by Prefs.targetPackageFlow(ctx).collectAsState(initial = "")

    val remapOn = !blockOnly

    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var picking by remember { mutableStateOf(false) }
    var accessibilityOn by remember { mutableStateOf(false) }
    // Play policy: prominent in-app disclosure before redirecting to system
    // settings. Shown inline (same layer) whenever access is still off.
    var showDisclosure by remember { mutableStateOf(false) }
    val requestAccess: () -> Unit = {
        if (accessibilityOn) onOpenAccessibilitySettings()
        else showDisclosure = true
    }

    LaunchedEffect(Unit) {
        accessibilityOn = isAccessibilityEnabled(ctx)
        apps = withContext(Dispatchers.IO) { queryLaunchableApps(ctx) }
    }

    // The status line must refresh every time the user returns from the
    // system settings (granting access there doesn't recreate this screen).
    val activity = ctx as? LifecycleOwner
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { accessibilityOn = isAccessibilityEnabled(ctx) }
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(enabled) {
        accessibilityOn = isAccessibilityEnabled(ctx)
    }

    val targetLabel = apps.firstOrNull { it.packageName == targetPkg }?.label
        ?: targetPkg.ifEmpty { stringResource(R.string.target_none) }

    // One rule for every toggle: nothing may be switched ON without access.
    // Stored values are kept (no destructive reset), rows just lock + dim.
    val guardedToggle: (Boolean, () -> Unit) -> Unit = { newValue, apply ->
        if (!isToggleChangeAllowed(accessibilityOn, newValue)) {
            Toast.makeText(
                ctx,
                ctx.getString(R.string.access_required_toast),
                Toast.LENGTH_LONG
            ).show()
        } else apply()
    }

    // Remap and toast only act inside the block pipeline: with blocking OFF
    // presses pass through to Netflix, so enabling either of them first
    // explains itself with the block-first message (access first if off).
    val guardedDependentToggle: (Boolean, () -> Unit) -> Unit = { newValue, apply ->
        if (!isDependentToggleChangeAllowed(accessibilityOn, enabled, newValue)) {
            Toast.makeText(
                ctx,
                ctx.getString(
                    if (!accessibilityOn) R.string.access_required_toast
                    else R.string.block_required_toast
                ),
                Toast.LENGTH_LONG
            ).show()
        } else apply()
    }

    MaterialTheme(colorScheme = Scheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))

                // Required access first — hidden entirely once granted.
                // Compact: single-line content, no leftover subtitle gap.
                if (isAccessRowVisible(accessibilityOn)) {
                FocusableRow(onClick = requestAccess) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (accessibilityOn) stringResource(R.string.access_title_on)
                                else stringResource(R.string.access_title),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        TvButton(
                            text = if (accessibilityOn) stringResource(R.string.access_open)
                            else stringResource(R.string.access_enable),
                            onClick = requestAccess,
                            warning = !accessibilityOn,
                            modifier = Modifier.focusProperties { canFocus = false }
                        )
                    }
                }
                }
                if (isDisclosureVisible(showDisclosure, accessibilityOn)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(16.dp)
                    ) {
                        Text(
                            stringResource(R.string.access_disclosure),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TvButton(
                                text = stringResource(R.string.access_open),
                                onClick = {
                                    showDisclosure = false
                                    onOpenAccessibilitySettings()
                                }
                            )
                            TvOutlinedButton(
                                text = stringResource(R.string.not_now),
                                onClick = { showDisclosure = false }
                            )
                        }
                    }
                }
                if (!accessibilityOn) {
                    Text(
                        stringResource(R.string.access_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))

                SettingRow(
                    title = stringResource(R.string.block_title),
                    subtitle = stringResource(R.string.block_sub),
                    checked = enabled,
                    rowEnabled = accessibilityOn,
                    onChecked = {
                        // Blocking without accessibility access does nothing:
                        // refuse with guidance instead of a dead toggle.
                        guardedToggle(it) {
                            scope.launch {
                                Prefs.setEnabled(ctx, it)
                                CachedSettings.enabled = it
                                onToggleService(it)
                            }
                        }
                    }
                )
                HorizontalDivider()
                SettingRow(
                    title = stringResource(R.string.remap_title),
                    subtitle = stringResource(R.string.remap_sub),
                    checked = remapOn,
                    rowEnabled = accessibilityOn && enabled,
                    onChecked = {
                        guardedDependentToggle(it) {
                            scope.launch { Prefs.setBlockOnly(ctx, !it) }
                            if (it) scope.launch {
                                apps = withContext(Dispatchers.IO) { queryLaunchableApps(ctx) }
                            }
                        }
                    }
                )

                if (isTargetCardVisible(remapOn, accessibilityOn, enabled)) {
                    // Flat inline section (same layer as the menu, no elevation).
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(16.dp)
                    ) {
                        Text(
                            stringResource(R.string.target_title, targetLabel),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            stringResource(R.string.target_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TvButton(
                                text = if (picking) stringResource(R.string.hide_apps)
                                else stringResource(R.string.pick_app),
                                onClick = { picking = !picking }
                            )
                            if (targetPkg.isNotEmpty()) {
                                TvOutlinedButton(
                                    text = stringResource(R.string.clear),
                                    onClick = {
                                        scope.launch { Prefs.setTargetPackage(ctx, "") }
                                    }
                                )
                            }
                        }
                        if (picking) {
                            Spacer(Modifier.height(8.dp))
                            LazyColumn(modifier = Modifier.height(240.dp)) {
                                items(apps, key = { it.packageName }) { app ->
                                    FocusableRow(
                                        onClick = {
                                            scope.launch {
                                                Prefs.setTargetPackage(ctx, app.packageName)
                                            }
                                            picking = false
                                        }
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text(
                                                app.label,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                app.packageName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()
                SettingRow(
                    title = stringResource(R.string.toast_title),
                    subtitle = stringResource(R.string.toast_sub),
                    checked = showToast,
                    rowEnabled = accessibilityOn && enabled,
                    onChecked = {
                        guardedDependentToggle(it) {
                            scope.launch { Prefs.setShowToast(ctx, it) }
                        }
                    }
                )
            }
            Column(
                modifier = Modifier.align(Alignment.End),
                horizontalAlignment = Alignment.End
            ) {
            Text(
                stringResource(R.string.disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            }
            }
        }
    }
}

/**
 * One D-pad focus target with a full-width highlight (background +
 * border), so the focused row is unmistakable at TV distance.
 */
@Composable
private fun FocusableRow(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (focused) MaterialTheme.colorScheme.surfaceContainerHigh
                else Color.Transparent
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) MaterialTheme.colorScheme.primary
                else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    rowEnabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (focused) MaterialTheme.colorScheme.surfaceContainerHigh
                else Color.Transparent
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) MaterialTheme.colorScheme.primary
                else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusRequester(focusRequester)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onChecked(!checked) }
            )
            .padding(vertical = 8.dp, horizontal = 8.dp)
            .graphicsLayer { alpha = if (rowEnabled) 1f else 0.45f },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // A locked row keeps a single focus target; the caller guards onChecked.
        Switch(
            checked = checked && rowEnabled,
            onCheckedChange = null,
            enabled = rowEnabled,
            modifier = Modifier.focusProperties { canFocus = false }
        )
    }
}

@Composable
private fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(24.dp)
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (warning) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primary,
            contentColor = if (warning) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onPrimary
        )
    ) { Text(text) }
}

@Composable
private fun TvOutlinedButton(
    text: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) MaterialTheme.colorScheme.primary
                else Color.Transparent,
                shape = RoundedCornerShape(24.dp)
            )
    ) { Text(text) }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    return enabled.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
}
