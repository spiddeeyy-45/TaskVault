package UI

import Adapters.PdfAdapter
import CloudinaryImageHandler.CloudinaryClient
import DataClass.PdfModel
import LoginHandler.Login
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.taskvault.R
import com.example.taskvault.databinding.SettingActivityBinding
import com.example.taskvault.databinding.SettingPdfBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class setting_activity : Fragment() {

    private var _binding: SettingActivityBinding? = null
    private val binding get() = _binding!!
    private var dialogProfileImage: ImageView? = null
    private lateinit var sharedPrefs: SharedPreferences
    private var selectedImageUri: Uri? = null
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
        savedInstanceState: Bundle?
    ): View {
        _binding = SettingActivityBinding.inflate(inflater, container, false)
        sharedPrefs = requireContext().getSharedPreferences("user_prefs", 0)
        setupUI()

        return binding.root
    }
    private fun showChangePasswordDialog() {

        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)

        val oldPassword = dialogView.findViewById<TextInputEditText>(R.id.etOldPassword)
        val newPassword = dialogView.findViewById<TextInputEditText>(R.id.etNewPassword)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Update", null)
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {

            val updateBtn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)

            updateBtn.setOnClickListener {

                val oldPass = oldPassword.text.toString().trim()
                val newPass = newPassword.text.toString().trim()

                if (oldPass.isEmpty() || newPass.isEmpty()) {
                    Toast.makeText(requireContext(), "Fields cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (newPass.length < 6) {
                    Toast.makeText(requireContext(), "New password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val user = FirebaseAuth.getInstance().currentUser
                val email = user?.email

                if (user != null && email != null) {

                    val credential = com.google.firebase.auth.EmailAuthProvider
                        .getCredential(email, oldPass)

                    user.reauthenticate(credential)
                        .addOnCompleteListener { authTask ->

                            if (authTask.isSuccessful) {

                                // 🔁 Update Password
                                user.updatePassword(newPass)
                                    .addOnCompleteListener { updateTask ->

                                        if (updateTask.isSuccessful) {
                                            Toast.makeText(
                                                requireContext(),
                                                "Password updated successfully",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            dialog.dismiss()
                                        } else {
                                            Toast.makeText(
                                                requireContext(),
                                                updateTask.exception?.localizedMessage,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }

                            } else {
                                Toast.makeText(
                                    requireContext(),
                                    "Old password is incorrect",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                }
            }
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply { setTextColor(Color.GREEN) }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).apply { setTextColor(Color.RED) }


    }
    private fun setupUI() {
        loadUserInfo()
        setupClicks()
    }
    /* ======================= LOAD USER ======================= */
    private fun loadUserInfo() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Load name & email from User
        FirebaseFirestore.getInstance()
            .collection("User")
            .document(userId)
            .get()
            .addOnSuccessListener { userDoc ->
                if (_binding == null || !isAdded) return@addOnSuccessListener
                if (userDoc.exists()) {
                    binding.tvUserName.text = userDoc.getString("fullName") ?: ""
                    binding.tvUserEmail.text = userDoc.getString("email") ?: ""
                }
            }

        // Load profile image from Profile
        FirebaseFirestore.getInstance()
            .collection("User")
            .document(userId)
            .collection("ProfileImage")
            .document("Image")
            .get()
            .addOnSuccessListener { profileDoc ->
                if (_binding == null || !isAdded) return@addOnSuccessListener
                val imageUrl = profileDoc.getString("profileImageUrl")
                if (!imageUrl.isNullOrEmpty()) {
                    Glide.with(this)
                        .load(imageUrl)
                        .placeholder(R.drawable.image)
                        .into(binding.profileImage)
                } else {
                    binding.profileImage.setImageResource(R.drawable.image)
                }
            }
    }
    /* ======================= SAVE PROFILE ======================= */
    private fun saveProfile(name: String, email: String, imageUri: Uri?) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // Update User collection
        db.collection("User")
            .document(userId)
            .update(
                mapOf(
                    "fullName" to name,
                    "email" to email,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

        // If no new image selected → stop here
        if (imageUri == null) {
            loadUserInfo()
            return
        }

        // Upload image to Cloudinary
        lifecycleScope.launch {
            try {
                val filePart = uriToMultipart(imageUri)
                val preset =
                    "TaskVaultProfile".toRequestBody("text/plain".toMediaType())

                val response = CloudinaryClient.api.uploadImage(filePart, preset)

                // Save image URL in Profile collection
                db.collection("User")
                    .document(userId)
                    .collection("ProfileImage")
                    .document("Image")
                    .set(
                        mapOf(
                            "profileImageUrl" to response.secureUrl,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )

                loadUserInfo()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Image upload failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
    /* ======================= CLICKS ======================= */
    private fun setupClicks() {
        binding.tvEditProfile.setOnClickListener { showEditProfileDialog() }
        binding.btnChangePassword.setOnClickListener { showChangePasswordDialog() }

        binding.btnPDFSettings.setOnClickListener {
            showPdfBottomSheet()
        }

        binding.tvAboutApp.setOnClickListener {
            val alert = AlertDialog.Builder(requireContext())
                .setView(R.layout.dialog_about)
                .setPositiveButton("OK", null)
                .show()
            alert.window?.setBackgroundDrawableResource(android.R.color.transparent)
            alert.show()
            alert.getButton(AlertDialog.BUTTON_POSITIVE).apply { setTextColor(Color.RED) }
        }
        binding.tvLogout.setOnClickListener{
            val uid = FirebaseAuth.getInstance().currentUser?.uid

            if (uid != null) {

                val statusRef = FirebaseDatabase
                    .getInstance()
                    .getReference("status/$uid")

                val offlineStatus = mapOf(
                    "state" to "offline",
                    "lastChanged" to ServerValue.TIMESTAMP
                )

                statusRef.setValue(offlineStatus)
            }
            FirebaseAuth.getInstance().signOut()
            val intent =Intent(requireContext(), Login::class.java)
            intent.flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }

    }
    /* ======================= PDFs ======================= */
    private fun showPdfBottomSheet() {

        val dialog = BottomSheetDialog(requireContext())
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        val binding = SettingPdfBinding.inflate(layoutInflater)

        dialog.setContentView(binding.root)

        val pdfList = mutableListOf<PdfModel>()

        val adapter = PdfAdapter { pdf ->
            val cleanUrl = pdf.url
                .trim()
                .removePrefix("\"")
                .removeSuffix("\"")

            Log.d("PDF_OPEN_URL", cleanUrl)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(cleanUrl)
                flags = Intent.FLAG_ACTIVITY_NO_HISTORY
            }

            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "No PDF viewer installed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        binding.recyclerViewPdfs.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewPdfs.adapter = adapter

        binding.btnClose.setOnClickListener {
            dialog.dismiss()
        }
        binding.etSearch.addTextChangedListener {
            val query = it?.toString()?.trim() ?: ""
            adapter.filter(query)
            val isEmpty = adapter.itemCount == 0
            binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.recyclerViewPdfs.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }
        binding.fabUpload.setOnClickListener {
            pdfPickerLauncher.launch("application/pdf")
        }
        loadUserPdfs(binding, pdfList, adapter)
        dialog.show()
        val bottomSheet =
            dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            val screenHeight = Resources.getSystem().displayMetrics.heightPixels
            val desiredHeight = (screenHeight * 0.85).toInt()
            it.layoutParams.height = desiredHeight

            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
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
    private fun uploadPdfToCloudinary(uri: Uri, customName: String) {

        lifecycleScope.launch {

            try {

                val contentResolver = requireContext().contentResolver

                val fileName = "upload_${System.currentTimeMillis()}.pdf"
                val tempFile = File(requireContext().cacheDir, fileName)

                contentResolver.openInputStream(uri)?.use { inputStream ->
                    tempFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                //
                if (tempFile.length() == 0L) {
                    Toast.makeText(
                        requireContext(),
                        "File is empty. Try another PDF.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                val requestFile = tempFile.asRequestBody(
                    "application/pdf".toMediaTypeOrNull()
                )

                val body = MultipartBody.Part.createFormData(
                    "file",
                    tempFile.name,
                    requestFile
                )

                val preset = "TaskVaultPDF"
                    .toRequestBody("text/plain".toMediaTypeOrNull())

                val response =
                    CloudinaryClient.api.uploadPdf(body, preset)

                val pdfUrl = response.secureUrl.trim()
                savePdfUrlToFirestore(customName, pdfUrl)

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    "Upload failed",
                    Toast.LENGTH_SHORT
                ).show()
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
    private fun loadUserPdfs(binding: SettingPdfBinding, pdfList: MutableList<PdfModel>, adapter: PdfAdapter) {

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("User")
            .document(uid)
            .collection("PDFs")
            .addSnapshotListener { snapshots,error  ->
                if (error != null) {
                    Log.d("PDF_DEBUG", "Error: ${error.message}")
                    return@addSnapshotListener
                }
                Log.d("PDF_DEBUG", "Docs count: ${snapshots?.size()}")

                pdfList.clear()

                if (snapshots != null) {
                    for (doc in snapshots.documents) {

                        val model = PdfModel(
                            id = doc.id,
                            name = doc.getString("pdfName") ?: "",
                            url = doc.getString("pdfUrl") ?: "",
                            uploadedAt = doc.getLong("uploadedAt") ?: 0L
                        )

                        pdfList.add(model)
                    }
                }

                binding.tvPdfCount.text =
                    "${pdfList.size} documents"
                adapter.updateData(pdfList)
                val isEmpty = pdfList.isEmpty()

                binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
                binding.recyclerViewPdfs.visibility = if (isEmpty) View.GONE else View.VISIBLE
            }
    }
    /* ======================= EDIT PROFILE ======================= */
    private fun showEditProfileDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit, null)
        val etName = dialogView.findViewById<EditText>(R.id.etName)
        val etEmail = dialogView.findViewById<EditText>(R.id.etEmail)
        val ivProfile = dialogView.findViewById<ImageView>(R.id.ivProfile)
        dialogProfileImage = ivProfile

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("User")
            .document(userId)
            .get()
            .addOnSuccessListener { doc ->
                etName.setText(doc.getString("fullName") ?: "")
                etEmail.setText(doc.getString("email") ?: "")

            }
        FirebaseFirestore.getInstance()
            .collection("User")
            .document(userId)
            .collection("ProfileImage")
            .document("Image")
            .get()
            .addOnSuccessListener { doc ->
                val imageUrl = doc.getString("profileImageUrl")
                if (!imageUrl.isNullOrEmpty()) {
                    Glide.with(this)
                        .load(imageUrl)
                        .placeholder(R.drawable.image)
                        .into(ivProfile)
                }
            }


        ivProfile.setOnClickListener {
            startActivityForResult(
                Intent(Intent.ACTION_PICK).apply { type = "image/*" },
                1001
            )
        }

        val alert=AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim()
                val email = etEmail.text.toString().trim()

                if (name.isNotEmpty() && email.isNotEmpty()) {
                    saveProfile(name, email, selectedImageUri)
                    binding.tvUserName.text = name
                    binding.tvUserEmail.text = email
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
        alert.window?.setBackgroundDrawableResource(android.R.color.transparent)
        alert.getButton(AlertDialog.BUTTON_POSITIVE).apply { setTextColor(Color.GREEN) }
        alert.getButton(AlertDialog.BUTTON_NEGATIVE).apply { setTextColor(Color.RED) }
        alert.show()
    }
    /* ======================= IMAGE PICK RESULT ======================= */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1001 && resultCode == AppCompatActivity.RESULT_OK) {
            selectedImageUri = data?.data

            selectedImageUri?.let { uri ->
                // Preview in dialog
                dialogProfileImage?.setImageURI(uri)

                // Optional: preview in main profile immediately
                binding.profileImage.setImageURI(uri)
            }
        }
    }
    /* ======================= MULTIPART ======================= */
    private fun uriToMultipart(uri: Uri): MultipartBody.Part {
        val inputStream = requireContext().contentResolver.openInputStream(uri)!!
        val file = File(requireContext().cacheDir, "profile_${System.currentTimeMillis()}.jpg")

        file.outputStream().use { inputStream.copyTo(it) }

        return MultipartBody.Part.createFormData(
            "file",
            file.name,
            file.asRequestBody("image/*".toMediaTypeOrNull())
        )
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
