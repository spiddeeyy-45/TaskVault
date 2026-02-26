package com.example.taskvault

import Adapters.OnBoardSlideAdapter
import DataClass.OnBoardSlide
import LoginHandler.Login
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.viewpager2.widget.ViewPager2
import com.example.taskvault.databinding.OnBoardingScreenBinding
import kotlin.math.abs

class on_boarding_screen : AppCompatActivity() {

    private lateinit var binding: OnBoardingScreenBinding
    private lateinit var slideAdapter: OnBoardSlideAdapter
    private lateinit var slideList: List<OnBoardSlide>
    private lateinit var dots: Array<TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // Check if onboarding already seen
        val prefs = getSharedPreferences("TaskVaultPrefs", MODE_PRIVATE)
        if (prefs.getBoolean("onboarding_seen", false)) {
            startActivity(Intent(this, Login::class.java))
            finish()
            return
        }

        binding = DataBindingUtil.setContentView(this, R.layout.on_boarding_screen)

        setupSlides()
        setupViewPager()
        setupPageTransformer()
        setupListeners()
    }

    private fun setupSlides() {
        slideList = listOf(

            OnBoardSlide(
                R.raw.load,
                "UPLOAD",
                "Store Your Study Resources",
                "Upload PDFs, notes, and important documents securely so you can access them anytime, anywhere."
            ),

            OnBoardSlide(
                R.raw.search,
                "DISCOVER",
                "Find & Connect with Users",
                "Search for other users, explore their profiles, and connect to build your productivity circle."
            ),

            OnBoardSlide(
                R.raw.chat,
                "COLLABORATE",
                "Chat & Share Progress",
                "Talk with friends in real time, discuss tasks, and stay aligned on shared goals."
            ),

            OnBoardSlide(
                R.raw.focus_session,
                "FOCUS",
                "Deep Focus Sessions",
                "Start distraction-free focus sessions and track your productivity to stay consistent every day."
            )
        )

        slideAdapter = OnBoardSlideAdapter(slideList)
        binding.onboardimages.adapter = slideAdapter
    }

    private fun setupViewPager() {

        dots = Array(slideList.size) { TextView(this) }

        binding.onboardimages.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateDots(position)
                    updateBottomUI(position)
                }
            }
        )

        updateDots(0)
        updateBottomUI(0)
    }

    private fun setupPageTransformer() {

        binding.onboardimages.setPageTransformer { page, position ->

            val scaleFactor = 0.85f + (1 - abs(position)) * 0.15f
            page.scaleX = scaleFactor
            page.scaleY = scaleFactor

            page.alpha = 0.5f + (1 - abs(position)) * 0.5f
        }
    }

    private fun updateDots(position: Int) {

        binding.dots.removeAllViews()

        for (i in slideList.indices) {

            val dot = TextView(this).apply {
                text = "•"
                textSize = if (i == position) 38f else 28f

                setTextColor(
                    if (i == position)
                        ContextCompat.getColor(this@on_boarding_screen, android.R.color.holo_green_dark)
                    else
                        ContextCompat.getColor(this@on_boarding_screen, android.R.color.darker_gray)
                )

                animate().scaleX(if (i == position) 1.3f else 1f)
                    .scaleY(if (i == position) 1.3f else 1f)
                    .setDuration(250)
                    .start()
            }

            binding.dots.addView(dot)
        }
    }

    private fun updateBottomUI(position: Int) {

        binding.tvPageCount.text = "${position + 1} / ${slideList.size}"

        if (position == slideList.size - 1) {
            binding.tvButtonLabel.text = "Let's Get Started"
            binding.nexticon.alpha = 0f
        } else {
            binding.tvButtonLabel.text = "Next"
            binding.nexticon.alpha = 0.6f
        }
    }

    private fun setupListeners() {

        binding.getstartedbutton.setOnClickListener {

            val current = binding.onboardimages.currentItem

            if (current < slideList.size - 1) {
                binding.onboardimages.currentItem = current + 1
            } else {
                finishOnboarding()
            }
        }

        binding.tvSkip.setOnClickListener {
            finishOnboarding()
        }
    }

    private fun finishOnboarding() {

        val prefs = getSharedPreferences("TaskVaultPrefs", MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_seen", true).apply()

        startActivity(Intent(this, Login::class.java))
        finish()
    }
}