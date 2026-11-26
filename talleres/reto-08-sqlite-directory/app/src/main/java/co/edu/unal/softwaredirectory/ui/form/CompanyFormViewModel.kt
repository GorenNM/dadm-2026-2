package co.edu.unal.softwaredirectory.ui.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.unal.softwaredirectory.data.Classification
import co.edu.unal.softwaredirectory.data.Company
import co.edu.unal.softwaredirectory.data.CompanyRepository
import co.edu.unal.softwaredirectory.data.SaveResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Diálogos del formulario. Viven en el estado (no solo en pantalla) para que sobrevivan a la rotación. */
enum class FormDialog { CONFIRM_DELETE, DISCARD_CHANGES }

data class CompanyFormUiState(
    val isNew: Boolean,
    /** Editando: todavía no llegó la empresa de la base. */
    val loading: Boolean,
    val draft: CompanyDraft,
    /** La empresa como está guardada (vacía si es nueva): para saber si hay cambios sin guardar. */
    val original: CompanyDraft = CompanyDraft(),
    val errors: Map<CompanyField, FieldError> = emptyMap(),
    /** Guardando o borrando: no se aceptan más acciones. */
    val saving: Boolean = false,
    val dialog: FormDialog? = null
) {
    val hasChanges: Boolean get() = draft != original

    // Los íconos de abrir la web, llamar y escribir se activan solo si el valor es válido
    val websiteToOpen: String? get() = CompanyValidator.normalizeWebsite(draft.website)?.ifEmpty { null }
    val phoneToCall: String? get() = draft.phone.trim().takeIf { CompanyValidator.isValidPhone(it) }
    val emailToWrite: String? get() = draft.email.trim().takeIf { CompanyValidator.isValidEmail(it) }
}

enum class FormOutcome { CREATED, UPDATED, DELETED, CANCELED }

sealed interface FormEvent {
    /** Cerrar el formulario; la lista muestra un aviso según [outcome]. */
    data class Finished(val outcome: FormOutcome) : FormEvent
    /** La empresa ya no existe (la borraron mientras tanto). */
    data object NotFound : FormEvent
    data object Failed : FormEvent
}

/**
 * Crear, ver y editar una empresa: la misma pantalla. [KEY_COMPANY_ID] llega como extra del Intent
 * (sin él, es una empresa nueva).
 *
 * El borrador vive en el [SavedStateHandle]: sobrevive a la rotación y a la muerte del proceso. Al
 * editar, la empresa se lee de la base una sola vez ([KEY_LOADED]); si se leyera de nuevo al volver
 * de la muerte del proceso, pisaría lo que el usuario había cambiado sin guardar.
 */
class CompanyFormViewModel(
    private val repository: CompanyRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {

    companion object {
        const val KEY_COMPANY_ID = "companyId"
        private const val KEY_LOADED = "loaded"
        private const val KEY_DIALOG = "dialog"
        private const val DRAFT = "draft_"
        private const val ORIGINAL = "original_"

        fun factory(repository: CompanyRepository) = viewModelFactory {
            initializer { CompanyFormViewModel(repository, createSavedStateHandle()) }
        }
    }

    private val mCompanyId: Long = savedState[KEY_COMPANY_ID] ?: Company.NEW_ID
    private val mIsNew = mCompanyId == Company.NEW_ID

    private val _uiState = MutableStateFlow(
        CompanyFormUiState(
            isNew = mIsNew,
            loading = !mIsNew && savedState.get<Boolean>(KEY_LOADED) != true,
            draft = savedState.getDraft(DRAFT) ?: CompanyDraft(),
            original = savedState.getDraft(ORIGINAL) ?: CompanyDraft(),
            dialog = savedState.get<String>(KEY_DIALOG)?.let(FormDialog::valueOf)
        )
    )
    val uiState: StateFlow<CompanyFormUiState> = _uiState.asStateFlow()

    private val _events = Channel<FormEvent>(Channel.BUFFERED)
    val events: Flow<FormEvent> = _events.receiveAsFlow()

    init {
        if (_uiState.value.loading) load()
    }

    fun onNameChanged(value: String) = editDraft(CompanyField.NAME) { it.copy(name = value) }
    fun onWebsiteChanged(value: String) = editDraft(CompanyField.WEBSITE) { it.copy(website = value) }
    fun onPhoneChanged(value: String) = editDraft(CompanyField.PHONE) { it.copy(phone = value) }
    fun onEmailChanged(value: String) = editDraft(CompanyField.EMAIL) { it.copy(email = value) }
    fun onProductsChanged(value: String) = editDraft(null) { it.copy(products = value) }

    fun onClassificationChecked(classification: Classification, checked: Boolean) =
        editDraft(CompanyField.CLASSIFICATIONS) {
            it.copy(classifications = if (checked) it.classifications + classification else it.classifications - classification)
        }

    /** Valida; si hay errores se muestran en los campos, si no, crea o actualiza en la base. */
    fun save() {
        val state = _uiState.value
        if (state.loading || state.saving) return
        when (val validation = CompanyValidator.validate(state.draft, mCompanyId)) {
            is Validation.Invalid -> _uiState.update { it.copy(errors = validation.errors) }
            is Validation.Valid -> runBusy {
                val result = if (mIsNew) repository.insert(validation.company) else repository.update(validation.company)
                when (result) {
                    is SaveResult.Saved -> FormEvent.Finished(if (mIsNew) FormOutcome.CREATED else FormOutcome.UPDATED)
                    SaveResult.NotFound -> FormEvent.NotFound
                    SaveResult.DuplicateName -> {
                        _uiState.update { it.copy(errors = it.errors + (CompanyField.NAME to FieldError.DUPLICATE)) }
                        null
                    }
                }
            }
        }
    }

    /** El enunciado pide confirmar antes de eliminar: primero el diálogo, después [confirmDelete]. */
    fun requestDelete() {
        val state = _uiState.value
        if (!mIsNew && !state.loading && !state.saving) setDialog(FormDialog.CONFIRM_DELETE)
    }

    fun confirmDelete() {
        setDialog(null)
        runBusy {
            if (repository.delete(mCompanyId)) FormEvent.Finished(FormOutcome.DELETED) else FormEvent.NotFound
        }
    }

    /** Back o la flecha de la toolbar: si hay cambios sin guardar, se pregunta antes de descartarlos. */
    fun requestClose() {
        val state = _uiState.value
        if (state.saving) return
        if (state.hasChanges) setDialog(FormDialog.DISCARD_CHANGES) else _events.trySend(FormEvent.Finished(FormOutcome.CANCELED))
    }

    fun confirmDiscard() {
        setDialog(null)
        _events.trySend(FormEvent.Finished(FormOutcome.CANCELED))
    }

    fun dismissDialog() = setDialog(null)

    private fun load() {
        viewModelScope.launch {
            val company = repository.getCompany(mCompanyId)
            if (company == null) {
                _events.send(FormEvent.NotFound)
                return@launch
            }
            val draft = CompanyDraft.from(company)
            savedState.putDraft(ORIGINAL, draft)
            savedState.putDraft(DRAFT, draft)
            savedState[KEY_LOADED] = true
            _uiState.update { it.copy(loading = false, draft = draft, original = draft) }
        }
    }

    /** Cambia el borrador y borra el error de ese campo (el usuario lo está corrigiendo). */
    private fun editDraft(field: CompanyField?, change: (CompanyDraft) -> CompanyDraft) {
        val state = _uiState.value
        // Mientras carga, los campos están vacíos: lo que llegue de ellos no es del usuario
        if (state.loading) return
        val draft = change(state.draft)
        if (draft == state.draft) return
        savedState.putDraft(DRAFT, draft)
        _uiState.update { it.copy(draft = draft, errors = if (field == null) it.errors else it.errors - field) }
    }

    private fun setDialog(dialog: FormDialog?) {
        savedState[KEY_DIALOG] = dialog?.name
        _uiState.update { it.copy(dialog = dialog) }
    }

    /**
     * Una escritura a la vez. Si [block] devuelve un evento, se emite; si termina el formulario,
     * se queda en `saving` para que un segundo toque no guarde otra vez antes de cerrar.
     */
    private fun runBusy(block: suspend () -> FormEvent?) {
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            var event: FormEvent? = null
            try {
                event = block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                event = FormEvent.Failed
            } finally {
                if (event !is FormEvent.Finished && event != FormEvent.NotFound) _uiState.update { it.copy(saving = false) }
            }
            event?.let { _events.send(it) }
        }
    }

    // El SavedStateHandle solo guarda tipos simples: cada campo va con su clave
    private fun SavedStateHandle.putDraft(prefix: String, draft: CompanyDraft) {
        this[prefix + "name"] = draft.name
        this[prefix + "website"] = draft.website
        this[prefix + "phone"] = draft.phone
        this[prefix + "email"] = draft.email
        this[prefix + "products"] = draft.products
        this[prefix + "classifications"] = ArrayList(draft.classifications.map { it.code })
    }

    private fun SavedStateHandle.getDraft(prefix: String): CompanyDraft? {
        val name = get<String>(prefix + "name") ?: return null
        return CompanyDraft(
            name = name,
            website = get<String>(prefix + "website").orEmpty(),
            phone = get<String>(prefix + "phone").orEmpty(),
            email = get<String>(prefix + "email").orEmpty(),
            products = get<String>(prefix + "products").orEmpty(),
            classifications = get<ArrayList<String>>(prefix + "classifications").orEmpty()
                .mapNotNull(Classification::fromCode).toSet()
        )
    }
}
