package co.edu.unal.nearbyplaces

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import co.edu.unal.nearbyplaces.databinding.AboutDialogBinding

/** Diálogos del menú ⋮: tema y Acerca de. */

/** Claro/Oscuro/Según el sistema; el actual queda preseleccionado. Cambiar recrea la Activity sola. */
fun AppCompatActivity.showThemeDialog() {
    val options = arrayOf(
        getString(R.string.theme_light),
        getString(R.string.theme_dark),
        getString(R.string.theme_system)
    )
    val nightModes = intArrayOf(
        AppCompatDelegate.MODE_NIGHT_NO,
        AppCompatDelegate.MODE_NIGHT_YES,
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    )
    val selected = nightModes.indexOf(AppCompatDelegate.getDefaultNightMode()).coerceAtLeast(0)

    AlertDialog.Builder(this)
        .setTitle(R.string.theme_choose)
        .setSingleChoiceItems(options, selected) { dialog, item ->
            dialog.dismiss()
            val mode = nightModes[item]
            ThemePreference.saveNightMode(applicationContext, mode)
            AppCompatDelegate.setDefaultNightMode(mode)
        }
        .show()
}

/** Extra Challenge del reto 04: diálogo custom con about_dialog.xml inflado a mano. */
fun AppCompatActivity.showAboutDialog() {
    val about = AboutDialogBinding.inflate(layoutInflater)
    about.aboutVersion.text = getString(R.string.about_version, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
    AlertDialog.Builder(this)
        .setView(about.root)
        .setPositiveButton(android.R.string.ok, null)
        .show()
}
