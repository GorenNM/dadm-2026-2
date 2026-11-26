package co.edu.unal.aichat

import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Usa la Application real (la del manifest) que arma Robolectric. */
@RunWith(RobolectricTestRunner::class)
class ChatAppTest {

    private val app = RuntimeEnvironment.getApplication() as ChatApp

    @Test
    fun theClientExistsOnlyWhenFirebaseIsConfigured() {
        // Con app/google-services.json el plugin inicializa Firebase y hay cliente; sin él, la app abre sin cliente
        val configured = FirebaseApp.getApps(app).isNotEmpty()
        assertEquals(configured, app.chatClient != null)
    }

    @Test
    fun theClientIsCreatedOnce() {
        assertSame(app.chatClient, app.chatClient)
    }

    @Test
    fun theSavedThemeIsAppliedOnStart() {
        ThemePreference.saveNightMode(app, AppCompatDelegate.MODE_NIGHT_YES)
        app.onCreate()
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())

        ThemePreference.saveNightMode(app, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        app.onCreate()
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.getDefaultNightMode())
    }
}
