package co.edu.unal.tourismregistry.ui.search

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter

/**
 * Adaptador de un desplegable (exposed dropdown) que siempre ofrece todas las opciones. El ArrayAdapter normal filtra
 * por el texto actual: con "BOYACA" elegido, al abrirlo mostraría solo "BOYACA" y no se podría cambiar.
 * La primera opción es "Todos" ([allLabel]).
 */
class DropdownAdapter(context: Context, private val allLabel: String, values: List<String>) :
    ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, listOf(allLabel) + values) {

    private val mNoFilter = object : Filter() {
        // Sin filtrar: el resultado dice que "hay todas" y publishResults no cambia nada
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val results = FilterResults()
            results.count = this@DropdownAdapter.count
            return results
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) = Unit
    }

    override fun getFilter(): Filter = mNoFilter

    /** El valor de la opción [position], o null si es "Todos". */
    fun valueAt(position: Int): String? = if (position <= 0) null else getItem(position)
}
