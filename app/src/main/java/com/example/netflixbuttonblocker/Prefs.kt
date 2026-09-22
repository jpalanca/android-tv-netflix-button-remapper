package com.example.netflixbuttonblocker

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal val Context.dataStore by preferencesDataStore(name = "settings")

object Prefs {
    internal val KEY_ENABLED = booleanPreferencesKey("enabled")
    internal val KEY_BLOCK_ONLY = booleanPreferencesKey("block_only")
    internal val KEY_SHOW_TOAST = booleanPreferencesKey("show_toast")
    internal val KEY_TARGET_PACKAGE = stringPreferencesKey("target_package")

    fun isEnabledFlow(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { it[KEY_ENABLED] ?: false }

    fun blockOnlyFlow(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { it[KEY_BLOCK_ONLY] ?: true }

    fun showToastFlow(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { it[KEY_SHOW_TOAST] ?: false }

    fun targetPackageFlow(ctx: Context): Flow<String> =
        ctx.dataStore.data.map { it[KEY_TARGET_PACKAGE].orEmpty() }

    suspend fun setEnabled(ctx: Context, v: Boolean) {
        ctx.dataStore.edit { it[KEY_ENABLED] = v }
    }

    suspend fun setBlockOnly(ctx: Context, v: Boolean) {
        ctx.dataStore.edit { it[KEY_BLOCK_ONLY] = v }
    }

    suspend fun setShowToast(ctx: Context, v: Boolean) {
        ctx.dataStore.edit { it[KEY_SHOW_TOAST] = v }
    }

    suspend fun setTargetPackage(ctx: Context, pkg: String) {
        ctx.dataStore.edit { it[KEY_TARGET_PACKAGE] = pkg }
    }
}
