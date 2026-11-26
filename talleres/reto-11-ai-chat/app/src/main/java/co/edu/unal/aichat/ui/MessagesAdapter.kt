package co.edu.unal.aichat.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.edu.unal.aichat.R
import co.edu.unal.aichat.data.ChatMessage
import co.edu.unal.aichat.data.MessageStatus
import co.edu.unal.aichat.data.Role
import co.edu.unal.aichat.databinding.ItemMessageModelBinding
import co.edu.unal.aichat.databinding.ItemMessageUserBinding

/**
 * Burbujas de la conversación: las del usuario a la derecha y las del modelo a la izquierda.
 * ListAdapter: mientras la respuesta llega por fragmentos solo se redibuja la burbuja que cambia.
 */
class MessagesAdapter(private val onLongClick: (ChatMessage) -> Unit) :
    ListAdapter<ChatMessage, MessagesAdapter.MessageViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem == newItem
        }

        private const val TYPE_USER = 0
        private const val TYPE_MODEL = 1
    }

    class MessageViewHolder(root: View, val text: TextView, val status: TextView) : RecyclerView.ViewHolder(root)

    override fun getItemViewType(position: Int) = if (getItem(position).role == Role.USER) TYPE_USER else TYPE_MODEL

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            val binding = ItemMessageUserBinding.inflate(inflater, parent, false)
            MessageViewHolder(binding.root, binding.messageText, binding.messageStatus)
        } else {
            val binding = ItemMessageModelBinding.inflate(inflater, parent, false)
            MessageViewHolder(binding.root, binding.messageText, binding.messageStatus)
        }
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = getItem(position)
        // Respuesta que todavía no mandó nada: "Pensando…"
        val thinking = message.status == MessageStatus.STREAMING && message.text.isEmpty()
        if (thinking) holder.text.setText(R.string.message_thinking) else holder.text.text = message.text

        val statusRes = when (message.status) {
            MessageStatus.STOPPED -> R.string.message_stopped
            MessageStatus.INTERRUPTED -> R.string.message_interrupted
            else -> 0
        }
        holder.status.isVisible = statusRes != 0
        if (statusRes != 0) holder.status.setText(statusRes)

        holder.itemView.setOnLongClickListener {
            if (message.text.isNotEmpty()) onLongClick(message)
            true
        }
    }
}
