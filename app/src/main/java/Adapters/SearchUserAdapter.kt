package Adapters

import DataClass.UserModel
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.taskvault.R
import com.example.taskvault.databinding.ItemUserResultBinding
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class SearchUserAdapter(
    private val list: ArrayList<UserModel>,
    private val currentUid: String,
    private val onFriendRequestSent: (String) -> Unit
) : RecyclerView.Adapter<SearchUserAdapter.ViewHolder>() {
    private val firestore = FirebaseFirestore.getInstance()


    inner class ViewHolder(val binding: ItemUserResultBinding)
        : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUserResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val user = list[position]
        val targetUid = user.uid

        holder.binding.tvFullName.text = user.fullName
        holder.binding.tvUsername.text = user.email

        holder.binding.ivAvatar.setImageResource(R.drawable.ic_person)
        Log.d("ADAPTER_URL", user.profileImage)
        if (user.profileImage.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(user.profileImage)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(holder.binding.ivAvatar)
        }

        // Default button state
        holder.binding.btnAddFriend.visibility = View.VISIBLE
        holder.binding.btnPending.visibility = View.GONE
        holder.binding.btnAlreadyFriends.visibility = View.GONE

        //Reset Button
        holder.binding.btnAddFriend.visibility=View.GONE
        holder.binding.btnPending.visibility=View.GONE
        holder.binding.btnAlreadyFriends.visibility=View.GONE

        firestore.collection("User")
            .document(currentUid)
            .collection("Friends")
            .document(targetUid)
            .get()
            .addOnSuccessListener { friendDoc ->

                if (friendDoc.exists()) {
                    holder.binding.btnAlreadyFriends.visibility = View.VISIBLE
                } else {

                    // Check if request already sent
                    firestore.collection("User")
                        .document(targetUid)
                        .collection("FriendRequests")
                        .document(currentUid)
                        .get()
                        .addOnSuccessListener { requestDoc ->

                            if (requestDoc.exists()) {
                                holder.binding.btnPending.visibility = View.VISIBLE
                            } else {
                                holder.binding.btnAddFriend.visibility = View.VISIBLE
                            }
                        }
                }
            }

        holder.binding.btnAddFriend.setOnClickListener {

            val requestData = hashMapOf(
                "status" to "pending",
                "sentAt" to FieldValue.serverTimestamp()
            )

            firestore.collection("User")
                .document(targetUid)
                .collection("FriendRequests")
                .document(currentUid)
                .set(requestData)
                .addOnSuccessListener {

                    holder.binding.btnAddFriend.visibility = View.GONE
                    holder.binding.btnPending.visibility = View.VISIBLE

                    onFriendRequestSent(targetUid)
                }
        }

    }

}
