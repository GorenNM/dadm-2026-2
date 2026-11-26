package co.edu.unal.aichat

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import co.edu.unal.aichat.data.ChatClient
import co.edu.unal.aichat.data.FirebaseChatClient
import com.google.firebase.FirebaseApp

class ChatApp : Application() {

    /**
     * El cliente del modelo, o null si Firebase no está configurado (falta app/google-services.json: el
     * plugin no corrió y FirebaseApp no se inicializó). Así la app abre y lo explica en vez de caerse.
     */
    val chatClient: ChatClient? by lazy {
        if (FirebaseApp.getApps(this).isEmpty()) null else FirebaseChatClient()
    }

    /**
     * Aplica el tema guardado antes de crear cualquier Activity (evita el parpadeo claro/oscuro) e instala
     * App Check. Firebase ya está inicializado: su ContentProvider corre antes de Application.onCreate.
     * App Check va antes de la primera llamada al modelo, que adjunta el token sola.
     */
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(ThemePreference.loadNightMode(this))
        if (FirebaseApp.getApps(this).isNotEmpty()) AppCheckSetup.install()
    }
}
