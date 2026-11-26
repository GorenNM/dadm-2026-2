package co.edu.unal.softwaredirectory.ui.form

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import co.edu.unal.softwaredirectory.DirectoryApp
import co.edu.unal.softwaredirectory.R
import co.edu.unal.softwaredirectory.data.Classification
import co.edu.unal.softwaredirectory.databinding.ActivityCompanyFormBinding
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Crear, ver y editar una empresa (CRUD del enunciado), con confirmación antes de eliminar.
 * Devuelve a la lista un [FormOutcome] en [EXTRA_OUTCOME].
 */
class CompanyFormActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OUTCOME = "outcome"
    }

    private lateinit var binding: ActivityCompanyFormBinding

    private val mViewModel: CompanyFormViewModel by viewModels {
        CompanyFormViewModel.factory((application as DirectoryApp).repository)
    }

    private lateinit var mChips: Map<Classification, Chip>
    private var mMenu: Menu? = null

    // El diálogo que se está mostrando. El que debe mostrarse lo dice el estado; al rotar se vuelve a abrir
    private var mDialog: AlertDialog? = null
    private var mShownDialog: FormDialog? = null

    // Solo intercepta Back si hay cambios sin guardar; si no, Back cierra normal (con la animación predictiva)
    private val mBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = mViewModel.requestClose()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityCompanyFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ime: con el teclado abierto, el formulario se achica y los últimos campos se pueden desplazar a la vista
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setTitle(if (mViewModel.uiState.value.isNew) R.string.form_title_new else R.string.form_title_edit)
        onBackPressedDispatcher.addCallback(this, mBackCallback)

        binding.nameInput.doAfterTextChanged { mViewModel.onNameChanged(it?.toString().orEmpty()) }
        binding.websiteInput.doAfterTextChanged { mViewModel.onWebsiteChanged(it?.toString().orEmpty()) }
        binding.phoneInput.doAfterTextChanged { mViewModel.onPhoneChanged(it?.toString().orEmpty()) }
        binding.emailInput.doAfterTextChanged { mViewModel.onEmailChanged(it?.toString().orEmpty()) }
        binding.productsInput.doAfterTextChanged { mViewModel.onProductsChanged(it?.toString().orEmpty()) }

        mChips = mapOf(
            Classification.CONSULTING to binding.chipConsulting,
            Classification.CUSTOM_DEVELOPMENT to binding.chipCustomDevelopment,
            Classification.SOFTWARE_FACTORY to binding.chipSoftwareFactory
        )
        for ((classification, chip) in mChips) {
            chip.setOnCheckedChangeListener { _, checked -> mViewModel.onClassificationChecked(classification, checked) }
        }

        // Extras: abrir la web, el marcador (ACTION_DIAL no pide permiso de llamadas) o el correo
        binding.websiteLayout.setEndIconOnClickListener {
            mViewModel.uiState.value.websiteToOpen?.let { startOrWarn(Intent(Intent.ACTION_VIEW, it.toUri())) }
        }
        binding.phoneLayout.setEndIconOnClickListener {
            mViewModel.uiState.value.phoneToCall?.let { startOrWarn(Intent(Intent.ACTION_DIAL, "tel:$it".toUri())) }
        }
        binding.emailLayout.setEndIconOnClickListener {
            mViewModel.uiState.value.emailToWrite?.let { startOrWarn(Intent(Intent.ACTION_SENDTO, "mailto:$it".toUri())) }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { mViewModel.uiState.collect { render(it) } }
                launch { mViewModel.events.collect { onEvent(it) } }
            }
        }
    }

    override fun onDestroy() {
        // Sin esto el diálogo "se filtra" al rotar; el estado lo vuelve a abrir en la Activity nueva
        mDialog?.dismiss()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_form, menu)
        mMenu = menu
        renderMenu(mViewModel.uiState.value)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                mViewModel.requestClose()
                true
            }
            R.id.save -> {
                mViewModel.save()
                true
            }
            R.id.delete -> {
                mViewModel.requestDelete()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun render(state: CompanyFormUiState) {
        // El ViewModel manda, pero solo se toca un campo si no coincide: así no se mueve el cursor al escribir
        val draft = state.draft
        binding.nameInput.syncText(draft.name)
        binding.websiteInput.syncText(draft.website)
        binding.phoneInput.syncText(draft.phone)
        binding.emailInput.syncText(draft.email)
        binding.productsInput.syncText(draft.products)
        for ((classification, chip) in mChips) {
            val checked = classification in draft.classifications
            if (chip.isChecked != checked) chip.isChecked = checked
        }

        val editable = !state.loading && !state.saving
        binding.progress.isVisible = state.loading || state.saving
        listOf(binding.nameLayout, binding.websiteLayout, binding.phoneLayout, binding.emailLayout, binding.productsLayout)
            .forEach { it.isEnabled = editable }
        mChips.values.forEach { it.isEnabled = editable }

        binding.nameLayout.error = errorText(CompanyField.NAME, state.errors[CompanyField.NAME])
        binding.websiteLayout.error = errorText(CompanyField.WEBSITE, state.errors[CompanyField.WEBSITE])
        binding.phoneLayout.error = errorText(CompanyField.PHONE, state.errors[CompanyField.PHONE])
        binding.emailLayout.error = errorText(CompanyField.EMAIL, state.errors[CompanyField.EMAIL])
        binding.classificationsError.isVisible = CompanyField.CLASSIFICATIONS in state.errors

        binding.websiteLayout.isEndIconVisible = state.websiteToOpen != null
        binding.phoneLayout.isEndIconVisible = state.phoneToCall != null
        binding.emailLayout.isEndIconVisible = state.emailToWrite != null

        mBackCallback.isEnabled = state.hasChanges || state.saving
        renderMenu(state)
        renderDialog(state)
    }

    private fun renderMenu(state: CompanyFormUiState) {
        val menu = mMenu ?: return
        val editable = !state.loading && !state.saving
        menu.findItem(R.id.save).isEnabled = editable
        menu.findItem(R.id.delete).apply {
            isVisible = !state.isNew
            isEnabled = editable
        }
    }

    private fun renderDialog(state: CompanyFormUiState) {
        if (state.dialog == mShownDialog) return
        mDialog?.dismiss()
        mShownDialog = state.dialog
        mDialog = when (state.dialog) {
            null -> null
            FormDialog.CONFIRM_DELETE -> AlertDialog.Builder(this)
                .setTitle(R.string.delete_title)
                .setMessage(getString(R.string.delete_message, state.original.name))
                .setPositiveButton(R.string.action_delete) { _, _ -> mViewModel.confirmDelete() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> mViewModel.dismissDialog() }
                .setOnCancelListener { mViewModel.dismissDialog() }
                .show()
            FormDialog.DISCARD_CHANGES -> AlertDialog.Builder(this)
                .setTitle(R.string.discard_title)
                .setMessage(R.string.discard_message)
                .setPositiveButton(R.string.discard_confirm) { _, _ -> mViewModel.confirmDiscard() }
                .setNegativeButton(R.string.keep_editing) { _, _ -> mViewModel.dismissDialog() }
                .setOnCancelListener { mViewModel.dismissDialog() }
                .show()
        }
    }

    private fun onEvent(event: FormEvent) {
        when (event) {
            is FormEvent.Finished -> {
                setResult(RESULT_OK, Intent().putExtra(EXTRA_OUTCOME, event.outcome.name))
                finish()
            }
            FormEvent.NotFound -> {
                Toast.makeText(this, R.string.company_not_found, Toast.LENGTH_SHORT).show()
                finish()
            }
            FormEvent.Failed -> Snackbar.make(binding.main, R.string.save_failed, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun errorText(field: CompanyField, error: FieldError?): String? {
        val message = when (error) {
            null -> return null
            FieldError.REQUIRED -> R.string.error_name_required
            FieldError.TOO_LONG -> R.string.error_name_too_long
            FieldError.DUPLICATE -> R.string.error_name_duplicate
            FieldError.INVALID -> when (field) {
                CompanyField.WEBSITE -> R.string.error_website_invalid
                CompanyField.PHONE -> R.string.error_phone_invalid
                else -> R.string.error_email_invalid
            }
        }
        return getString(message)
    }

    private fun startOrWarn(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(binding.main, R.string.no_app_for_action, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun EditText.syncText(value: String) {
        if (text?.toString().orEmpty() != value) setText(value)
    }
}
