package Adapters

import DataClass.ChatModel
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.taskvault.R
import com.example.taskvault.databinding.ItemChatBinding
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class ChatListAdapter(
    private val chatList: List<ChatModel>,
    private val onChatClick: (ChatModel) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.ChatViewHolder>() {

    inner class ChatViewHolder(val binding: ItemChatBinding)
        : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChatViewHolder(binding)
    }

    override fun getItemCount(): Int = chatList.size

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {

        val chat = chatList[position]

        holder.binding.root.setOnClickListener {
            onChatClick(chat)
        }

        // ==============================
        // Fetch Friend Basic Info
        // ==============================

        FirebaseFirestore.getInstance()
            .collection("User")
            .document(chat.friendUid)
            .get()
            .addOnSuccessListener { doc ->

                val name = doc.getString("fullName") ?: "User"

                holder.binding.tvChatName.text = name

                //  Online Status
                val isOnline = doc.getBoolean("isOnline") ?: false
                holder.binding.onlineDot.visibility =
                    if (isOnline) android.view.View.VISIBLE
                    else android.view.View.GONE
            }

        // ==============================
        // Load Profile Image
        // ==============================

        FirebaseFirestore.getInstance()
            .collection("User")
            .document(chat.friendUid)
            .collection("ProfileImage")
            .document("Image")
            .get()
            .addOnSuccessListener { imageDoc ->

                val imageUrl =
                    imageDoc.getString("profileImageUrl") ?: ""

                if (imageUrl.isNotEmpty()) {
                    Glide.with(holder.itemView.context)
                        .load(imageUrl)
                        .placeholder(R.drawable.ic_person)
                        .into(holder.binding.ivChatAvatar)
                } else {
                    holder.binding.ivChatAvatar.setImageResource(R.drawable.ic_person)
                }
            }

        // ==============================
        // Last Message
        // ==============================

        holder.binding.tvLastMessage.text =
            chat.lastMessage ?: "No messages yet"

        // ==============================
        // Time Formatting
        // ==============================

        chat.lastMessageTime?.let { timestamp ->
            val date = Date(timestamp)
            val formattedTime =
                DateFormat.format("hh:mm a", date).toString()

            holder.binding.tvChatTime.text = formattedTime
        }

        // ==============================
        // Unread Count
        // ==============================

        if (chat.unreadCount > 0) {
            holder.binding.unreadBadge.visibility =
                android.view.View.VISIBLE

            holder.binding.tvUnreadCount.text =
                chat.unreadCount.toString()
        } else {
            holder.binding.unreadBadge.visibility =
                android.view.View.GONE
        }
    }
}
