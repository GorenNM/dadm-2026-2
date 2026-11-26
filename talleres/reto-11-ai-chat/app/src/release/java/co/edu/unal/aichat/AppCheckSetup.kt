package co.edu.unal.aichat

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release: App Check con Play Integrity (la app tiene que estar publicada en Google Play y el proveedor
 * activado en la consola). La versión de depuración de este objeto está en src/debug.
 */
object AppCheckSetup {
    fun install() {
        Firebase.appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}
