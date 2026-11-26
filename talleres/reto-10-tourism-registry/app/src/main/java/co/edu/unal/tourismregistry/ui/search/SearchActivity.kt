package co.edu.unal.tourismregistry.ui.search

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.AutoCompleteTextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import co.edu.unal.tourismregistry.R
import co.edu.unal.tourismregistry.TourismApp
import co.edu.unal.tourismregistry.data.Provider
import co.edu.unal.tourismregistry.databinding.ActivitySearchBinding
import co.edu.unal.tourismregistry.showAboutDialog
import co.edu.unal.tourismregistry.showThemeDialog
import co.edu.unal.tourismregistry.ui.describe
import co.edu.unal.tourismregistry.ui.detail.DetailActivity
import co.edu.unal.tourismregistry.ui.detail.DetailViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat

/** Pantalla inicial: parámetros de búsqueda al Registro Nacional de Turismo y la lista paginada de resultados. */
class SearchActivity : AppCompatActivity() {

    companion object {
        private const val STATE_QUERY_VISIBLE = "query_visible"
    }

    private lateinit var binding: ActivitySearchBinding

    private val mViewModel: SearchViewModel by viewModels {
        SearchViewModel.factory((application as TourismApp).repository)
    }

    private val mAdapter = ProvidersAdapter(
        onClick = { provider -> openDetail(provider) },
        onLoadMore = { mViewModel.loadMore() }
    )

    private var mQueryVisible = false

    // Qué opciones tienen puestos los desplegables: solo se recrea el adaptador cuando cambian
    private var mDepartmentOptions: List<String>? = null
    private var mMunicipalityOptions: List<String>? = null
    private var mCategoryOptions: List<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        setSupportActionBar(binding.toolbar)
        mQueryVisible = savedInstanceState?.getBoolean(STATE_QUERY_VISIBLE) ?: false

        binding.providers.layoutManager = LinearLayoutManager(this)
        binding.providers.adapter = mAdapter

        val filters = binding.filters
        filters.nameInput.doAfterTextChanged { mViewModel.onNameChanged(it?.toString().orEmpty()) }
        filters.nameInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search()
                true
            } else {
                false
            }
        }
        filters.departmentInput.setOnItemClickListener { _, _, position, _ ->
            mViewModel.onDepartmentSelected((binding.filters.departmentInput.adapter as DropdownAdapter).valueAt(position))
        }
        filters.municipalityInput.setOnItemClickListener { _, _, position, _ ->
            mViewModel.onMunicipalitySelected((binding.filters.municipalityInput.adapter as DropdownAdapter).valueAt(position))
        }
        filters.categoryInput.setOnItemClickListener { _, _, position, _ ->
            mViewModel.onCategorySelected((binding.filters.categoryInput.adapter as DropdownAdapter).valueAt(position))
        }
        filters.searchButton.setOnClickListener { search() }
        filters.optionsRetryButton.setOnClickListener { mViewModel.retryOptions() }
        filters.queryToggle.setOnClickListener {
            mQueryVisible = !mQueryVisible
            renderQueryPanel(mViewModel.uiState.value.query)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mViewModel.uiState.collect { render(it) }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_QUERY_VISIBLE, mQueryVisible)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_search, menu)
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

    private fun search() {
        // El teclado estorba para leer los resultados
        binding.filters.nameInput.clearFocus()
        WindowCompat.getInsetsController(window, binding.main).hide(WindowInsetsCompat.Type.ime())
        mViewModel.search()
    }

    private fun render(state: SearchUiState) {
        renderFilters(state)
        renderResults(state)
    }

    private fun renderFilters(state: SearchUiState) {
        val filters = binding.filters
        // El ViewModel manda: solo se toca la vista si no coincide (p. ej. al limpiar los filtros)
        if (filters.nameInput.text?.toString().orEmpty() != state.filter.name) filters.nameInput.setText(state.filter.name)

        mDepartmentOptions = bindDropdown(
            filters.departmentInput, mDepartmentOptions, state.departments, state.filter.department
        )
        mMunicipalityOptions = bindDropdown(
            filters.municipalityInput, mMunicipalityOptions, state.municipalities, state.filter.municipality
        )
        mCategoryOptions = bindDropdown(
            filters.categoryInput, mCategoryOptions, state.categories, state.filter.category
        )

        // El municipio depende del departamento: sin departamento no hay municipios que elegir
        val hasDepartment = state.filter.department != null
        filters.municipalityLayout.isEnabled = hasDepartment
        filters.municipalityLayout.helperText = if (hasDepartment) null else getString(R.string.filter_municipality_helper)

        filters.optionsErrorGroup.isVisible = state.optionsError != null
        state.optionsError?.let {
            filters.optionsErrorText.text = getString(R.string.filter_options_error, it.describe(this))
        }

        filters.searchButton.isEnabled = state.phase != SearchPhase.LOADING
        renderQueryPanel(state.query)
    }

    /** Pone las opciones (con "Todos" arriba) si cambiaron y deja visible la elegida. Devuelve las opciones puestas. */
    private fun bindDropdown(
        input: AutoCompleteTextView,
        current: List<String>?,
        options: List<String>,
        selected: String?
    ): List<String> {
        if (current != options) {
            input.setAdapter(DropdownAdapter(this, getString(R.string.filter_all), options))
        }
        val text = selected ?: getString(R.string.filter_all)
        // false: no filtrar el desplegable con este texto
        if (input.text?.toString() != text) input.setText(text, false)
        return options
    }

    private fun renderQueryPanel(query: String?) {
        val filters = binding.filters
        filters.queryToggle.isVisible = query != null
        val show = query != null && mQueryVisible
        filters.queryText.isVisible = show
        if (show) filters.queryText.text = query
        filters.queryToggle.setText(if (show) R.string.query_hide else R.string.query_show)
    }

    private fun renderResults(state: SearchUiState) {
        binding.progress.isVisible = state.phase == SearchPhase.LOADING

        val showList = state.phase == SearchPhase.LOADED && state.providers.isNotEmpty()
        binding.providers.isVisible = showList
        mAdapter.submitList(if (showList) rowsFor(state) else emptyList())

        // Los estados que reemplazan a la lista: instrucción inicial, vacío, error (todos con su botón)
        val message: String?
        val buttonLabel: Int?
        when {
            state.phase == SearchPhase.IDLE -> {
                message = getString(R.string.search_idle)
                buttonLabel = null
            }
            state.phase == SearchPhase.ERROR -> {
                message = state.error?.describe(this)
                buttonLabel = R.string.action_retry
            }
            state.phase == SearchPhase.LOADED && state.providers.isEmpty() -> {
                message = getString(R.string.search_empty)
                buttonLabel = if (state.filter.isEmpty) null else R.string.action_clear_filters
            }
            else -> {
                message = null
                buttonLabel = null
            }
        }
        binding.stateGroup.isVisible = message != null
        binding.stateText.text = message
        binding.stateButton.isVisible = buttonLabel != null
        if (buttonLabel != null) {
            binding.stateButton.setText(buttonLabel)
            binding.stateButton.setOnClickListener {
                if (state.phase == SearchPhase.ERROR) mViewModel.retry() else mViewModel.clearFilters()
            }
        }

        binding.toolbar.subtitle = if (state.phase == SearchPhase.LOADED) {
            resources.getQuantityString(R.plurals.results_count, state.total, NumberFormat.getIntegerInstance().format(state.total))
        } else {
            null
        }
    }

    private fun rowsFor(state: SearchUiState): List<ProviderRow> {
        val rows = state.providers.map { ProviderRow.Item(it) }
        val footer = when {
            state.loadingMore -> FooterState.Loading
            state.loadMoreError != null -> FooterState.Failed(state.loadMoreError)
            state.canLoadMore -> FooterState.More(state.providers.size, state.total)
            else -> null
        }
        return if (footer == null) rows else rows + ProviderRow.Footer(footer)
    }

    private fun openDetail(provider: Provider) {
        startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailViewModel.KEY_CODE, provider.code))
    }
}
