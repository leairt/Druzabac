package com.example.druzabac.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit

object ThemeManager {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_DARK_THEME = "dark_theme"

    private var prefs: SharedPreferences? = null

    // Observable state for the theme
    var isDarkTheme = mutableStateOf(false)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isDarkTheme.value = prefs?.getBoolean(KEY_DARK_THEME, false) ?: false
    }

    fun toggleTheme() {
        isDarkTheme.value = !isDarkTheme.value
        prefs?.edit { putBoolean(KEY_DARK_THEME, isDarkTheme.value) }
    }

    fun setDarkTheme(dark: Boolean) {
        isDarkTheme.value = dark
        prefs?.edit { putBoolean(KEY_DARK_THEME, dark) }
    }
}

