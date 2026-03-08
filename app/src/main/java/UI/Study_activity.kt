package Fragments

import Adapters.PDFpickerAdapter
import CloudinaryImageHandler.CloudinaryClient
import DataClass.PdfModel
import UI.FaceTracker
import android.Manifest
import android.app.NotificationManager
import android.content.Context
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.core.widget.addTextChangedListener
import android.content.Context.VIBRATOR_SERVICE
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.taskvault.R
import com.example.taskvault.databinding.StudyActivityBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

@androidx.camera.core.ExperimentalGetImage
class study_activity : Fragment() {

    /* ======================= UI & LIFECYCLE ======================= */

    private var _binding: StudyActivityBinding? = null
    private val binding get() = _binding!!

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val imageAnalysisExecutor = Executors.newFixedThreadPool(2)
    private var cameraProvider: ProcessCameraProvider? = null
    private var faceDetector: com.google.mlkit.vision.face.FaceDetector? = null
    private lateinit var faceTracker: FaceTracker

    /* ======================= ALERT SYSTEM ======================= */

    private var mediaPlayer: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var warningCount = 0
    private var lastAlarmTime = 0L
    private var ALARM_TYPE = "ringtone"
    private var ALARM_COOLDOWN_MS = 5000L
    private var selectedRingtoneUri: Uri? = null

    /* ======================= DROWSINESS LOGIC ======================= */

    private var eyeClosedStartTime = 0L

    private var mouthOpenStartTime = 0L
    private var faceNotVisibleStartTime = 0L
    private var eyeClosedFrameCount = 0
    private var eyeClosedStableFrames = 0
    private val EYE_STABLE_FRAME_THRESHOLD = 5
    private val EYE_FRAME_THRESHOLD = 4

    private val EYE_CLOSE_TIME_THRESHOLD = 800L
    private val YAWN_TIME_THRESHOLD = 1000L
    private val FACE_NOT_VISIBLE_THRESHOLD = 1000L

    private var DETECTION_PROB_THRESHOLD = 0.3f
    private var sessionMinutes = 0
    private var selectedImageUri: Uri? = null
    private var sessionStartTime = 0L

    /* ======================= STATE MANAGEMENT ======================= */

    private enum class TrackingState {
        IDLE, TRACKING, EYE_CLOSED, YAWNING, LOOKING_AWAY, ALERT
    }
    private var saveDialogImageView: ImageView? = null

    private var currentState = TrackingState.IDLE
    private var faceDetectionCount = 0
    private var totalFrameCount = 0

    /* ======================= ACTIVITY RESULT CONTRACTS ======================= */

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let { uri ->
            selectedImageUri = uri

            saveDialogImageView?.let { imageView ->
                Glide.with(this)
                    .load(uri)
                    .centerCrop()
                    .into(imageView)
            }
        }
    }

    private val ringtoneLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.let { data ->
            val uri = data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            uri?.let {
                selectedRingtoneUri = it
                ringtone = RingtoneManager.getRingtone(requireContext(), it)
                saveRingtonePreference(it.toString())
                Toast.makeText(requireContext(), "Ringtone selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(
                requireContext(),
                "Camera permission is required for focus tracking",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /* ======================= VIEW ======================= */

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = StudyActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    /* ======================= SETUP ======================= */

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        faceTracker = FaceTracker()
        vibrator = requireContext().getSystemService(VIBRATOR_SERVICE) as Vibrator
        loadSettings()
        loadSavedRingtone()
        setupUI()
    }

    private fun setupUI() {
        binding.btnStart.setOnClickListener {
            startFocusSession()
        }


        binding.btnSelectRingtone.setOnClickListener {
            selectRingtone()
        }
        binding.btnStopSession.setOnClickListener {
            stopSession()
        }
        binding.btnOpenPdf.setOnClickListener {
            showPdfSelectionDialog()
        }
        binding.btnClosePdf.setOnClickListener {
            binding.layoutPdfReader.visibility = View.GONE
        }

        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        // Initialize status
        binding.layoutIdle.visibility = View.VISIBLE
        binding.layoutSession.visibility = View.GONE
        binding.layoutPdfReader.visibility = View.GONE
        updateStatus("Ready to start session", Color.GREEN)
        binding.tvTimer.text = "00:00"
        binding.tvWarningCount.text = "Warnings: 0"
        binding.tvStats.text = "Faces: 0 | Frames: 0"
    }

    /* ======================= FOCUS SESSION ======================= */

    private fun startFocusSession() {
        val minutes = binding.etTimerMinutes.text.toString().toIntOrNull()
        if (minutes == null || minutes <= 0) {
            Toast.makeText(requireContext(), "Enter valid duration (1-240)", Toast.LENGTH_SHORT).show()
            return
        }

        if (minutes > 240) {
            Toast.makeText(requireContext(), "Maximum 240 minutes allowed", Toast.LENGTH_SHORT).show()
            return
        }

        sessionMinutes = minutes
        sessionStartTime = System.currentTimeMillis()
        startCameraPermissionCheck()
        disableNotifications()

        // Reset tracking state
        eyeClosedStartTime = 0L
        mouthOpenStartTime = 0L
        faceNotVisibleStartTime = 0L
        warningCount = 0
        currentState = TrackingState.TRACKING
        faceDetectionCount = 0
        totalFrameCount = 0

        faceTracker.reset()

        //  SWITCH TO SESSION LAYOUT
        binding.layoutIdle.visibility = View.GONE
        binding.layoutSession.visibility = View.VISIBLE

        updateStatus("Tracking active...", Color.BLUE)

        // Start session timer
        startSessionTimer()
    }
    private fun startSessionTimer() {
        lifecycleScope.launch {
            val endTime = sessionStartTime + (sessionMinutes * 60 * 1000L)

            while (System.currentTimeMillis() < endTime && currentState != TrackingState.IDLE) {
                val remaining = endTime - System.currentTimeMillis()
                val minutes = (remaining / 60000).toInt()
                val seconds = ((remaining % 60000) / 1000).toInt()

                withContext(Dispatchers.Main) {
                    binding.tvTimerSession.text = String.format("%02d:%02d", minutes, seconds)
                    binding.tvPdfTimer.text = String.format("%02d:%02d", minutes, seconds)

                }

                delay(1000)
            }

            if (System.currentTimeMillis() >= endTime) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Session completed!", Toast.LENGTH_LONG).show()
                    stopSession()
                }
            }
        }
    }
    private fun stopSession() {
        currentState=TrackingState.IDLE
        cameraProvider?.unbindAll()
        faceDetector?.close()
        ringtone?.stop()
        enableNotifications()
        faceTracker.reset()

        binding.layoutSession.visibility = View.GONE
        binding.layoutPdfReader.visibility = View.GONE
        binding.layoutIdle.visibility = View.VISIBLE

        binding.tvTimerSession.text = "00:00"
        binding.tvPdfTimer.text = "00:00"

        updateStatus("Session ended", Color.GREEN)
        val actualDurationSeconds = (System.currentTimeMillis() - sessionStartTime) / 1000
        showSaveSessionDialog(actualDurationSeconds)
    }

    /* ======================= CAMERA ======================= */

    private fun startCameraPermissionCheck() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .setTargetResolution(android.util.Size(640, 480))
                    .build()
                    .apply {
                        setSurfaceProvider(binding.previewViewSession.surfaceProvider)
                    }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(android.util.Size(640, 480))
                    .build()

                val options = FaceDetectorOptions.Builder()
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setMinFaceSize(0.3f)
                    .build()

                faceDetector = FaceDetection.getClient(options)

                imageAnalysis.setAnalyzer(imageAnalysisExecutor) { imageProxy ->
                    processImageProxy(imageProxy)
                }

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalysis
                )

            } catch (e: Exception) {
                Log.e("Camera", "Failed to start camera", e)
                Toast.makeText(requireContext(), "Camera failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }
    private fun processImageProxy(imageProxy: ImageProxy) {
        totalFrameCount++

        // Skip frames for performance (process every 3rd frame)
        if (totalFrameCount % 3 != 0) {
            imageProxy.close()
            return
        }

        // Use imageProxy.image safely with @Suppress annotation
        @Suppress("UnsafeOptInUsageError")
        val image = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            image,
            imageProxy.imageInfo.rotationDegrees
        )

        faceDetector?.process(inputImage)
            ?.addOnSuccessListener { faces ->
                handleFaces(faces, imageProxy.width, imageProxy.height)
            }
            ?.addOnFailureListener { e ->
                Log.e("FaceDetection", "Detection failed", e)
            }
            ?.addOnCompleteListener {
                imageProxy.close()
            }
    }
    private fun handleFaces(faces: List<com.google.mlkit.vision.face.Face>, imageWidth: Int, imageHeight: Int) {
        if (currentState == TrackingState.IDLE) return

        if (faces.isEmpty()) {
            handleNoFaceDetected()
            return
        }

        faceDetectionCount++
        val face = faces[0] // Use the largest face

        val trackingResult = faceTracker.analyzeFace(face, imageWidth, imageHeight)

        lifecycleScope.launch(Dispatchers.Main) {
            updateTrackingState(trackingResult)
        }
    }
    private fun handleNoFaceDetected() {
        if (faceNotVisibleStartTime == 0L) {
            faceNotVisibleStartTime = System.currentTimeMillis()
        }

        if (System.currentTimeMillis() - faceNotVisibleStartTime >= FACE_NOT_VISIBLE_THRESHOLD) {
            lifecycleScope.launch(Dispatchers.Main) {
                triggerAlarm("Face not detected")
                currentState = TrackingState.LOOKING_AWAY
                updateStatus("Face not visible!", Color.RED)
            }
        }
    }
    private fun updateTrackingState(result: FaceTracker.TrackingResult) {
        when {
            result.isLookingAway -> {
                currentState = TrackingState.LOOKING_AWAY
                updateStatus("Looking away!", Color.YELLOW)
                if (System.currentTimeMillis() - faceNotVisibleStartTime >= FACE_NOT_VISIBLE_THRESHOLD) {
                    triggerAlarm("Looking away too long")
                    currentState = TrackingState.ALERT
                    updateStatus("Focus on screen!", Color.RED)
                    faceNotVisibleStartTime = 0L
                }
            }

            result.isEyeClosed -> {
                eyeClosedStableFrames++

                if (eyeClosedStableFrames >= EYE_STABLE_FRAME_THRESHOLD) {

                    if (eyeClosedStartTime == 0L) {
                        eyeClosedStartTime = System.currentTimeMillis()
                    }

                    if (System.currentTimeMillis() - eyeClosedStartTime >= 800L) {
                        triggerAlarm("Eyes closed too long")
                        eyeClosedStartTime = 0L
                        eyeClosedStableFrames = 0
                    }
                }
            }

            result.isYawning -> {
                if (mouthOpenStartTime == 0L) {
                    mouthOpenStartTime = System.currentTimeMillis()
                    updateStatus("Yawning detected...", Color.YELLOW)
                }

                if (System.currentTimeMillis() - mouthOpenStartTime >= YAWN_TIME_THRESHOLD) {
                    triggerAlarm("Yawning detected")
                    currentState = TrackingState.ALERT
                    updateStatus("Yawning! Stay focused!", Color.RED)
                    mouthOpenStartTime = 0L
                }
            }

            else -> {
                // Reset timers when conditions are normal
                eyeClosedFrameCount = 0
                eyeClosedStartTime = 0L
                eyeClosedStableFrames = 0
                eyeClosedStartTime = 0L
                mouthOpenStartTime = 0L
                faceNotVisibleStartTime = 0L

                currentState = TrackingState.TRACKING
                updateStatus("Tracking active ✓", Color.GREEN)
            }
        }

        // Update stats
        binding.tvSessionFaces.text = faceDetectionCount.toString()
        binding.tvSessionFrames.text = totalFrameCount.toString()
    }

    /* ======================= ALERT SYSTEM ======================= */

    private fun triggerAlarm(reason: String) {
        if (System.currentTimeMillis() - lastAlarmTime < ALARM_COOLDOWN_MS) return

        lastAlarmTime = System.currentTimeMillis()
        warningCount++

        Log.d("Alarm", "Triggered: $reason (Warning #$warningCount)")

        // Flash screen
        flashScreen()

        // Vibrate
        if (ALARM_TYPE != "silent") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(
                        1000,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(1000)
            }
        }
        val audioManager =
            requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()

            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
        // Play ringtone
        if (ALARM_TYPE == "ringtone") {
            ringtone?.play()

            // Auto-stop ringtone after 3 seconds
            Handler(Looper.getMainLooper()).postDelayed({
                ringtone?.stop()
            }, 3000)
        }

        // Update UI
        binding.tvSessionWarnings.text = warningCount.toString()
    }
    private fun flashScreen() {
        binding.flashOverlay.apply {
            visibility = View.VISIBLE
            setBackgroundColor(Color.WHITE)
            alpha = 0.8f

            animate()
                .alpha(0f)
                .setDuration(500)
                .withEndAction {
                    visibility = View.GONE
                }
        }
    }
    private fun updateStatus(message: String, color: Int) {
        binding.tvStatus.text = message
        binding.tvSessionStatus.text=message
        binding.tvSessionStatus.setTextColor(color)
        binding.tvStatus.setTextColor(color)
    }

    /* ======================= SETTINGS ======================= */

    private fun showSettingsDialog() {
        val prefs = requireContext().getSharedPreferences("stay_focus_prefs", 0)

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_setting, null)

        // Initialize UI with current values
        val seekBarSensitivity = dialogView.findViewById<android.widget.SeekBar>(R.id.seekBarSensitivity)
        val tvSensitivityValue = dialogView.findViewById<android.widget.TextView>(R.id.tvSensitivityValue)
        val switchNotifications = dialogView.findViewById<MaterialSwitch>(R.id.switchNotifications)
        val radioGroupAlarm = dialogView.findViewById<android.widget.RadioGroup>(R.id.radioGroupAlarm)

        val sensitivity = (prefs.getFloat("DETECTION_PROB_THRESHOLD", 0.3f) * 100).toInt()
        seekBarSensitivity.progress = sensitivity
        tvSensitivityValue.text = "$sensitivity%"

        seekBarSensitivity.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                tvSensitivityValue.text = "$progress%"
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        val alarmType = prefs.getString("ALARM_TYPE", "ringtone") ?: "ringtone"
        when (alarmType) {
            "ringtone" -> radioGroupAlarm.check(R.id.radioRingtone)
            "vibration" -> radioGroupAlarm.check(R.id.radioVibration)
            "silent" -> radioGroupAlarm.check(R.id.radioSilent)
        }

        val alert =  AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                saveSettings(
                    sensitivity = seekBarSensitivity.progress / 100f,
                    alarmType = when (radioGroupAlarm.checkedRadioButtonId) {
                        R.id.radioRingtone -> "ringtone"
                        R.id.radioVibration -> "vibration"
                        R.id.radioSilent -> "silent"
                        else -> "ringtone"
                    }
                )
                loadSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
        alert.window?.setBackgroundDrawableResource(android.R.color.transparent)
        alert.getButton(AlertDialog.BUTTON_POSITIVE).apply { setTextColor(Color.GREEN) }
        alert.getButton(AlertDialog.BUTTON_NEGATIVE).apply { setTextColor(Color.RED) }
        alert.show()
    }
    private fun saveSettings(sensitivity: Float, alarmType: String) {
        val prefs = requireContext().getSharedPreferences("stay_focus_prefs", 0)
        prefs.edit()
            .putFloat("DETECTION_PROB_THRESHOLD", sensitivity)
            .putString("ALARM_TYPE", alarmType)
            .apply()

        DETECTION_PROB_THRESHOLD = sensitivity
        ALARM_TYPE = alarmType

        Toast.makeText(requireContext(), "Settings saved", Toast.LENGTH_SHORT).show()
    }
    private fun loadSettings() {
        val prefs = requireContext().getSharedPreferences("stay_focus_prefs", 0)

        DETECTION_PROB_THRESHOLD = prefs.getFloat("DETECTION_PROB_THRESHOLD", 0.3f)
        val cooldownSec = prefs.getInt("ALARM_COOLDOWN_SEC", 5)
        ALARM_COOLDOWN_MS = (cooldownSec * 1000L).coerceAtLeast(1000L)
        ALARM_TYPE = prefs.getString("ALARM_TYPE", "ringtone") ?: "ringtone"
    }

    /* ======================= RINGTONE SELECTION ======================= */

    private fun selectRingtone() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Tone")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)

            selectedRingtoneUri?.let {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it)
            }
        }

        ringtoneLauncher.launch(intent)
    }
    private fun saveRingtonePreference(uriString: String) {
        val prefs = requireContext().getSharedPreferences("stay_focus_prefs", 0)
        prefs.edit().putString("RINGTONE_URI", uriString).apply()
    }
    private fun loadSavedRingtone() {
        val uriString = requireContext()
            .getSharedPreferences("stay_focus_prefs", 0)
            .getString("RINGTONE_URI", null)

        val uri = if (uriString != null) {
            Uri.parse(uriString)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }

        ringtone = RingtoneManager.getRingtone(requireContext(), uri)

        ringtone?.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    /* ======================= SAVE SESSION ======================= */

    private fun showSaveSessionDialog(actualDurationSeconds: Long) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_save_session, null)

        val etTitle = dialogView.findViewById<android.widget.EditText>(R.id.etTitle)
        val etDesc = dialogView.findViewById<android.widget.EditText>(R.id.etDescription)
        val ivCover = dialogView.findViewById<android.widget.ImageView>(R.id.ivCover)
        saveDialogImageView = ivCover
        val btnSelectImage = dialogView.findViewById<android.widget.Button>(R.id.btnSelectImage)
        val tvDuration = dialogView.findViewById<android.widget.TextView>(R.id.tvDuration)
        val tvWarnings = dialogView.findViewById<android.widget.TextView>(R.id.tvWarnings)
        val btnSave = dialogView.findViewById<android.widget.Button>(R.id.btnSave)

        // Set default title with date
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        etTitle.setText("Focus Session ${dateFormat.format(Date())}")

        // Set session stats
        val minutesPart = actualDurationSeconds / 60
        val secondsPart = actualDurationSeconds % 60
        tvDuration.text = "${minutesPart}m ${secondsPart}s"
        tvWarnings.text = warningCount.toString()

        // Load default image or selected image
        selectedImageUri?.let { uri ->
            Glide.with(requireContext())
                .load(uri)
                .centerCrop()
                .into(ivCover)
        }

        btnSelectImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
            }
            galleryLauncher.launch(intent)
        }

        val alertDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Make dialog background transparent
        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnSave.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val description = etDesc.text.toString().trim()

            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a title", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedImageUri == null) {
                Toast.makeText(requireContext(), "Please select a cover image", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveSession(
                imageUri = selectedImageUri!!,
                title = title,
                description = description,
                actualDurationSeconds = actualDurationSeconds,
                warnings = warningCount
            )

            alertDialog.dismiss()
        }

        alertDialog.show()
    }
    private fun showPdfSelectionDialog() {

        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.select_pdf_reader, null)
        dialog.setContentView(view)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewPdfs)
        val tvCount = view.findViewById<TextView>(R.id.tvPdfCount)
        val emptyState = view.findViewById<View>(R.id.emptyState)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        val btnClose = view.findViewById<View>(R.id.btnClose)

        val pdfList = mutableListOf<PdfModel>()

        val adapter = PDFpickerAdapter { pdf ->
            dialog.dismiss()
            openPdfInReader(pdf.url, pdf.name)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        btnClose.setOnClickListener { dialog.dismiss() }

        etSearch.addTextChangedListener {
            adapter.filter(it.toString())
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("User")
            .document(uid)
            .collection("PDFs")
            .get()
            .addOnSuccessListener { snapshot ->

                pdfList.clear()

                for (doc in snapshot.documents) {
                    pdfList.add(
                        PdfModel(
                            id = doc.id,
                            name = doc.getString("pdfName") ?: "",
                            url = doc.getString("pdfUrl") ?: "",
                            uploadedAt = doc.getLong("uploadedAt") ?: 0L
                        )
                    )
                }

                adapter.updateData(pdfList)

                tvCount.text = "${pdfList.size} documents"

                emptyState.visibility =
                    if (pdfList.isEmpty()) View.VISIBLE else View.GONE
                recyclerView.visibility =
                    if (pdfList.isEmpty()) View.GONE else View.VISIBLE
            }

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
    private fun openPdfInReader(url: String, title: String) {

        binding.layoutPdfReader.visibility = View.VISIBLE
        binding.tvPdfTitle.text = title

        lifecycleScope.launch(Dispatchers.IO) {
            try {

                val inputStream = java.net.URL(url).openStream()

                withContext(Dispatchers.Main) {

                    binding.pdfView.fromStream(inputStream)
                        .enableSwipe(true)
                        .swipeHorizontal(false)
                        .enableDoubletap(true)
                        .onPageChange { page, pageCount ->
                            binding.tvPdfPageInfo.text = "Page ${page + 1} / $pageCount"
                        }
                        .load()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to load PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun saveSession(imageUri: Uri, title: String, description: String, actualDurationSeconds: Long, warnings: Int) {
        // Show progress overlay
        binding.progressOverlay.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // Upload image to Cloudinary
                val filePart = uriToMultipart(imageUri)
                val preset = "TaskVaultSession".toRequestBody("text/plain".toMediaType())

                val response = CloudinaryClient.api.uploadImage(filePart, preset)
                val plannedDurationSeconds = sessionMinutes * 60

                val status =
                    if (actualDurationSeconds >= plannedDurationSeconds)
                        "Completed"
                    else
                        "Incomplete"

                // Save to Firestore
                saveSessionToFirestore(
                    title = title,
                    description = description,
                    imageUrl = response.secureUrl,
                    durationMinutes = actualDurationSeconds,
                    warnings = warnings,
                    status=status
                )

                withContext(Dispatchers.Main) {
                    binding.progressOverlay.visibility = View.GONE
                    Toast.makeText(
                        requireContext(),
                        "Session saved successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressOverlay.visibility = View.GONE
                    Toast.makeText(
                        requireContext(),
                        "Failed to save session: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("SaveSession", "Error", e)
                }
            }
        }
    }
    private fun saveSessionToFirestore(title: String, description: String, imageUrl: String, durationMinutes: Long, warnings: Int,status:String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val session = hashMapOf(
            "title" to title,
            "description" to description,
            "coverImageUrl" to imageUrl,
            "durationMinutes" to durationMinutes,
            "warnings" to warnings,
            "status" to status,
            "userId" to userId,
            "timestamp" to System.currentTimeMillis(),
            "sessionDate" to System.currentTimeMillis()
        )

        FirebaseFirestore.getInstance()
            .collection("User")
            .document(userId)
            .collection("Sessions")
            .add(session)
            .addOnSuccessListener {
                Log.d("Firestore", "Session saved with ID: ${it.id}")
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error saving session", e)
                throw e
            }
    }
    private fun uriToMultipart(uri: Uri): MultipartBody.Part {
        val inputStream = requireContext().contentResolver.openInputStream(uri)!!
        val file = File(requireContext().cacheDir, "upload_${System.currentTimeMillis()}.jpg")

        file.outputStream().use { output ->
            inputStream.copyTo(output)
        }

        val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())

        return MultipartBody.Part.createFormData(
            "file",
            file.name,
            requestFile
        )
    }

    /* ======================= NOTIFICATIONS ======================= */

    private fun disableNotifications() {
        val nm = requireContext().getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
            } else {
                askForDndPermission()
            }
        }
    }
    private fun enableNotifications() {
        val nm = requireContext().getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        }
    }
    private fun askForDndPermission() {
        AlertDialog.Builder(requireContext())
            .setTitle("Do Not Disturb Access Required")
            .setMessage("Please allow Do Not Disturb access to block notifications during focus sessions")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
            .setNegativeButton("Later", null)
            .show()
    }

    /* ======================= CLEANUP ======================= */

    override fun onDestroyView() {
        super.onDestroyView()
        cameraProvider?.unbindAll()
        faceDetector?.close()
        cameraExecutor.shutdown()
        imageAnalysisExecutor.shutdown()
        mediaPlayer?.release()
        ringtone?.stop()
        _binding = null
    }
}