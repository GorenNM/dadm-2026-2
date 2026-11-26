package co.edu.unal.softwaredirectory.ui.form

import androidx.lifecycle.SavedStateHandle
import co.edu.unal.softwaredirectory.data.Classification.CONSULTING
import co.edu.unal.softwaredirectory.data.Classification.CUSTOM_DEVELOPMENT
import co.edu.unal.softwaredirectory.data.Company
import co.edu.unal.softwaredirectory.ui.FakeCompanyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompanyFormViewModelTest {

    private val acme = Company(
        id = 1,
        name = "Acme",
        website = "https://acme.example.com",
        phone = "+57 601 000 0101",
        email = "info@acme.example.com",
        products = "Apps",
        classifications = setOf(CONSULTING)
    )
    private val other = Company(id = 2, name = "Otra Empresa", classifications = setOf(CUSTOM_DEVELOPMENT))

    private val repository = FakeCompanyRepository(listOf(acme, other))

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newForm(savedState: SavedStateHandle = SavedStateHandle()) = CompanyFormViewModel(repository, savedState)

    private fun editForm(id: Long, savedState: SavedStateHandle = SavedStateHandle()) =
        CompanyFormViewModel(repository, savedState.apply { set(CompanyFormViewModel.KEY_COMPANY_ID, id) })

    private fun <T> TestScope.collected(flow: Flow<T>): List<T> {
        val items = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.toList(items) }
        return items
    }

    /** Lo que quedaría después de la muerte del proceso: un SavedStateHandle nuevo con los mismos valores. */
    private fun SavedStateHandle.restored() = SavedStateHandle(keys().associateWith { get<Any>(it) })

    @Test
    fun newCompany_withMissingFields_showsErrors_andSavesNothing() = runTest {
        val vm = newForm()
        val events = collected(vm.events)

        vm.save()
        advanceUntilIdle()
        assertEquals(
            mapOf(CompanyField.NAME to FieldError.REQUIRED, CompanyField.CLASSIFICATIONS to FieldError.REQUIRED),
            vm.uiState.value.errors
        )
        assertEquals(2, repository.companies.value.size)
        assertTrue(events.isEmpty())
    }

    @Test
    fun typingInAField_clearsOnlyItsError() = runTest {
        val vm = newForm()
        vm.save()
        vm.onNameChanged("A")
        assertEquals(mapOf(CompanyField.CLASSIFICATIONS to FieldError.REQUIRED), vm.uiState.value.errors)

        vm.onClassificationChecked(CONSULTING, true)
        assertTrue(vm.uiState.value.errors.isEmpty())
    }

    @Test
    fun newCompany_valid_insertsCleanValues_andFinishes() = runTest {
        val vm = newForm()
        val events = collected(vm.events)
        assertTrue(vm.uiState.value.isNew)
        assertFalse(vm.uiState.value.loading)

        vm.onNameChanged("  Pixel & Byte ")
        vm.onWebsiteChanged("pixelbyte.example.com")
        vm.onClassificationChecked(CUSTOM_DEVELOPMENT, true)
        vm.save()
        assertTrue(vm.uiState.value.saving)
        advanceUntilIdle()

        val saved = repository.companies.value.values.single { it.name == "Pixel & Byte" }
        assertEquals("https://pixelbyte.example.com", saved.website)
        assertEquals(setOf(CUSTOM_DEVELOPMENT), saved.classifications)
        assertEquals(listOf<FormEvent>(FormEvent.Finished(FormOutcome.CREATED)), events)
        // Sigue "guardando" hasta que la Activity cierre: un segundo toque no guarda otra vez
        assertTrue(vm.uiState.value.saving)
    }

    @Test
    fun editing_loadsTheCompany_thenUpdatesIt() = runTest {
        val vm = editForm(acme.id)
        val events = collected(vm.events)
        assertTrue(vm.uiState.value.loading)

        advanceUntilIdle()
        assertFalse(vm.uiState.value.loading)
        assertEquals(CompanyDraft.from(acme), vm.uiState.value.draft)
        assertFalse(vm.uiState.value.hasChanges)

        vm.onProductsChanged("Apps y consultoría")
        vm.onClassificationChecked(CUSTOM_DEVELOPMENT, true)
        assertTrue(vm.uiState.value.hasChanges)
        vm.save()
        advanceUntilIdle()

        val updated = repository.companies.value.getValue(acme.id)
        assertEquals("Apps y consultoría", updated.products)
        assertEquals(setOf(CONSULTING, CUSTOM_DEVELOPMENT), updated.classifications)
        assertEquals(listOf<FormEvent>(FormEvent.Finished(FormOutcome.UPDATED)), events)
    }

    @Test
    fun fieldChangesWhileLoading_areIgnored() = runTest {
        val vm = editForm(acme.id)
        // Lo que mandarían los campos vacíos antes de que llegue la empresa
        vm.onNameChanged("")
        vm.onClassificationChecked(CONSULTING, false)
        advanceUntilIdle()
        assertEquals(CompanyDraft.from(acme), vm.uiState.value.draft)
    }

    @Test
    fun duplicateName_isShownOnTheNameField() = runTest {
        val vm = editForm(acme.id)
        advanceUntilIdle()
        vm.onNameChanged("OTRA empresa")
        vm.save()
        advanceUntilIdle()

        assertEquals(mapOf(CompanyField.NAME to FieldError.DUPLICATE), vm.uiState.value.errors)
        assertFalse(vm.uiState.value.saving)
        assertEquals("Acme", repository.companies.value.getValue(acme.id).name)

        vm.onNameChanged("Otra Empresa 2")
        assertTrue(vm.uiState.value.errors.isEmpty())
    }

    @Test
    fun draftSurvivesProcessDeath_withoutReloadingOverIt() = runTest {
        val savedState = SavedStateHandle()
        val before = editForm(acme.id, savedState)
        advanceUntilIdle()
        before.onPhoneChanged("300 000 0000")

        // Mientras tanto la empresa cambió en la base: igual manda lo que el usuario estaba escribiendo
        repository.update(acme.copy(phone = "999 999 9999"))
        val after = CompanyFormViewModel(repository, savedState.restored())
        advanceUntilIdle()

        assertFalse(after.uiState.value.loading)
        assertEquals("300 000 0000", after.uiState.value.draft.phone)
        assertEquals(acme.name, after.uiState.value.draft.name)
        assertTrue(after.uiState.value.hasChanges)
    }

    @Test
    fun newCompanyDraft_survivesProcessDeath() = runTest {
        val savedState = SavedStateHandle()
        val before = newForm(savedState)
        before.onNameChanged("A medio llenar")
        before.onClassificationChecked(CUSTOM_DEVELOPMENT, true)

        val after = newForm(savedState.restored())
        assertEquals(CompanyDraft(name = "A medio llenar", classifications = setOf(CUSTOM_DEVELOPMENT)), after.uiState.value.draft)
    }

    @Test
    fun delete_asksFirst_andOnlyDeletesAfterConfirming() = runTest {
        val vm = editForm(acme.id)
        val events = collected(vm.events)
        advanceUntilIdle()

        vm.requestDelete()
        assertEquals(FormDialog.CONFIRM_DELETE, vm.uiState.value.dialog)
        assertTrue(acme.id in repository.companies.value)

        vm.confirmDelete()
        advanceUntilIdle()
        assertNull(vm.uiState.value.dialog)
        assertFalse(acme.id in repository.companies.value)
        assertEquals(listOf<FormEvent>(FormEvent.Finished(FormOutcome.DELETED)), events)
    }

    @Test
    fun cancellingTheDeleteDialog_keepsTheCompany() = runTest {
        val vm = editForm(acme.id)
        advanceUntilIdle()
        vm.requestDelete()
        vm.dismissDialog()
        advanceUntilIdle()
        assertNull(vm.uiState.value.dialog)
        assertTrue(acme.id in repository.companies.value)
    }

    @Test
    fun aNewCompany_cannotBeDeleted() = runTest {
        val vm = newForm()
        vm.requestDelete()
        assertNull(vm.uiState.value.dialog)
    }

    @Test
    fun openDialog_survivesProcessDeath() = runTest {
        val savedState = SavedStateHandle()
        val before = editForm(acme.id, savedState)
        advanceUntilIdle()
        before.requestDelete()

        val after = CompanyFormViewModel(repository, savedState.restored())
        assertEquals(FormDialog.CONFIRM_DELETE, after.uiState.value.dialog)
    }

    @Test
    fun closing_withoutChanges_finishes_withChanges_asksFirst() = runTest {
        val vm = editForm(acme.id)
        val events = collected(vm.events)
        advanceUntilIdle()

        vm.requestClose()
        advanceUntilIdle()
        assertEquals(listOf<FormEvent>(FormEvent.Finished(FormOutcome.CANCELED)), events)

        vm.onNameChanged("Acme 2")
        vm.requestClose()
        assertEquals(FormDialog.DISCARD_CHANGES, vm.uiState.value.dialog)
        vm.confirmDiscard()
        advanceUntilIdle()
        assertEquals(2, events.size)
        assertEquals("Acme", repository.companies.value.getValue(acme.id).name)
    }

    @Test
    fun aCompanyThatNoLongerExists_closesTheForm() = runTest {
        val vm = editForm(99L)
        val events = collected(vm.events)
        advanceUntilIdle()
        assertEquals(listOf<FormEvent>(FormEvent.NotFound), events)
    }

    @Test
    fun aFailedWrite_reportsIt_andAllowsRetrying() = runTest {
        val vm = editForm(acme.id)
        val events = collected(vm.events)
        advanceUntilIdle()
        repository.failWrites = true

        vm.save()
        advanceUntilIdle()
        assertEquals(listOf<FormEvent>(FormEvent.Failed), events)
        assertFalse(vm.uiState.value.saving)

        repository.failWrites = false
        vm.save()
        advanceUntilIdle()
        assertEquals(FormEvent.Finished(FormOutcome.UPDATED), events.last())
    }

    @Test
    fun contactActions_areOnlyOfferedForValidValues() = runTest {
        val vm = newForm()
        assertNull(vm.uiState.value.websiteToOpen)

        vm.onWebsiteChanged("acme.example.com")
        vm.onPhoneChanged("123")
        vm.onEmailChanged("info@acme.example.com")
        assertEquals("https://acme.example.com", vm.uiState.value.websiteToOpen)
        assertNull(vm.uiState.value.phoneToCall)
        assertEquals("info@acme.example.com", vm.uiState.value.emailToWrite)

        vm.onPhoneChanged("601 000 0101")
        assertEquals("601 000 0101", vm.uiState.value.phoneToCall)
    }
}
