package com.example.mc

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.mc.home_fragment.HomeFragment
import com.example.mc.imageToPdf_fragment.ImageToPdfFragment
import com.example.mc.tts_fragment.TextToSpeechFragment

private const val NUM_TABS = 3

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = NUM_TABS

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeFragment()
            1 -> ImageToPdfFragment()
            2 -> TextToSpeechFragment()
            else -> throw IllegalStateException("Invalid position $position")
        }
    }
}
