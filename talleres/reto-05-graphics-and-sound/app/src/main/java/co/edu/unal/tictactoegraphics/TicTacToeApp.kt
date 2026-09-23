package co.edu.unal.tictactoegraphics

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/** Aplica el tema guardado antes de crear cualquier Activity: evita el parpadeo claro/oscuro al abrir la app. */
class TicTacToeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(ThemePreference.loadNightMode(this))
    }
}
