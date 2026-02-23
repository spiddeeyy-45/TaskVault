package Adapters

import android.content.res.Resources
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.taskvault.R
import com.example.taskvault.databinding.ItemFriendsBinding

data class FriendModel(
    val uid: String = "",
    val name: String = "",
    val profileUrl: String? = null,
    var isOnline: Boolean = false
)

class FriendStatAdapter(
    private val list: List<FriendModel>,
    private val onAddClick: () -> Unit,
    private val onFriendClick: (FriendModel) -> Unit
) : RecyclerView.Adapter<FriendStatAdapter.ViewHolder>() {

    override fun getItemCount(): Int = list.size + 1 // +1 for Add button

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) 0 else 1
    }

    inner class ViewHolder(val binding: ItemFriendsBinding)
        : RecyclerView.ViewHolder(binding.root) {

        fun bind(position: Int) {

            if (position == 0) {
                // Add Friend Button
                binding.layoutAddFriend.visibility = View.VISIBLE
                binding.layoutFriendItem.visibility = View.GONE

                binding.btnAddFriend.setOnClickListener {
                    onAddClick()
                }

            } else {

                val friend = list[position - 1]

                binding.layoutAddFriend.visibility = View.GONE
                binding.layoutFriendItem.visibility = View.VISIBLE

                binding.tvFriendName.text = friend.name

                Glide.with(binding.root.context)
                    .load(friend.profileUrl)
                    .placeholder(R.drawable.ic_person)
                    .circleCrop()
                    .into(binding.ivFriend)

                // Online Ring
                if (friend.isOnline) {
                    binding.cvFriendRing.setCardBackgroundColor(Color.TRANSPARENT)
                    binding.cvFriendRing.strokeWidth = 3.dpToPx()
                    binding.cvFriendRing.setStrokeColor(Color.parseColor("#C4A882"))
                    binding.cvFriendRing.cardElevation = 8f
                } else {
                    binding.cvFriendRing.setCardBackgroundColor(Color.TRANSPARENT)
                    binding.cvFriendRing.strokeWidth = 2.dpToPx()
                    binding.cvFriendRing.setStrokeColor(Color.parseColor("#30C4A882"))
                    binding.cvFriendRing.cardElevation = 0f
                }

                binding.root.setOnClickListener {
                    onFriendClick(friend)
                }
            }
        }
    }
    fun Int.dpToPx(): Int {
        return (this * Resources.getSystem().displayMetrics.density).toInt()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFriendsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(position)
    }
}