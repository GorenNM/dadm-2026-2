package co.edu.unal.softwaredirectory.ui.list

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import co.edu.unal.softwaredirectory.DirectoryApp
import co.edu.unal.softwaredirectory.R
import co.edu.unal.softwaredirectory.data.Classification
import co.edu.unal.softwaredirectory.data.Company
import co.edu.unal.softwaredirectory.databinding.ActivityCompanyListBinding
import co.edu.unal.softwaredirectory.showAboutDialog
import co.edu.unal.softwaredirectory.showThemeDialog
import co.edu.unal.softwaredirectory.ui.form.CompanyFormActivity
import co.edu.unal.softwaredirectory.ui.form.CompanyFormViewModel
import co.edu.unal.softwaredirectory.ui.form.FormOutcome
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** Pantalla inicial: la lista de empresas, con búsqueda por nombre y filtro por clasificación. */
class CompanyListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompanyListBinding

    private val mViewModel: CompanyListViewModel by viewModels {
        CompanyListViewModel.factory((application as DirectoryApp).repository)
    }

    private val mAdapter = CompaniesAdapter { company -> openForm(company.id) }

    // El formulario avisa qué pasó (creada, guardada, eliminada) y acá se muestra el Snackbar
    private val mFormLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult(), ::showOutcome)

    private lateinit var mFilterChips: Map<Classification, Chip>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityCompanyListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setSupportActionBar(binding.toolbar)

        binding.companies.layoutManager = LinearLayoutManager(this)
        binding.companies.adapter = mAdapter

        mFilterChips = mapOf(
            Classification.CONSULTING to binding.chipConsulting,
            Classification.CUSTOM_DEVELOPMENT to binding.chipCustomDevelopment,
            Classification.SOFTWARE_FACTORY to binding.chipSoftwareFactory
        )
        for ((classification, chip) in mFilterChips) {
            chip.setOnCheckedChangeListener { _, checked -> mViewModel.onClassificationChecked(classification, checked) }
        }
        binding.searchInput.doAfterTextChanged { mViewModel.onQueryChanged(it?.toString().orEmpty()) }
        binding.clearFiltersButton.setOnClickListener { mViewModel.clearFilters() }
        binding.addButton.setOnClickListener { openForm(Company.NEW_ID) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mViewModel.uiState.collect { render(it) }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
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

    private fun render(state: CompanyListUiState) {
        mAdapter.submitList(state.companies)

        // El ViewModel manda: solo se toca la vista si no coincide (p. ej. al limpiar los filtros)
        if (binding.searchInput.text?.toString().orEmpty() != state.query) binding.searchInput.setText(state.query)
        for ((classification, chip) in mFilterChips) {
            val checked = classification in state.classifications
            if (chip.isChecked != checked) chip.isChecked = checked
        }

        binding.progress.isVisible = state.loading
        binding.emptyGroup.isVisible = !state.loading && state.companies.isEmpty()
        binding.emptyText.setText(if (state.filtered) R.string.list_no_results else R.string.list_empty)
        binding.clearFiltersButton.isVisible = state.filtered

        binding.toolbar.subtitle = if (state.loading) null else {
            resources.getQuantityString(R.plurals.list_count, state.companies.size, state.companies.size)
        }
    }

    private fun openForm(companyId: Long) {
        val intent = Intent(this, CompanyFormActivity::class.java)
        if (companyId != Company.NEW_ID) intent.putExtra(CompanyFormViewModel.KEY_COMPANY_ID, companyId)
        mFormLauncher.launch(intent)
    }

    private fun showOutcome(result: ActivityResult) {
        val outcome = result.data?.getStringExtra(CompanyFormActivity.EXTRA_OUTCOME)?.let(FormOutcome::valueOf)
        val message = when (outcome) {
            FormOutcome.CREATED -> R.string.list_created
            FormOutcome.UPDATED -> R.string.list_updated
            FormOutcome.DELETED -> R.string.list_deleted
            FormOutcome.CANCELED, null -> return
        }
        Snackbar.make(binding.main, message, Snackbar.LENGTH_SHORT).setAnchorView(binding.addButton).show()
    }
}
