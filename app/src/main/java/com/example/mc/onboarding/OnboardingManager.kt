package com.example.mc.onboarding

import android.content.Context
import android.content.SharedPreferences

object OnboardingManager {

    private const val PREFS_NAME = "OnboardingPrefs"
    private const val KEY_ONBOARDING_COMPLETED = "onboardingCompleted"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isFirstTimeLaunch(context: Context): Boolean {
        return !getPreferences(context).getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(context: Context) {
        getPreferences(context).edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
    }

    fun resetOnboarding(context: Context) {
        getPreferences(context).edit().remove(KEY_ONBOARDING_COMPLETED).apply()
    }
}