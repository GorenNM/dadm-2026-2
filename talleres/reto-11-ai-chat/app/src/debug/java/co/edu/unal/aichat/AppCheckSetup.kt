package co.edu.unal.aichat

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Build de depuración: App Check con el proveedor de depuración. El SDK genera un token propio de cada
 * instalación y lo imprime en Logcat ("Enter this debug secret into the allow list..."); hay que
 * registrarlo en la consola de Firebase (ver README). Ese token NO va en el código: es un secreto.
 * El release tiene su propia versión de este objeto (src/release) y no incluye esta librería.
 */
object AppCheckSetup {
    fun install() {
        Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
