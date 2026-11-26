package co.edu.unal.softwaredirectory.ui.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.edu.unal.softwaredirectory.R
import co.edu.unal.softwaredirectory.data.Classification
import co.edu.unal.softwaredirectory.data.Company
import co.edu.unal.softwaredirectory.databinding.ItemCompanyBinding
import co.edu.unal.softwaredirectory.ui.labelRes

/** Tarjetas de la lista. ListAdapter: cuando llega una lista nueva (al filtrar) solo se redibuja lo que cambió. */
class CompaniesAdapter(private val onClick: (Company) -> Unit) :
    ListAdapter<Company, CompaniesAdapter.CompanyViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Company>() {
            override fun areItemsTheSame(oldItem: Company, newItem: Company) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Company, newItem: Company) = oldItem == newItem
        }

        private val SCHEME = "^[a-zA-Z][a-zA-Z0-9+.-]*://".toRegex()
    }

    class CompanyViewHolder(val binding: ItemCompanyBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        CompanyViewHolder(ItemCompanyBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: CompanyViewHolder, position: Int) {
        val company = getItem(position)
        val context = holder.itemView.context
        val separator = context.getString(R.string.list_contact_separator)

        holder.binding.companyName.text = company.name
        // En el orden del enum, sin importar en qué orden las devolvió el GROUP_CONCAT
        holder.binding.companyClassifications.text = Classification.entries
            .filter { it in company.classifications }
            .joinToString(separator) { context.getString(it.labelRes) }

        // La web sin "https://" se lee mejor en una línea
        val contact = listOf(company.website.replace(SCHEME, "").trimEnd('/'), company.phone, company.email)
            .filter { it.isNotEmpty() }
            .joinToString(separator)
        holder.binding.companyContact.text = contact
        holder.binding.companyContact.isVisible = contact.isNotEmpty()

        holder.binding.root.setOnClickListener { onClick(company) }
    }
}
