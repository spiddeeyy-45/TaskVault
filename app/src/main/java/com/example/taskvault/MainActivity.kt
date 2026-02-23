package com.example.taskvault

import Fragments.study_activity
import LoginHandler.Login
import UI.home_activity
import UI.setting_activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalGetImage
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.example.taskvault.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var statusRef: com.google.firebase.database.DatabaseReference
    private var connectionListener: ValueEventListener? = null
    private lateinit var connectedRef: DatabaseReference

    @OptIn(ExperimentalGetImage::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        // Load Home fragment only once
        if (savedInstanceState == null) {
            loadFragment(home_activity())
            setSelectedTab(Tab.HOME)
        }
        setupPresence()

        // Custom bottom bar clicks
        binding.navHome.setOnClickListener {
            loadFragment(home_activity())
            setSelectedTab(Tab.HOME)
        }

        binding.navMonitor.setOnClickListener {
            loadFragment(study_activity())
            setSelectedTab(Tab.MONITOR)
        }

        binding.navSetting.setOnClickListener {
            loadFragment(setting_activity())
            setSelectedTab(Tab.SETTING)
        }
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d("FCM_TOKEN", token)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.FragmentContainer, fragment)
            .commit()
    }

    // Simple state handling for UI
    private fun setSelectedTab(tab: Tab) {
        binding.iconHome.isSelected = tab == Tab.HOME
        binding.hometext.isSelected = tab ==Tab.HOME
        binding.iconMonitor.isSelected = tab == Tab.MONITOR
        binding.textmonitor.isSelected = tab ==Tab.MONITOR
        binding.iconSetting.isSelected = tab == Tab.SETTING
        binding.settingtext.isSelected = tab == Tab.SETTING
    }
    private fun setupPresence() {
        Log.d("PRESENCE_DEBUG", "setupPresence called")
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        Log.d("PRESENCE_DEBUG", "UID: $uid")
        val database = FirebaseDatabase.getInstance()
        statusRef = database.getReference("status/$uid")
        connectedRef = database.getReference(".info/connected")

        Log.d("PRESENCE_DEBUG", "Connected: $connectedRef")

        connectionListener = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                val connected = snapshot.getValue(Boolean::class.java) ?: false

                if (connected) {

                    val onlineStatus = mapOf(
                        "state" to "online",
                        "lastChanged" to ServerValue.TIMESTAMP
                    )

                    val offlineStatus = mapOf(
                        "state" to "offline",
                        "lastChanged" to ServerValue.TIMESTAMP
                    )

                    statusRef.setValue(onlineStatus)
                    statusRef.onDisconnect().setValue(offlineStatus)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        connectedRef.addValueEventListener(connectionListener!!)
    }

    enum class Tab {
        HOME, MONITOR, SETTING
    }
    override fun onDestroy() {
        super.onDestroy()
        connectionListener?.let {
            connectedRef.removeEventListener(it)
        }
    }

}
