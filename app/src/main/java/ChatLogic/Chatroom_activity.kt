package ChatLogic

import CloudinaryImageHandler.CloudinaryClient
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import Adapters.MessageAdapter
import DataClass.MessageModel
import com.example.taskvault.R
import com.example.taskvault.databinding.ChatroomActivityBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class chatroom_activity : Fragment() {

    private lateinit var binding: ChatroomActivityBinding
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var statusRef: DatabaseReference
    private var statusListener: ValueEventListener? = null
    private var myProfileUrl: String? = null

    private var chatId = ""
    private var friendUid = ""
    private var currentUid = ""

    private val messageList = ArrayList<MessageModel>()
    private lateinit var adapter: MessageAdapter
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->

            uri?.let {
                uploadImageToCloudinary(it)
            }
        }

    private var messageListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        chatId = arguments?.getString("chatId") ?: ""
        friendUid = arguments?.getString("friendUid") ?: ""
        currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = ChatroomActivityBinding.inflate(inflater, container, false)

        setupRecycler()
        loadFriendProfile()
        loadMyProfile()
        loadMessages()
        listenFriendStatus()
        setupSend()
        setupEmojiButton()
        resetUnread()
        setupBackButton()
        binding.btnAttach.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }


        return binding.root
    }

    // ---------------
    // Recycler Setup
    // ---------------

    private fun uploadImageToCloudinary(uri: Uri) {

        lifecycleScope.launch {

            try {

                val filePart = uriToMultipart(uri)

                val preset =
                    "TaskVaultChat".toRequestBody("text/plain".toMediaType())

                val response =
                    CloudinaryClient.api.mediaUpload(filePart, preset)

                val imageUrl = response.secureUrl
                sendImageMessage(imageUrl)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    private fun uriToMultipart(uri: Uri): MultipartBody.Part {

        val inputStream =
            requireContext().contentResolver.openInputStream(uri)!!

        val file = File(
            requireContext().cacheDir,
            "chat_${System.currentTimeMillis()}.jpg"
        )

        file.outputStream().use {
            inputStream.copyTo(it)
        }

        return MultipartBody.Part.createFormData(
            "file",
            file.name,
            file.asRequestBody("image/*".toMediaTypeOrNull())
        )
    }
    private fun setupRecycler() {

        adapter = MessageAdapter(messageList, currentUid)

        binding.recyclerMessages.layoutManager =
            LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }

        binding.recyclerMessages.adapter = adapter
    }

    // -------------------
    // Load Friend Profile
    // -------------------

    private fun loadFriendProfile() {

        // Load Friend Name
        firestore.collection("User")
            .document(friendUid)
            .get()
            .addOnSuccessListener { userDoc ->
                val name = userDoc.getString("fullName") ?: "User"
                binding.tvFriendName.text = name
            }

        // Load Friend Profile Image
        firestore.collection("User")
            .document(friendUid)
            .collection("ProfileImage")
            .document("Image")
            .get()
            .addOnSuccessListener { imageDoc ->

                val profileUrl =
                    imageDoc.getString("profileImageUrl")

                if (!profileUrl.isNullOrEmpty()) {

                    Glide.with(requireContext())
                        .load(profileUrl)
                        .placeholder(R.drawable.ic_person)
                        .circleCrop()
                        .into(binding.ivFriendAvatar)
                    adapter.updateFriendImage(profileUrl)
                }
            }
    }

    // -------------
    // Load Messages
    // -------------

    private fun loadMessages() {

        messageListener = firestore.collection("Chats")
            .document(chatId)
            .collection("Messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshots, _ ->

                if (snapshots == null) return@addSnapshotListener

                messageList.clear()

                for (doc in snapshots.documents) {
                    val msg = doc.toObject(MessageModel::class.java)
                        ?: continue
                    messageList.add(msg)
                }

                adapter.notifyDataSetChanged()

                if (messageList.isNotEmpty()) {
                    binding.recyclerMessages
                        .scrollToPosition(messageList.size - 1)
                }
            }
    }

    // ------------
    // Send Message
    // ------------

    private fun setupSend() {

        binding.btnSend.setOnClickListener {

            val text = binding.etMessage.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            val message = MessageModel(
                senderId = currentUid,
                message = text,
                timestamp = System.currentTimeMillis(),
                type = "text"
            )

            val chatRef = firestore.collection("Chats").document(chatId)
            chatRef.collection("Messages").add(message)
            chatRef.update(
                mapOf(
                    "lastMessage" to text,
                    "lastTimestamp" to System.currentTimeMillis(),
                    "unreadCount.$friendUid" to FieldValue.increment(1)
                )
            )

            binding.etMessage.setText("")
        }
    }
    private fun sendImageMessage(imageUrl: String) {

        val message = MessageModel(
            senderId = currentUid,
            message = "",
            timestamp = System.currentTimeMillis(),
            type = "image",
            imageUrl = imageUrl
        )

        firestore.collection("Chats")
            .document(chatId)
            .collection("Messages")
            .add(message)

        firestore.collection("Chats")
            .document(chatId)
            .update(
                mapOf(
                    "lastMessage" to "Image",
                    "lastTimestamp" to System.currentTimeMillis(),
                    "unreadCount.$friendUid" to FieldValue.increment(1)
                )
            )
    }
    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    // -------------------
    // Reset Unread Count
    // -------------------

    private fun resetUnread() {

        firestore.collection("Chats")
            .document(chatId)
            .update("unreadCount.$currentUid", 0)
    }
    private fun listenFriendStatus() {

        val database = FirebaseDatabase.getInstance()
        statusRef = database.getReference("status/$friendUid")

        statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                val state = snapshot.child("state")
                    .getValue(String::class.java) ?: "offline"

                if (state == "online") {
                    binding.onlineDot.visibility = View.VISIBLE
                    binding.tvStatus.text = "Online"
                } else {

                    binding.onlineDot.visibility = View.INVISIBLE

                    val lastSeen = snapshot.child("lastChanged")
                        .getValue(Long::class.java)

                    if (lastSeen != null) {
                        binding.tvStatus.text =
                            "Last seen ${formatLastSeen(lastSeen)}"
                    } else {
                        binding.tvStatus.text = "Offline"
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        statusRef.addValueEventListener(statusListener!!)
    }
    private fun formatLastSeen(timestamp: Long): String {

        val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    // ------------
    // Emoji Button
    // ------------

    private fun showKeyboard() {
        val imm = requireContext()
        .getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager

        if (imm.isActive(binding.etMessage)) {
            imm.hideSoftInputFromWindow(binding.etMessage.windowToken, 0)
        } else {
            binding.etMessage.requestFocus()
            imm.showSoftInput(binding.etMessage, 0)
        }
    }
    private fun setupEmojiButton() {

        binding.btnEmoji.setOnClickListener {
            showKeyboard()
        }
    }
    private fun loadMyProfile() {

        firestore.collection("User")
            .document(currentUid)
            .collection("ProfileImage")
            .document("Image")
            .get()
            .addOnSuccessListener { imageDoc ->

                myProfileUrl =
                    imageDoc.getString("profileImageUrl")

                adapter.updateMyImage(myProfileUrl)
            }
    }

    // -------------------------
    // Remove Firestore Listener
    // -------------------------

    override fun onDestroyView() {
        super.onDestroyView()
        messageListener?.remove()

        statusListener?.let {
            statusRef.removeEventListener(it)
        }
    }
}