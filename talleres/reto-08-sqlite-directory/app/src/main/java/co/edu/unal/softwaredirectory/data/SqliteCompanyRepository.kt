package co.edu.unal.softwaredirectory.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import co.edu.unal.softwaredirectory.data.DirectoryContract.Companies
import co.edu.unal.softwaredirectory.data.DirectoryContract.CompanyClassifications
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * El CRUD sobre SQLite. Todo valor que viene del usuario va como argumento `?` (o en ContentValues),
 * nunca pegado al SQL. Las consultas corren en [ioDispatcher], fuera del hilo principal.
 */
class SqliteCompanyRepository(
    private val dbHelper: DirectoryDbHelper,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis
) : CompanyRepository {

    companion object {
        // Las clasificaciones de cada empresa llegan juntas en una columna: "CONSULTING,SOFTWARE_FACTORY"
        private const val COLUMN_CLASSIFICATIONS = "classifications"

        // LEFT JOIN: también salen las empresas sin clasificación (no debería haber, pero no se pierden)
        private val SQL_SELECT = """
            SELECT c.${Companies.ID}, c.${Companies.NAME}, c.${Companies.WEBSITE}, c.${Companies.PHONE},
                   c.${Companies.EMAIL}, c.${Companies.PRODUCTS},
                   GROUP_CONCAT(cc.${CompanyClassifications.CLASSIFICATION}) AS $COLUMN_CLASSIFICATIONS
            FROM ${Companies.TABLE} c
            LEFT JOIN ${CompanyClassifications.TABLE} cc ON cc.${CompanyClassifications.COMPANY_ID} = c.${Companies.ID}
        """.trimIndent()

        private const val SQL_GROUP_AND_ORDER = "GROUP BY c.${Companies.ID} ORDER BY c.${Companies.NAME_KEY}"

        private val SQL_NAME_CONTAINS = "c.${Companies.NAME_KEY} LIKE ? ESCAPE '${SearchText.LIKE_ESCAPE}'"

        /**
         * "Tiene alguna de estas clasificaciones". Va en un EXISTS aparte y no sobre el JOIN de arriba:
         * si no, el GROUP_CONCAT solo traería las clasificaciones que coinciden con el filtro.
         */
        private fun sqlHasAnyClassification(count: Int) = """
            EXISTS (SELECT 1 FROM ${CompanyClassifications.TABLE} f
                    WHERE f.${CompanyClassifications.COMPANY_ID} = c.${Companies.ID}
                      AND f.${CompanyClassifications.CLASSIFICATION} IN (${List(count) { "?" }.joinToString(", ")}))
        """.trimIndent()

        /** Inserta la empresa y sus clasificaciones. Lo usa también [SampleData]; quien llama abre la transacción. */
        internal fun insertCompany(db: SQLiteDatabase, company: Company, now: Long): Long {
            val values = companyValues(company, now).apply { put(Companies.CREATED_AT, now) }
            // insertOrThrow: un nombre repetido lanza SQLiteConstraintException (insert() devolvería -1 sin decir por qué)
            val id = db.insertOrThrow(Companies.TABLE, null, values)
            insertClassifications(db, id, company.classifications)
            return id
        }

        private fun insertClassifications(db: SQLiteDatabase, companyId: Long, classifications: Set<Classification>) {
            for (classification in classifications) {
                val values = ContentValues().apply {
                    put(CompanyClassifications.COMPANY_ID, companyId)
                    put(CompanyClassifications.CLASSIFICATION, classification.code)
                }
                db.insertOrThrow(CompanyClassifications.TABLE, null, values)
            }
        }

        private fun companyValues(company: Company, now: Long) = ContentValues().apply {
            put(Companies.NAME, company.name.trim())
            put(Companies.NAME_KEY, SearchText.normalize(company.name))
            put(Companies.WEBSITE, company.website.trim().ifEmpty { null })
            put(Companies.PHONE, company.phone.trim().ifEmpty { null })
            put(Companies.EMAIL, company.email.trim().ifEmpty { null })
            put(Companies.PRODUCTS, company.products.trim().ifEmpty { null })
            put(Companies.UPDATED_AT, now)
        }
    }

    // Cada escritura lo incrementa, y así las consultas abiertas con observeCompanies se vuelven a ejecutar
    private val mVersion = MutableStateFlow(0)

    override fun observeCompanies(filter: CompanyFilter): Flow<List<Company>> =
        mVersion.map { search(filter) }.flowOn(ioDispatcher)

    override suspend fun getCompany(id: Long): Company? = withContext(ioDispatcher) {
        query("WHERE c.${Companies.ID} = ?", arrayOf(id.toString())).firstOrNull()
    }

    override suspend fun insert(company: Company): SaveResult = write { db ->
        SaveResult.Saved(insertCompany(db, company, clock()))
    }

    /** Actualiza la fila y reemplaza sus clasificaciones (borrar + insertar), en una sola transacción. */
    override suspend fun update(company: Company): SaveResult = write { db ->
        val idArgs = arrayOf(company.id.toString())
        // update() también lanza SQLiteConstraintException si el nombre nuevo ya existe
        val rows = db.update(Companies.TABLE, companyValues(company, clock()), "${Companies.ID} = ?", idArgs)
        if (rows == 0) return@write SaveResult.NotFound
        db.delete(CompanyClassifications.TABLE, "${CompanyClassifications.COMPANY_ID} = ?", idArgs)
        insertClassifications(db, company.id, company.classifications)
        SaveResult.Saved(company.id)
    }

    /** Las clasificaciones se van solas por el ON DELETE CASCADE. */
    override suspend fun delete(id: Long): Boolean {
        val deleted = withContext(ioDispatcher) {
            dbHelper.writableDatabase.delete(Companies.TABLE, "${Companies.ID} = ?", arrayOf(id.toString())) > 0
        }
        if (deleted) mVersion.update { it + 1 }
        return deleted
    }

    private fun search(filter: CompanyFilter): List<Company> {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (SearchText.normalize(filter.query).isNotEmpty()) {
            conditions += SQL_NAME_CONTAINS
            args += SearchText.containsPattern(filter.query)
        }
        if (filter.classifications.isNotEmpty()) {
            // Solo la cantidad de "?" sale del filtro; los valores van como argumentos
            conditions += sqlHasAnyClassification(filter.classifications.size)
            args += filter.classifications.map { it.code }
        }
        val where = if (conditions.isEmpty()) "" else "WHERE " + conditions.joinToString(" AND ")
        return query(where, args.toTypedArray())
    }

    private fun query(where: String, args: Array<String>): List<Company> =
        dbHelper.readableDatabase.rawQuery("$SQL_SELECT $where $SQL_GROUP_AND_ORDER", args).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toCompany()) }
        }

    private fun Cursor.toCompany(): Company {
        fun text(column: String) = getString(getColumnIndexOrThrow(column)).orEmpty()
        return Company(
            id = getLong(getColumnIndexOrThrow(Companies.ID)),
            name = text(Companies.NAME),
            website = text(Companies.WEBSITE),
            phone = text(Companies.PHONE),
            email = text(Companies.EMAIL),
            products = text(Companies.PRODUCTS),
            classifications = text(COLUMN_CLASSIFICATIONS).split(',').mapNotNull(Classification::fromCode).toSet()
        )
    }

    /** Una escritura en una transacción: o se aplica completa o nada. Un nombre repetido termina en DuplicateName. */
    private suspend fun write(block: (SQLiteDatabase) -> SaveResult): SaveResult {
        val result = withContext(ioDispatcher) {
            val db = dbHelper.writableDatabase
            try {
                db.beginTransaction()
                try {
                    block(db).also { if (it is SaveResult.Saved) db.setTransactionSuccessful() }
                } finally {
                    db.endTransaction()
                }
            } catch (e: SQLiteConstraintException) {
                // El único UNIQUE del esquema es name_key; los CHECK los evita el validador del formulario
                if (e.message?.contains("UNIQUE") == true) SaveResult.DuplicateName else throw e
            }
        }
        if (result is SaveResult.Saved) mVersion.update { it + 1 }
        return result
    }
}
