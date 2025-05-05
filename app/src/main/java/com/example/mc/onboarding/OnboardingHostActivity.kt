package com.example.mc.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.mc.MainActivity
import com.example.mc.databinding.ActivityOnboardingHostBinding

class OnboardingHostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingHostBinding
    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: OnboardingPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewPager = binding.onboardingViewPager
        pagerAdapter = OnboardingPagerAdapter(this)
        viewPager.adapter = pagerAdapter

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewPager.currentItem == 0) {
                    finish()
                } else {
                    viewPager.currentItem = viewPager.currentItem - 1
                }
            }
        })
    }

    fun navigateToNextPage() {
        if (viewPager.currentItem < pagerAdapter.itemCount - 1) {
            viewPager.currentItem = viewPager.currentItem + 1
        }
    }

    fun finishOnboarding() {
        OnboardingManager.setOnboardingCompleted(this)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)

        finish()
    }
}
