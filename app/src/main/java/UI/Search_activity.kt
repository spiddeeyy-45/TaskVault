package UI

import Adapters.RecentSearchAdapter
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import DataClass.NotificationRequest
import FCM.NotificationRetrofitClient
import Adapters.SearchUserAdapter
import DataClass.RecentSearch
import DataClass.UserModel
import com.example.taskvault.databinding.SearchActivityBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class search_activity : Fragment() {

    private lateinit var binding: SearchActivityBinding
    private val firestore = FirebaseFirestore.getInstance()
    private val currentUid = FirebaseAuth.getInstance().currentUser!!.uid
    private val userList = ArrayList<UserModel>()
    private lateinit var adapter: SearchUserAdapter
    private lateinit var recentAdapter: RecentSearchAdapter
    private val recentList = mutableListOf<RecentSearch>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = SearchActivityBinding.inflate(inflater, container, false)

        setupRecycler()
        setupListeners()

        return binding.root
    }

    // ==========================
    // Recycler Setup
    // ==========================
    private fun setupRecycler() {

        adapter = SearchUserAdapter(userList, currentUid) { targetuid ->
            sendFriendRequestNotification(targetuid)
        }

        recentAdapter = RecentSearchAdapter(
            onItemClick = { item ->
                binding.etSearch.setText(item.username)
                binding.etSearch.setSelection(item.username.length)
                searchUsers(item.username)
            },
            onDeleteClick = { item ->
                deleteRecentSearch(item.username)
            }
        )

        binding.recyclerSearchResults.layoutManager =
            LinearLayoutManager(requireContext())

        // Default → show recent
        binding.recyclerSearchResults.adapter = recentAdapter

        loadRecentSearches()
        showRecentState()
    }
    private fun sendFriendRequestNotification(targetUid: String) {

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        //   sender name from Firestore
        firestore.collection("User")
            .document(currentUid)
            .get()
            .addOnSuccessListener { senderDoc ->

                val myName = senderDoc.getString("fullName") ?: "Someone"

                //  receiver token
                firestore.collection("User")
                    .document(targetUid)
                    .get()
                    .addOnSuccessListener { doc ->

                        val token = doc.getString("fcmToken")
                            ?: return@addOnSuccessListener

                        lifecycleScope.launch {
                            try {
                                NotificationRetrofitClient.api.sendNotification(
                                    NotificationRequest(
                                        token = token,
                                        title = "New Friend Request",
                                        body = "$myName sent you a friend request",
                                        type = "friend_request",
                                        senderUid = currentUid
                                    )
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
            }
    }
    // ==========================
    // Listeners
    // ==========================
    private fun setupListeners() {

        // Back
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Clear button
        binding.btnClear.setOnClickListener {
            binding.etSearch.text.clear()
        }
        binding.btnClearAll.setOnClickListener {

            requireContext()
                .getSharedPreferences("recent_searches", 0)
                .edit()
                .clear()
                .apply()

            loadRecentSearches()
        }

        // Search typing
        binding.etSearch.addTextChangedListener(object : TextWatcher {

            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {

                val query = text.toString().trim()

                binding.btnClear.visibility =
                    if (query.isNotEmpty()) View.VISIBLE else View.GONE

                if (query.isNotEmpty()) {
                    searchUsers(query)
                } else {
                    showRecentState()
                }
            }
        })
    }
    // ==========================
    // Firestore Search
    // ==========================
    private fun searchUsers(query: String) {

        firestore.collection("User")
            .get()
            .addOnSuccessListener { result ->

                userList.clear()

                val matchedDocs = result.documents.filter { doc ->
                    val name = doc.getString("fullName") ?: ""
                    name.contains(query, ignoreCase = true) &&
                            doc.id != currentUid
                }

                if (matchedDocs.isEmpty()) {
                    showNoResults()
                    adapter.notifyDataSetChanged()
                    return@addOnSuccessListener
                }

                var processed = 0
                val total = matchedDocs.size

                for (doc in matchedDocs) {

                    val uid = doc.id
                    val name = doc.getString("fullName") ?: ""
                    val email = doc.getString("email") ?: ""

                    firestore.collection("User")
                        .document(uid)
                        .collection("ProfileImage")
                        .document("Image")
                        .get()
                        .addOnSuccessListener { imageDoc ->

                            val imageUrl =
                                imageDoc.getString("profileImageUrl") ?: ""
                            Log.d("SEARCH_IMAGE_URL", "User: $name -> $imageUrl")

                            userList.add(
                                UserModel(uid, name, email, imageUrl)
                            )

                            processed++

                            if (processed == total) {

                                showResults()
                                adapter.notifyDataSetChanged()

                                if (query.isNotEmpty()) {
                                    saveRecentSearch(query)
                                    loadRecentSearches()
                                }
                            }
                        }
                }
            }
    }
    // ==========================
    // UI STATES
    // ==========================
    private fun showRecentState() {

        binding.recentSearchesSection.visibility = View.VISIBLE
        binding.searchResultsSection.visibility = View.GONE
        binding.recyclerSearchResults.visibility = View.VISIBLE
        binding.noResultsState.visibility = View.GONE

        binding.recyclerSearchResults.adapter = recentAdapter
        loadRecentSearches()
    }
    private fun saveRecentSearch(username: String) {

        val prefs = requireContext()
            .getSharedPreferences("recent_searches", 0)

        val set = prefs.getStringSet("search_list", mutableSetOf())
            ?.toMutableSet() ?: mutableSetOf()

        set.remove(username)
        set.add(username)

        if (set.size > 10) {
            set.remove(set.first())
        }

        prefs.edit().putStringSet("search_list", set).apply()
    }
    private fun loadRecentSearches() {

        val prefs = requireContext()
            .getSharedPreferences("recent_searches", 0)

        val set = prefs.getStringSet("search_list", emptySet())

        val list = set?.map { RecentSearch(it) }
            ?.reversed() ?: emptyList()

        recentAdapter.submitList(list)
    }
    private fun deleteRecentSearch(username: String) {

        val prefs = requireContext()
            .getSharedPreferences("recent_searches", 0)

        val set = prefs.getStringSet("search_list", mutableSetOf())
            ?.toMutableSet() ?: mutableSetOf()

        set.remove(username)

        prefs.edit().putStringSet("search_list", set).apply()

        loadRecentSearches()
    }
    private fun showResults() {

        binding.recentSearchesSection.visibility = View.GONE
        binding.searchResultsSection.visibility = View.VISIBLE
        binding.recyclerSearchResults.visibility = View.VISIBLE
        binding.noResultsState.visibility = View.GONE
        binding.recyclerSearchResults.adapter = adapter
    }
    private fun showNoResults() {
        binding.recentSearchesSection.visibility = View.GONE
        binding.searchResultsSection.visibility = View.GONE
        binding.recyclerSearchResults.visibility = View.GONE
        binding.noResultsState.visibility = View.VISIBLE
    }
}
