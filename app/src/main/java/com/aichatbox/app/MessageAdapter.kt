package com.aichatbox.app

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aichatbox.app.databinding.ItemMessageBinding
import java.text.SimpleDateFormat
import java.util.*

data class Message(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

class MessageAdapter(
    private val context: Context,
    private val messages: MutableList<Message> = mutableListOf()
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    class MessageViewHolder(val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        
        with(holder.binding) {
            messageText.text = message.content
            timeText.text = timeFormat.format(Date(message.timestamp))
            
            if (message.role == "user") {
                senderText.text = "你"
                avatarImage.setImageResource(R.drawable.ic_person)
                messageText.setBackgroundResource(R.color.user_bubble)
            } else {
                senderText.text = "AI"
                avatarImage.setImageResource(R.drawable.ic_ai)
                messageText.setBackgroundResource(R.color.ai_bubble)
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun updateLastMessage(content: String) {
        if (messages.isNotEmpty()) {
            val lastIndex = messages.size - 1
            messages[lastIndex] = messages[lastIndex].copy(content = content)
            notifyItemChanged(lastIndex)
        }
    }

    fun clearMessages() {
        messages.clear()
        notifyDataSetChanged()
    }

    fun getMessages(): List<Message> = messages.toList()
}
