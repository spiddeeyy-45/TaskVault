package Adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.taskvault.databinding.ItemFriendRequestBinding
import com.google.firebase.firestore.FirebaseFirestore

class FriendRequestAdapter(
    private val requestList: List<String>,
    private val onAccept: (String) -> Unit,
    private val onDecline: (String) -> Unit
) : RecyclerView.Adapter<FriendRequestAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemFriendRequestBinding)
        : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFriendRequestBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount() = requestList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val senderUid = requestList[position]

        //  Fetch sender user data
        FirebaseFirestore.getInstance()
            .collection("User")
            .document(senderUid)
            .get()
            .addOnSuccessListener { doc ->

                val name = doc.getString("fullName") ?: "Unknown"
                val email = doc.getString("email") ?: ""

                holder.binding.tvRequestName.text = name
                holder.binding.tvRequestUsername.text = email
            }

        // Load profile image
        FirebaseFirestore.getInstance()
            .collection("User")
            .document(senderUid)
            .collection("ProfileImage")
            .document("Image")
            .get()
            .addOnSuccessListener { imageDoc ->

                val imageUrl =
                    imageDoc.getString("profileImageUrl") ?: ""

                if (imageUrl.isNotEmpty()) {
                    Glide.with(holder.itemView.context)
                        .load(imageUrl)
                        .into(holder.binding.ivRequestAvatar)
                }
            }

        holder.binding.btnAccept.setOnClickListener {
            onAccept(senderUid)
        }

        holder.binding.btnDecline.setOnClickListener {
            onDecline(senderUid)
        }
    }
}
