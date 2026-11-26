package co.edu.unal.tourismregistry.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.edu.unal.tourismregistry.R
import co.edu.unal.tourismregistry.data.ApiError
import co.edu.unal.tourismregistry.data.Provider
import co.edu.unal.tourismregistry.databinding.ItemFooterBinding
import co.edu.unal.tourismregistry.databinding.ItemProviderBinding
import co.edu.unal.tourismregistry.ui.describe
import java.text.NumberFormat

/** Lo que muestra la lista: tarjetas de prestadores y, al final, un pie con "Cargar más" / carga / error. */
sealed interface ProviderRow {
    data class Item(val provider: Provider) : ProviderRow

    data class Footer(val state: FooterState) : ProviderRow
}

sealed interface FooterState {
    /** Quedan más: "Mostrando [shown] de [total]" y el botón. */
    data class More(val shown: Int, val total: Int) : FooterState

    data object Loading : FooterState

    /** Falló cargar más: la lista de arriba se conserva. */
    data class Failed(val error: ApiError) : FooterState
}

/** ListAdapter: al cargar más solo se dibujan las tarjetas nuevas. */
class ProvidersAdapter(
    private val onClick: (Provider) -> Unit,
    private val onLoadMore: () -> Unit
) : ListAdapter<ProviderRow, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_FOOTER = 1

        private val DIFF = object : DiffUtil.ItemCallback<ProviderRow>() {
            override fun areItemsTheSame(oldItem: ProviderRow, newItem: ProviderRow) = when {
                oldItem is ProviderRow.Item && newItem is ProviderRow.Item -> oldItem.provider.code == newItem.provider.code
                else -> oldItem is ProviderRow.Footer && newItem is ProviderRow.Footer
            }

            override fun areContentsTheSame(oldItem: ProviderRow, newItem: ProviderRow) = oldItem == newItem
        }
    }

    class ItemHolder(val binding: ItemProviderBinding) : RecyclerView.ViewHolder(binding.root)

    class FooterHolder(val binding: ItemFooterBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int) =
        if (getItem(position) is ProviderRow.Item) TYPE_ITEM else TYPE_FOOTER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_ITEM) {
            ItemHolder(ItemProviderBinding.inflate(inflater, parent, false))
        } else {
            FooterHolder(ItemFooterBinding.inflate(inflater, parent, false)).also {
                it.binding.footerButton.setOnClickListener { onLoadMore() }
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is ProviderRow.Item -> bindItem(holder as ItemHolder, row.provider)
            is ProviderRow.Footer -> bindFooter(holder as FooterHolder, row.state)
        }
    }

    private fun bindItem(holder: ItemHolder, provider: Provider) {
        val context = holder.itemView.context
        val binding = holder.binding
        binding.providerName.text = provider.name
        val category = listOf(provider.category, provider.subCategory).filter { it.isNotEmpty() }
            .joinToString(context.getString(R.string.list_separator))
        binding.providerCategory.text = category
        binding.providerCategory.isVisible = category.isNotEmpty()
        val place = listOf(provider.municipality, provider.department).filter { it.isNotEmpty() }
            .joinToString(context.getString(R.string.place_separator))
        binding.providerPlace.text = place
        binding.providerPlace.isVisible = place.isNotEmpty()
        binding.root.setOnClickListener { onClick(provider) }
    }

    private fun bindFooter(holder: FooterHolder, state: FooterState) {
        val context = holder.itemView.context
        val binding = holder.binding
        val numbers = NumberFormat.getIntegerInstance()
        binding.footerProgress.isVisible = state is FooterState.Loading
        binding.footerButton.isVisible = state !is FooterState.Loading
        when (state) {
            is FooterState.More -> {
                binding.footerText.text = context.getString(
                    R.string.results_showing, numbers.format(state.shown), numbers.format(state.total)
                )
                binding.footerButton.setText(R.string.action_load_more)
            }
            FooterState.Loading -> binding.footerText.text = ""
            is FooterState.Failed -> {
                binding.footerText.text = state.error.describe(context)
                binding.footerButton.setText(R.string.action_retry)
            }
        }
        binding.footerText.isVisible = binding.footerText.text.isNotEmpty()
    }
}
