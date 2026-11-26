package co.edu.unal.softwaredirectory.data

import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import co.edu.unal.softwaredirectory.data.DirectoryContract.Companies
import co.edu.unal.softwaredirectory.data.DirectoryContract.CompanyClassifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DirectoryDbHelperTest {

    private var mHelper: DirectoryDbHelper? = null

    private fun open(seed: Boolean) =
        DirectoryDbHelper(RuntimeEnvironment.getApplication(), name = null, seed = seed).also { mHelper = it }

    @After
    fun tearDown() {
        mHelper?.close()
    }

    @Test
    fun schema_hasVersion1_andForeignKeysOn() {
        val db = open(seed = false).writableDatabase
        assertEquals(DirectoryDbHelper.DATABASE_VERSION, db.version)
        db.rawQuery("PRAGMA foreign_keys", null).use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
    }

    @Test
    fun firstOpen_insertsTheSampleCompanies_eachWithAClassification() = runTest {
        val repository = SqliteCompanyRepository(open(seed = true), Dispatchers.Unconfined)
        val companies = repository.observeCompanies(CompanyFilter()).first()

        assertEquals(SampleData.companies.map { it.name }.sorted(), companies.map { it.name }.sorted())
        assertTrue(companies.all { it.classifications.isNotEmpty() })
        // Cada clasificación aparece en al menos una empresa de ejemplo, para probar el filtro
        assertEquals(Classification.entries.toSet(), companies.flatMap { it.classifications }.toSet())
    }

    @Test
    fun constraints_rejectBlankNamesUnknownClassificationsAndOrphans() {
        val db = open(seed = false).writableDatabase
        val blank = ContentValues().apply {
            put(Companies.NAME, "   ")
            put(Companies.NAME_KEY, "")
            put(Companies.CREATED_AT, 0)
            put(Companies.UPDATED_AT, 0)
        }
        assertThrows(SQLiteConstraintException::class.java) { db.insertOrThrow(Companies.TABLE, null, blank) }

        val orphan = ContentValues().apply {
            put(CompanyClassifications.COMPANY_ID, 42)
            put(CompanyClassifications.CLASSIFICATION, Classification.CONSULTING.code)
        }
        assertThrows(SQLiteConstraintException::class.java) { db.insertOrThrow(CompanyClassifications.TABLE, null, orphan) }

        val id = SqliteCompanyRepository.insertCompany(db, Company(name = "Acme"), now = 0)
        val unknown = ContentValues().apply {
            put(CompanyClassifications.COMPANY_ID, id)
            put(CompanyClassifications.CLASSIFICATION, "FREELANCE")
        }
        assertThrows(SQLiteConstraintException::class.java) { db.insertOrThrow(CompanyClassifications.TABLE, null, unknown) }
    }
}
