package com.example.kronos_

import android.app.Application
import com.example.kronos_.ui.ThemePreference

class KronosApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ThemePreference.applySaved(this)
    }
}
