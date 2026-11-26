package co.edu.unal.nearbyplaces.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import co.edu.unal.nearbyplaces.R

/** La lista de preferencias (por ahora solo el radio), armada desde `res/xml/preferences.xml`. */
class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}
