package com.snapstreakrecoverer.ssr.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeSelection {
    LIGHT, DARK, SYSTEM
}

class ThemeManager(private val context: Context) {
    private val THEME_KEY = stringPreferencesKey("theme_selection")

    val themeSelection: Flow<ThemeSelection> = context.dataStore.data
        .map { preferences ->
            val themeName = preferences[THEME_KEY] ?: ThemeSelection.SYSTEM.name
            ThemeSelection.valueOf(themeName)
        }

    suspend fun setThemeSelection(selection: ThemeSelection) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = selection.name
        }
    }
}
