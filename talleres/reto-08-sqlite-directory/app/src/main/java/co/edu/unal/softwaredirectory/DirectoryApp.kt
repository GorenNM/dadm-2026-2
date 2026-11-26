package co.edu.unal.softwaredirectory

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import co.edu.unal.softwaredirectory.data.CompanyRepository
import co.edu.unal.softwaredirectory.data.DirectoryDbHelper
import co.edu.unal.softwaredirectory.data.SqliteCompanyRepository

class DirectoryApp : Application() {

    /** Un solo helper (y una sola conexión) para toda la app; la base se crea al primer uso, no al abrir. */
    val repository: CompanyRepository by lazy { SqliteCompanyRepository(DirectoryDbHelper(this)) }

    /** Aplica el tema guardado antes de crear cualquier Activity: evita el parpadeo claro/oscuro al abrir la app. */
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(ThemePreference.loadNightMode(this))
    }
}
