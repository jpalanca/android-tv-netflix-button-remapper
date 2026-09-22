package com.example.netflixbuttonblocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the Netflix-button interception decision logic.
 * Runs on CI via :app:testDebugUnitTest and locally via Gradle once the
 * Android SDK platform is installed. No Android framework needed
 * (Constants.kt intentionally has no android.* imports).
 */
class NetflixButtonMatcherTest {

    // --- signature matcher ---

    @Test
    fun primaryKeyCode181_matches() {
        assertTrue(isNetflixButton(181, 0))
    }

    @Test
    fun altKeyCode191_matches() {
        assertTrue(isNetflixButton(191, 0))
    }

    @Test
    fun button3KeyCode190_matches() {
        // Chromecast with Google TV reports its Netflix key as BUTTON_3
        // (Button Mapper shows it as "Button_3"); the Google TV Streamer
        // uses the same Voice Remote family.
        assertTrue(isNetflixButton(KEYCODE_BUTTON_3, 0))
        assertTrue(isNetflixButton(190, 0))
    }

    @Test
    fun button12KeyCode199_matches() {
        assertTrue(isNetflixButton(KEYCODE_BUTTON_12, 0))
        assertTrue(isNetflixButton(199, 0))
    }

    @Test
    fun scanCode440_matchesRegardlessOfKeyCode() {
        assertTrue(isNetflixButton(0, 440))
        assertTrue(isNetflixButton(181, 440))
        assertTrue(isNetflixButton(4, 440))
    }

    @Test
    fun ordinaryKeys_doNotMatch() {
        // HOME=3, BACK=4, DPAD_CENTER=23, VOLUME_UP=24, VOLUME_DOWN=25,
        // A=29, MENU=82, MEDIA_PLAY_PAUSE=85, MEDIA_STOP=86
        listOf(3, 4, 23, 24, 25, 29, 82, 85, 86).forEach { key ->
            assertFalse("keyCode $key must not match", isNetflixButton(key, 0))
        }
    }

    @Test
    fun unrelatedScanCodes_doNotMatch() {
        assertFalse(isNetflixButton(0, 0))
        assertFalse(isNetflixButton(4, 100))
        assertFalse(isNetflixButton(29, 439))
        assertFalse(isNetflixButton(29, 441))
    }

    @Test
    fun constants_haveExpectedWireValues() {
        assertEquals(181, KEYCODE_NETFLIX)
        assertEquals(191, KEYCODE_NETFLIX_ALT)
        assertEquals(190, KEYCODE_BUTTON_3)
        assertEquals(199, KEYCODE_BUTTON_12)
        assertEquals(440, SCANCODE_NETFLIX)
        assertEquals(0, KEY_ACTION_DOWN)
        assertEquals(1, KEY_ACTION_UP)
    }

    // --- press gating ---

    @Test
    fun freshDownPress_isHandled() {
        assertTrue(isFreshDownPress(KEY_ACTION_DOWN, false))
    }

    @Test
    fun keyUp_isNotHandled() {
        assertFalse(isFreshDownPress(KEY_ACTION_UP, false))
    }

    @Test
    fun canceledDown_isNotHandled() {
        assertFalse(isFreshDownPress(KEY_ACTION_DOWN, true))
    }

    @Test
    fun unknownActions_areNotHandled() {
        // ACTION_MULTIPLE = 2
        assertFalse(isFreshDownPress(2, false))
    }

    // --- remap suppression (target already foreground) ---

    @Test
    fun remapSuppressed_whenTargetAlreadyForeground() {
        assertTrue(shouldSuppressRemap("com.plexapp.android", "com.plexapp.android"))
    }

    @Test
    fun remapNotSuppressed_whenDifferentAppForeground() {
        assertFalse(shouldSuppressRemap("com.plexapp.android", "com.netflix.ninja"))
    }

    @Test
    fun remapNotSuppressed_whenNoTargetSet() {
        assertFalse(shouldSuppressRemap("", "com.plexapp.android"))
        assertFalse(shouldSuppressRemap("", ""))
    }

    @Test
    fun remapNotSuppressed_whenNothingForeground() {
        assertFalse(shouldSuppressRemap("com.plexapp.android", ""))
    }

    // --- settings visibility matrix (must match SettingsScreen) ---

    @Test
    fun accessRow_visibleOnlyWhenAccessOff() {
        assertTrue(isAccessRowVisible(false))
        assertFalse(isAccessRowVisible(true))
    }

    @Test
    fun disclosure_visibleOnlyWhenRequestedAndAccessOff() {
        assertTrue(isDisclosureVisible(true, false))
        assertFalse(isDisclosureVisible(true, true))
        assertFalse(isDisclosureVisible(false, false))
        assertFalse(isDisclosureVisible(false, true))
    }

    @Test
    fun targetCard_visibleOnlyWhenRemapOnAndAccessOnAndBlockOn() {
        assertTrue(isTargetCardVisible(true, true, true))
        assertFalse(isTargetCardVisible(true, false, true))
        assertFalse(isTargetCardVisible(false, true, true))
        assertFalse(isTargetCardVisible(true, true, false))
        assertFalse(isTargetCardVisible(false, false, false))
    }

    @Test
    fun grantedState_hidesAccessUiEntirely() {
        // The reported bug: access row must never render when granted.
        val on = true
        assertFalse(isAccessRowVisible(on))
        assertFalse(isDisclosureVisible(true, on))
        assertFalse(isDisclosureVisible(false, on))
    }

    // --- toggle lock: nothing may be switched ON without access ---

    @Test
    fun toggleOn_requiresAccess() {
        assertTrue(isToggleChangeAllowed(true, true))
        assertFalse(isToggleChangeAllowed(false, true))
    }

    @Test
    fun toggleOff_alwaysAllowed() {
        assertTrue(isToggleChangeAllowed(true, false))
        assertTrue(isToggleChangeAllowed(false, false))
    }

    // --- dependent toggles (remap, toast): require block ON as well ---

    @Test
    fun dependentToggleOn_requiresAccessAndBlock() {
        assertTrue(isDependentToggleChangeAllowed(true, true, true))
        assertFalse(isDependentToggleChangeAllowed(false, true, true))
        assertFalse(isDependentToggleChangeAllowed(true, false, true))
        assertFalse(isDependentToggleChangeAllowed(false, false, true))
    }

    @Test
    fun dependentToggleOff_alwaysAllowed() {
        assertTrue(isDependentToggleChangeAllowed(true, true, false))
        assertTrue(isDependentToggleChangeAllowed(false, true, false))
        assertTrue(isDependentToggleChangeAllowed(true, false, false))
        assertTrue(isDependentToggleChangeAllowed(false, false, false))
    }

    // --- settings-handler guard (stub package claims intents it can't open) ---

    @Test
    fun realSettingsHandler_isUsable() {
        assertTrue(isUsableSettingsHandler("com.android.tv.settings"))
        assertTrue(isUsableSettingsHandler("com.android.settings"))
    }

    @Test
    fun stubSettingsHandler_isRejected() {
        assertFalse(isUsableSettingsHandler(SETTINGS_STUB_PACKAGE))
        assertFalse(isUsableSettingsHandler("com.google.android.tv.frameworkpackagestubs"))
    }

    @Test
    fun nullOrEmptyHandler_isRejected() {
        assertFalse(isUsableSettingsHandler(null))
        assertFalse(isUsableSettingsHandler(""))
    }
}
