package co.edu.unal.nearbyplaces.ui.map

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import co.edu.unal.nearbyplaces.BuildConfig
import co.edu.unal.nearbyplaces.NearbyPlacesApp
import co.edu.unal.nearbyplaces.R
import co.edu.unal.nearbyplaces.data.GeoMath
import co.edu.unal.nearbyplaces.data.GeoPoint
import co.edu.unal.nearbyplaces.data.Poi
import co.edu.unal.nearbyplaces.data.PoiCategory
import co.edu.unal.nearbyplaces.databinding.ActivityMapBinding
import co.edu.unal.nearbyplaces.databinding.ItemCategoryChipBinding
import co.edu.unal.nearbyplaces.showAboutDialog
import co.edu.unal.nearbyplaces.showThemeDialog
import co.edu.unal.nearbyplaces.ui.settings.SettingsActivity
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow
import java.io.File

/** Pantalla única: el mapa de OpenStreetMap con los lugares cercanos, las categorías arriba y el estado abajo. */
class MapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapBinding

    private val mViewModel: MapViewModel by viewModels {
        MapViewModel.factory(application as NearbyPlacesApp)
    }

    private lateinit var mMarkerFactory: PoiMarkerFactory
    private lateinit var mCategoryChips: Map<PoiCategory, Chip>

    private val mPoiOverlay = FolderOverlay()
    private var mCenterMarker: Marker? = null

    // Lo último que se dibujó: solo se rehace el mapa si el estado trae algo distinto (render corre con cada cambio)
    private var mShownPois: List<Poi>? = null
    private var mShownCenter: GeoPoint? = null
    private var mFramed: Pair<GeoPoint, Int>? = null

    // Qué hace el botón del mensaje de estado; lo fija render según el estado
    private var mStatusAction: (() -> Unit)? = null

    private val mPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        mViewModel.onPermissionState(hasLocationPermission())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureOsmdroid()
        enableEdgeToEdge()

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setSupportActionBar(binding.toolbar)

        setUpMap()
        setUpChips()
        binding.myLocationButton.setOnClickListener { mViewModel.onMyLocationClicked(hasLocationPermission()) }
        binding.statusAction.setOnClickListener { mStatusAction?.invoke() }

        mViewModel.start()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mViewModel.uiState.collect { render(it) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Se vuelve de los ajustes de la app (permiso) o de los de la pantalla (radio): el ViewModel decide si hay algo que rehacer
        mViewModel.onPermissionState(hasLocationPermission())
        mViewModel.onRadiusMaybeChanged()
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        binding.map.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        binding.map.onDetach()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_map, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.choose_theme -> {
                showThemeDialog()
                true
            }
            R.id.about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * osmdroid: el User-Agent es obligatorio (la política de OpenStreetMap bloquea el de por defecto) y el caché de
     * tiles va a carpetas propias de la app, que no piden permiso de almacenamiento.
     */
    private fun configureOsmdroid() {
        val config = Configuration.getInstance()
        config.load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
        config.userAgentValue = BuildConfig.APPLICATION_ID
        config.osmdroidBasePath = File(filesDir, "osmdroid")
        config.osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
    }

    private fun setUpMap() {
        val map = binding.map
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        // Sin botones +/-: se usa pellizco y doble toque
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        map.minZoomLevel = 3.0
        map.controller.setZoom(15.0)
        // Mientras no haya ninguna búsqueda, el mapa muestra la UNAL
        map.controller.setCenter(MapViewModel.UNAL_BOGOTA.toOsm())

        mMarkerFactory = PoiMarkerFactory(this, map)

        // La pulsación larga busca alrededor de ese punto; va debajo de los marcadores para que ellos reciban sus toques
        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: org.osmdroid.util.GeoPoint) = false

            override fun longPressHelper(p: org.osmdroid.util.GeoPoint): Boolean {
                map.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                mViewModel.onMapLongPress(GeoPoint(p.latitude, p.longitude))
                return true
            }
        }))
        map.overlays.add(mPoiOverlay)
    }

    private fun setUpChips() {
        val selected = mViewModel.uiState.value.categories
        mCategoryChips = PoiCategory.entries.associateWith { category ->
            val chip = ItemCategoryChipBinding.inflate(layoutInflater, binding.categoryChips, false).root
            chip.setText(category.labelRes)
            chip.chipIcon = ResourcesCompat.getDrawable(resources, R.drawable.ic_pin, theme)
            chip.chipIconTint = ColorStateList.valueOf(category.color)
            chip.isChecked = category in selected
            chip.setOnCheckedChangeListener { _, checked -> mViewModel.onCategoryToggled(category, checked) }
            binding.categoryChips.addView(chip)
            chip
        }
    }

    private fun render(state: MapUiState) {
        // El ViewModel manda: solo se toca el chip si no coincide
        for ((category, chip) in mCategoryChips) {
            val checked = category in state.categories
            if (chip.isChecked != checked) chip.isChecked = checked
        }

        val center = state.center
        binding.toolbar.subtitle = getString(
            R.string.map_toolbar_subtitle,
            state.address ?: getString(R.string.map_coordinates, center.latitude, center.longitude),
            state.radiusKm
        )

        binding.progress.isVisible = state.status == MapStatus.Locating || state.status == MapStatus.LoadingPois
        renderStatus(state)
        renderMap(state)

        if (state.shouldRequestPermission) promptForPermission()
    }

    private fun renderStatus(state: MapUiState) {
        when (val status = state.status) {
            MapStatus.Idle -> showStatus(null)
            MapStatus.PermissionNeeded -> showStatus(
                getString(R.string.status_permission_needed),
                if (mustOpenSettings(state)) R.string.status_permission_settings else R.string.status_permission_allow,
                ::promptForPermission
            )
            MapStatus.Locating -> showStatus(getString(R.string.status_locating))
            MapStatus.LocationOff -> showStatus(
                getString(R.string.status_location_off), R.string.status_location_settings, ::openLocationSettings
            )
            MapStatus.LocationFailed -> showStatus(getString(R.string.status_location_failed), R.string.action_retry, ::retry)
            MapStatus.LoadingPois -> showStatus(getString(R.string.status_loading))
            MapStatus.NoCategories -> showStatus(getString(R.string.status_no_categories))
            is MapStatus.Found -> showStatus(withHint(foundMessage(status.count, state), state))
            MapStatus.Empty -> showStatus(withHint(getString(R.string.status_empty, state.radiusKm), state))
            MapStatus.NetworkError -> showStatus(getString(R.string.status_network_error), R.string.action_retry, ::retry)
        }
    }

    /** El mensaje de abajo, con su botón si [action] no es null. [text] null lo oculta. */
    private fun showStatus(text: String?, @StringRes actionLabel: Int = 0, action: (() -> Unit)? = null) {
        binding.statusCard.isVisible = text != null
        binding.statusText.text = text
        binding.statusAction.isVisible = action != null
        if (action != null) binding.statusAction.setText(actionLabel)
        mStatusAction = action
    }

    /** Con el centro por defecto (la UNAL al abrir) se agrega la pista de tocar "mi ubicación". */
    private fun withHint(message: String, state: MapUiState) =
        if (state.showDefaultHint) message + " " + getString(R.string.status_default_hint) else message

    private fun foundMessage(count: Int, state: MapUiState): String {
        val found = resources.getQuantityString(R.plurals.status_found, count, count, state.radiusKm)
        if (state.truncated.isEmpty()) return found
        val names = state.truncated.joinToString(getString(R.string.list_separator)) { getString(it.labelRes) }
        return found + " " + getString(R.string.status_truncated, names)
    }

    /** Dibuja el mapa solo con lo que cambió desde la última vez. */
    private fun renderMap(state: MapUiState) {
        val map = binding.map
        val center = state.center

        if (center != mShownCenter) {
            mShownCenter = center
            mCenterMarker?.let { map.overlays.remove(it) }
            mCenterMarker = mMarkerFactory.createCenter(center).also { map.overlays.add(it) }
        }

        if (state.pois !== mShownPois) {
            mShownPois = state.pois
            InfoWindow.closeAllInfoWindowsOn(map)
            mPoiOverlay.items.clear()
            state.pois.forEach { mPoiOverlay.add(mMarkerFactory.create(it)) }
            // El centro encima de los lugares
            mCenterMarker?.let {
                map.overlays.remove(it)
                map.overlays.add(it)
            }
        }

        // Se encuadra el círculo del radio cuando cambia el centro o el radio, no al activar un chip (el usuario puede estar explorando)
        if (mFramed != (center to state.radiusKm)) {
            mFramed = center to state.radiusKm
            val bounds = GeoMath.bounds(center, state.radiusKm * 1000.0)
            map.zoomToBoundingBox(
                BoundingBox(bounds.north, bounds.east, bounds.south, bounds.west),
                true,
                resources.getDimensionPixelSize(R.dimen.frame_padding)
            )
        }
        map.invalidate()
    }

    // --- Permiso de ubicación ---

    private fun hasLocationPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun shouldShowRationale(): Boolean =
        ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION) ||
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)

    /**
     * Ya se pidió, el usuario dijo que no y el sistema no va a volver a mostrar el diálogo (no hay rationale):
     * lo único que sirve es mandarlo a los ajustes de la app.
     */
    private fun mustOpenSettings(state: MapUiState) = state.permissionAsked && !shouldShowRationale()

    /** Pide el permiso: con una explicación antes si el usuario ya lo negó, o a los ajustes si el sistema ya no pregunta. */
    private fun promptForPermission() {
        val askedBefore = mViewModel.uiState.value.permissionAsked
        mViewModel.onPermissionPromptHandled()
        when {
            shouldShowRationale() -> AlertDialog.Builder(this)
                .setTitle(R.string.rationale_title)
                .setMessage(R.string.rationale_message)
                .setPositiveButton(R.string.rationale_continue) { _, _ -> requestLocationPermission() }
                .setNegativeButton(R.string.rationale_not_now, null)
                .show()
            askedBefore -> openAppSettings()
            else -> requestLocationPermission()
        }
    }

    private fun requestLocationPermission() {
        mPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Sin pantalla de ajustes de la app no hay nada más que hacer: queda el aviso
        }
    }

    private fun openLocationSettings() {
        try {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            // Igual que arriba
        }
    }

    private fun retry() = mViewModel.onRetry(hasLocationPermission())

    private fun GeoPoint.toOsm() = org.osmdroid.util.GeoPoint(latitude, longitude)
}
