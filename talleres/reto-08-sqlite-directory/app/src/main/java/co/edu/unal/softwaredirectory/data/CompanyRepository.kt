package co.edu.unal.softwaredirectory.data

import kotlinx.coroutines.flow.Flow

/** El CRUD del enunciado. La implementación real es [SqliteCompanyRepository]; los tests usan un fake en memoria. */
interface CompanyRepository {

    /** Las empresas que cumplen [filter], ordenadas por nombre. Emite de nuevo después de cada escritura. */
    fun observeCompanies(filter: CompanyFilter): Flow<List<Company>>

    suspend fun getCompany(id: Long): Company?

    /** Crea la empresa (su [Company.id] se ignora). */
    suspend fun insert(company: Company): SaveResult

    suspend fun update(company: Company): SaveResult

    /** false si ya no existía. */
    suspend fun delete(id: Long): Boolean
}
