package co.edu.unal.tourismregistry

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import co.edu.unal.tourismregistry.data.AndroidConnectivityChecker
import co.edu.unal.tourismregistry.data.HttpSodaClient
import co.edu.unal.tourismregistry.data.SodaTourismRepository
import co.edu.unal.tourismregistry.data.TourismRepository

class TourismApp : Application() {

    /** Un solo repositorio para toda la app (no guarda estado: solo sabe armar y enviar consultas). */
    val repository: TourismRepository by lazy {
        SodaTourismRepository(HttpSodaClient(), AndroidConnectivityChecker(this))
    }

    /** Aplica el tema guardado antes de crear cualquier Activity: evita el parpadeo claro/oscuro al abrir la app. */
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(ThemePreference.loadNightMode(this))
    }
}
