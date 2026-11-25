package com.xrey7.aogiri.ai

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class ChatItem(
    val sender: String,
    val message: String,
    val isUser: Boolean
)

class ChatAdapter(
    private val items: MutableList<ChatItem>,
    private var accentColor: Int
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rowContainer: LinearLayout = view.findViewById(R.id.rowContainer)
        val tvSender: TextView = view.findViewById(R.id.tvSender)
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)
    }

    fun updateAccentColor(newColor: Int) {
        accentColor = newColor
        notifyDataSetChanged()
    }

    fun addMessage(item: ChatItem) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val item = items[position]
        holder.tvSender.text = item.sender
        holder.tvMessage.text = item.message

        if (item.isUser) {
            holder.rowContainer.gravity = Gravity.END
            holder.tvMessage.setBackgroundColor(accentColor)
            holder.tvMessage.setTextColor(Color.WHITE)
        } else {
            holder.rowContainer.gravity = Gravity.START
            holder.tvMessage.setBackgroundColor(Color.parseColor("#1E293B"))
            holder.tvMessage.setTextColor(Color.parseColor("#F8FAFC"))
        }
    }

    override fun getItemCount(): Int = items.size
}
