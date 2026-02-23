package Adapters

import DataClass.MessageModel
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.taskvault.R
import com.example.taskvault.databinding.ItemMessageRecievedBinding
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val messageList: List<MessageModel>,
    private val currentUid: String
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private var friendProfileUrl: String? = null
    private var myProfileUrl: String? = null

    //   updating image after async load
    fun updateFriendImage(url: String?) {
        friendProfileUrl = url
        notifyDataSetChanged()
    }
    fun updateMyImage(url: String?) {
        myProfileUrl = url
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageRecievedBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }

    override fun getItemCount(): Int = messageList.size

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messageList[position])
    }

    override fun onViewRecycled(holder: MessageViewHolder) {
        super.onViewRecycled(holder)
        holder.binding.ivAvatar.visibility = View.VISIBLE
    }

    inner class MessageViewHolder(
        val binding: ItemMessageRecievedBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: MessageModel) {

            val isSentByMe = message.senderId == currentUid

            binding.imageView.visibility = View.GONE
            binding.tvMessage.visibility = View.VISIBLE
            binding.tvMessage.text = ""
            binding.imageView.setImageDrawable(null)


            binding.tvTime.text = formatTime(message.timestamp)

            if (message.type == "image" && !message.imageUrl.isNullOrEmpty()) {

                binding.tvMessage.visibility = View.GONE
                binding.imageView.visibility = View.VISIBLE

                Glide.with(binding.root.context)
                    .load(message.imageUrl)
                    .placeholder(R.drawable.ic_person)
                    .into(binding.imageView)

            } else {

                binding.imageView.visibility = View.GONE
                binding.tvMessage.visibility = View.VISIBLE
                binding.tvMessage.text = message.message
            }
            if (isSentByMe) {
                setupSentMessage()
            } else {
                setupReceivedMessage()
            }
        }

        private fun setupSentMessage() {

            binding.rootLayout.gravity = Gravity.END
            binding.ivAvatar.visibility = View.VISIBLE

            // Load MY profile image
            if (!myProfileUrl.isNullOrEmpty()) {
                Glide.with(binding.root.context)
                    .load(myProfileUrl)
                    .placeholder(R.drawable.ic_person)
                    .circleCrop()
                    .into(binding.ivAvatar)
            } else {
                binding.ivAvatar.setImageResource(R.drawable.ic_person)
            }

            binding.messageBubble.apply {
                setCardBackgroundColor(Color.parseColor("#C4A882"))
                strokeWidth = 0
            }

            binding.tvMessage.setTextColor(Color.parseColor("#1A0E05"))
            binding.tvTime.textAlignment = View.TEXT_ALIGNMENT_VIEW_END
        }

        private fun setupReceivedMessage() {

            binding.rootLayout.gravity = Gravity.START
            binding.ivAvatar.visibility = View.VISIBLE

            //  Load friend profile image
            if (!friendProfileUrl.isNullOrEmpty()) {
                Glide.with(binding.root.context)
                    .load(friendProfileUrl)
                    .placeholder(R.drawable.ic_person)
                    .circleCrop()
                    .into(binding.ivAvatar)
            } else {
                binding.ivAvatar.setImageResource(R.drawable.ic_person)
            }

            binding.messageBubble.apply {
                setCardBackgroundColor(Color.parseColor("#1EC4A882"))
                strokeColor = Color.parseColor("#28C4A882")
                strokeWidth = 1
            }

            binding.tvMessage.setTextColor(Color.parseColor("#EDD9B8"))
            binding.tvTime.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            binding.tvTime.setTextColor(Color.parseColor("#35C4A882"))
        }
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}