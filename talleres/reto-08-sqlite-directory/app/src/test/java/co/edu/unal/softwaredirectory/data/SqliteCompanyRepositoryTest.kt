package co.edu.unal.softwaredirectory.data

import co.edu.unal.softwaredirectory.data.Classification.CONSULTING
import co.edu.unal.softwaredirectory.data.Classification.CUSTOM_DEVELOPMENT
import co.edu.unal.softwaredirectory.data.Classification.SOFTWARE_FACTORY
import co.edu.unal.softwaredirectory.data.DirectoryContract.Companies
import co.edu.unal.softwaredirectory.data.DirectoryContract.CompanyClassifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** El SQL de verdad: Robolectric corre el SQLite nativo en la JVM, sobre una base en memoria. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SqliteCompanyRepositoryTest {

    private lateinit var helper: DirectoryDbHelper
    private lateinit var repository: SqliteCompanyRepository

    private val andes = Company(
        name = "Andes Software Factory",
        website = "https://andes.example.com",
        phone = "+57 601 000 0101",
        email = "contacto@andes.example.com",
        products = "Fábrica de software y QA",
        classifications = setOf(SOFTWARE_FACTORY)
    )
    private val nandu = Company(name = "Ñandú Tecnología", classifications = setOf(CONSULTING, CUSTOM_DEVELOPMENT))
    private val bogota = Company(name = "Soluciones Bogotá Cloud", classifications = setOf(CONSULTING, SOFTWARE_FACTORY))

    @Before
    fun setUp() {
        helper = DirectoryDbHelper(RuntimeEnvironment.getApplication(), name = null, seed = false, clock = { 1_000L })
        repository = SqliteCompanyRepository(helper, Dispatchers.Unconfined) { 2_000L }
    }

    @After
    fun tearDown() {
        helper.close()
    }

    private suspend fun insert(company: Company): Long = (repository.insert(company) as SaveResult.Saved).id

    private suspend fun search(query: String = "", vararg classifications: Classification) =
        repository.observeCompanies(CompanyFilter(query, classifications.toSet())).first().map { it.name }

    private fun count(table: String): Long =
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use { it.moveToFirst(); it.getLong(0) }

    @Test
    fun insert_thenGet_returnsTheSameCompany() = runTest {
        val id = insert(andes)
        assertEquals(andes.copy(id = id), repository.getCompany(id))
    }

    @Test
    fun emptyOptionalFields_areStoredAsNull_andReadBackAsEmpty() = runTest {
        val id = insert(nandu)
        helper.readableDatabase.rawQuery(
            "SELECT ${Companies.WEBSITE}, ${Companies.PHONE}, ${Companies.EMAIL}, ${Companies.PRODUCTS} FROM ${Companies.TABLE} WHERE ${Companies.ID} = ?",
            arrayOf(id.toString())
        ).use { cursor ->
            cursor.moveToFirst()
            for (column in 0 until 4) assertTrue(cursor.isNull(column))
        }
        assertEquals(nandu.copy(id = id), repository.getCompany(id))
    }

    @Test
    fun getCompany_missing_isNull() = runTest {
        assertNull(repository.getCompany(42))
    }

    @Test
    fun duplicateName_ignoringCaseAccentsAndSpaces_isRejected() = runTest {
        insert(nandu)
        assertEquals(SaveResult.DuplicateName, repository.insert(Company(name = "  NANDU   tecnologia ", classifications = setOf(CONSULTING))))
        assertEquals(1L, count(Companies.TABLE))
        assertEquals(2L, count(CompanyClassifications.TABLE))
    }

    @Test
    fun update_replacesFieldsAndClassifications() = runTest {
        val id = insert(andes)
        val changed = andes.copy(id = id, phone = "", products = "Solo QA", classifications = setOf(CONSULTING, CUSTOM_DEVELOPMENT))

        assertEquals(SaveResult.Saved(id), repository.update(changed))
        assertEquals(changed, repository.getCompany(id))
        assertEquals(2L, count(CompanyClassifications.TABLE))
    }

    @Test
    fun update_toAnotherCompanysName_isRejected_andChangesNothing() = runTest {
        val andesId = insert(andes)
        insert(nandu)

        val result = repository.update(andes.copy(id = andesId, name = "ñandú tecnología", classifications = setOf(CONSULTING)))
        assertEquals(SaveResult.DuplicateName, result)
        // La transacción se deshizo entera, también las clasificaciones
        assertEquals(andes.copy(id = andesId), repository.getCompany(andesId))
    }

    @Test
    fun update_missingCompany_isNotFound() = runTest {
        assertEquals(SaveResult.NotFound, repository.update(andes.copy(id = 42)))
        assertEquals(0L, count(CompanyClassifications.TABLE))
    }

    @Test
    fun delete_removesTheCompanyAndCascadesToItsClassifications() = runTest {
        val id = insert(bogota)
        insert(andes)

        assertTrue(repository.delete(id))
        assertNull(repository.getCompany(id))
        assertEquals(1L, count(Companies.TABLE))
        // Solo queda la de Andes: el ON DELETE CASCADE funciona porque las FK están activas
        assertEquals(1L, count(CompanyClassifications.TABLE))
        assertFalse(repository.delete(id))
    }

    @Test
    fun list_isSortedByName_ignoringAccents() = runTest {
        insert(bogota)
        insert(nandu)
        insert(andes)
        insert(Company(name = "Ábaco Labs", classifications = setOf(CONSULTING)))
        assertEquals(listOf("Ábaco Labs", andes.name, nandu.name, bogota.name), search())
    }

    @Test
    fun searchByName_containsIgnoringCaseAndAccents() = runTest {
        insert(andes)
        insert(nandu)
        insert(bogota)
        assertEquals(listOf(bogota.name), search("BOGOTA"))
        assertEquals(listOf(nandu.name), search("nandu"))
        assertEquals(listOf(nandu.name), search("TECNOLOGÍA"))
        assertEquals(listOf(andes.name, nandu.name, bogota.name), search("   "))
    }

    @Test
    fun searchByName_likeWildcardsTypedByTheUser_areLiteral() = runTest {
        insert(Company(name = "100% Devs", classifications = setOf(CONSULTING)))
        insert(Company(name = "1000 Devs", classifications = setOf(CONSULTING)))
        insert(Company(name = "Dev_Ops Co", classifications = setOf(CONSULTING)))
        insert(Company(name = "DevXOps Co", classifications = setOf(CONSULTING)))

        assertEquals(listOf("100% Devs"), search("100%"))
        assertEquals(listOf("Dev_Ops Co"), search("v_o"))
    }

    @Test
    fun valuesThatLookLikeSql_areJustText() = runTest {
        val name = "Robert'); DROP TABLE companies;--"
        val id = insert(Company(name = name, classifications = setOf(CONSULTING)))
        insert(andes)

        assertEquals(name, repository.getCompany(id)?.name)
        assertTrue(search("x' OR '1'='1").isEmpty())
        assertEquals(2L, count(Companies.TABLE))
    }

    @Test
    fun classificationFilter_isAnyOf_andKeepsAllClassificationsOfEachCompany() = runTest {
        insert(andes)
        insert(nandu)
        insert(bogota)

        assertEquals(listOf(nandu.name), search("", CUSTOM_DEVELOPMENT))
        assertEquals(listOf(andes.name, nandu.name, bogota.name), search("", CUSTOM_DEVELOPMENT, SOFTWARE_FACTORY))

        // Filtrar por consultoría no recorta las otras clasificaciones de la empresa
        val consulting = repository.observeCompanies(CompanyFilter(classifications = setOf(CONSULTING))).first()
        assertEquals(setOf(CONSULTING, CUSTOM_DEVELOPMENT), consulting.single { it.name == nandu.name }.classifications)
        assertEquals(setOf(CONSULTING, SOFTWARE_FACTORY), consulting.single { it.name == bogota.name }.classifications)
    }

    @Test
    fun nameAndClassification_combine() = runTest {
        insert(andes)
        insert(nandu)
        insert(bogota)
        assertEquals(listOf(andes.name, bogota.name), search("so", SOFTWARE_FACTORY))
        assertEquals(listOf(bogota.name), search("so", CONSULTING))
        assertEquals(listOf(andes.name), search("andes", SOFTWARE_FACTORY))
        assertTrue(search("andes", CONSULTING).isEmpty())
    }

    @Test
    fun observeCompanies_emitsAgainAfterEachWrite() = runTest {
        val emissions = mutableListOf<List<Company>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeCompanies(CompanyFilter()).toList(emissions)
        }
        val id = insert(andes)
        repository.update(andes.copy(id = id, name = "Andes SF"))
        repository.delete(id)

        assertEquals(listOf(emptyList(), listOf(andes.name), listOf("Andes SF"), emptyList()), emissions.map { list -> list.map { it.name } })
    }

    @Test
    fun aRejectedWrite_doesNotNotifyTheList() = runTest {
        insert(nandu)
        val emissions = mutableListOf<List<Company>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeCompanies(CompanyFilter()).toList(emissions)
        }
        repository.insert(nandu)
        assertEquals(1, emissions.size)
    }
}
