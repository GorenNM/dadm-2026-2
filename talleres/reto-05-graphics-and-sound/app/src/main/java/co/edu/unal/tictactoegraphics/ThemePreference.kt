package co.edu.unal.tictactoegraphics

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

/**
 * Preferencia de tema (claro/oscuro/según el sistema), persistida entre reinicios de la app.
 * Se guarda un [AppCompatDelegate.NightMode] directamente, así no hace falta traducirlo.
 */
object ThemePreference {

    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_NIGHT_MODE = "night_mode"

    fun loadNightMode(context: Context): Int =
        prefs(context).getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    fun saveNightMode(context: Context, nightMode: Int) {
        prefs(context).edit { putInt(KEY_NIGHT_MODE, nightMode) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
