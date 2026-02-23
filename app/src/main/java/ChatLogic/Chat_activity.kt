package ChatLogic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import Adapters.ChatListAdapter
import Adapters.FriendRequestAdapter
import DataClass.ChatModel
import com.example.taskvault.R
import com.example.taskvault.databinding.ChatActivityBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class chat_activity : Fragment() {

    private lateinit var binding: ChatActivityBinding
    private val firestore = FirebaseFirestore.getInstance()
    private var friendRequestListener: ListenerRegistration? = null
    private var friendsListener: ListenerRegistration? = null
    private var currentUid: String = ""

    private val requestList = ArrayList<String>()
    private val chatList = ArrayList<ChatModel>()

    private lateinit var requestAdapter: FriendRequestAdapter
    private lateinit var chatAdapter: ChatListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = ChatActivityBinding.inflate(inflater, container, false)
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            return binding.root
        }

        currentUid = user.uid

        setupRecyclerViews()
        loadFriendRequests()
        loadFriends()
        setupClicks()
            return binding.root
    }

    // ==========================
    // Recycler Setup
    // ==========================

    private fun setupRecyclerViews() {

        requestAdapter = FriendRequestAdapter(requestList,
            onAccept = { senderUid -> acceptRequest(senderUid) },
            onDecline = { senderUid -> declineRequest(senderUid) }
        )

        binding.recyclerFriendRequests.layoutManager =
            LinearLayoutManager(requireContext())
        binding.recyclerFriendRequests.adapter = requestAdapter

        chatAdapter = ChatListAdapter(chatList) { chat ->
            openOrCreateChat(chat.friendUid)
        }

        binding.recyclerChats.layoutManager =
            LinearLayoutManager(requireContext())
        binding.recyclerChats.adapter = chatAdapter
    }

    // ==========================
    // LOAD FRIEND REQUESTS
    // ==========================

    private fun loadFriendRequests() {

        friendRequestListener = firestore.collection("User")
            .document(currentUid)
            .collection("FriendRequests")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshots, error ->

                if (!isAdded) return@addSnapshotListener
                if (error != null || snapshots == null) return@addSnapshotListener

                requestList.clear()

                if (snapshots.isEmpty) {
                    binding.tvRequestCount.text = "0 pending"
                    requestAdapter.notifyDataSetChanged()
                    return@addSnapshotListener
                }

                for (doc in snapshots.documents) {
                    requestList.add(doc.id)
                }

                binding.tvRequestCount.text =
                    "${requestList.size} pending"

                requestAdapter.notifyDataSetChanged()
            }
    }


    // ==========================
    // ACCEPT REQUEST
    // ==========================

    private fun acceptRequest(senderUid: String) {

        val batch = firestore.batch()

        val currentFriendRef = firestore.collection("User")
            .document(currentUid)
            .collection("Friends")
            .document(senderUid)

        val senderFriendRef = firestore.collection("User")
            .document(senderUid)
            .collection("Friends")
            .document(currentUid)

        val requestRef = firestore.collection("User")
            .document(currentUid)
            .collection("FriendRequests")
            .document(senderUid)

        batch.set(currentFriendRef, mapOf("uid" to senderUid))
        batch.set(senderFriendRef, mapOf("uid" to currentUid))
        batch.delete(requestRef)

        batch.commit()
    }

    // ==========================
    // DECLINE REQUEST
    // ==========================

    private fun declineRequest(senderUid: String) {

        firestore.collection("User")
            .document(currentUid)
            .collection("FriendRequests")
            .document(senderUid)
            .delete()
    }

    // ==========================
    // LOAD CHATS
    // ==========================
    private fun loadFriends() {

        friendsListener = firestore.collection("User")
            .document(currentUid)
            .collection("Friends")
            .addSnapshotListener { snapshots, error ->

                if (!isAdded) return@addSnapshotListener
                if (error != null || snapshots == null) return@addSnapshotListener

                chatList.clear()

                for (doc in snapshots.documents) {

                    val friendUid = doc.getString("uid") ?: continue

                    chatList.add(
                        ChatModel(
                            chatId = "",
                            friendUid = friendUid,
                            lastMessage = "",
                            lastTimestamp = 0L,
                            unreadCount = 0
                        )
                    )
                }

                chatAdapter.notifyDataSetChanged()
            }
    }
    private fun openOrCreateChat(friendUid: String) {

        val chatId = listOf(currentUid, friendUid)
            .sorted()
            .joinToString("_")

        val chatRef = firestore.collection("Chats").document(chatId)

        chatRef.get().addOnSuccessListener { document ->

            if (!document.exists()) {

                chatRef.set(
                    mapOf(
                        "participants" to listOf(currentUid, friendUid),
                        "lastMessage" to "",
                        "lastTimestamp" to 0L,
                        "unreadCount" to mapOf(
                            currentUid to 0,
                            friendUid to 0
                        )
                    )
                )
            }

            openChatRoom(chatId, friendUid)
        }
    }
    private fun setupClicks(){

        // Back
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }


    // ==========================
    // OPEN CHAT ROOM
    // ==========================

    private fun openChatRoom(chatId: String, friendUid: String) {

        val fragment = chatroom_activity().apply {
            arguments = Bundle().apply {
                putString("chatId", chatId)
                putString("friendUid", friendUid)
            }
        }

        parentFragmentManager.beginTransaction()
            .replace(R.id.FragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }
    override fun onDestroyView() {
        super.onDestroyView()
        friendRequestListener?.remove()
        friendsListener?.remove()
    }
}
