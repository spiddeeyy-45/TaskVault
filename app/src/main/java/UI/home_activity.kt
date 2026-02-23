package UI

import Adapters.FriendModel
import Adapters.FriendStatAdapter
import Adapters.SessionAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.taskvault.R
import ChatLogic.chat_activity
import CloudinaryImageHandler.CloudinaryClient
import DataClass.SessionModel
import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.taskvault.databinding.HomeActivityBinding
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class home_activity : Fragment() {

    private val friendsList = ArrayList<FriendModel>()
    private lateinit var friendsAdapter: FriendStatAdapter

    private val sessionList = ArrayList<SessionModel>()
    private lateinit var sessionAdapter: SessionAdapter

    private var isMySessionSelected = true
    private lateinit var binding: HomeActivityBinding
    private val firestore = FirebaseFirestore.getInstance()
    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    private val pdfPickerLauncher =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->

            uri?.let {
                showPdfNameDialog(it)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?): View? {
        binding = HomeActivityBinding.inflate(inflater,container,false)
        setupFriendsRecycler()
        setupSessionRecycler()
        setupSessionToggle()
        loadFriends()
        loadMySessions()
        setupSearch()
        setupClicks()
        binding.fabUploadPdf.setOnClickListener {
            pdfPickerLauncher.launch("application/pdf")
        }
        return binding.root
    }
    private fun showPdfNameDialog(uri: Uri) {

        val dialog = AlertDialog.Builder(requireContext()).create()
        val view = layoutInflater.inflate(R.layout.pdf_name_dialog, null)

        dialog.setView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etPdfName = view.findViewById<EditText>(R.id.etPdfName)
        val btnCancel = view.findViewById<View>(R.id.btnCancel)
        val btnUpload = view.findViewById<View>(R.id.btnUpload)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnUpload.setOnClickListener {

            val customName = etPdfName.text.toString().trim()

            if (customName.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Please enter PDF name",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            dialog.dismiss()

            uploadPdfToCloudinary(uri, customName)
        }

        dialog.show()
    }
    private fun uploadPdfToCloudinary(uri: Uri,customeName:String) {

        lifecycleScope.launch {

            try {

                val inputStream =
                    requireContext().contentResolver.openInputStream(uri)

                val file = File(
                    requireContext().cacheDir,
                    "upload_${System.currentTimeMillis()}.pdf"
                )

                file.outputStream().use { output ->
                    inputStream?.copyTo(output)
                }

                val requestFile = file.asRequestBody(
                    "application/pdf".toMediaTypeOrNull()
                )

                val body = MultipartBody.Part.createFormData(
                    "file",
                    file.name,
                    requestFile
                )

                val preset = "TaskVaultPDF"
                    .toRequestBody("text/plain".toMediaTypeOrNull())

                val response =
                    CloudinaryClient.api.uploadPdf(body, preset)

                val pdfUrl = response.secureUrl
                val pdfName = customeName

                savePdfUrlToFirestore(pdfName,pdfUrl)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun savePdfUrlToFirestore(pdfName: String, pdfUrl: String) {

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val pdfData = hashMapOf(
            "pdfName" to pdfName,
            "pdfUrl" to pdfUrl,
            "uploadedAt" to System.currentTimeMillis()
        )

        FirebaseFirestore.getInstance()
            .collection("User")
            .document(uid)
            .collection("PDFs")
            .add(pdfData)
    }

    private fun setupSearch() {
        binding.searchBarContainer.setOnClickListener{
            parentFragmentManager.beginTransaction()
                .replace(R.id.FragmentContainer,search_activity())
                .addToBackStack(null)
                .commit()
        }
        binding.searchBar.setOnClickListener{
            parentFragmentManager.beginTransaction()
                .replace(R.id.FragmentContainer,search_activity())
                .addToBackStack(null)
                .commit()
        }
    }
    private fun setupClicks(){
        binding.btnChat.setOnClickListener{
            parentFragmentManager.beginTransaction()
                .replace(R.id.FragmentContainer, chat_activity())
                .addToBackStack(null)
                .commit()
        }
    }
    private fun setupSessionRecycler() {

        sessionAdapter = SessionAdapter(
            sessionList
        ) { session ->

            val bundle = Bundle().apply {
                putString("sessionId", session.sessionId)
            }

            //  navigation later
        }

        binding.recyclerViewSessions.layoutManager =
            LinearLayoutManager(requireContext())

        binding.recyclerViewSessions.adapter = sessionAdapter
    }
    private fun setupFriendsRecycler() {

        friendsAdapter = FriendStatAdapter(
            friendsList,
            onAddClick = {
                val fragment = search_activity()
                parentFragmentManager.beginTransaction()
                    .replace(R.id.FragmentContainer,fragment)
                    .addToBackStack(null)
                    .commit()
            },
            onFriendClick = { friend ->
                openChat(friend.uid)
            }
        )

        binding.friendsScrollView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL,false)

        binding.friendsScrollView.adapter = friendsAdapter
    }
    private fun loadFriends() {

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("User")
            .document(currentUid)
            .collection("Friends")
            .addSnapshotListener { snapshots, _ ->

                if (snapshots == null) return@addSnapshotListener

                friendsList.clear()

                for (doc in snapshots.documents) {

                    val friendUid = doc.id
                    fetchFriendData(friendUid)
                }
            }
    }
    private fun fetchFriendData(friendUid: String) {

        val firestore = FirebaseFirestore.getInstance()

        firestore.collection("User")
            .document(friendUid)
            .get()
            .addOnSuccessListener { userDoc ->

                val name = userDoc.getString("fullName") ?: "User"

                firestore.collection("User")
                    .document(friendUid)
                    .collection("ProfileImage")
                    .document("Image")
                    .get()
                    .addOnSuccessListener { imageDoc ->

                        val imageUrl =
                            imageDoc.getString("profileImageUrl")

                        val friend = FriendModel(
                            uid = friendUid,
                            name = name,
                            profileUrl = imageUrl
                        )

                        listenFriendStatus(friend)
                    }
            }
    }
    private fun listenFriendStatus(friend: FriendModel) {

        val statusRef =
            FirebaseDatabase.getInstance()
                .getReference("status/${friend.uid}")

        statusRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                val state = snapshot.child("state")
                    .getValue(String::class.java)

                friend.isOnline = state == "online"

                if (!friendsList.any { it.uid == friend.uid }) {
                    friendsList.add(friend)
                }

                friendsAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }
    private fun setupSessionToggle() {

        binding.tabMySessions.setOnClickListener {
            isMySessionSelected = true
            setActiveTab(true)
            loadMySessions()
        }

        binding.tabFriendsSessions.setOnClickListener {
            isMySessionSelected = false
            setActiveTab(false)
            loadFriendsSessions()
        }
    }
    private fun loadMySessions() {

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        firestore.collection("User")
            .document(uid)
            .collection("Sessions")
            .addSnapshotListener { snapshots, _ ->
                Log.d("MY_SESSION", "Docs size: ${snapshots?.size()}")

                sessionList.clear()

                if (snapshots != null) {
                    for (doc in snapshots.documents) {
                        Log.d("MY_SESSION", "Doc data: ${doc.data}")
                        val session =
                            doc.toObject(SessionModel::class.java)

                        if (session != null) {
                            Log.d("MY_SESSION", "Mapping failed")
                            sessionList.add(
                                session.copy(sessionId = doc.id)
                            )
                        }
                    }
                }

                sessionAdapter.notifyDataSetChanged()
                checkEmptyState()
            }
    }
    private fun loadFriendsSessions() {

        sessionList.clear()

        val friendUids = friendsList.map { it.uid }

        if (friendUids.isEmpty()) {
            sessionAdapter.notifyDataSetChanged()
            checkEmptyState()
            return
        }

        for (friendUid in friendUids) {
            Log.d("FRIEND_SESSION", "Fetching sessions of $friendUid")

            firestore.collection("User")
                .document(friendUid)
                .collection("Sessions")
                .get()
                .addOnSuccessListener { snapshots ->

                    for (doc in snapshots.documents) {

                        val session =
                            doc.toObject(SessionModel::class.java)

                        if (session != null) {
                            sessionList.add(
                                session.copy(sessionId = doc.id)
                            )
                        }
                    }

                    sessionAdapter.notifyDataSetChanged()
                    checkEmptyState()
                }
        }
    }
    private fun checkEmptyState() {

        if (sessionList.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerViewSessions.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerViewSessions.visibility = View.VISIBLE
        }
    }
    private fun setActiveTab(isMy: Boolean) {

        if (isMy) {

            binding.tabMySessions.setCardBackgroundColor(
                Color.parseColor("#C4A882")
            )
            binding.tabFriendsSessions.setCardBackgroundColor(Color.TRANSPARENT)
            binding.tvMysession.setTextColor(Color.BLACK)
            binding.icMySession.setColorFilter(Color.BLACK)
            binding.icFriendSession.setColorFilter(Color.parseColor("#A68A64"))
            binding.tvfriendsession.setTextColor(Color.parseColor("#A68A64"))

        } else {

            binding.tabMySessions.setCardBackgroundColor(Color.TRANSPARENT)
            binding.tabFriendsSessions.setCardBackgroundColor(
                Color.parseColor("#C4A882")
            )
            binding.icFriendSession.setColorFilter(Color.BLACK)
            binding.icMySession.setColorFilter(Color.parseColor("#A68A64"))
            binding.tvMysession.setTextColor(Color.parseColor("#A68A64"))
            binding.tvfriendsession.setTextColor(Color.BLACK)
        }
    }
    private fun openChat(friendUid: String) {

        val fragment = chat_activity().apply {
            arguments = Bundle().apply {
                putString("friendUid", friendUid)
            }
        }

        parentFragmentManager.beginTransaction()
            .replace(R.id.FragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

}
