package com.example.netflixbuttonblocker

/**
 * Netflix-button signatures.
 *
 * Pure-JVM file (no android.* imports) so the matchers stay unit-testable.
 * KeyCode 181 is the common Shield mapping; 191, 199 (KEYCODE_BUTTON_12)
 * and scanCode 440 (0x1B8) appear on other models/firmware; 190
 * (KEYCODE_BUTTON_3) is what Chromecast with Google TV reports, and the
 * Google TV Streamer shares that Voice Remote family.
 */
const val KEYCODE_NETFLIX = 181
const val KEYCODE_NETFLIX_ALT = 191
const val KEYCODE_BUTTON_3 = 190 // android.view.KeyEvent.KEYCODE_BUTTON_3
const val KEYCODE_BUTTON_12 = 199 // android.view.KeyEvent.KEYCODE_BUTTON_12
const val SCANCODE_NETFLIX = 440

/** Union matcher: true for any known Netflix-button signature. */
fun isNetflixButton(keyCode: Int, scanCode: Int): Boolean =
    keyCode == KEYCODE_NETFLIX ||
        keyCode == KEYCODE_NETFLIX_ALT ||
        keyCode == KEYCODE_BUTTON_3 ||
        keyCode == KEYCODE_BUTTON_12 ||
        scanCode == SCANCODE_NETFLIX

/**
 * Package that claims settings intents on some Shield builds but renders
 * nothing (verified on-device: it is the sole handler of
 * ACTION_ACCESSIBILITY_SETTINGS and launching it is a silent no-op).
 */
const val SETTINGS_STUB_PACKAGE = "com.google.android.tv.frameworkpackagestubs"

/** True when a resolved settings handler will actually open something. */
fun isUsableSettingsHandler(resolvedPackage: String?): Boolean =
    !resolvedPackage.isNullOrEmpty() && resolvedPackage != SETTINGS_STUB_PACKAGE

/**
 * KeyEvent actions mirrored without android.* import for testability
 * (KeyEvent.ACTION_DOWN = 0, ACTION_UP = 1).
 */
const val KEY_ACTION_DOWN = 0
const val KEY_ACTION_UP = 1

/** True for a fresh, non-canceled initial down-press. Repeats are handled separately. */
fun isFreshDownPress(action: Int, isCanceled: Boolean): Boolean =
    action == KEY_ACTION_DOWN && !isCanceled

/**
 * Visibility rules for the settings screen, kept as pure functions so the
 * granted/disabled matrix is unit-testable (the composables must call
 * exactly these — see NetflixButtonMatcherTest#settingsVisibility).
 */
fun isAccessRowVisible(accessibilityOn: Boolean): Boolean = !accessibilityOn

fun isDisclosureVisible(showDisclosure: Boolean, accessibilityOn: Boolean): Boolean =
    showDisclosure && !accessibilityOn

fun isTargetCardVisible(remapOn: Boolean, accessibilityOn: Boolean, blockEnabled: Boolean): Boolean =
    remapOn && accessibilityOn && blockEnabled

/**
 * A toggle may only be switched ON while accessibility access is granted;
 * switching OFF is always allowed. When access is off, rows render locked
 * (dimmed) instead of silently holding stale ON values.
 */
fun isToggleChangeAllowed(accessibilityOn: Boolean, newValue: Boolean): Boolean =
    !newValue || accessibilityOn

/**
 * Remap and toast are sub-features of the block pipeline: with blocking
 * OFF, presses pass straight through to Netflix, so switching either of
 * them ON while blocking is OFF is a contradiction. Same access rule as
 * above, plus the master toggle must be ON.
 */
fun isDependentToggleChangeAllowed(
    accessibilityOn: Boolean,
    blockEnabled: Boolean,
    newValue: Boolean,
): Boolean = !newValue || (accessibilityOn && blockEnabled)

/**
 * True when a remap launch must be suppressed: the target app is already in
 * the foreground, so relaunching would yank the user out of a playback
 * session. The press is still consumed (Netflix must not open).
 */
fun shouldSuppressRemap(targetPackage: String, foregroundPackage: String): Boolean =
    targetPackage.isNotEmpty() && targetPackage == foregroundPackage
