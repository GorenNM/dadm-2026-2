package co.edu.unal.softwaredirectory.ui

import co.edu.unal.softwaredirectory.data.Company
import co.edu.unal.softwaredirectory.data.CompanyFilter
import co.edu.unal.softwaredirectory.data.CompanyRepository
import co.edu.unal.softwaredirectory.data.SaveResult
import co.edu.unal.softwaredirectory.data.SearchText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.IOException

/** El directorio en memoria, con el mismo filtro y las mismas reglas que [co.edu.unal.softwaredirectory.data.SqliteCompanyRepository]. */
class FakeCompanyRepository(initial: List<Company> = emptyList()) : CompanyRepository {

    val companies = MutableStateFlow(initial.associateBy { it.id })

    /** Cada filtro con el que se consultó: para comprobar que la búsqueda espera a que se deje de escribir. */
    val searches = mutableListOf<CompanyFilter>()

    /** Para simular un error de la base al escribir. */
    var failWrites = false

    private var mNextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeCompanies(filter: CompanyFilter): Flow<List<Company>> {
        searches += filter
        val query = SearchText.normalize(filter.query)
        return companies.map { all ->
            all.values
                .filter { query.isEmpty() || query in SearchText.normalize(it.name) }
                .filter { filter.classifications.isEmpty() || it.classifications.any { c -> c in filter.classifications } }
                .sortedBy { SearchText.normalize(it.name) }
        }
    }

    override suspend fun getCompany(id: Long): Company? = companies.value[id]

    override suspend fun insert(company: Company): SaveResult {
        if (failWrites) throw IOException("disk full")
        if (nameTaken(company.name, exceptId = null)) return SaveResult.DuplicateName
        val id = mNextId++
        companies.update { it + (id to company.copy(id = id)) }
        return SaveResult.Saved(id)
    }

    override suspend fun update(company: Company): SaveResult {
        if (failWrites) throw IOException("disk full")
        if (company.id !in companies.value) return SaveResult.NotFound
        if (nameTaken(company.name, exceptId = company.id)) return SaveResult.DuplicateName
        companies.update { it + (company.id to company) }
        return SaveResult.Saved(company.id)
    }

    override suspend fun delete(id: Long): Boolean {
        if (failWrites) throw IOException("disk full")
        if (id !in companies.value) return false
        companies.update { it - id }
        return true
    }

    private fun nameTaken(name: String, exceptId: Long?): Boolean {
        val key = SearchText.normalize(name)
        return companies.value.values.any { it.id != exceptId && SearchText.normalize(it.name) == key }
    }
}
