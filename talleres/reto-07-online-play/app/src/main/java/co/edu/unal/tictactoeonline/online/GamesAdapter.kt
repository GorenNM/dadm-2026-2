package co.edu.unal.tictactoeonline.online

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.edu.unal.tictactoeonline.R
import co.edu.unal.tictactoeonline.databinding.ItemGameBinding

/** Tarjetas de la lista de juegos. ListAdapter: cuando llega una lista nueva solo se redibuja lo que cambió. */
class GamesAdapter(private val onJoin: (OnlineGame) -> Unit) :
    ListAdapter<OnlineGame, GamesAdapter.GameViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<OnlineGame>() {
            override fun areItemsTheSame(oldItem: OnlineGame, newItem: OnlineGame) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: OnlineGame, newItem: OnlineGame) = oldItem == newItem
        }
    }

    class GameViewHolder(val binding: ItemGameBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        GameViewHolder(ItemGameBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        val game = getItem(position)
        val context = holder.itemView.context
        holder.binding.gameTitle.text = context.getString(R.string.lobby_game_title, game.xName)
        holder.binding.root.setOnClickListener { onJoin(game) }
        holder.binding.joinButton.setOnClickListener { onJoin(game) }
    }
}
