package co.edu.unal.tourismregistry.ui.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import co.edu.unal.tourismregistry.R
import co.edu.unal.tourismregistry.TourismApp
import co.edu.unal.tourismregistry.data.Provider
import co.edu.unal.tourismregistry.databinding.ActivityDetailBinding
import co.edu.unal.tourismregistry.databinding.ItemDetailRowBinding
import co.edu.unal.tourismregistry.ui.describe
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * El detalle de un prestador. Recibe solo el código RNT y vuelve a consultar el servicio, así que se reconstruye
 * igual aunque Android haya matado el proceso. Extras: llamar (marcador, sin permiso) y ver en el mapa.
 */
class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding

    private val mViewModel: DetailViewModel by viewModels {
        DetailViewModel.factory((application as TourismApp).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.stateButton.setOnClickListener { mViewModel.retry() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mViewModel.uiState.collect { render(it) }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun render(state: DetailUiState) {
        binding.progress.isVisible = state is DetailUiState.Loading
        binding.content.isVisible = state is DetailUiState.Loaded
        binding.stateGroup.isVisible = state is DetailUiState.NotFound || state is DetailUiState.Failed
        binding.stateButton.isVisible = state is DetailUiState.Failed
        when (state) {
            is DetailUiState.Loaded -> showProvider(state.provider)
            DetailUiState.NotFound -> binding.stateText.setText(R.string.detail_not_found)
            is DetailUiState.Failed -> binding.stateText.text = state.error.describe(this)
            DetailUiState.Loading -> Unit
        }
    }

    private fun showProvider(provider: Provider) {
        supportActionBar?.title = getString(R.string.detail_title)
        binding.detailName.text = provider.name
        binding.detailCategory.text = provider.category
        binding.detailCategory.isVisible = provider.category.isNotEmpty()

        // Un teléfono válido para marcar; con "Sin dato" o "0" el botón ni aparece
        val dial = provider.dialNumber
        binding.callButton.isVisible = dial != null
        binding.callButton.setOnClickListener { dial?.let { startOrWarn(Intent(Intent.ACTION_DIAL, "tel:$it".toUri())) } }

        val hasPlace = provider.mapQuery.isNotEmpty()
        binding.mapButton.isVisible = hasPlace
        binding.mapButton.setOnClickListener {
            startOrWarn(Intent(Intent.ACTION_VIEW, "geo:0,0?q=${android.net.Uri.encode(provider.mapQuery)}".toUri()))
        }

        binding.fields.removeAllViews()
        // Los números en 0 (habitaciones de una agencia de viajes) no dicen nada: no se muestran
        val rows = listOf(
            R.string.detail_category to provider.category,
            R.string.detail_sub_category to provider.subCategory,
            R.string.detail_provider_type to provider.providerType,
            R.string.detail_address to provider.address,
            R.string.detail_municipality to provider.municipality,
            R.string.detail_department to provider.department,
            R.string.detail_phone to provider.phone,
            R.string.detail_mobile to provider.mobile,
            R.string.detail_rooms to provider.rooms.positiveText(),
            R.string.detail_beds to provider.beds.positiveText(),
            R.string.detail_employees to provider.employees.positiveText(),
            R.string.detail_code to provider.code
        )
        for ((label, value) in rows) {
            if (value.isEmpty()) continue
            val row = ItemDetailRowBinding.inflate(layoutInflater, binding.fields, false)
            row.rowLabel.setText(label)
            row.rowValue.text = value
            binding.fields.addView(row.root)
        }
    }

    private fun Int?.positiveText(): String = if (this != null && this > 0) toString() else ""

    private fun startOrWarn(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(binding.main, R.string.no_app_for_action, Snackbar.LENGTH_SHORT).show()
        }
    }
}
