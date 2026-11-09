package com.autumn.douyin.liquidglass.settings

import android.content.Context

/** UI-only theme preference for the module settings app. */
object AppThemePreference {
    const val System = 0
    const val Light = 1
    const val Dark = 2

    private const val PreferencesName = "m3_bar_ui"
    private const val KeyThemeMode = "theme_mode"

    fun read(context: Context): Int = context
        .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        .getInt(KeyThemeMode, System)
        .coerceIn(System, Dark)

    fun write(context: Context, mode: Int) {
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putInt(KeyThemeMode, mode.coerceIn(System, Dark))
            .apply()
    }
}
